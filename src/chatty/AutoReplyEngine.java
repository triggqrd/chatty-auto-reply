package chatty;

import chatty.AutoReplyManager.AutoReplyConfig;
import chatty.AutoReplyManager.AutoReplyProfile;
import chatty.AutoReplyManager.AutoReplyTrigger;
import chatty.AutoReplyManager.PatternType;
import chatty.gui.notifications.NotificationManager;
import chatty.util.Sound;
import chatty.util.StringUtil;
import chatty.util.irc.MsgTags;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Runtime engine that observes chat messages and dispatches automatic replies
 * according to {@link AutoReplyManager} configuration.
 */
public class AutoReplyEngine implements AutoReplyManager.Listener {

    private static final Logger LOGGER = Logger.getLogger(AutoReplyEngine.class.getName());
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_]{3,25})");
    private static final long DEFAULT_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(1);

    private final TwitchClient client;
    private final AutoReplyManager manager;
    private final NotificationManager notificationManager;
    private final ScheduledExecutorService scheduler;

    private final Object lock = new Object();
    private final Map<String, TriggerState> triggerStates = new HashMap<>();
    private final Deque<QueuedReply> pendingReplies = new ArrayDeque<>();

    private volatile AutoReplyConfig currentConfig;
    private volatile boolean shutdown;
    private ScheduledFuture<?> queueTask;
    private long globalCooldownUntil;

    public AutoReplyEngine(TwitchClient client, AutoReplyManager manager,
            NotificationManager notificationManager) {
        this.client = Objects.requireNonNull(client);
        this.manager = Objects.requireNonNull(manager);
        this.notificationManager = Objects.requireNonNull(notificationManager);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AutoReplyEngine");
            t.setDaemon(true);
            return t;
        });
        this.currentConfig = manager.getConfig();
        rebuildStates(currentConfig);
        manager.addListener(this);
    }

    @Override
    public void autoReplyConfigChanged(AutoReplyConfig config) {
        currentConfig = config;
        rebuildStates(config);
        clearPendingReplies();
    }

    public void onChatMessage(User user, String text, boolean action, MsgTags tags) {
        if (shutdown) {
            return;
        }
        if (user == null || StringUtil.isNullOrEmpty(text)) {
            return;
        }
        if (tags != null && tags.isHistoricMsg()) {
            return;
        }

        AutoReplyConfig config = currentConfig;
        if (config == null) {
            return;
        }
        if (config.isSelfIgnore() && client.isOwnUsername(user.getName())) {
            return;
        }

        long now = System.currentTimeMillis();
        flushQueueIfReady(now);

        AutoReplyProfile profile = resolveActiveProfile(config);
        if (profile == null) {
            return;
        }

        MessageContext context = new MessageContext(user, text, tags, client.getUsername());
        String textLower = text.toLowerCase(Locale.ENGLISH);
        String authorLower = user.getName().toLowerCase(Locale.ENGLISH);

        for (AutoReplyTrigger trigger : profile.getTriggers()) {
            TriggerState state = getState(trigger);
            if (state == null || !state.isPatternActive()) {
                continue;
            }
            if (!state.matches(text, textLower)) {
                continue;
            }
            if (!state.isAuthorAllowed(authorLower)) {
                continue;
            }

            state.recordMatch(authorLower, context.category, now, trigger);
            if (!state.canTrigger(now, trigger)) {
                continue;
            }
            if (!state.meetsUniqueRequirement(trigger)) {
                continue;
            }
            if (!state.meetsPerUserRequirement(authorLower, context.category, trigger)) {
                continue;
            }

            String reply = state.selectReply(authorLower, trigger);
            if (StringUtil.isNullOrEmpty(reply)) {
                continue;
            }

            QueuedReply immediate = scheduleReply(state, trigger, context, reply, now);
            if (immediate != null) {
                dispatch(immediate, now);
                scheduleNextFromQueue(now);
            }
        }
    }

    public void shutdown() {
        shutdown = true;
        manager.removeListener(this);
        clearPendingReplies();
        scheduler.shutdownNow();
    }

    private AutoReplyProfile resolveActiveProfile(AutoReplyConfig config) {
        if (config == null) {
            return null;
        }
        String id = config.getActiveProfileId();
        if (StringUtil.isNullOrEmpty(id) || "default".equalsIgnoreCase(id)) {
            return null;
        }
        for (AutoReplyProfile profile : config.getProfiles()) {
            if (profile.getId().equalsIgnoreCase(id)) {
                return profile;
            }
        }
        if (!config.getProfiles().isEmpty()) {
            return config.getProfiles().get(0);
        }
        return null;
    }

    private void rebuildStates(AutoReplyConfig config) {
        if (config == null) {
            return;
        }
        synchronized (lock) {
            Set<String> validIds = new HashSet<>();
            for (AutoReplyProfile profile : config.getProfiles()) {
                for (AutoReplyTrigger trigger : profile.getTriggers()) {
                    TriggerState state = triggerStates.computeIfAbsent(trigger.getId(),
                            id -> new TriggerState());
                    state.updateFrom(trigger);
                    validIds.add(trigger.getId());
                }
            }
            triggerStates.keySet().retainAll(validIds);
        }
    }

    private TriggerState getState(AutoReplyTrigger trigger) {
        synchronized (lock) {
            return triggerStates.get(trigger.getId());
        }
    }

    private QueuedReply scheduleReply(TriggerState state, AutoReplyTrigger trigger,
            MessageContext context, String reply, long now) {
        QueuedReply immediate = null;
        long cooldownMillis = getGlobalCooldownMillis();
        PendingReply pending = new PendingReply(trigger, context.user.getChannel(), reply,
                context.user.getName(), context.originalMessage, context.category);
        synchronized (lock) {
            state.onReplyScheduled(now);
            if (pendingReplies.isEmpty() && now >= globalCooldownUntil) {
                globalCooldownUntil = cooldownMillis > 0 ? now + cooldownMillis : now;
                immediate = new QueuedReply(pending);
            }
            else {
                pendingReplies.addLast(new QueuedReply(pending));
                ensureQueueScheduled(now);
            }
        }
        return immediate;
    }

    private void dispatch(QueuedReply entry, long now) {
        if (shutdown) {
            return;
        }
        boolean sent = client.sendAutoReply(entry.pending.channel, entry.pending.reply);
        if (!sent) {
            LOGGER.log(Level.FINE, "Auto reply not sent for channel {0}", entry.pending.channel);
            return;
        }
        handleNotification(entry.pending);
        handleSound(entry.pending);
    }

    private void handleNotification(PendingReply pending) {
        boolean enabled = pending.trigger.isNotificationEnabled();
        if (!enabled) {
            return;
        }
        String message = String.format("%s -> %s", pending.author, pending.reply);
        notificationManager.commandNotification(pending.channel,
                "[Auto Reply] %s", message, false, true);
    }

    private void handleSound(PendingReply pending) {
        String sound = pending.trigger.getSound();
        AutoReplyConfig config = currentConfig;
        if (StringUtil.isNullOrEmpty(sound) && config != null) {
            sound = config.getDefaultSound();
        }
        if (StringUtil.isNullOrEmpty(sound) || "off".equalsIgnoreCase(sound)) {
            return;
        }
        try {
            Path path = Chatty.getPath(Chatty.PathType.SOUND).resolve(sound);
            Sound.play(path, 20f, "autoReply_" + pending.trigger.getId(), 0);
        }
        catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to play auto reply sound {0}: {1}",
                    new Object[]{sound, ex.getMessage()});
        }
    }

    private void flushQueueIfReady(long now) {
        synchronized (lock) {
            if (pendingReplies.isEmpty()) {
                return;
            }
            if (now < globalCooldownUntil) {
                return;
            }
            if (queueTask == null || queueTask.isDone()) {
                queueTask = scheduler.schedule(this::processQueue, 0, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void ensureQueueScheduled(long now) {
        if (shutdown) {
            return;
        }
        synchronized (lock) {
            if (pendingReplies.isEmpty()) {
                return;
            }
            if (queueTask != null && !queueTask.isDone()) {
                return;
            }
            long delay = Math.max(0, globalCooldownUntil - now);
            queueTask = scheduler.schedule(this::processQueue, delay, TimeUnit.MILLISECONDS);
        }
    }

    private void scheduleNextFromQueue(long now) {
        synchronized (lock) {
            if (pendingReplies.isEmpty()) {
                if (queueTask != null) {
                    queueTask.cancel(false);
                    queueTask = null;
                }
                return;
            }
            long delay = Math.max(0, globalCooldownUntil - now);
            if (queueTask != null) {
                queueTask.cancel(false);
            }
            queueTask = scheduler.schedule(this::processQueue, delay, TimeUnit.MILLISECONDS);
        }
    }

    private void processQueue() {
        if (shutdown) {
            return;
        }
        QueuedReply next;
        long now = System.currentTimeMillis();
        long cooldownMillis = getGlobalCooldownMillis();
        synchronized (lock) {
            if (pendingReplies.isEmpty()) {
                queueTask = null;
                return;
            }
            if (now < globalCooldownUntil) {
                long delay = globalCooldownUntil - now;
                queueTask = scheduler.schedule(this::processQueue, delay, TimeUnit.MILLISECONDS);
                return;
            }
            next = pendingReplies.pollFirst();
            globalCooldownUntil = cooldownMillis > 0 ? now + cooldownMillis : now;
        }
        dispatch(next, now);
        scheduleNextFromQueue(now);
    }

    private void clearPendingReplies() {
        synchronized (lock) {
            pendingReplies.clear();
            globalCooldownUntil = 0;
            if (queueTask != null) {
                queueTask.cancel(false);
                queueTask = null;
            }
        }
    }

    private long getGlobalCooldownMillis() {
        AutoReplyConfig config = currentConfig;
        if (config == null) {
            return 0;
        }
        return Math.max(0, config.getGlobalCooldown()) * 1000L;
    }

    private static long windowMillisFor(AutoReplyTrigger trigger) {
        long cooldown = Math.max(0, trigger.getCooldown());
        return Math.max(DEFAULT_WINDOW_MILLIS, TimeUnit.SECONDS.toMillis(cooldown));
    }

    private static Set<String> extractMentions(String text) {
        Set<String> result = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(text);
        while (matcher.find()) {
            result.add(matcher.group(1).toLowerCase(Locale.ENGLISH));
        }
        return result;
    }

    private enum MentionCategory {
        GENERAL,
        DIRECT,
        RECIPIENT
    }

    private static class MessageContext {

        final User user;
        final String originalMessage;
        final MentionCategory category;

        MessageContext(User user, String text, MsgTags tags, String selfName) {
            this.user = user;
            this.originalMessage = text;
            Set<String> mentions = extractMentions(text);
            String selfLower = selfName == null ? null : selfName.toLowerCase(Locale.ENGLISH);
            if (selfLower != null && mentions.contains(selfLower)) {
                this.category = MentionCategory.DIRECT;
            }
            else if (!mentions.isEmpty()) {
                this.category = MentionCategory.RECIPIENT;
            }
            else {
                this.category = MentionCategory.GENERAL;
            }
        }
    }

    private static class PendingReply {

        final AutoReplyTrigger trigger;
        final String channel;
        final String reply;
        final String author;
        final String originalMessage;
        final MentionCategory category;

        PendingReply(AutoReplyTrigger trigger, String channel, String reply,
                String author, String originalMessage, MentionCategory category) {
            this.trigger = trigger;
            this.channel = channel;
            this.reply = reply;
            this.author = author;
            this.originalMessage = originalMessage;
            this.category = category;
        }
    }

    private static class QueuedReply {

        final PendingReply pending;

        QueuedReply(PendingReply pending) {
            this.pending = pending;
        }
    }

    private static class TriggerState {

        private AutoReplyTrigger trigger;
        private String plainPatternLower;
        private Pattern regexPattern;
        private boolean patternValid;
        private Set<String> allowAuthors = new HashSet<>();
        private Set<String> blockAuthors = new HashSet<>();
        private Map<String, String> overrides = new HashMap<>();
        private List<String> replyOptions = new ArrayList<>();
        private final Map<String, AuthorCounters> authorCounters = new HashMap<>();
        private long lastTriggeredAt;

        void updateFrom(AutoReplyTrigger trigger) {
            this.trigger = trigger;
            compilePattern(trigger);
            updateAuthors(trigger);
            updateOverrides(trigger);
            updateReplies(trigger);
        }

        private void compilePattern(AutoReplyTrigger trigger) {
            String pattern = trigger.getPattern();
            if (StringUtil.isNullOrEmpty(pattern)) {
                patternValid = false;
                plainPatternLower = null;
                regexPattern = null;
                return;
            }
            if (trigger.getPatternType() == PatternType.REGEX) {
                try {
                    regexPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                    plainPatternLower = null;
                    patternValid = true;
                }
                catch (PatternSyntaxException ex) {
                    patternValid = false;
                    regexPattern = null;
                    LOGGER.log(Level.WARNING, "Invalid auto reply regex pattern {0}: {1}",
                            new Object[]{pattern, ex.getMessage()});
                }
            }
            else {
                plainPatternLower = pattern.toLowerCase(Locale.ENGLISH);
                regexPattern = null;
                patternValid = true;
            }
        }

        private void updateAuthors(AutoReplyTrigger trigger) {
            allowAuthors = new HashSet<>();
            for (String entry : trigger.getAllowAuthors()) {
                if (!StringUtil.isNullOrEmpty(entry)) {
                    allowAuthors.add(entry.toLowerCase(Locale.ENGLISH));
                }
            }
            blockAuthors = new HashSet<>();
            for (String entry : trigger.getBlockAuthors()) {
                if (!StringUtil.isNullOrEmpty(entry)) {
                    blockAuthors.add(entry.toLowerCase(Locale.ENGLISH));
                }
            }
        }

        private void updateOverrides(AutoReplyTrigger trigger) {
            overrides = new HashMap<>();
            for (Map.Entry<String, String> entry : trigger.getAuthorOverrides().entrySet()) {
                String key = entry.getKey();
                if (!StringUtil.isNullOrEmpty(key)) {
                    overrides.put(key.toLowerCase(Locale.ENGLISH), entry.getValue());
                }
            }
        }

        private void updateReplies(AutoReplyTrigger trigger) {
            replyOptions = parseReplyOptions(trigger.getReply());
        }

        boolean isPatternActive() {
            return patternValid;
        }

        boolean matches(String text, String textLower) {
            if (!patternValid) {
                return false;
            }
            if (regexPattern != null) {
                return regexPattern.matcher(text).find();
            }
            if (plainPatternLower != null) {
                return textLower.contains(plainPatternLower);
            }
            return false;
        }

        boolean isAuthorAllowed(String authorLower) {
            if (!allowAuthors.isEmpty() && !allowAuthors.contains(authorLower)) {
                return false;
            }
            return !blockAuthors.contains(authorLower);
        }

        void recordMatch(String authorLower, MentionCategory category, long now,
                AutoReplyTrigger trigger) {
            pruneCounters(now, trigger);
            AuthorCounters counters = authorCounters.computeIfAbsent(authorLower,
                    key -> new AuthorCounters());
            counters.add(category, now);
        }

        boolean canTrigger(long now, AutoReplyTrigger trigger) {
            long cooldown = TimeUnit.SECONDS.toMillis(Math.max(0, trigger.getCooldown()));
            if (cooldown <= 0) {
                return true;
            }
            return now - lastTriggeredAt >= cooldown;
        }

        boolean meetsUniqueRequirement(AutoReplyTrigger trigger) {
            int minUnique = trigger.getMinUniqueUsers();
            if (minUnique <= 0) {
                return true;
            }
            int count = 0;
            for (AuthorCounters counters : authorCounters.values()) {
                if (!counters.isEmpty()) {
                    count++;
                }
                if (count >= minUnique) {
                    return true;
                }
            }
            return false;
        }

        boolean meetsPerUserRequirement(String authorLower, MentionCategory category,
                AutoReplyTrigger trigger) {
            int minMentions = trigger.getMinMentionsPerUser();
            if (minMentions <= 0) {
                return true;
            }
            AuthorCounters counters = authorCounters.get(authorLower);
            if (counters == null) {
                return false;
            }
            return counters.getCount(category) >= minMentions;
        }

        String selectReply(String authorLower, AutoReplyTrigger trigger) {
            String override = overrides.get(authorLower);
            if (!StringUtil.isNullOrEmpty(override)) {
                return override;
            }
            if (replyOptions.isEmpty()) {
                return trigger.getReply();
            }
            if (replyOptions.size() == 1) {
                return replyOptions.get(0);
            }
            return replyOptions.get(ThreadLocalRandom.current().nextInt(replyOptions.size()));
        }

        void onReplyScheduled(long timestamp) {
            lastTriggeredAt = timestamp;
        }

        private void pruneCounters(long now, AutoReplyTrigger trigger) {
            long cutoff = now - windowMillisFor(trigger);
            for (Iterator<Map.Entry<String, AuthorCounters>> it = authorCounters.entrySet().iterator();
                    it.hasNext();) {
                Map.Entry<String, AuthorCounters> entry = it.next();
                entry.getValue().prune(cutoff);
                if (entry.getValue().isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    private static class AuthorCounters {

        private final EnumMap<MentionCategory, Deque<Long>> categoryCounts =
                new EnumMap<>(MentionCategory.class);
        private final Deque<Long> allTimes = new ArrayDeque<>();

        AuthorCounters() {
            for (MentionCategory category : MentionCategory.values()) {
                categoryCounts.put(category, new ArrayDeque<>());
            }
        }

        void add(MentionCategory category, long timestamp) {
            allTimes.addLast(timestamp);
            categoryCounts.get(category).addLast(timestamp);
        }

        void prune(long cutoff) {
            pruneDeque(allTimes, cutoff);
            for (Deque<Long> deque : categoryCounts.values()) {
                pruneDeque(deque, cutoff);
            }
        }

        private void pruneDeque(Deque<Long> deque, long cutoff) {
            while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
                deque.pollFirst();
            }
        }

        boolean isEmpty() {
            return allTimes.isEmpty();
        }

        int getCount(MentionCategory category) {
            Deque<Long> deque = categoryCounts.get(category);
            return deque == null ? 0 : deque.size();
        }
    }

    private static List<String> parseReplyOptions(String reply) {
        List<String> result = new ArrayList<>();
        if (StringUtil.isNullOrEmpty(reply)) {
            return result;
        }
        String[] groups = reply.split("(?:\\r?\\n){2,}");
        for (String group : groups) {
            String trimmed = group.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        if (result.isEmpty()) {
            String trimmed = reply.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}

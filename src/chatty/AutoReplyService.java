package chatty;

import chatty.AutoReplyManager.AutoReplyConfig;
import chatty.AutoReplyManager.AutoReplyProfile;
import chatty.AutoReplyManager.AutoReplyTrigger;
import chatty.AutoReplyManager.PatternType;
import chatty.Chatty;
import chatty.Chatty.PathType;
import chatty.gui.MainGui;
import chatty.util.StringUtil;
import chatty.util.irc.MsgTags;
import chatty.util.Sound;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.nio.file.Path;

/**
 * Runtime component that evaluates {@link AutoReplyTrigger}s for incoming chat
 * messages and dispatches the configured replies.
 */
public class AutoReplyService implements AutoReplyManager.Listener {

    private static final Logger LOGGER = Logger.getLogger(AutoReplyService.class.getName());

    /**
     * Minimum global cooldown enforced across all rules (seconds).
     */
    private static final long COOLDOWN_SEC = 2L;

    private final Object lock = new Object();
    private final TwitchClient client;
    private final MainGui gui;
    private final AutoReplyManager manager;

    private final Map<String, TriggerState> stateById = new HashMap<>();
    private List<PreparedTrigger> triggers = Collections.emptyList();

    private boolean selfIgnore = true;
    private boolean defaultNotification;
    private String defaultSound;
    private long globalCooldownMillis = 0L;
    private long nextGlobalAvailable = 0L;

    public AutoReplyService(TwitchClient client, MainGui gui, AutoReplyManager manager) {
        this.client = Objects.requireNonNull(client);
        this.gui = Objects.requireNonNull(gui);
        this.manager = Objects.requireNonNull(manager);
        this.manager.addListener(this);
        updateConfig(manager.getConfig());
    }

    @Override
    public void autoReplyConfigChanged(AutoReplyConfig config) {
        updateConfig(config);
    }

    public void handleMessage(User user, String text, boolean action, MsgTags tags) {
        PreparedTrigger[] activeTriggers;
        boolean ignoreSelf;
        long globalAvailable;

        synchronized (lock) {
            activeTriggers = triggers.toArray(new PreparedTrigger[0]);
            ignoreSelf = selfIgnore;
            globalAvailable = nextGlobalAvailable;
        }

        if (activeTriggers.length == 0) {
            return;
        }

        if (ignoreSelf && client.isOwnUsername(user.getName())) {
            return;
        }

        final long now = System.currentTimeMillis();
        MatchContext context = createContext(text, tags);

        for (PreparedTrigger trigger : activeTriggers) {
            if (!trigger.matchesAuthor(user.getName())) {
                continue;
            }
            if (!trigger.matchesMessage(context)) {
                continue;
            }

            TriggerState state = trigger.getState(stateById);
            if (state == null) {
                continue;
            }

            state.recordMatch(user.getName(), now, trigger.requiredMentionsPerUser,
                    trigger.timeWindowMillis, context.recipientMention, context.directMention);

            if (!state.isReady(trigger.requiredUniqueUsers, trigger.requiredMentionsPerUser, trigger.timeWindowMillis, now)) {
                continue;
            }

            if (now < globalAvailable) {
                continue;
            }

            if (!state.isCooldownComplete(now)) {
                continue;
            }

            String reply = trigger.chooseReply(user.getName());
            if (StringUtil.isNullOrEmpty(reply)) {
                state.reset();
                continue;
            }

            boolean sent = client.sendAutoReplyMessage(user.getChannel(), reply);
            if (sent) {
                handlePostSend(trigger, state, now);
            }
            else {
                LOGGER.log(Level.FINE, "Failed to send auto reply for trigger {0}", trigger.id);
            }
            break;
        }
    }

    private void handlePostSend(PreparedTrigger trigger, TriggerState state, long now) {
        synchronized (lock) {
            state.reset();
            state.markCooldown(now + trigger.cooldownMillis);
            long cooldown = Math.max(globalCooldownMillis, 0L);
            nextGlobalAvailable = Math.max(nextGlobalAvailable, now) + cooldown;
        }
        if (trigger.shouldNotify(defaultNotification)) {
            gui.printSystem(trigger.buildNotificationMessage());
        }
        playSoundIfConfigured(trigger.resolveSound(defaultSound), trigger.id);
    }

    private void updateConfig(AutoReplyConfig config) {
        AutoReplyProfile profile = resolveActiveProfile(config);
        List<PreparedTrigger> prepared = new ArrayList<>();
        Map<String, TriggerState> existing;
        synchronized (lock) {
            existing = new HashMap<>(stateById);
            stateById.clear();
        }

        if (profile != null) {
            for (AutoReplyTrigger trigger : profile.getTriggers()) {
                PreparedTrigger preparedTrigger = PreparedTrigger.create(trigger, existing.get(trigger.getId()));
                if (preparedTrigger != null) {
                    prepared.add(preparedTrigger);
                    synchronized (lock) {
                        stateById.put(preparedTrigger.id, preparedTrigger.state);
                    }
                }
            }
        }

        long globalCooldown = Math.max(config.getGlobalCooldown(), COOLDOWN_SEC);
        synchronized (lock) {
            triggers = prepared;
            selfIgnore = config.isSelfIgnore();
            defaultNotification = config.isDefaultNotification();
            defaultSound = config.getDefaultSound();
            globalCooldownMillis = Math.max(0L, globalCooldown) * 1000L;
            if (nextGlobalAvailable < System.currentTimeMillis()) {
                nextGlobalAvailable = System.currentTimeMillis();
            }
        }
    }

    private AutoReplyProfile resolveActiveProfile(AutoReplyConfig config) {
        String activeId = config.getActiveProfileId();
        if (!StringUtil.isNullOrEmpty(activeId)) {
            for (AutoReplyProfile profile : config.getProfiles()) {
                if (profile.getId().equals(activeId)) {
                    return profile;
                }
            }
        }
        if (!config.getProfiles().isEmpty()) {
            return config.getProfiles().get(0);
        }
        return null;
    }

    private void playSoundIfConfigured(String soundFile, String triggerId) {
        if (StringUtil.isNullOrEmpty(soundFile)) {
            return;
        }
        if ("off".equalsIgnoreCase(soundFile)) {
            return;
        }
        try {
            Chatty.updateCustomPathFromSettings(PathType.SOUND);
            Path base = Chatty.getPath(PathType.SOUND);
            Path path = Chatty.toAbsolutePathWdir(base.resolve(soundFile));
            Sound.play(path, 100f, "auto_reply_" + triggerId, 0);
        }
        catch (Exception ex) {
            LOGGER.log(Level.FINE, "Could not play auto reply sound {0}: {1}", new Object[]{soundFile, ex.getMessage()});
        }
    }

    private MatchContext createContext(String text, MsgTags tags) {
        String ownUsername = client.getUsername();
        boolean recipientMention = false;
        boolean directMention = false;
        if (!StringUtil.isNullOrEmpty(ownUsername)) {
            if (tags != null) {
                String target = tags.get("reply-parent-user-login");
                if (!StringUtil.isNullOrEmpty(target) && target.equalsIgnoreCase(ownUsername)) {
                    recipientMention = true;
                }
            }
            String lowerText = text.toLowerCase(Locale.ENGLISH);
            String usernameLower = ownUsername.toLowerCase(Locale.ENGLISH);
            if (lowerText.contains("@" + usernameLower)) {
                directMention = true;
            }
        }
        return new MatchContext(text, recipientMention, directMention);
    }

    private static final class PreparedTrigger {

        private final String id;
        private final Pattern regexPattern;
        private final String plainPattern;
        private final List<String> replies;
        private final Map<String, List<String>> authorOverrides;
        private final Set<String> allowedAuthors;
        private final Set<String> blockedAuthors;
        private final boolean notify;
        private final String sound;
        private final String patternDisplay;
        private final long cooldownMillis;
        private final long timeWindowMillis;
        private final long requiredUniqueUsers;
        private final long requiredMentionsPerUser;
        private final TriggerState state;

        private PreparedTrigger(String id,
                                Pattern regexPattern,
                                String plainPattern,
                                List<String> replies,
                                Map<String, List<String>> authorOverrides,
                                Set<String> allowedAuthors,
                                Set<String> blockedAuthors,
                                boolean notify,
                                String sound,
                                String patternDisplay,
                                long cooldownMillis,
                                long timeWindowMillis,
                                long requiredUniqueUsers,
                                long requiredMentionsPerUser,
                                TriggerState state) {
            this.id = id;
            this.regexPattern = regexPattern;
            this.plainPattern = plainPattern;
            this.replies = replies;
            this.authorOverrides = authorOverrides;
            this.allowedAuthors = allowedAuthors;
            this.blockedAuthors = blockedAuthors;
            this.notify = notify;
            this.sound = sound;
            this.patternDisplay = patternDisplay;
            this.cooldownMillis = cooldownMillis;
            this.timeWindowMillis = timeWindowMillis;
            this.requiredUniqueUsers = requiredUniqueUsers;
            this.requiredMentionsPerUser = requiredMentionsPerUser;
            this.state = state;
        }

        private static PreparedTrigger create(AutoReplyTrigger trigger, TriggerState previousState) {
            if (StringUtil.isNullOrEmpty(trigger.getPattern())) {
                return null;
            }
            List<String> replies = parseReplies(trigger.getReply());
            if (replies.isEmpty()) {
                return null;
            }
            Pattern regex = null;
            String plain = null;
            if (trigger.getPatternType() == PatternType.REGEX) {
                try {
                    regex = Pattern.compile(trigger.getPattern(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                }
                catch (PatternSyntaxException ex) {
                    LOGGER.log(Level.WARNING, "Invalid auto reply regex pattern: {0}", trigger.getPattern());
                    return null;
                }
            }
            else {
                plain = normalizeBasic(trigger.getPattern());
                if (plain.isEmpty()) {
                    return null;
                }
            }

            Map<String, List<String>> overrides = buildOverrides(trigger.getAuthorOverrides());
            Set<String> allow = toLowerCaseSet(trigger.getAllowAuthors());
            Set<String> block = toLowerCaseSet(trigger.getBlockAuthors());
            long cooldown = Math.max(0L, trigger.getCooldown()) * 1000L;
            long timeWindow = Math.max(0L, trigger.getTimeWindowSec()) * 1000L;
            long requiredUsers = trigger.getMinUniqueUsers() > 0 ? trigger.getMinUniqueUsers() : 1L;
            long requiredMentions = trigger.getMinMentionsPerUser() > 0 ? trigger.getMinMentionsPerUser() : 1L;
            TriggerState state = previousState != null ? previousState : new TriggerState();

            return new PreparedTrigger(trigger.getId(), regex, plain, replies, overrides, allow, block,
                    trigger.isNotificationEnabled(), trigger.getSound(), trigger.getPattern(), cooldown,
                    timeWindow, requiredUsers, requiredMentions, state);
        }

        private static Map<String, List<String>> buildOverrides(Map<String, String> overrides) {
            if (overrides == null || overrides.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, List<String>> result = new HashMap<>();
            for (Map.Entry<String, String> entry : overrides.entrySet()) {
                String key = entry.getKey();
                if (StringUtil.isNullOrEmpty(key)) {
                    continue;
                }
                List<String> replies = parseReplies(entry.getValue());
                if (!replies.isEmpty()) {
                    result.put(key.toLowerCase(Locale.ENGLISH), replies);
                }
            }
            return result.isEmpty() ? Collections.emptyMap() : result;
        }

        private static Set<String> toLowerCaseSet(Collection<String> values) {
            if (values == null || values.isEmpty()) {
                return Collections.emptySet();
            }
            Set<String> result = new HashSet<>();
            for (String value : values) {
                if (!StringUtil.isNullOrEmpty(value)) {
                    result.add(value.toLowerCase(Locale.ENGLISH));
                }
            }
            return result;
        }

        private boolean matchesAuthor(String author) {
            String normalized = author.toLowerCase(Locale.ENGLISH);
            if (!allowedAuthors.isEmpty() && !allowedAuthors.contains(normalized)) {
                return false;
            }
            return !blockedAuthors.contains(normalized);
        }

        private boolean matchesMessage(MatchContext context) {
            if (regexPattern != null) {
                return regexPattern.matcher(context.text).find();
            }
            if (plainPattern == null) {
                return false;
            }
            String normalizedText = normalizeBasic(context.text);
            if (normalizedText.contains(plainPattern)) {
                return true;
            }
            if (plainPattern.contains("@")) {
                String patternNoAt = normalizeBasic(plainPattern.replace("@", ""));
                String textNoAt = normalizeBasic(context.text.replace("@", ""));
                return !patternNoAt.isEmpty() && textNoAt.contains(patternNoAt);
            }
            return false;
        }

        private TriggerState getState(Map<String, TriggerState> stateById) {
            TriggerState state = stateById.get(id);
            return state != null ? state : this.state;
        }

        private String chooseReply(String author) {
            List<String> override = authorOverrides.get(author.toLowerCase(Locale.ENGLISH));
            List<String> pool = override != null ? override : replies;
            if (pool.isEmpty()) {
                return null;
            }
            int index = ThreadLocalRandom.current().nextInt(pool.size());
            return pool.get(index);
        }

        private boolean shouldNotify(boolean defaultNotify) {
            return notify || defaultNotify;
        }

        private String buildNotificationMessage() {
            return String.format("[Auto Reply] Triggered: %s", patternDisplay);
        }

        private String resolveSound(String defaultSound) {
            String result = sound;
            if (StringUtil.isNullOrEmpty(result)) {
                result = defaultSound;
            }
            return result;
        }

        private static List<String> parseReplies(String text) {
            if (StringUtil.isNullOrEmpty(text)) {
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>();
            for (String line : StringUtil.splitLines(text)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result;
        }

        private static String normalizeBasic(String input) {
            String lowered = input == null ? "" : input.toLowerCase(Locale.ENGLISH);
            return lowered.replaceAll("\\s+", " ").trim();
        }
    }

    private static final class MatchContext {

        final String text;
        final boolean recipientMention;
        final boolean directMention;

        MatchContext(String text, boolean recipientMention, boolean directMention) {
            this.text = text;
            this.recipientMention = recipientMention;
            this.directMention = directMention;
        }
    }

    private static final class TriggerState {

        private final Map<String, Deque<MatchEntry>> matches = new HashMap<>();
        private long nextAvailableTime = 0L;

        private void recordMatch(String user, long timestamp, long requiredMentions, long windowMillis,
                boolean recipientMention, boolean directMention) {
            String key = user.toLowerCase(Locale.ENGLISH);
            Deque<MatchEntry> entries = matches.computeIfAbsent(key, k -> new ArrayDeque<>());
            entries.addLast(new MatchEntry(timestamp, recipientMention, directMention));
            pruneEntries(entries, timestamp, requiredMentions, windowMillis);
        }

        private boolean isReady(long requiredUsers, long requiredMentions, long windowMillis, long now) {
            pruneAll(now, requiredMentions, windowMillis);
            int matchesFound = 0;
            for (Deque<MatchEntry> entries : matches.values()) {
                if (requiredMentions <= 1) {
                    if (!entries.isEmpty()) {
                        matchesFound++;
                    }
                }
                else if (entries.size() >= requiredMentions) {
                    matchesFound++;
                }
                if (matchesFound >= requiredUsers) {
                    return true;
                }
            }
            return false;
        }

        private boolean isCooldownComplete(long now) {
            return now >= nextAvailableTime;
        }

        private void markCooldown(long timestamp) {
            nextAvailableTime = Math.max(nextAvailableTime, timestamp);
        }

        private void reset() {
            matches.clear();
        }

        private void pruneAll(long now, long requiredMentions, long windowMillis) {
            if (matches.isEmpty()) {
                return;
            }
            long cutoff = windowMillis > 0 ? now - windowMillis : Long.MIN_VALUE;
            Set<String> remove = new HashSet<>();
            for (Map.Entry<String, Deque<MatchEntry>> entry : matches.entrySet()) {
                Deque<MatchEntry> values = entry.getValue();
                pruneQueue(values, cutoff, requiredMentions);
                if (values.isEmpty()) {
                    remove.add(entry.getKey());
                }
            }
            for (String key : remove) {
                matches.remove(key);
            }
        }

        private void pruneEntries(Deque<MatchEntry> queue, long now, long requiredMentions, long windowMillis) {
            long cutoff = windowMillis > 0 ? now - windowMillis : Long.MIN_VALUE;
            pruneQueue(queue, cutoff, requiredMentions);
        }

        private void pruneQueue(Deque<MatchEntry> queue, long cutoff, long requiredMentions) {
            while (!queue.isEmpty() && queue.peekFirst().timestamp < cutoff) {
                queue.pollFirst();
            }
            if (requiredMentions > 0) {
                while (queue.size() > requiredMentions) {
                    queue.pollFirst();
                }
            }
        }

        private static final class MatchEntry {

            final long timestamp;
            final boolean recipientMention;
            final boolean directMention;

            MatchEntry(long timestamp, boolean recipientMention, boolean directMention) {
                this.timestamp = timestamp;
                this.recipientMention = recipientMention;
                this.directMention = directMention;
            }
        }
    }
}


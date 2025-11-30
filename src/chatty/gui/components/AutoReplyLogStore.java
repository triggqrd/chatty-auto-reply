package chatty.gui.components;

import chatty.AutoReplyEvent;
import chatty.AutoReplyService;
import chatty.Chatty;
import chatty.Chatty.PathType;
import chatty.util.StringUtil;
import chatty.util.settings.Settings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists auto-reply log entries and broadcasts updates to any listeners that
 * want to render them.
 */
public class AutoReplyLogStore implements AutoReplyService.Listener {

    public static final String SETTING_KEY = "autoReplyLogEntries";

    private static final Logger LOGGER = Logger.getLogger(AutoReplyLogStore.class.getName());
    private static final DateTimeFormatter DATE_HEADER_FORMATTER = DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String LOG_DIR_NAME = "auto-reply";
    private static final String DEFAULT_CHANNEL_FILENAME = "general";

    private static final int MAX_ENTRIES = 400;

    private final Settings settings;
    private final List<AutoReplyLogEntry> entries = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, LocalDate> lastWrittenDateByChannel = new LinkedHashMap<>();

    public AutoReplyLogStore(Settings settings) {
        this.settings = Objects.requireNonNull(settings);
        loadFromSettings();
        rebuildLastWrittenDates();
    }

    @Override
    public void autoReplySent(AutoReplyEvent event) {
        addEntry(AutoReplyLogEntry.fromEvent(event));
    }

    public List<AutoReplyLogEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        listener.onLogUpdated(getEntries(), null);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void clear() {
        entries.clear();
        persist();
        notifyListeners(null);
    }

    private void addEntry(AutoReplyLogEntry entry) {
        if (entry == null) {
            return;
        }
        entries.add(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
        persist();
        writeEntryToFile(entry);
        notifyListeners(entry);
    }

    private void notifyListeners(AutoReplyLogEntry newEntry) {
        List<AutoReplyLogEntry> snapshot = getEntries();
        for (Listener listener : listeners) {
            try {
                listener.onLogUpdated(snapshot, newEntry);
            }
            catch (Exception ignored) {
                // Ignore listener errors to avoid interrupting logging
            }
        }
    }

    private void loadFromSettings() {
        @SuppressWarnings("unchecked")
        Collection<Object> raw = settings.getList(SETTING_KEY);
        if (raw == null) {
            return;
        }
        for (Object item : raw) {
            AutoReplyLogEntry entry = AutoReplyLogEntry.fromSettings(item);
            if (entry != null) {
                entries.add(entry);
            }
        }
    }

    private void rebuildLastWrittenDates() {
        for (AutoReplyLogEntry entry : entries) {
            recordLastWrittenDate(entry);
        }
    }

    private void writeEntryToFile(AutoReplyLogEntry entry) {
        try {
            Chatty.updateCustomPathFromSettings(PathType.LOGS);
            Path base = Chatty.getPathCreate(PathType.LOGS);
            Path folder = base.resolve(LOG_DIR_NAME);
            Files.createDirectories(folder);

            String filename = sanitizeChannel(entry.getChannel()) + ".txt";
            Path file = folder.resolve(filename);

            LocalDate entryDate = Instant.ofEpochMilli(entry.getDisplayTimeMillis())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            List<String> lines = new ArrayList<>();

            LocalDate lastWritten = lastWrittenDateByChannel.get(filename);
            if (!entryDate.equals(lastWritten)) {
                lines.add(entryDate.format(DATE_HEADER_FORMATTER));
            }

            String time = TIME_FORMATTER
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(entry.getDisplayTimeMillis()));
            String channelLabel = StringUtil.isNullOrEmpty(entry.getChannel())
                    ? ""
                    : " | Channel: #" + entry.getChannel();
            lines.add(String.format("[%s] Trigger: %s | Profile: %s%s", time, entry.getTrigger(), entry.getProfile(), channelLabel));
            lines.add("Sent: " + entry.getReply());
            lines.add("");

            Files.write(file, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            lastWrittenDateByChannel.put(filename, entryDate);
        }
        catch (IOException ex) {
            LOGGER.log(Level.FINE, "Failed to write auto-reply log entry", ex);
        }
    }

    private void recordLastWrittenDate(AutoReplyLogEntry entry) {
        LocalDate entryDate = Instant.ofEpochMilli(entry.getDisplayTimeMillis())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        String key = sanitizeChannel(entry.getChannel()) + ".txt";
        lastWrittenDateByChannel.put(key, entryDate);
    }

    private String sanitizeChannel(String channel) {
        if (StringUtil.isNullOrEmpty(channel)) {
            return DEFAULT_CHANNEL_FILENAME;
        }
        String normalized = channel.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9-_]", "_")
                .replaceAll("_+", "_");
        if (normalized.isEmpty()) {
            return DEFAULT_CHANNEL_FILENAME;
        }
        return normalized;
    }

    private void persist() {
        List<Object> data = new ArrayList<>();
        for (AutoReplyLogEntry entry : entries) {
            data.add(entry.toMap());
        }
        settings.putList(SETTING_KEY, data);
        settings.setSettingChanged(SETTING_KEY);
    }

    public interface Listener {

        void onLogUpdated(List<AutoReplyLogEntry> entries, AutoReplyLogEntry newEntry);
    }

    public static class AutoReplyLogEntry {

        private final long triggeredAtMillis;
        private final long sentAtMillis;
        private final String trigger;
        private final String reply;
        private final String profile;
        private final String channel;

        public AutoReplyLogEntry(long triggeredAtMillis, long sentAtMillis, String trigger, String reply, String profile, String channel) {
            this.triggeredAtMillis = triggeredAtMillis;
            this.sentAtMillis = sentAtMillis;
            this.trigger = trigger == null ? "" : trigger;
            this.reply = reply == null ? "" : reply;
            this.profile = profile == null ? "" : profile;
            this.channel = channel == null ? "" : channel;
        }

        public static AutoReplyLogEntry fromEvent(AutoReplyEvent event) {
            if (event == null) {
                return null;
            }
            return new AutoReplyLogEntry(
                    event.getMatchedAtMillis(),
                    event.getSentAtMillis(),
                    event.getTriggerName(),
                    event.getReplyText(),
                    event.getProfileName(),
                    event.getChannel());
        }

        public long getTriggeredAtMillis() {
            return triggeredAtMillis;
        }

        public long getDisplayTimeMillis() {
            return sentAtMillis > 0 ? sentAtMillis : triggeredAtMillis;
        }

        public String getTrigger() {
            return trigger;
        }

        public String getReply() {
            return reply;
        }

        public String getProfile() {
            return profile;
        }

        public String getChannel() {
            return channel;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("triggeredAt", triggeredAtMillis);
            result.put("sentAt", sentAtMillis);
            result.put("trigger", trigger);
            result.put("reply", reply);
            result.put("profile", profile);
            result.put("channel", channel);
            return result;
        }

        public static AutoReplyLogEntry fromSettings(Object data) {
            if (!(data instanceof Map)) {
                return null;
            }
            Map<?, ?> map = (Map<?, ?>) data;
            long triggered = toLong(map.get("triggeredAt"));
            long sent = toLong(map.get("sentAt"));
            String trigger = toString(map.get("trigger"));
            String reply = toString(map.get("reply"));
            String profile = toString(map.get("profile"));
            String channel = toString(map.get("channel"));
            return new AutoReplyLogEntry(triggered, sent, trigger, reply, profile, channel);
        }

        private static long toLong(Object value) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            if (value instanceof String) {
                try {
                    return Long.parseLong((String) value);
                }
                catch (NumberFormatException ex) {
                    return 0L;
                }
            }
            return 0L;
        }

        private static String toString(Object value) {
            return value == null ? "" : value.toString();
        }
    }
}

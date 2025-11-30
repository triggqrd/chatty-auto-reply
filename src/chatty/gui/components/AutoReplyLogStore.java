package chatty.gui.components;

import chatty.AutoReplyEvent;
import chatty.AutoReplyService;
import chatty.Chatty;
import chatty.Chatty.PathType;
import chatty.util.settings.Settings;
import java.io.BufferedReader;
import java.io.BufferedWriter;
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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists auto-reply log entries and broadcasts updates to any listeners that
 * want to render them.
 */
public class AutoReplyLogStore implements AutoReplyService.Listener {

    public static final String SETTING_KEY = "autoReplyLogEntries";

    private static final int MAX_ENTRIES = 400;
    private static final Logger LOGGER = Logger.getLogger(AutoReplyLogStore.class.getName());
    private static final DateTimeFormatter FILE_HEADER_FORMATTER = DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu");
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String DATE_HEADER_PREFIX = "===== ";

    private final Settings settings;
    private final List<AutoReplyLogEntry> entries = new ArrayList<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, LocalDate> lastLoggedDateByChannel = new ConcurrentHashMap<>();

    public AutoReplyLogStore(Settings settings) {
        this.settings = Objects.requireNonNull(settings);
        loadFromSettings();
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
        appendToFile(entry);
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

    private void persist() {
        List<Object> data = new ArrayList<>();
        for (AutoReplyLogEntry entry : entries) {
            data.add(entry.toMap());
        }
        settings.putList(SETTING_KEY, data);
        settings.setSettingChanged(SETTING_KEY);
    }

    private void appendToFile(AutoReplyLogEntry entry) {
        try {
            Chatty.updateCustomPathFromSettings(PathType.LOGS);
            Path logDirectory = Chatty.getPathCreate(PathType.LOGS).resolve("auto-reply");
            Files.createDirectories(logDirectory);

            String safeChannel = sanitizeChannel(entry.getChannel());
            Path logFile = logDirectory.resolve(safeChannel + ".txt");

            LocalDate entryDate = Instant.ofEpochMilli(entry.getDisplayTimeMillis())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            LocalDate lastDate = lastLoggedDateByChannel.computeIfAbsent(safeChannel, key -> readLastLoggedDate(logFile));

            try (BufferedWriter writer = Files.newBufferedWriter(logFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                if (lastDate == null || !entryDate.equals(lastDate)) {
                    writer.write(DATE_HEADER_PREFIX + entryDate.format(FILE_HEADER_FORMATTER) + " =====");
                    writer.newLine();
                    lastLoggedDateByChannel.put(safeChannel, entryDate);
                }

                String time = Instant.ofEpochMilli(entry.getDisplayTimeMillis())
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime()
                        .format(FILE_TIME_FORMATTER);
                String channelLabel = entry.getChannel().isEmpty() ? "" : " #" + entry.getChannel();
                String logLine = String.format("[%s]%s Trigger: %s | Profile: %s | Reply: %s",
                        time,
                        channelLabel,
                        entry.getTrigger(),
                        entry.getProfile(),
                        entry.getReply());
                writer.write(logLine);
                writer.newLine();
            }
        }
        catch (IOException ex) {
            LOGGER.log(Level.FINE, "Could not write auto-reply log entry", ex);
        }
    }

    private LocalDate readLastLoggedDate(Path logFile) {
        if (!Files.exists(logFile)) {
            return null;
        }
        LocalDate lastDate = null;
        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(DATE_HEADER_PREFIX) && line.endsWith(" =====")) {
                    String dateText = line.substring(DATE_HEADER_PREFIX.length(), line.length() - " =====".length());
                    try {
                        lastDate = LocalDate.parse(dateText, FILE_HEADER_FORMATTER);
                    }
                    catch (Exception ignored) {
                        // Ignore malformed headers and continue searching
                    }
                }
            }
        }
        catch (IOException ex) {
            LOGGER.log(Level.FINE, "Could not read auto-reply log file", ex);
        }
        return lastDate;
    }

    private String sanitizeChannel(String channel) {
        String base = channel == null || channel.trim().isEmpty()
                ? "unknown_channel"
                : channel.trim();
        return base.replaceAll("[^A-Za-z0-9-_]+", "_");
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

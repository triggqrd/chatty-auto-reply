package chatty;

import chatty.util.StringUtil;
import chatty.util.settings.SettingChangeListener;
import chatty.util.settings.Settings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages auto reply configuration and ensures settings changes are propagated
 * to listeners.
 */
public class AutoReplyManager {

    private static final Logger LOGGER = Logger.getLogger(AutoReplyManager.class.getName());

    public static final String SETTING_PROFILES = "autoReplyProfiles";
    public static final String SETTING_ACTIVE_PROFILE = "autoReplyActiveProfile";
    public static final String SETTING_GLOBAL_COOLDOWN = "autoReplyGlobalCooldown";
    public static final String SETTING_SELF_IGNORE = "autoReplySelfIgnore";
    public static final String SETTING_DEFAULT_SOUND = "autoReplyDefaultSound";
    public static final String SETTING_DEFAULT_NOTIFICATION = "autoReplyDefaultNotification";

    private final Settings settings;
    private final Object lock = new Object();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private AutoReplyConfig currentConfig = new AutoReplyConfig();
    private boolean suppressReload;

    public AutoReplyManager(Settings settings) {
        this.settings = settings;
        this.settings.addSettingChangeListener(new SettingChangeListener() {
            @Override
            public void settingChanged(String setting, int type, Object value) {
                if (isManagedSetting(setting)) {
                    synchronized (lock) {
                        if (suppressReload) {
                            return;
                        }
                    }
                    reloadFromSettings();
                }
            }
        });
        reloadFromSettings();
    }

    private boolean isManagedSetting(String setting) {
        return SETTING_PROFILES.equalsIgnoreCase(setting)
                || SETTING_ACTIVE_PROFILE.equalsIgnoreCase(setting)
                || SETTING_GLOBAL_COOLDOWN.equalsIgnoreCase(setting)
                || SETTING_SELF_IGNORE.equalsIgnoreCase(setting)
                || SETTING_DEFAULT_SOUND.equalsIgnoreCase(setting)
                || SETTING_DEFAULT_NOTIFICATION.equalsIgnoreCase(setting);
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public AutoReplyConfig getConfig() {
        synchronized (lock) {
            return currentConfig.copy();
        }
    }

    public void applyConfig(AutoReplyConfig config) {
        AutoReplyConfig copy = config.copy();
        synchronized (lock) {
            currentConfig = copy;
            suppressReload = true;
        }
        try {
            writeSettings(copy);
        }
        finally {
            synchronized (lock) {
                suppressReload = false;
            }
        }
        notifyListeners(copy.copy());
    }

    private void reloadFromSettings() {
        AutoReplyConfig config = loadConfigFromSettings();
        synchronized (lock) {
            currentConfig = config;
        }
        notifyListeners(config.copy());
    }

    private void notifyListeners(AutoReplyConfig config) {
        for (Listener listener : listeners) {
            try {
                listener.autoReplyConfigChanged(config.copy());
            }
            catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error notifying auto reply listener", ex);
            }
        }
    }

    private AutoReplyConfig loadConfigFromSettings() {
        AutoReplyConfig config = new AutoReplyConfig();
        @SuppressWarnings("unchecked")
        List<Object> rawProfiles = settings.getList(SETTING_PROFILES);
        config.profiles = parseProfiles(rawProfiles);
        String active = settings.getString(SETTING_ACTIVE_PROFILE);
        if (StringUtil.isNullOrEmpty(active)) {
            active = "default";
        }
        config.activeProfileId = active;
        config.globalCooldown = settings.getLong(SETTING_GLOBAL_COOLDOWN);
        config.selfIgnore = settings.getBoolean(SETTING_SELF_IGNORE);
        config.defaultNotification = settings.getBoolean(SETTING_DEFAULT_NOTIFICATION);
        String defaultSound = settings.getString(SETTING_DEFAULT_SOUND);
        config.defaultSound = normalize(defaultSound);
        return config;
    }

    private void writeSettings(AutoReplyConfig config) {
        List<Object> data = toSettingsList(config.profiles);
        settings.putList(SETTING_PROFILES, data);
        settings.setSettingChanged(SETTING_PROFILES);
        settings.setString(SETTING_ACTIVE_PROFILE, config.activeProfileId);
        settings.setLong(SETTING_GLOBAL_COOLDOWN, config.globalCooldown);
        settings.setBoolean(SETTING_SELF_IGNORE, config.selfIgnore);
        settings.setBoolean(SETTING_DEFAULT_NOTIFICATION, config.defaultNotification);
        settings.setString(SETTING_DEFAULT_SOUND, config.defaultSound == null ? "" : config.defaultSound);
    }

    private static List<Object> toSettingsList(List<AutoReplyProfile> profiles) {
        List<Object> result = new ArrayList<>();
        for (AutoReplyProfile profile : profiles) {
            Map<String, Object> profileData = new LinkedHashMap<>();
            profileData.put("id", profile.getId());
            profileData.put("name", profile.getName());
            List<Object> triggerData = new ArrayList<>();
            for (AutoReplyTrigger trigger : profile.getTriggers()) {
                triggerData.add(trigger.toMap());
            }
            profileData.put("triggers", triggerData);
            result.add(profileData);
        }
        return result;
    }

    private static List<AutoReplyProfile> parseProfiles(Collection<Object> data) {
        List<AutoReplyProfile> result = new ArrayList<>();
        if (data == null) {
            return result;
        }
        for (Object item : data) {
            AutoReplyProfile profile = parseProfile(item);
            if (profile != null) {
                result.add(profile);
            }
        }
        return result;
    }

    private static AutoReplyProfile parseProfile(Object data) {
        if (!(data instanceof Map)) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) data;
        String id = normalize(map.get("id"));
        if (StringUtil.isNullOrEmpty(id)) {
            id = generateId();
        }
        String name = normalize(map.get("name"));
        if (StringUtil.isNullOrEmpty(name)) {
            name = id;
        }
        @SuppressWarnings("unchecked")
        Collection<Object> rawTriggers = (Collection<Object>) map.get("triggers");
        List<AutoReplyTrigger> triggers = new ArrayList<>();
        if (rawTriggers != null) {
            for (Object item : rawTriggers) {
                AutoReplyTrigger trigger = parseTrigger(item);
                if (trigger != null) {
                    triggers.add(trigger);
                }
            }
        }
        return new AutoReplyProfile(id, name, triggers);
    }

    private static AutoReplyTrigger parseTrigger(Object data) {
        if (!(data instanceof Map)) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) data;
        String id = normalize(map.get("id"));
        if (StringUtil.isNullOrEmpty(id)) {
            id = generateId();
        }
        String pattern = normalize(map.get("pattern"));
        PatternType patternType = PatternType.fromString(normalize(map.get("patternType")));
        String reply = normalize(map.get("reply"));
        long cooldown = toLong(map.get("cooldown"), 0);
        long minUniqueUsers = toLong(map.get("minUniqueUsers"), 0);
        long minMentionsPerUser = toLong(map.get("minMentionsPerUser"), 0);
        long timeWindowSec = toLong(map.get("timeWindowSec"), 0);
        boolean notify = toBoolean(map.get("notify"), false);
        String sound = normalize(map.get("sound"));
        Map<String, String> overrides = toStringMap(map.get("overrides"));
        Object allowData = map.containsKey("authors") ? map.get("authors") : map.get("allow");
        List<String> allow = toStringList(allowData);
        List<String> block = toStringList(map.get("block"));
        return new AutoReplyTrigger(id, pattern, patternType, reply, cooldown,
                overrides, allow, block, notify, sound,
                minUniqueUsers, minMentionsPerUser, timeWindowSec);
    }

    private static String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String input = value.toString().trim();
        if (input.isEmpty()) {
            return null;
        }
        return input;
    }

    private static long toLong(Object value, long defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            }
            catch (NumberFormatException ex) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static boolean toBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue() != 0;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    private static Map<String, String> toStringMap(Object value) {
        if (!(value instanceof Map)) {
            return new LinkedHashMap<>();
        }
        Map<String, String> result = new LinkedHashMap<>();
        Map<?, ?> map = (Map<?, ?>) value;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = normalize(entry.getKey());
            if (!StringUtil.isNullOrEmpty(key)) {
                result.put(key, entry.getValue() == null ? "" : entry.getValue().toString());
            }
        }
        return result;
    }

    private static List<String> toStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            for (Object item : collection) {
                String normalized = normalize(item);
                if (!StringUtil.isNullOrEmpty(normalized)) {
                    result.add(normalized);
                }
            }
        }
        return result;
    }

    private static String generateId() {
        return UUID.randomUUID().toString();
    }

    public interface Listener {

        void autoReplyConfigChanged(AutoReplyConfig config);
    }

    public static class AutoReplyConfig {

        private List<AutoReplyProfile> profiles = new ArrayList<>();
        private String activeProfileId = "default";
        private long globalCooldown;
        private boolean selfIgnore = true;
        private boolean defaultNotification;
        private String defaultSound;

        public AutoReplyConfig copy() {
            AutoReplyConfig copy = new AutoReplyConfig();
            for (AutoReplyProfile profile : profiles) {
                copy.profiles.add(profile.copy());
            }
            copy.activeProfileId = activeProfileId;
            copy.globalCooldown = globalCooldown;
            copy.selfIgnore = selfIgnore;
            copy.defaultNotification = defaultNotification;
            copy.defaultSound = defaultSound;
            return copy;
        }

        public List<AutoReplyProfile> getProfiles() {
            return profiles;
        }

        public String getActiveProfileId() {
            return activeProfileId;
        }

        public void setActiveProfileId(String activeProfileId) {
            this.activeProfileId = StringUtil.isNullOrEmpty(activeProfileId) ? "default" : activeProfileId;
        }

        public long getGlobalCooldown() {
            return globalCooldown;
        }

        public void setGlobalCooldown(long globalCooldown) {
            this.globalCooldown = Math.max(0, globalCooldown);
        }

        public boolean isSelfIgnore() {
            return selfIgnore;
        }

        public void setSelfIgnore(boolean selfIgnore) {
            this.selfIgnore = selfIgnore;
        }

        public boolean isDefaultNotification() {
            return defaultNotification;
        }

        public void setDefaultNotification(boolean defaultNotification) {
            this.defaultNotification = defaultNotification;
        }

        public String getDefaultSound() {
            return defaultSound;
        }

        public void setDefaultSound(String defaultSound) {
            this.defaultSound = normalize(defaultSound);
        }
    }

    public static class AutoReplyProfile {

        private final String id;
        private String name;
        private final List<AutoReplyTrigger> triggers;

        public AutoReplyProfile(String id, String name, List<AutoReplyTrigger> triggers) {
            this.id = Objects.requireNonNull(id);
            this.name = name == null ? id : name;
            this.triggers = new ArrayList<>(triggers == null ? Collections.emptyList() : triggers);
        }

        public static AutoReplyProfile create(String name) {
            return new AutoReplyProfile(generateId(), name, new ArrayList<>());
        }

        public AutoReplyProfile copy() {
            List<AutoReplyTrigger> triggerCopies = new ArrayList<>();
            for (AutoReplyTrigger trigger : triggers) {
                triggerCopies.add(trigger.copy());
            }
            return new AutoReplyProfile(id, name, triggerCopies);
        }

        public AutoReplyProfile duplicate() {
            List<AutoReplyTrigger> triggerCopies = new ArrayList<>();
            for (AutoReplyTrigger trigger : triggers) {
                triggerCopies.add(trigger.copyWithNewId());
            }
            AutoReplyProfile copy = new AutoReplyProfile(generateId(), name, triggerCopies);
            return copy;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            if (!StringUtil.isNullOrEmpty(name)) {
                this.name = name;
            }
        }

        public List<AutoReplyTrigger> getTriggers() {
            return triggers;
        }

        public AutoReplyTrigger addTrigger(AutoReplyTrigger trigger) {
            triggers.add(trigger);
            return trigger;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class AutoReplyTrigger {

        private final String id;
        private String pattern;
        private PatternType patternType;
        private String reply;
        private long cooldown;
        private Map<String, String> authorOverrides;
        private List<String> allowAuthors;
        private List<String> blockAuthors;
        private boolean notificationEnabled;
        private String sound;
        private long minUniqueUsers;
        private long minMentionsPerUser;
        private long timeWindowSec;

        public AutoReplyTrigger(String id, String pattern, PatternType patternType, String reply,
                long cooldown, Map<String, String> authorOverrides,
                List<String> allowAuthors, List<String> blockAuthors,
                boolean notificationEnabled, String sound,
                long minUniqueUsers, long minMentionsPerUser, long timeWindowSec) {
            this.id = Objects.requireNonNull(id);
            this.pattern = pattern == null ? "" : pattern;
            this.patternType = patternType == null ? PatternType.PLAIN : patternType;
            this.reply = reply == null ? "" : reply;
            this.cooldown = Math.max(0, cooldown);
            this.authorOverrides = new LinkedHashMap<>(authorOverrides == null ? Collections.emptyMap() : authorOverrides);
            this.allowAuthors = new ArrayList<>(allowAuthors == null ? Collections.emptyList() : allowAuthors);
            this.blockAuthors = new ArrayList<>(blockAuthors == null ? Collections.emptyList() : blockAuthors);
            this.notificationEnabled = notificationEnabled;
            this.sound = normalize(sound);
            this.minUniqueUsers = Math.max(0, minUniqueUsers);
            this.minMentionsPerUser = Math.max(0, minMentionsPerUser);
            this.timeWindowSec = Math.max(0, timeWindowSec);
        }

        public static AutoReplyTrigger create() {
            return new AutoReplyTrigger(generateId(), "", PatternType.PLAIN, "", 0,
                    new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>(), false, null,
                    0, 0, 0);
        }

        public AutoReplyTrigger copy() {
            return new AutoReplyTrigger(id, pattern, patternType, reply, cooldown,
                    authorOverrides, allowAuthors, blockAuthors, notificationEnabled, sound,
                    minUniqueUsers, minMentionsPerUser, timeWindowSec);
        }

        public AutoReplyTrigger copyWithNewId() {
            return new AutoReplyTrigger(generateId(), pattern, patternType, reply, cooldown,
                    authorOverrides, allowAuthors, blockAuthors, notificationEnabled, sound,
                    minUniqueUsers, minMentionsPerUser, timeWindowSec);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("pattern", pattern);
            result.put("patternType", patternType.name());
            result.put("reply", reply);
            result.put("cooldown", cooldown);
            result.put("minUniqueUsers", minUniqueUsers);
            result.put("minMentionsPerUser", minMentionsPerUser);
            result.put("timeWindowSec", timeWindowSec);
            if (!authorOverrides.isEmpty()) {
                result.put("overrides", new LinkedHashMap<>(authorOverrides));
            }
            if (!allowAuthors.isEmpty()) {
                List<String> authors = new ArrayList<>(allowAuthors);
                result.put("allow", new ArrayList<>(authors));
                result.put("authors", new ArrayList<>(authors));
            }
            if (!blockAuthors.isEmpty()) {
                result.put("block", new ArrayList<>(blockAuthors));
            }
            result.put("notify", notificationEnabled);
            if (!StringUtil.isNullOrEmpty(sound)) {
                result.put("sound", sound);
            }
            return result;
        }

        public String getId() {
            return id;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern == null ? "" : pattern;
        }

        public PatternType getPatternType() {
            return patternType;
        }

        public void setPatternType(PatternType patternType) {
            this.patternType = patternType == null ? PatternType.PLAIN : patternType;
        }

        public String getReply() {
            return reply;
        }

        public void setReply(String reply) {
            this.reply = reply == null ? "" : reply;
        }

        public long getCooldown() {
            return cooldown;
        }

        public void setCooldown(long cooldown) {
            this.cooldown = Math.max(0, cooldown);
        }

        public Map<String, String> getAuthorOverrides() {
            return authorOverrides;
        }

        public void setAuthorOverrides(Map<String, String> authorOverrides) {
            this.authorOverrides = new LinkedHashMap<>(authorOverrides == null ? Collections.emptyMap() : authorOverrides);
        }

        public List<String> getAllowAuthors() {
            return allowAuthors;
        }

        public void setAllowAuthors(List<String> allowAuthors) {
            this.allowAuthors = new ArrayList<>(allowAuthors == null ? Collections.emptyList() : allowAuthors);
        }

        public List<String> getBlockAuthors() {
            return blockAuthors;
        }

        public void setBlockAuthors(List<String> blockAuthors) {
            this.blockAuthors = new ArrayList<>(blockAuthors == null ? Collections.emptyList() : blockAuthors);
        }

        public boolean isNotificationEnabled() {
            return notificationEnabled;
        }

        public void setNotificationEnabled(boolean notificationEnabled) {
            this.notificationEnabled = notificationEnabled;
        }

        public String getSound() {
            return sound;
        }

        public void setSound(String sound) {
            this.sound = normalize(sound);
        }

        public long getMinUniqueUsers() {
            return minUniqueUsers;
        }

        public void setMinUniqueUsers(long minUniqueUsers) {
            this.minUniqueUsers = Math.max(0, minUniqueUsers);
        }

        public long getMinMentionsPerUser() {
            return minMentionsPerUser;
        }

        public void setMinMentionsPerUser(long minMentionsPerUser) {
            this.minMentionsPerUser = Math.max(0, minMentionsPerUser);
        }

        public long getTimeWindowSec() {
            return timeWindowSec;
        }

        public void setTimeWindowSec(long timeWindowSec) {
            this.timeWindowSec = Math.max(0, timeWindowSec);
        }

        @Override
        public String toString() {
            return pattern;
        }
    }

    public enum PatternType {
        PLAIN,
        REGEX;

        public static PatternType fromString(String input) {
            if (StringUtil.isNullOrEmpty(input)) {
                return PLAIN;
            }
            for (PatternType value : values()) {
                if (value.name().equalsIgnoreCase(input)) {
                    return value;
                }
            }
            return PLAIN;
        }

        public String getLabelKey() {
            return "settings.autoReply.patternType." + name();
        }
    }
}

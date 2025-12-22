package chatty;

import chatty.AutoReplyManager.AutoReplyConfig;
import chatty.AutoReplyManager.AutoReplyProfile;
import chatty.AutoReplyManager.AutoReplyTrigger;
import chatty.AutoReplyManager.ReplySelection;
import chatty.util.settings.FileManager;
import chatty.util.settings.Setting;
import chatty.util.settings.Settings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.Test;

import static org.junit.Assert.*;

public class AutoReplySequentialProgressTest {

    @Test
    public void sequentialProgressPersistsAcrossRestart() throws Exception {
        Path basePath = Files.createTempDirectory("auto-reply-progress");
        FileManager fileManager = new FileManager(basePath, basePath.resolve("backup"));
        fileManager.add("settings", "settings.json", false, null);

        Settings settings = new Settings("settings", fileManager);
        addAutoReplySettings(settings);

        AutoReplyManager manager = new AutoReplyManager(settings);
        AutoReplyConfig config = createSequentialConfig();
        manager.applyConfig(config);
        settings.saveSettingsToJson(true);

        String triggerId = config.getProfiles().get(0).getTriggers().get(0).getId();
        manager.storeSequentialReplyIndex(triggerId, 1);

        FileManager reloadFileManager = new FileManager(basePath, basePath.resolve("backup"));
        reloadFileManager.add("settings", "settings.json", false, null);
        Settings reloadedSettings = new Settings("settings", reloadFileManager);
        addAutoReplySettings(reloadedSettings);
        reloadedSettings.loadSettingsFromJson();
        AutoReplyManager reloadedManager = new AutoReplyManager(reloadedSettings);

        assertEquals(1, reloadedManager.getSequentialReplyIndex(triggerId));
    }

    private static AutoReplyConfig createSequentialConfig() {
        AutoReplyConfig config = new AutoReplyConfig();
        AutoReplyProfile profile = AutoReplyProfile.create("default");
        AutoReplyTrigger trigger = AutoReplyTrigger.create();
        trigger.setPattern("hello");
        trigger.setReply("first\nsecond");
        trigger.setReplySelection(ReplySelection.SEQUENTIAL);
        profile.addTrigger(trigger);
        config.getProfiles().add(profile);
        config.setActiveProfileId(profile.getId());
        return config;
    }

    private static void addAutoReplySettings(Settings settings) {
        settings.addList("autoReplyProfiles", new ArrayList<>(), Setting.MAP);
        settings.addString("autoReplyActiveProfile", "default");
        settings.addLong("autoReplyGlobalCooldown", 0);
        settings.addBoolean("autoReplySelfIgnore", true);
        settings.addBoolean("autoReplyDefaultNotification", false);
        settings.addString("autoReplyDefaultSound", "");
        settings.addBoolean("autoReplyEnabled", true);
        settings.addMap("autoReplySequentialProgress", new java.util.HashMap<>(), Setting.LONG);
    }
}

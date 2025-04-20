package com.trenton.oneplayersleepplus;

import com.trenton.coreapi.api.PluginInitializer;
import com.trenton.updater.api.UpdaterImpl;
import com.trenton.updater.api.UpdaterService;
import org.bstats.bukkit.Metrics;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class OnePlayerSleepPlus extends JavaPlugin {
    private FileConfiguration messagesConfig;
    private UpdaterService updater;
    private PluginInitializer initializer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultMessagesConfig();
        File messagesFile = new File(getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        String packageName = getClass().getPackageName();
        initializer = new PluginInitializer(this, packageName);
        initializer.initialize();

        boolean autoUpdaterEnabled = getConfig().getBoolean("auto_updater.enabled", true);
        updater = new UpdaterImpl(this, 124265);
        if (autoUpdaterEnabled) {
            updater.checkForUpdates(true);
        }

        new Metrics(this, 25550);
        getLogger().info("OnePlayerSleepPlus enabled!");
    }

    @Override
    public void onDisable() {
        if (initializer != null) {
            initializer.shutdown();
        }
        if (updater != null) {
            updater.handleUpdateOnShutdown();
        }
        getLogger().info("OnePlayerSleepPlus disabled!");
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public PluginInitializer getInitializer() {
        return initializer;
    }

    private void saveDefaultMessagesConfig() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
    }

    public UpdaterService getUpdater() {
        return updater;
    }
}
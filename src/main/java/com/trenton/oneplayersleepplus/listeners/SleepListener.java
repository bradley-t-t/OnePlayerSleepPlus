package com.trenton.oneplayersleepplus.listeners;

import com.trenton.coreapi.api.ListenerBase;
import com.trenton.coreapi.util.MessageUtils;
import com.trenton.oneplayersleepplus.OnePlayerSleepPlus;
import com.trenton.oneplayersleepplus.SleepManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.plugin.Plugin;

import java.util.function.Supplier;

public class SleepListener implements ListenerBase, Listener {
    private OnePlayerSleepPlus plugin;
    private SleepManager sleepManager;
    private FileConfiguration messages;

    @Override
    public void register(Plugin plugin) {
        this.plugin = (OnePlayerSleepPlus) plugin;
        this.messages = this.plugin.getMessagesConfig();
        this.sleepManager = this.plugin.getInitializer().getManagers().stream()
                .filter(m -> m instanceof SleepManager)
                .map(m -> (SleepManager) m)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("SleepManager not found"));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("SleepListener registered");
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        if (!sleepManager.canSkipNight()) {
            event.setCancelled(true);
            MessageUtils.sendMessage(plugin, messages, event.getPlayer(), "night_skip_blocked");
            return;
        }
        sleepManager.addSleepingPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        sleepManager.removeSleepingPlayer(event.getPlayer());
    }
}
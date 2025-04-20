package com.trenton.oneplayersleepplus;

import com.trenton.coreapi.api.ManagerBase;
import com.trenton.coreapi.util.MessageUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SleepManager implements ManagerBase {
    private OnePlayerSleepPlus plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private final ConcurrentHashMap<UUID, Player> sleepingPlayers = new ConcurrentHashMap<>();
    private String sleepMode;
    private double sleepPercentage;
    private int fixedSleepers;
    private boolean announceSleep;
    private boolean showActionBar;
    private boolean particlesEnabled;
    private String particleType;
    private int particleCount;
    private boolean soundEnabled;
    private String soundType;
    private float soundVolume;
    private float soundPitch;
    private boolean restrictEveryOtherNight;
    private boolean canSkipNight = true;
    private BukkitRunnable actionBarTask;
    private BukkitRunnable timeSkipTask;
    private Logger logger;

    @Override
    public void init(Plugin plugin) {
        this.plugin = (OnePlayerSleepPlus) plugin;
        this.logger = plugin.getLogger();
        this.config = this.plugin.getConfig();
        this.messages = this.plugin.getMessagesConfig();
        loadConfig();
        scheduleNightReset();
    }

    @Override
    public void shutdown() {
        stopActionBarTask();
        stopTimeSkip();
        sleepingPlayers.clear();
    }

    private void loadConfig() {
        sleepMode = config.getString("sleep_requirement.mode", "fixed").toLowerCase();
        sleepPercentage = Math.max(1.0, Math.min(100.0, config.getDouble("sleep_requirement.percentage", 50.0)));
        fixedSleepers = Math.max(1, config.getInt("sleep_requirement.fixed_players", 1));
        announceSleep = config.getBoolean("announce.enabled", true);
        showActionBar = config.getBoolean("action_bar.enabled", true);
        particlesEnabled = config.getBoolean("time_skip_effects.particles_enabled", true);
        particleType = config.getString("time_skip_effects.particle_type", "PORTAL").toUpperCase();
        particleCount = Math.max(1, config.getInt("time_skip_effects.particle_count", 5));
        soundEnabled = config.getBoolean("time_skip_effects.sound_enabled", true);
        soundType = config.getString("time_skip_effects.sound_type", "BLOCK_PORTAL_AMBIENT").toUpperCase();
        soundVolume = (float) Math.max(0.0, Math.min(1.0, config.getDouble("time_skip_effects.sound_volume", 0.5)));
        soundPitch = (float) Math.max(0.5, Math.min(2.0, config.getDouble("time_skip_effects.sound_pitch", 1.0)));
        restrictEveryOtherNight = config.getBoolean("night_skip_restriction.every_other_night", true);
        logger.info("SleepManager initialized: mode=" + sleepMode + ", percentage=" + sleepPercentage + ", fixed=" + fixedSleepers);
    }

    public void addSleepingPlayer(Player player) {
        sleepingPlayers.put(player.getUniqueId(), player);
        logger.info("Player " + player.getName() + " added to sleeping. Total: " + sleepingPlayers.size());
        if (showActionBar) {
            startActionBarTask();
        }
        trySkipNight(player);
    }

    public void removeSleepingPlayer(Player player) {
        sleepingPlayers.remove(player.getUniqueId());
        logger.info("Player " + player.getName() + " removed from sleeping. Total: " + sleepingPlayers.size());
        if (showActionBar && sleepingPlayers.isEmpty()) {
            stopActionBarTask();
        }
        if (!sleepingPlayers.isEmpty()) {
            trySkipNight(player);
        } else {
            stopTimeSkip();
        }
    }

    public boolean canSkipNight() {
        return !restrictEveryOtherNight || canSkipNight;
    }

    private void startActionBarTask() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
        actionBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (sleepingPlayers.isEmpty()) {
                    stopActionBarTask();
                    return;
                }
                int needed = getRequiredSleepers();
                int current = Math.min(sleepingPlayers.size(), needed);
                logger.info("ActionBar update: current=" + current + ", needed=" + needed + ", actual sleeping=" + sleepingPlayers.size());
                String message = messages.getString("sleepers_needed", "");
                if (message.isEmpty()) return;
                message = message.replace("{progress}", String.valueOf(current))
                        .replace("{time}", String.valueOf(needed));
                message = ChatColor.translateAlternateColorCodes('&', message);
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
                }
            }
        };
        actionBarTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void stopActionBarTask() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
    }

    private int getRequiredSleepers() {
        int onlinePlayers = plugin.getServer().getOnlinePlayers().size();
        int required = sleepMode.equals("percentage")
                ? (int) Math.ceil(onlinePlayers * (sleepPercentage / 100.0))
                : fixedSleepers;
        required = Math.max(1, required);
        logger.info("Calculated required sleepers: " + required + " (online: " + onlinePlayers + ", mode: " + sleepMode + ")");
        return required;
    }

    private void trySkipNight(Player player) {
        if (!canSkipNight()) {
            return;
        }
        // Ensure it's night (world time > 12000)
        boolean isNight = false;
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL && world.getTime() > 12000) {
                isNight = true;
                break;
            }
        }
        if (!isNight) {
            logger.info("Cannot skip: not night time");
            return;
        }
        int needed = getRequiredSleepers();
        int current = sleepingPlayers.size();
        logger.info("Checking skip: " + current + "/" + needed + " sleeping");
        if (current >= needed) {
            startTimeSkip();
        }
    }

    private void startTimeSkip() {
        if (timeSkipTask != null) {
            timeSkipTask.cancel();
        }
        if (restrictEveryOtherNight) {
            canSkipNight = false; // Lock skipping immediately
            logger.info("Night skip started, canSkipNight set to false");
        }
        logger.info("Starting time skip with " + sleepingPlayers.size() + " players");
        timeSkipTask = new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 100;
            final long targetTime = 0;
            final long timeIncrement = 120;
            final int soundInterval = 30;

            @Override
            public void run() {
                for (World world : plugin.getServer().getWorlds()) {
                    if (world.getEnvironment() != World.Environment.NORMAL) continue;

                    long currentTime = world.getTime();
                    long newTime = currentTime + timeIncrement;
                    if (newTime >= 24000) newTime %= 24000;
                    world.setTime(newTime);

                    for (Player player : sleepingPlayers.values()) {
                        Location bedLocation = player.getBedSpawnLocation() != null ? player.getBedSpawnLocation() : player.getLocation();
                        if (particlesEnabled) {
                            try {
                                world.spawnParticle(
                                        Particle.valueOf(particleType),
                                        bedLocation.clone().add(0, 1.0, 0),
                                        particleCount,
                                        0.5, 0.5, 0.5,
                                        0.05
                                );
                            } catch (IllegalArgumentException e) {
                                logger.warning("Invalid particle type: " + particleType + ", using PORTAL");
                                particleType = "PORTAL";
                            }
                        }
                        if (soundEnabled && ticks % soundInterval == 0) {
                            try {
                                world.playSound(bedLocation, Sound.valueOf(soundType), soundVolume, soundPitch);
                            } catch (IllegalArgumentException e) {
                                logger.warning("Invalid sound type: " + soundType + ", using BLOCK_PORTAL_AMBIENT");
                                soundType = "BLOCK_PORTAL_AMBIENT";
                            }
                        }
                    }

                    if (world.getTime() < 1000 || world.getTime() > 23000) {
                        world.setTime(targetTime);
                        world.setStorm(false);
                        if (announceSleep) {
                            MessageUtils.broadcast(plugin, messages, "night_skipped");
                        }
                        sleepingPlayers.clear();
                        stopTimeSkip();
                        scheduleNightReset();
                    }
                }
                ticks++;
                if (ticks >= maxTicks) {
                    for (World world : plugin.getServer().getWorlds()) {
                        if (world.getEnvironment() == World.Environment.NORMAL) {
                            world.setTime(targetTime);
                            world.setStorm(false);
                        }
                    }
                    if (announceSleep) {
                        MessageUtils.broadcast(plugin, messages, "night_skipped");
                    }
                    sleepingPlayers.clear();
                    stopTimeSkip();
                    scheduleNightReset();
                }
            }
        };
        timeSkipTask.runTaskTimer(plugin, 0L, 1L);
    }

    private void scheduleNightReset() {
        if (!restrictEveryOtherNight) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : plugin.getServer().getWorlds()) {
                    if (world.getEnvironment() == World.Environment.NORMAL && world.getTime() > 13000 && world.getTime() < 14000) {
                        if (timeSkipTask == null) { // Ensure no skip is in progress
                            canSkipNight = true;
                            logger.info("Night reset: canSkipNight set to true at time " + world.getTime());
                            cancel();
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 200L);
    }

    private void stopTimeSkip() {
        if (timeSkipTask != null) {
            timeSkipTask.cancel();
            timeSkipTask = null;
        }
    }
}
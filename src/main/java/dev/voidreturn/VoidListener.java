/*
 * Safe landing-spot search (air at feet and head, solid ground below, 3-block
 * radius scan) is based on NoVoidX by UnknowUser0:
 * https://github.com/UnknowUser0/NoVoidX
 *
 * Copyright 2025 UnknowUser0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package dev.voidreturn;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VoidListener implements Listener {

    private static final int SEARCH_RADIUS = 3;
    private static final int SAVE_INTERVAL_TICKS = 40; // 2s debounce window

    private final VoidReturnPlugin plugin;
    private final File dataFile;
    // Latest cross-world teleport origin per player; every cross-world hop overwrites the previous one.
    private final Map<UUID, Location> lastSource = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRescue = new ConcurrentHashMap<>();
    // Players currently being rescued by this plugin, so our own teleport does not overwrite their source record.
    private final Set<UUID> rescuing = ConcurrentHashMap.newKeySet();
    // Players inside an active countdown, so repeated move events do not re-trigger it.
    private final Set<UUID> countingDown = ConcurrentHashMap.newKeySet();
    private volatile boolean dirty = false;

    VoidListener(VoidReturnPlugin plugin, File dataFile) {
        this.plugin = plugin;
        this.dataFile = dataFile;
        loadSources();
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::tickSave, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
    }

    // MONITOR: record the final, non-cancelled outcome of the teleport.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (rescuing.contains(player.getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() == null || from.getWorld().equals(to.getWorld())) {
            return;
        }
        lastSource.put(player.getUniqueId(), from.clone());
        saveSourcesAsync();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        WorldConfig config = plugin.worldConfigs.get(to.getWorld().getName());
        if (config == null || to.getY() > config.voidThreshold()) {
            return;
        }

        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (countingDown.contains(id)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastRescue.get(id);
        if (last != null && now - last < config.cooldownMillis()) {
            return;
        }

        if (config.hasCountdown()) {
            startCountdown(player, config);
        } else {
            performRescue(player, config);
        }
    }

    private void startCountdown(Player player, WorldConfig config) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        lastRescue.put(id, now);
        countingDown.add(id);

        int totalSecs = config.delaySecs();
        int totalTicks = totalSecs * 20;

        BossBar bar = null;
        for (MessageSpec m : config.beforeMessages()) {
            if (m.type() == MsgType.BOSS_BAR) {
                bar = Bukkit.createBossBar(m.text(), BarColor.PURPLE, BarStyle.SOLID);
                bar.addPlayer(player);
                bar.setProgress(1.0);
                break;
            }
        }
        // Title/subtitle/chat are sent exactly once, with the title stay covering the whole countdown.
        sendMessages(player, config.beforeMessages(), totalSecs, bar, totalTicks + 20);

        final int[] elapsed = {0};
        final BossBar bossBar = bar;
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                task[0].cancel();
                if (bossBar != null) {
                    bossBar.removePlayer(player);
                }
                countingDown.remove(id);
                return;
            }
            elapsed[0]++;
            // Let the player fall freely; void/fall damage is cancelled by onDamage.
            player.setFallDistance(0f);
            if (elapsed[0] % 20 == 0) {
                updateCountdownBar(player, config, Math.max(0, totalSecs - elapsed[0] / 20), bossBar);
            }
            if (elapsed[0] >= totalTicks) {
                task[0].cancel();
                if (bossBar != null) {
                    bossBar.removePlayer(player);
                }
                countingDown.remove(id);
                performRescue(player, config);
            }
        }, 0, 1);
    }

    // Keep the countdown UI updated each second. Title/subtitle are refreshed with zero fade
    // so they stay visible even if another plugin overwrites them, without flicker.
    private void updateCountdownBar(Player player, WorldConfig config, int remaining, BossBar bar) {
        String title = null, subtitle = null, actionBar = null;
        for (MessageSpec m : config.beforeMessages()) {
            String text = ChatColor.translateAlternateColorCodes('&',
                    m.text().replace("{seconds}", String.valueOf(remaining)));
            switch (m.type()) {
                case TITLE -> title = text;
                case SUBTITLE -> subtitle = text;
                case ACTION_BAR -> actionBar = text;
                case BOSS_BAR -> {
                    if (bar != null) {
                        bar.setTitle(text);
                        bar.setProgress(config.delaySecs() > 0 ? (double) remaining / config.delaySecs() : 1.0);
                    }
                }
                default -> { }
            }
        }
        if (title != null || subtitle != null) {
            player.sendTitle(title == null ? "" : title, subtitle == null ? "" : subtitle, 0, 20, 0);
        }
        if (actionBar != null) {
            player.sendActionBar(actionBar);
        }
    }

    private void sendMessages(Player player, List<MessageSpec> messages, int remaining, BossBar bar, int titleStay) {
        String title = null, subtitle = null, actionBar = null, chat = null;
        for (MessageSpec m : messages) {
            String text = ChatColor.translateAlternateColorCodes('&',
                    m.text().replace("{seconds}", String.valueOf(remaining)));
            switch (m.type()) {
                case TITLE -> title = text;
                case SUBTITLE -> subtitle = text;
                case ACTION_BAR -> actionBar = text;
                case CHAT -> chat = text;
                case BOSS_BAR -> {
                    if (bar != null) {
                        bar.setTitle(text);
                    }
                }
            }
        }
        if (title != null || subtitle != null) {
            player.sendTitle(title == null ? "" : title, subtitle == null ? "" : subtitle, 0, titleStay, 0);
        }
        if (actionBar != null) {
            player.sendActionBar(actionBar);
        }
        if (chat != null) {
            player.sendMessage(chat);
        }
    }

    private void performRescue(Player player, WorldConfig config) {
        UUID id = player.getUniqueId();
        Location target = lastSource.get(id);
        if (target == null || !target.isWorldLoaded()) {
            target = new Location(player.getWorld(), config.fallbackX(), config.fallbackY(), config.fallbackZ(),
                    config.fallbackYaw(), config.fallbackPitch());
        }

        Location safe = findSafe(target);
        if (safe == null) {
            World world = target.getWorld();
            plugin.getLogger().warning("No safe spot near " + format(target) + " for " + player.getName()
                    + ", using spawn of world '" + world.getName() + "'");
            safe = world.getSpawnLocation();
        }

        lastRescue.put(id, System.currentTimeMillis());
        rescuing.add(id);
        try {
            player.teleport(safe);
            // Reset fall state so the player does not die from the earlier fall.
            player.setFallDistance(0f);
            player.setVelocity(new Vector(0, 0, 0));
            sendArrival(player, config);
        } finally {
            rescuing.remove(id);
        }
    }

    private void sendArrival(Player player, WorldConfig config) {
        List<MessageSpec> arrival = config.afterMessages();
        if (arrival.isEmpty()) {
            return;
        }
        BossBar bar = null;
        for (MessageSpec m : arrival) {
            if (m.type() == MsgType.BOSS_BAR) {
                bar = Bukkit.createBossBar(m.text(), BarColor.PURPLE, BarStyle.SOLID);
                bar.addPlayer(player);
                break;
            }
        }
        sendMessages(player, arrival, 0, bar, 40);
        if (bar != null) {
            final BossBar b = bar;
            Bukkit.getScheduler().runTaskLater(plugin, () -> b.removePlayer(player), 40);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        // Cooldown is transient; keep the source record (persisted) for when the player returns.
        lastRescue.remove(id);
        countingDown.remove(id);
        saveSourcesAsync();
    }

    // While a player is inside the countdown, let them fall through the void without dying.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !countingDown.contains(player.getUniqueId())) {
            return;
        }
        DamageCause cause = event.getCause();
        if (cause == DamageCause.VOID || cause == DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    private void loadSources() {
        if (!dataFile.exists()) {
            return;
        }
        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection sources = data.getConfigurationSection("sources");
        if (sources == null) {
            return;
        }
        for (String key : sources.getKeys(false)) {
            ConfigurationSection s = sources.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            World world = Bukkit.getWorld(s.getString("world", ""));
            if (world == null) {
                continue;
            }
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            lastSource.put(id, new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw"), (float) s.getDouble("pitch")));
        }
    }

    // Coalesce writes: mark dirty; the periodic async task flushes at most every 2s.
    private void saveSourcesAsync() {
        dirty = true;
    }

    private void tickSave() {
        if (dirty) {
            dirty = false;
            saveSources();
        }
    }

    void saveSources() {
        YamlConfiguration data = new YamlConfiguration();
        ConfigurationSection sources = data.createSection("sources");
        for (Map.Entry<UUID, Location> entry : lastSource.entrySet()) {
            Location loc = entry.getValue();
            World world = loc.getWorld();
            if (world == null) {
                continue;
            }
            ConfigurationSection s = sources.createSection(entry.getKey().toString());
            s.set("world", world.getName());
            s.set("x", loc.getX());
            s.set("y", loc.getY());
            s.set("z", loc.getZ());
            s.set("yaw", loc.getYaw());
            s.set("pitch", loc.getPitch());
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save " + dataFile.getName() + ": " + e.getMessage());
        }
    }

    private Location findSafe(Location start) {
        if (isSafe(start)) {
            return start;
        }
        for (int dy : new int[]{0, 1, -1}) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    Location check = start.clone().add(dx, dy, dz);
                    if (isSafe(check)) {
                        return check;
                    }
                }
            }
        }
        return null;
    }

    private boolean isSafe(Location location) {
        World world = location.getWorld();
        // Skip chunks that are not loaded to avoid synchronous chunk loads on the main thread.
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return false;
        }
        Block feet = location.getBlock();
        return feet.getType().isAir()
                && feet.getRelative(BlockFace.UP).getType().isAir()
                && feet.getRelative(BlockFace.DOWN).getType().isSolid();
    }

    private String format(Location location) {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f in '%s'",
                location.getX(), location.getY(), location.getZ(), location.getWorld().getName());
    }
}

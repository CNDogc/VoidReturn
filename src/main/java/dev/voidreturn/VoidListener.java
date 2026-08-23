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
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
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
        long now = System.currentTimeMillis();
        Long last = lastRescue.get(player.getUniqueId());
        if (last != null && now - last < config.cooldownMillis()) {
            return;
        }

        Location target = lastSource.get(player.getUniqueId());
        if (target == null || !target.isWorldLoaded()) {
            target = new Location(to.getWorld(), config.fallbackX(), config.fallbackY(), config.fallbackZ(),
                    config.fallbackYaw(), config.fallbackPitch());
        }

        Location safe = findSafe(target);
        if (safe == null) {
            World world = target.getWorld();
            plugin.getLogger().warning("No safe spot near " + format(target) + " for " + player.getName()
                    + ", using spawn of world '" + world.getName() + "'");
            safe = world.getSpawnLocation();
        }

        UUID id = player.getUniqueId();
        lastRescue.put(id, now);
        rescuing.add(id);
        try {
            player.teleport(safe);
            // Reset fall state so the player does not die from the earlier fall.
            player.setFallDistance(0f);
            player.setVelocity(new Vector(0, 0, 0));
            plugin.sendRescueMessage(player);
        } finally {
            rescuing.remove(id);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Cooldown is transient; keep the source record (persisted) for when the player returns.
        lastRescue.remove(event.getPlayer().getUniqueId());
        saveSourcesAsync();
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

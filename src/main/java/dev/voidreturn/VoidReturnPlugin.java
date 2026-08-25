/*
 * VoidReturn - Source-memory void teleport plugin for Paper
 * Copyright (C) 2026 狗晨Yz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package dev.voidreturn;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VoidReturnPlugin extends JavaPlugin implements CommandExecutor {

    final Map<String, WorldConfig> worldConfigs = new HashMap<>();
    private VoidListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        loadWorldConfigs();
        listener = new VoidListener(this, new File(getDataFolder(), "data.yml"));
        getServer().getPluginManager().registerEvents(listener, this);
        PluginCommand command = getCommand("voidreturn");
        if (command != null) {
            command.setExecutor(this);
        } else {
            getLogger().warning("Command 'voidreturn' missing from plugin.yml");
        }
    }

    @Override
    public void onDisable() {
        if (listener != null) {
            getServer().getScheduler().cancelTasks(this);
            listener.saveSources();
        }
    }

    // Merge new fields from the bundled default into an existing config, preserving user edits.
    private void migrateConfig() {
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                new InputStreamReader(getResource("config.yml"), StandardCharsets.UTF_8));
        int bundledVersion = bundled.getInt("config-version", 1);
        if (getConfig().getInt("config-version", 0) >= bundledVersion) {
            return;
        }
        ConfigurationSection worlds = getConfig().getConfigurationSection("enabled-worlds");
        ConfigurationSection template = bundled.getConfigurationSection("enabled-worlds.spawn");
        if (worlds != null && template != null) {
            for (String world : worlds.getKeys(false)) {
                ConfigurationSection ws = worlds.getConfigurationSection(world);
                if (ws == null) {
                    continue;
                }
                if (!ws.contains("delay-secs")) {
                    ws.set("delay-secs", template.getInt("delay-secs", 3));
                }
                if (!ws.contains("before-messages")) {
                    // carry over old "countdown" values, otherwise use the template defaults
                    Object old = ws.get("countdown");
                    ws.set("before-messages", old != null ? old : template.getList("before-messages"));
                }
                if (!ws.contains("after-messages")) {
                    Object old = ws.get("arrival");
                    ws.set("after-messages", old != null ? old : template.getList("after-messages"));
                }
                // remove obsolete keys so the migrated config stays clean
                ws.set("countdown", null);
                ws.set("arrival", null);
            }
        }
        // obsolete global notification section from very old versions
        getConfig().set("message", null);
        getConfig().set("config-version", bundledVersion);
        saveConfig();
        getLogger().info("Config migrated to version " + bundledVersion);
    }

    void loadWorldConfigs() {
        worldConfigs.clear();
        ConfigurationSection root = getConfig().getConfigurationSection("enabled-worlds");
        if (root == null) {
            getLogger().warning("No 'enabled-worlds' section in config.yml, void detection disabled");
            return;
        }
        for (String world : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(world);
            if (section == null) {
                continue;
            }
            ConfigurationSection fallback = section.getConfigurationSection("fallback");
            worldConfigs.put(world, new WorldConfig(
                    section.getDouble("void-threshold", -64.0),
                    section.getInt("cooldown-secs", 3) * 1000L,
                    fallback == null ? 0.5 : fallback.getDouble("x", 0.5),
                    fallback == null ? 70.0 : fallback.getDouble("y", 70.0),
                    fallback == null ? 0.5 : fallback.getDouble("z", 0.5),
                    (float) (fallback == null ? 0.0 : fallback.getDouble("yaw", 0.0)),
                    (float) (fallback == null ? 0.0 : fallback.getDouble("pitch", 0.0)),
                    section.getInt("delay-secs", 0),
                    parseMessages(section, "before-messages"),
                    parseMessages(section, "after-messages")));
        }
        getLogger().info("Void detection enabled for worlds: " + worldConfigs.keySet());
    }

    private List<MessageSpec> parseMessages(ConfigurationSection section, String key) {
        List<MessageSpec> list = new ArrayList<>();
        for (Map<?, ?> item : section.getMapList(key)) {
            MsgType type = WorldConfig.parseType(String.valueOf(item.get("type")));
            if (type != null) {
                list.add(new MessageSpec(type, String.valueOf(item.get("text"))));
            }
        }
        return list;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("voidreturn.reload")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadWorldConfigs();
            sender.sendMessage("VoidReturn reloaded. Enabled worlds: " + worldConfigs.keySet());
            return true;
        }
        sender.sendMessage("Usage: /" + label + " reload");
        return true;
    }
}

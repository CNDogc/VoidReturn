package dev.voidreturn;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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
                    parseMessages(section, "countdown"),
                    parseMessages(section, "arrival")));
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

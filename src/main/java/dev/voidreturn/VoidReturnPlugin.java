package dev.voidreturn;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class VoidReturnPlugin extends JavaPlugin implements CommandExecutor {

    final Map<String, WorldConfig> worldConfigs = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadWorldConfigs();
        getServer().getPluginManager().registerEvents(new VoidListener(this), this);
        PluginCommand command = getCommand("voidreturn");
        if (command != null) {
            command.setExecutor(this);
        } else {
            getLogger().warning("Command 'voidreturn' missing from plugin.yml");
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
                    (float) (fallback == null ? 0.0 : fallback.getDouble("pitch", 0.0))));
        }
        getLogger().info("Void detection enabled for worlds: " + worldConfigs.keySet());
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

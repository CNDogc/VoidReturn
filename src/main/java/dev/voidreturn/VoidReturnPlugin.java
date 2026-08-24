package dev.voidreturn;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VoidReturnPlugin extends JavaPlugin implements CommandExecutor {

    final Map<String, WorldConfig> worldConfigs = new HashMap<>();
    String msgTitle;
    String msgSubtitle;
    String msgChat;
    private VoidListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadWorldConfigs();
        loadMessageConfig();
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
            List<MessageSpec> messages = new ArrayList<>();
            for (Map<?, ?> item : section.getMapList("messages")) {
                MsgType type = WorldConfig.parseType(String.valueOf(item.get("type")));
                if (type != null) {
                    messages.add(new MessageSpec(type, String.valueOf(item.get("text"))));
                }
            }
            worldConfigs.put(world, new WorldConfig(
                    section.getDouble("void-threshold", -64.0),
                    section.getInt("cooldown-secs", 3) * 1000L,
                    fallback == null ? 0.5 : fallback.getDouble("x", 0.5),
                    fallback == null ? 70.0 : fallback.getDouble("y", 70.0),
                    fallback == null ? 0.5 : fallback.getDouble("z", 0.5),
                    (float) (fallback == null ? 0.0 : fallback.getDouble("yaw", 0.0)),
                    (float) (fallback == null ? 0.0 : fallback.getDouble("pitch", 0.0)),
                    section.getInt("delay-secs", 0),
                    messages));
        }
        getLogger().info("Void detection enabled for worlds: " + worldConfigs.keySet());
    }

    void loadMessageConfig() {
        ConfigurationSection m = getConfig().getConfigurationSection("message");
        msgTitle = m == null ? null : m.getString("title");
        msgSubtitle = m == null ? null : m.getString("subtitle");
        msgChat = m == null ? null : m.getString("chat");
    }

    void sendRescueMessage(Player player) {
        if ((msgTitle != null && !msgTitle.isEmpty()) || (msgSubtitle != null && !msgSubtitle.isEmpty())) {
            player.sendTitle(msgTitle == null || msgTitle.isEmpty() ? "" : ChatColor.translateAlternateColorCodes('&', msgTitle),
                    msgSubtitle == null || msgSubtitle.isEmpty() ? "" : ChatColor.translateAlternateColorCodes('&', msgSubtitle),
                    10, 70, 20);
        }
        if (msgChat != null && !msgChat.isEmpty()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msgChat));
        }
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
            loadMessageConfig();
            sender.sendMessage("VoidReturn reloaded. Enabled worlds: " + worldConfigs.keySet());
            return true;
        }
        sender.sendMessage("Usage: /" + label + " reload");
        return true;
    }
}

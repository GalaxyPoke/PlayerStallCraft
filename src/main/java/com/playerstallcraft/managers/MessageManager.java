package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {

    private final PlayerStallCraft plugin;
    private FileConfiguration messagesConfig;
    private String prefix;

    public MessageManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        this.prefix = colorize(plugin.getConfigManager().getMessagePrefix());
    }

    public String getMessage(String path) {
        String message = messagesConfig.getString(path, "&c消息未找到: " + path);
        return colorize(message);
    }

    public String getMessage(String path, Map<String, String> placeholders) {
        String message = getMessage(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(prefix + getMessage(path));
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(prefix + getMessage(path, placeholders));
    }

    public void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(prefix + colorize(message));
    }

    public static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static Map<String, String> placeholders(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length - 1; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    public String getPrefix() {
        return prefix;
    }
}

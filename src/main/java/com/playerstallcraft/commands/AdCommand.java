package com.playerstallcraft.commands;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.AdSlot;
import com.playerstallcraft.models.Advertisement;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 广告系统命令
 * /ad create <名称> <每小时价格> - 创建广告位 (管理员)
 * /ad delete <广告位ID> - 删除广告位 (管理员)
 * /ad list - 查看所有广告位
 * /ad place <广告位ID> <标题> <时长(小时)> [描述] - 投放广告
 * /ad cancel <广告ID> - 取消广告
 * /ad my - 查看我的广告
 */
public class AdCommand implements CommandExecutor, TabCompleter {

    private final PlayerStallCraft plugin;

    public AdCommand(PlayerStallCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args);
            case "delete" -> handleDelete(player, args);
            case "list" -> handleList(player);
            case "place" -> handlePlace(player, args);
            case "cancel" -> handleCancel(player, args);
            case "my" -> handleMy(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player player) {
        plugin.getMessageManager().sendRaw(player, "&6&l=== 广告系统帮助 ===");
        plugin.getMessageManager().sendRaw(player, "&e/ad list &7- 查看所有广告位");
        plugin.getMessageManager().sendRaw(player, "&e/ad place <广告位ID> <标题> <时长> [描述] &7- 投放广告");
        plugin.getMessageManager().sendRaw(player, "&e/ad cancel <广告ID> &7- 取消广告");
        plugin.getMessageManager().sendRaw(player, "&e/ad my &7- 查看我的广告");
        
        if (player.hasPermission("playerstallcraft.admin")) {
            plugin.getMessageManager().sendRaw(player, "&c&l--- 管理员命令 ---");
            plugin.getMessageManager().sendRaw(player, "&c/ad create <名称> <每小时价格> &7- 创建广告位");
            plugin.getMessageManager().sendRaw(player, "&c/ad delete <广告位ID> &7- 删除广告位");
        }
    }

    private void handleCreate(Player player, String[] args) {
        if (!player.hasPermission("playerstallcraft.admin")) {
            plugin.getMessageManager().sendRaw(player, "&c你没有权限执行此命令");
            return;
        }

        if (args.length < 3) {
            plugin.getMessageManager().sendRaw(player, "&c用法: /ad create <名称> <每小时价格>");
            return;
        }

        String name = args[1];
        double pricePerHour;
        try {
            pricePerHour = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendRaw(player, "&c价格必须是数字");
            return;
        }

        String currencyType = args.length > 3 ? args[3] : "nye";
        
        AdSlot slot = plugin.getAdManager().createAdSlot(name, player.getLocation(), pricePerHour, currencyType);
        String currencyName = plugin.getEconomyManager().getCurrencyName(currencyType);
        plugin.getMessageManager().sendRaw(player, "&a广告位创建成功! ID: " + slot.getId() + 
                ", 价格: " + pricePerHour + " " + currencyName + "/小时");
    }

    private void handleDelete(Player player, String[] args) {
        if (!player.hasPermission("playerstallcraft.admin")) {
            plugin.getMessageManager().sendRaw(player, "&c你没有权限执行此命令");
            return;
        }

        if (args.length < 2) {
            plugin.getMessageManager().sendRaw(player, "&c用法: /ad delete <广告位ID>");
            return;
        }

        int slotId;
        try {
            slotId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendRaw(player, "&c广告位ID必须是数字");
            return;
        }

        if (plugin.getAdManager().deleteAdSlot(slotId)) {
            plugin.getMessageManager().sendRaw(player, "&a广告位已删除");
        } else {
            plugin.getMessageManager().sendRaw(player, "&c广告位不存在");
        }
    }

    private void handleList(Player player) {
        var slots = plugin.getAdManager().getAdSlots();
        
        if (slots.isEmpty()) {
            plugin.getMessageManager().sendRaw(player, "&7暂无广告位");
            return;
        }

        plugin.getMessageManager().sendRaw(player, "&6&l=== 广告位列表 ===");
        for (AdSlot slot : slots.values()) {
            String currencyName = plugin.getEconomyManager().getCurrencyName(slot.getCurrencyType());
            int adCount = plugin.getAdManager().getSlotAds(slot.getId()).size();
            String status = slot.isActive() ? "&a可用" : "&c关闭";
            
            plugin.getMessageManager().sendRaw(player, 
                    "&e#" + slot.getId() + " &f" + slot.getName() + 
                    " &7- " + String.format("%.0f", slot.getPricePerHour()) + " " + currencyName + "/小时" +
                    " &7[" + adCount + "个广告] " + status);
        }
    }

    private void handlePlace(Player player, String[] args) {
        if (args.length < 4) {
            plugin.getMessageManager().sendRaw(player, "&c用法: /ad place <广告位ID> <标题> <时长(小时)> [描述]");
            return;
        }

        int slotId;
        try {
            slotId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendRaw(player, "&c广告位ID必须是数字");
            return;
        }

        String title = args[2];
        int hours;
        try {
            hours = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendRaw(player, "&c时长必须是整数小时");
            return;
        }

        if (hours <= 0 || hours > 168) { // 最长一周
            plugin.getMessageManager().sendRaw(player, "&c时长必须在1-168小时之间");
            return;
        }

        String description = args.length > 4 ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : "";
        
        // 获取玩家手持物品作为图标
        String iconMaterial = "GOLD_INGOT";
        if (player.getInventory().getItemInMainHand().getType().name() != null) {
            iconMaterial = player.getInventory().getItemInMainHand().getType().name();
        }

        // 检查玩家是否有商铺
        int shopId = 0;
        var playerShops = plugin.getShopManager().getPlayerShops(player.getUniqueId());
        if (!playerShops.isEmpty()) {
            shopId = playerShops.get(0).getId();
        }

        plugin.getAdManager().placeAdvertisement(player, slotId, title, description, iconMaterial, hours, shopId);
    }

    private void handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendRaw(player, "&c用法: /ad cancel <广告ID>");
            return;
        }

        int adId;
        try {
            adId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendRaw(player, "&c广告ID必须是数字");
            return;
        }

        plugin.getAdManager().cancelAdvertisement(adId, player);
    }

    private void handleMy(Player player) {
        var ads = plugin.getAdManager().getPlayerAds(player.getUniqueId());
        
        if (ads.isEmpty()) {
            plugin.getMessageManager().sendRaw(player, "&7你还没有投放任何广告");
            return;
        }

        plugin.getMessageManager().sendRaw(player, "&6&l=== 我的广告 ===");
        for (Advertisement ad : ads) {
            String status = ad.isExpired() ? "&c已过期" : "&a" + ad.getRemainingTimeString();
            plugin.getMessageManager().sendRaw(player, 
                    "&e#" + ad.getId() + " &f" + ad.getTitle() + " &7- " + status);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("list", "place", "cancel", "my"));
            if (sender.hasPermission("playerstallcraft.admin")) {
                subCommands.add("create");
                subCommands.add("delete");
            }
            for (String sub : subCommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("place") || args[0].equalsIgnoreCase("delete")) {
                for (Integer slotId : plugin.getAdManager().getAdSlots().keySet()) {
                    completions.add(String.valueOf(slotId));
                }
            } else if (args[0].equalsIgnoreCase("cancel") && sender instanceof Player player) {
                for (Advertisement ad : plugin.getAdManager().getPlayerAds(player.getUniqueId())) {
                    completions.add(String.valueOf(ad.getId()));
                }
            }
        }
        
        return completions;
    }
}

package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.BuyRequest;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BatchFulfillGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private final int requestId;
    private Inventory inventory;
    private int amount = 1;
    private boolean waitingForInput = false;

    public BatchFulfillGUI(PlayerStallCraft plugin, Player player, int requestId) {
        this.plugin = plugin;
        this.player = player;
        this.requestId = requestId;
    }

    public void open() {
        BuyRequest request = plugin.getBuyRequestManager().getRequest(requestId);
        if (request == null) {
            plugin.getMessageManager().sendRaw(player, "&c求购不存在!");
            return;
        }
        inventory = Bukkit.createInventory(null, 27, "§e批量接单 — 指定数量");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshDisplay(request);
        player.openInventory(inventory);
    }

    private void refreshDisplay(BuyRequest request) {
        inventory.clear();

        ItemStack bg = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inventory.setItem(i, bg);

        String zhName = plugin.getGlobalMarketManager().getChineseNamePublic(request.getItemType());
        String itemName = zhName != null ? zhName : request.getItemType().name();
        int remaining = request.getRemainingAmount();
        int inBag = countItems(player, request.getItemType());

        // 请求信息展示
        ItemStack info = new ItemStack(request.getItemType());
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§e§l" + itemName);
            List<String> lore = new ArrayList<>();
            lore.add("§7求购者: §f" + request.getPlayerName());
            lore.add("§7剩余需求: §f" + remaining + " 个");
            lore.add("§7买家出价: §a" + plugin.getEconomyManager().formatCurrency(request.getPrice(), request.getCurrencyType()) + "§7/个");
            lore.add("§7你的背包: §f" + inBag + " 个");
            infoMeta.setLore(lore);
            info.setItemMeta(infoMeta);
        }
        inventory.setItem(4, info);

        // 数量调整按钮
        inventory.setItem(10, createItem(Material.IRON_NUGGET, "§c-10", "§7左键减少10"));
        inventory.setItem(11, createItem(Material.IRON_NUGGET, "§c-1", "§7左键减少1"));
        inventory.setItem(13, createAmountDisplay(amount, request));
        inventory.setItem(15, createItem(Material.GOLD_NUGGET, "§a+1", "§7左键增加1"));
        inventory.setItem(16, createItem(Material.GOLD_NUGGET, "§a+10", "§7左键增加10"));

        // 全量按钮
        int maxProvide = Math.min(remaining, inBag);
        inventory.setItem(12, createItem(Material.PAPER, "§7输入数量", "§7点击在聊天输入自定义数量"));
        inventory.setItem(14, createItem(Material.EMERALD_BLOCK, "§a全量 (" + maxProvide + "个)", "§7提供全部可接数量"));

        // 确认/取消
        double earn = request.getPrice() * amount * (1 - plugin.getConfigManager().getConfig().getDouble("stall.tax-rate", 0.05));
        inventory.setItem(20, createItem(Material.LIME_CONCRETE, "§a§l确认接单",
            "§7提供数量: §f" + amount + " 个",
            "§7税后到手: §a" + plugin.getEconomyManager().formatCurrency(earn, request.getCurrencyType()),
            "",
            "§a点击确认"));
        inventory.setItem(22, createItem(Material.YELLOW_CONCRETE, "§e返回市场", "§7返回求购列表"));
        inventory.setItem(24, createItem(Material.RED_CONCRETE, "§c取消", "§7关闭"));
    }

    private ItemStack createAmountDisplay(int amount, BuyRequest request) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l接单数量: §f" + amount);
            double totalEarn = request.getPrice() * amount;
            double taxRate = plugin.getConfigManager().getConfig().getDouble("stall.tax-rate", 0.05);
            meta.setLore(Arrays.asList(
                "§7总收入: §a" + plugin.getEconomyManager().formatCurrency(totalEarn, request.getCurrencyType()),
                "§7税后实得: §e" + plugin.getEconomyManager().formatCurrency(totalEarn * (1 - taxRate), request.getCurrencyType())
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private int countItems(Player p, Material mat) {
        int count = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == mat) count += item.getAmount();
        }
        return count;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;
        event.setCancelled(true);

        BuyRequest request = plugin.getBuyRequestManager().getRequest(requestId);
        if (request == null) { player.closeInventory(); return; }

        int remaining = request.getRemainingAmount();
        int inBag = countItems(player, request.getItemType());
        int maxProvide = Math.min(remaining, inBag);
        int slot = event.getRawSlot();

        switch (slot) {
            case 10 -> { amount = Math.max(1, amount - 10); refreshDisplay(request); }
            case 11 -> { amount = Math.max(1, amount - 1); refreshDisplay(request); }
            case 13 -> { /* 数量展示，不操作 */ }
            case 15 -> { amount = Math.min(maxProvide, amount + 1); refreshDisplay(request); }
            case 16 -> { amount = Math.min(maxProvide, amount + 10); refreshDisplay(request); }
            case 12 -> {
                waitingForInput = true;
                player.closeInventory();
                plugin.getMessageManager().sendRaw(player, "&e请在聊天中输入接单数量 (输入 cancel 取消):");
            }
            case 14 -> { amount = maxProvide; refreshDisplay(request); }
            case 20 -> {
                if (amount > 0 && amount <= maxProvide) {
                    player.closeInventory();
                    plugin.getBuyRequestManager().fulfillPartial(requestId, player, amount);
                }
            }
            case 22 -> { player.closeInventory(); new BuyRequestGUI(plugin, player).open(); }
            case 24 -> player.closeInventory();
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (!waitingForInput) return;
        event.setCancelled(true);
        waitingForInput = false;
        String msg = event.getMessage().trim();
        if (msg.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, this::open);
            return;
        }
        try {
            int input = Integer.parseInt(msg);
            BuyRequest request = plugin.getBuyRequestManager().getRequest(requestId);
            if (request != null) {
                int max = Math.min(request.getRemainingAmount(), countItems(player, request.getItemType()));
                amount = Math.max(1, Math.min(input, max));
            }
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendRaw(player, "&c请输入有效数字!");
        }
        Bukkit.getScheduler().runTask(plugin, this::open);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;
        if (waitingForInput) return;
        HandlerList.unregisterAll(this);
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}

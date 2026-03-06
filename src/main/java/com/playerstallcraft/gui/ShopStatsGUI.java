package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.managers.TransactionLogManager.SellerStats;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ShopStatsGUI implements Listener {

    private static final int PERIOD_30D = 0;
    private static final int PERIOD_7D  = 1;
    private static final int PERIOD_1D  = 2;

    private final PlayerStallCraft plugin;
    private final Player player;
    private Inventory inventory;
    private boolean registered = false;

    private int period = PERIOD_30D;

    // 统计数据（异步加载后填充）
    private SellerStats vaultStats;
    private SellerStats nyeStats;
    private List<String> recentSales = new ArrayList<>();
    private boolean loading = true;

    public ShopStatsGUI(PlayerStallCraft plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 54, "§6我的交易统计");
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        drawLoading();
        player.openInventory(inventory);
        loadStats();
    }

    private void loadStats() {
        loading = true;
        long sinceMs = System.currentTimeMillis() - periodMs();
        plugin.getTransactionLogManager().getSellerStatsAsync(player.getUniqueId(), "vault", sinceMs, vs -> {
            vaultStats = vs;
            plugin.getTransactionLogManager().getSellerStatsAsync(player.getUniqueId(), "nye", sinceMs, ns -> {
                nyeStats = ns;
                plugin.getTransactionLogManager().getPlayerSalesAsync(player.getUniqueId(), 10, sales -> {
                    recentSales = sales;
                    loading = false;
                    if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory().equals(inventory)) {
                        refreshDisplay();
                    }
                });
            });
        });
    }

    private void drawLoading() {
        inventory.clear();
        ItemStack loading = createItem(Material.CLOCK, "§e加载中...", "§7正在查询统计数据，请稍候");
        inventory.setItem(22, loading);
        fillBorder();
    }

    private void refreshDisplay() {
        inventory.clear();
        fillBorder();

        // ── 标题栏 ──
        inventory.setItem(4, createItem(Material.GOLD_BLOCK, "§6§l交易统计看板",
            "§7统计范围: §e" + periodLabel(),
            "",
            "§7点击左边按钮切换时间范围"));

        // ── 周期切换 ──
        inventory.setItem(0, createItem(period == PERIOD_30D ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
            period == PERIOD_30D ? "§a§l▶ 近30天" : "§7近30天"));
        inventory.setItem(1, createItem(period == PERIOD_7D  ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
            period == PERIOD_7D  ? "§a§l▶ 近7天"  : "§7近7天"));
        inventory.setItem(2, createItem(period == PERIOD_1D  ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
            period == PERIOD_1D  ? "§a§l▶ 今日"   : "§7今日"));

        // ── 金币统计 ──
        if (plugin.getEconomyManager().hasVault() && vaultStats != null) {
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7成交笔数: §e" + vaultStats.totalOrders + " 笔");
            lore.add("§7总销售额: §a" + plugin.getEconomyManager().formatCurrency(vaultStats.totalRevenue, "vault"));
            lore.add("");
            lore.add("§7§l畅销商品 Top 3:");
            if (vaultStats.topItems.isEmpty()) {
                lore.add("§8  暂无数据");
            } else {
                int rank = 1;
                for (Map.Entry<String, Integer> e : vaultStats.topItems) {
                    lore.add("§8  " + rank++ + ". §f" + e.getKey() + " §7×" + e.getValue());
                }
            }
            inventory.setItem(20, createItemWithLore(Material.GOLD_INGOT, "§e§l金币销售", lore));
        }

        // ── 鸽币统计 ──
        if (plugin.getEconomyManager().hasNYE() && nyeStats != null) {
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7成交笔数: §e" + nyeStats.totalOrders + " 笔");
            lore.add("§7总销售额: §a" + plugin.getEconomyManager().formatCurrency(nyeStats.totalRevenue, "nye"));
            lore.add("");
            lore.add("§7§l畅销商品 Top 3:");
            if (nyeStats.topItems.isEmpty()) {
                lore.add("§8  暂无数据");
            } else {
                int rank = 1;
                for (Map.Entry<String, Integer> e : nyeStats.topItems) {
                    lore.add("§8  " + rank++ + ". §f" + e.getKey() + " §7×" + e.getValue());
                }
            }
            inventory.setItem(24, createItemWithLore(Material.PAPER, "§a§l鸽币销售", lore));
        }

        // ── 最近10笔销售 ──
        List<String> recentLore = new ArrayList<>();
        recentLore.add("");
        if (recentSales.isEmpty()) {
            recentLore.add("§8暂无记录");
        } else {
            for (String line : recentSales) {
                recentLore.add("§7" + line);
            }
        }
        inventory.setItem(31, createItemWithLore(Material.BOOK, "§b§l最近销售记录", recentLore));

        // ── 返回 ──
        inventory.setItem(49, createItem(Material.ARROW, "§7返回"));
    }

    private void fillBorder() {
        ItemStack pane = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 9; i < 18; i++) inventory.setItem(i, pane);
        for (int i = 36; i < 45; i++) inventory.setItem(i, pane);
    }

    private long periodMs() {
        return switch (period) {
            case PERIOD_7D -> 7L  * 24 * 60 * 60 * 1000;
            case PERIOD_1D -> 24L * 60 * 60 * 1000;
            default        -> 30L * 24 * 60 * 60 * 1000;
        };
    }

    private String periodLabel() {
        return switch (period) {
            case PERIOD_7D -> "近7天";
            case PERIOD_1D -> "今日";
            default        -> "近30天";
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(player)) return;
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        if (loading) return;

        int slot = event.getRawSlot();
        switch (slot) {
            case 0 -> { period = PERIOD_30D; loadStats(); drawLoading(); }
            case 1 -> { period = PERIOD_7D;  loadStats(); drawLoading(); }
            case 2 -> { period = PERIOD_1D;  loadStats(); drawLoading(); }
            case 49 -> player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (!event.getInventory().equals(inventory)) return;
        HandlerList.unregisterAll(this);
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItemWithLore(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}

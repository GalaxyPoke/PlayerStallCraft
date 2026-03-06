package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.managers.GlobalMarketManager.GlobalMarketItem;
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
import java.util.Comparator;
import java.util.List;

public class MarketAdminGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player admin;
    private Inventory inventory;
    private int currentPage = 0;
    private List<GlobalMarketItem> listings;
    private static final int ITEMS_PER_PAGE = 45;

    public MarketAdminGUI(PlayerStallCraft plugin, Player admin) {
        this.plugin = plugin;
        this.admin = admin;
        this.listings = new ArrayList<>(plugin.getGlobalMarketManager().getAllActiveListings());
        this.listings.sort(Comparator.comparingInt(GlobalMarketItem::getId).reversed());
    }

    public void open() {
        int total = listings.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / ITEMS_PER_PAGE));
        inventory = Bukkit.createInventory(null, 54, "§4[管理] §c全服市场 §7第" + (currentPage + 1) + "/" + totalPages + "页");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshDisplay();
        admin.openInventory(inventory);
    }

    private void refreshDisplay() {
        inventory.clear();

        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, listings.size());

        for (int i = startIndex; i < endIndex; i++) {
            GlobalMarketItem listing = listings.get(i);
            inventory.setItem(i - startIndex, createAdminDisplay(listing));
        }

        // 底部控制栏
        if (currentPage > 0) {
            inventory.setItem(45, ctrl(Material.ARROW, "§a上一页", "§7点击翻页"));
        }

        int totalPages = (int) Math.ceil((double) listings.size() / ITEMS_PER_PAGE);
        if (currentPage < totalPages - 1) {
            inventory.setItem(53, ctrl(Material.ARROW, "§a下一页", "§7点击翻页"));
        }

        inventory.setItem(49, ctrl(Material.SUNFLOWER, "§e刷新",
            "§7共 §f" + listings.size() + " §7件在售商品",
            "§7当前页: §f" + (currentPage + 1) + "/" + Math.max(1, totalPages)));
        inventory.setItem(51, ctrl(Material.BARRIER, "§c关闭", "§7关闭管理界面"));
        inventory.setItem(48, ctrl(Material.BOOK, "§6操作说明",
            "§7左键: 查看商品信息",
            "§cShift+左键: 强制下架该商品",
            "§7强制下架会将物品退还给卖家"));
    }

    private ItemStack createAdminDisplay(GlobalMarketItem listing) {
        ItemStack item = plugin.getGlobalMarketManager().deserializeItemPublic(listing.getItemData());
        if (item == null) item = new ItemStack(Material.BARRIER);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§7═══════════════════");
            lore.add("§7ID: §f" + listing.getId());
            lore.add("§7卖家: §f" + listing.getSellerName());
            lore.add("§7数量: §f" + listing.getAmount());
            lore.add("§7价格: §e" + plugin.getEconomyManager().formatCurrency(listing.getPrice(), listing.getCurrencyType()));
            long remainMs = listing.getExpireTime() - System.currentTimeMillis();
            if (remainMs > 0) {
                long hours = remainMs / 3600000;
                long mins  = (remainMs % 3600000) / 60000;
                lore.add("§7剩余: §f" + hours + "h" + mins + "m");
            } else {
                lore.add("§c已过期");
            }
            lore.add("§7═══════════════════");
            lore.add("§cShift+左键强制下架");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack ctrl(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(admin)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot < 45) {
            int idx = currentPage * ITEMS_PER_PAGE + slot;
            if (idx < listings.size()) {
                GlobalMarketItem listing = listings.get(idx);
                if (event.isShiftClick() && event.isLeftClick()) {
                    if (!admin.hasPermission("stall.admin")) {
                        plugin.getMessageManager().sendRaw(admin, "&c你没有管理员权限!");
                        return;
                    }
                    plugin.getGlobalMarketManager().cancelListing(admin, listing.getId());
                    plugin.getMessageManager().sendRaw(admin, "&a已强制下架商品 ID:" + listing.getId() + " (卖家:" + listing.getSellerName() + ")");
                    listings.remove(idx);
                    refreshDisplay();
                }
            }
            return;
        }

        switch (slot) {
            case 45 -> { if (currentPage > 0) { currentPage--; refreshDisplay(); } }
            case 49 -> {
                listings = new ArrayList<>(plugin.getGlobalMarketManager().getAllActiveListings());
                listings.sort(Comparator.comparingInt(GlobalMarketItem::getId).reversed());
                currentPage = 0;
                refreshDisplay();
            }
            case 51 -> admin.closeInventory();
            case 53 -> {
                int totalPages = (int) Math.ceil((double) listings.size() / ITEMS_PER_PAGE);
                if (currentPage < totalPages - 1) { currentPage++; refreshDisplay(); }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(admin)) return;
        HandlerList.unregisterAll(this);
    }
}

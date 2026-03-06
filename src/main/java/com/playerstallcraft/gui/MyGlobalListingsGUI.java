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
import java.util.List;

public class MyGlobalListingsGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private Inventory inventory;
    private int currentPage = 0;
    private List<GlobalMarketItem> myListings;
    private List<GlobalMarketItem> soldHistory;
    private boolean showingSold = false; // false=在售中, true=已售出
    private static final int ITEMS_PER_PAGE = 45;
    private boolean registered = false;

    public MyGlobalListingsGUI(PlayerStallCraft plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.myListings = plugin.getGlobalMarketManager().getSellerListings(player.getUniqueId());
        this.soldHistory = new ArrayList<>();
    }

    public void open() {
        String title = showingSold ? "§6已售出记录 §7第" + (currentPage + 1) + "页" : "§b我的上架 §7第" + (currentPage + 1) + "页";
        inventory = Bukkit.createInventory(null, 54, title);
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        refreshDisplay();
        player.openInventory(inventory);
        plugin.getGlobalMarketManager().getSellerSoldHistoryAsync(player.getUniqueId(), 100, history -> {
            this.soldHistory = history;
            if (player.getOpenInventory().getTopInventory().equals(inventory)) refreshDisplay();
        });
    }

    private void refreshDisplay() {
        inventory.clear();
        
        List<GlobalMarketItem> displayList = showingSold ? soldHistory : myListings;
        
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, displayList.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            GlobalMarketItem listing = displayList.get(i);
            ItemStack displayItem = showingSold ? createSoldDisplay(listing) : createListingDisplay(listing);
            inventory.setItem(i - startIndex, displayItem);
        }
        
        // 底部控制栏
        if (currentPage > 0) {
            ItemStack prevPage = createControlItem(Material.ARROW, "§a上一页", "§7点击翻到上一页");
            inventory.setItem(45, prevPage);
        }
        
        // 切换在售/已售出
        ItemStack toggleBtn = showingSold ? 
            createControlItem(Material.CHEST, "§b查看在售中", "§7当前: §6已售出", "§7点击切换到在售商品") :
            createControlItem(Material.GOLD_INGOT, "§6查看已售出", "§7当前: §b在售中", "§7点击查看售出记录");
        inventory.setItem(47, toggleBtn);
        
        // 返回市场
        ItemStack backToMarket = createControlItem(Material.COMPASS, "§e返回市场", "§7返回全服交易市场");
        inventory.setItem(49, backToMarket);
        
        // 刷新
        ItemStack refresh = createControlItem(Material.SUNFLOWER, "§e刷新", "§7刷新列表");
        inventory.setItem(50, refresh);
        
        // 关闭
        ItemStack close = createControlItem(Material.BARRIER, "§c关闭", "§7关闭界面");
        inventory.setItem(51, close);
        
        int totalPages = (int) Math.ceil((double) displayList.size() / ITEMS_PER_PAGE);
        if (currentPage < totalPages - 1) {
            ItemStack nextPage = createControlItem(Material.ARROW, "§a下一页", "§7点击翻到下一页");
            inventory.setItem(53, nextPage);
        }
        
        // 信息
        String infoTitle = showingSold ? "§6已售出信息" : "§b我的上架信息";
        String infoCount = showingSold ? "§7售出数量: §f" + soldHistory.size() : "§7上架数量: §f" + myListings.size();
        String infoAction = showingSold ? "§7这些商品已成功卖出" : "§7点击商品可下架";
        ItemStack info = createControlItem(Material.BOOK, infoTitle, infoCount, infoAction);
        inventory.setItem(46, info);
    }
    
    private ItemStack createSoldDisplay(GlobalMarketItem listing) {
        ItemStack item = plugin.getGlobalMarketManager().deserializeItemPublic(listing.getItemData());
        if (item == null) {
            item = new ItemStack(Material.BARRIER);
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§7═══════════════════");
            lore.add("§a✔ 已售出");
            lore.add("§7数量: §f" + listing.getAmount());
            lore.add("§7成交价: §e" + plugin.getEconomyManager().formatCurrency(listing.getPrice(), listing.getCurrencyType()));
            lore.add("§7═══════════════════");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    private ItemStack createListingDisplay(GlobalMarketItem listing) {
        ItemStack item = plugin.getGlobalMarketManager().deserializeItemPublic(listing.getItemData());
        if (item == null) {
            item = new ItemStack(Material.BARRIER);
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§7═══════════════════");
            lore.add("§7数量: §f" + listing.getAmount());
            lore.add("§7价格: §e" + plugin.getEconomyManager().formatCurrency(listing.getPrice(), listing.getCurrencyType()));
            
            long remainingTime = listing.getExpireTime() - System.currentTimeMillis();
            if (remainingTime > 0) {
                long hours = remainingTime / (1000 * 60 * 60);
                long minutes = (remainingTime % (1000 * 60 * 60)) / (1000 * 60);
                lore.add("§7剩余时间: §f" + hours + "小时" + minutes + "分钟");
            }
            
            lore.add("§7═══════════════════");
            lore.add("§c点击下架");
            lore.add("§7商品ID: " + listing.getId());
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    private ItemStack createControlItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(loreLines));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;
        
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        
        // 商品区域
        if (slot < 45) {
            int listingIndex = currentPage * ITEMS_PER_PAGE + slot;
            if (listingIndex < myListings.size()) {
                GlobalMarketItem listing = myListings.get(listingIndex);
                plugin.getGlobalMarketManager().cancelListing(player, listing.getId());
                myListings = plugin.getGlobalMarketManager().getSellerListings(player.getUniqueId());
                refreshDisplay();
            }
            return;
        }
        
        // 控制栏
        switch (slot) {
            case 45 -> {
                if (currentPage > 0) {
                    currentPage--;
                    refreshDisplay();
                }
            }
            case 47 -> {
                // 切换在售/已售出
                showingSold = !showingSold;
                currentPage = 0;
                open(); // 重新打开以更新标题
            }
            case 49 -> new GlobalMarketGUI(plugin, player).open();
            case 50 -> {
                myListings = plugin.getGlobalMarketManager().getSellerListings(player.getUniqueId());
                plugin.getGlobalMarketManager().getSellerSoldHistoryAsync(player.getUniqueId(), 100, history -> {
                    this.soldHistory = history;
                    refreshDisplay();
                });
            }
            case 51 -> player.closeInventory();
            case 53 -> {
                List<GlobalMarketItem> displayList = showingSold ? soldHistory : myListings;
                int totalPages = (int) Math.ceil((double) displayList.size() / ITEMS_PER_PAGE);
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    refreshDisplay();
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;
        
        HandlerList.unregisterAll(this);
    }
}

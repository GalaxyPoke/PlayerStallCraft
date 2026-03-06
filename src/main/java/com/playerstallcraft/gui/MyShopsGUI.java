package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.Shop;
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

public class MyShopsGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private Inventory inventory;
    private List<Shop> myShops;

    public MyShopsGUI(PlayerStallCraft plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.myShops = plugin.getShopManager().getPlayerShops(player.getUniqueId());
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 54, "§b我的商铺");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshDisplay();
        player.openInventory(inventory);
    }

    private void refreshDisplay() {
        inventory.clear();
        
        for (int i = 0; i < Math.min(myShops.size(), 45); i++) {
            Shop shop = myShops.get(i);
            ItemStack displayItem = createShopDisplay(shop);
            inventory.setItem(i, displayItem);
        }
        
        // 底部信息栏
        inventory.setItem(49, createItem(Material.BOOK, "§b我的商铺",
            "§7拥有商铺数: §f" + myShops.size(),
            "",
            "§7点击商铺进行管理"));
        
        // 返回
        inventory.setItem(45, createItem(Material.ARROW, "§e返回", "§7返回商铺列表"));
        
        // 刷新
        inventory.setItem(50, createItem(Material.SUNFLOWER, "§e刷新", "§7刷新列表"));
        
        // 关闭
        inventory.setItem(53, createItem(Material.BARRIER, "§c关闭", "§7关闭界面"));
    }

    private ItemStack createShopDisplay(Shop shop) {
        Material material = shop.isOwned() ? Material.DIAMOND_BLOCK : Material.GOLD_BLOCK;
        String status = shop.isOwned() ? "§b已购买" : "§e租赁中";
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6" + shop.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add("§7═══════════════════");
            lore.add("§7状态: " + status);
            if (shop.isRented()) {
                lore.add("§7剩余租期: §f" + shop.getRemainingRentTime());
            }
            lore.add("§7货架耐久: §f" + shop.getShelfDurability() + "%");
            lore.add("§7商品数量: §f" + plugin.getShopManager().getShopItems(shop.getId()).size());
            lore.add("§7═══════════════════");
            lore.add("§a点击管理商铺");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    private ItemStack createItem(Material material, String name, String... loreLines) {
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
        
        // 商铺区域
        if (slot < 45 && slot < myShops.size()) {
            Shop shop = myShops.get(slot);
            new ShopManageGUI(plugin, player, shop).open();
            return;
        }
        
        switch (slot) {
            case 45 -> new ShopListGUI(plugin, player).open();
            case 50 -> {
                myShops = plugin.getShopManager().getPlayerShops(player.getUniqueId());
                refreshDisplay();
            }
            case 53 -> player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;
        
        HandlerList.unregisterAll(this);
    }
}

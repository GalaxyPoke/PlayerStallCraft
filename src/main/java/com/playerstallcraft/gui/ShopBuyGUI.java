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

import java.util.Arrays;

public class ShopBuyGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private final Shop shop;
    private Inventory inventory;
    private String currencyType = "vault"; // vault 或 nye

    public ShopBuyGUI(PlayerStallCraft plugin, Player player, Shop shop) {
        this.plugin = plugin;
        this.player = player;
        this.shop = shop;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 27, "§6购买商铺: " + shop.getName());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshDisplay();
        player.openInventory(inventory);
    }

    private void refreshDisplay() {
        inventory.clear();
        
        double buyPrice;
        if (currencyType.equals("nye")) {
            buyPrice = plugin.getConfig().getDouble("shop.nye-buy-price", 500000);
        } else {
            buyPrice = plugin.getConfigManager().getConfig().getDouble("shop.buy-price", 100000);
        }
        
        // 商铺信息
        ItemStack shopInfo = createItem(Material.IRON_BLOCK, "§6" + shop.getName(),
            "§7货架数量: §f" + shop.getShelfCount(),
            "§7位置: §f" + formatLocation(shop.getLocation()),
            "",
            "§e购买后永久拥有此商铺");
        inventory.setItem(4, shopInfo);
        
        // 货币类型切换
        String nyeName = plugin.getConfig().getString("currency.nye-currency-name", "鸽币");
        Material currencyMaterial = currencyType.equals("nye") ? Material.EMERALD : Material.GOLD_INGOT;
        String currencyName = currencyType.equals("nye") ? nyeName : "金币";
        ItemStack currencyItem = createItem(currencyMaterial, "§e当前货币: §f" + currencyName,
            "",
            "§7点击切换货币类型");
        inventory.setItem(8, currencyItem);
        
        // 价格显示
        ItemStack priceItem = createItem(Material.GOLD_BLOCK, "§e购买价格",
            "§f" + plugin.getEconomyManager().formatCurrency(buyPrice, currencyType));
        inventory.setItem(13, priceItem);
        
        // 确认购买
        boolean hasLicense = plugin.getLicenseManager().hasLicense(player);
        boolean hasEnoughMoney = plugin.getEconomyManager().has(player, buyPrice, currencyType);
        
        ItemStack confirmItem;
        if (!hasLicense) {
            confirmItem = createItem(Material.BARRIER, "§c无法购买", "§c需要营业执照!");
        } else if (!hasEnoughMoney) {
            confirmItem = createItem(Material.BARRIER, "§c余额不足", 
                "§c需要 " + plugin.getEconomyManager().formatCurrency(buyPrice, currencyType));
        } else {
            confirmItem = createItem(Material.EMERALD_BLOCK, "§a确认购买",
                "§7价格: §f" + plugin.getEconomyManager().formatCurrency(buyPrice, currencyType),
                "",
                "§a点击确认购买");
        }
        inventory.setItem(22, confirmItem);
        
        // 返回
        inventory.setItem(18, createItem(Material.ARROW, "§e返回", "§7返回商铺列表"));
        
        // 关闭
        inventory.setItem(26, createItem(Material.BARRIER, "§c取消", "§7关闭界面"));
    }

    private String formatLocation(org.bukkit.Location loc) {
        return String.format("%s (%.0f, %.0f, %.0f)", 
            loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines.length > 0) {
                meta.setLore(Arrays.asList(loreLines));
            }
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
        
        switch (slot) {
            case 8 -> { currencyType = currencyType.equals("vault") ? "nye" : "vault"; refreshDisplay(); }
            case 18 -> new ShopListGUI(plugin, player).open();
            case 22 -> {
                if (plugin.getShopManager().buyShop(player, shop.getId(), currencyType)) {
                    player.closeInventory();
                }
            }
            case 26 -> player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;
        
        HandlerList.unregisterAll(this);
    }
}

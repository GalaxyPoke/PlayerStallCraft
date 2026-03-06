package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.Shop;
import com.playerstallcraft.models.ShopShelf;
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
import java.util.List;

/**
 * 货架上架商品GUI - 选择物品并设置价格
 */
public class ShelfAddItemGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private final Shop shop;
    private final ShopShelf shelf;
    private final int shelfSlot;
    private Inventory inventory;
    
    private ItemStack selectedItem = null;
    private double price = 100.0;
    private int quantity = 1;
    private String currencyType = "nye"; // "vault" 或 "nye"
    
    private static final int ITEM_SLOT = 13;
    private static final int PRICE_DISPLAY_SLOT = 22;
    private static final int CURRENCY_SLOT = 31;
    
    // 价格调整按钮槽位
    private static final int PRICE_ADD_10 = 19;
    private static final int PRICE_ADD_100 = 20;
    private static final int PRICE_ADD_1000 = 21;
    private static final int PRICE_ADD_10000 = 23; // 中键快捷位置
    private static final int PRICE_SUB_10 = 28;
    private static final int PRICE_SUB_100 = 29;
    private static final int PRICE_CLEAR = 30;

    public ShelfAddItemGUI(PlayerStallCraft plugin, Player player, Shop shop, ShopShelf shelf, int shelfSlot) {
        this.plugin = plugin;
        this.player = player;
        this.shop = shop;
        this.shelf = shelf;
        this.shelfSlot = shelfSlot;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 54, "§6上架商品到货架 #" + shelf.getId());
        refreshDisplay();
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        player.openInventory(inventory);
    }

    private void refreshDisplay() {
        inventory.clear();
        
        // 背景
        ItemStack bg = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, bg);
        }
        
        // 提示信息
        ItemStack info = createItem(Material.PAPER, "§e上架说明",
            "§71. 点击下方背包中的物品选择",
            "§7   §a左键§7=上架1个 §e右键§7=上架全部",
            "§72. 调整价格(点击金锭或按钮)",
            "§73. 点击确认上架");
        inventory.setItem(4, info);
        
        // 选中物品展示区
        if (selectedItem != null) {
            ItemStack display = selectedItem.clone();
            ItemMeta meta = display.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("§7───────────────");
            lore.add("§a已选中此物品");
            lore.add("§7数量: §f" + quantity);
            meta.setLore(lore);
            display.setItemMeta(meta);
            inventory.setItem(ITEM_SLOT, display);
        } else {
            inventory.setItem(ITEM_SLOT, createItem(Material.BARRIER, "§c未选择物品", "§7点击下方背包选择物品"));
        }
        
        // 价格显示
        String currencyName = currencyType.equals("vault") 
            ? plugin.getConfig().getString("currency.vault-currency-name", "金币")
            : plugin.getConfig().getString("currency.nye-currency-name", "鸽币");
        
        inventory.setItem(PRICE_DISPLAY_SLOT, createItem(Material.GOLD_INGOT, "§6当前价格", 
            "§e" + String.format("%.1f", price) + " " + currencyName,
            "§7",
            "§a点击输入自定义价格"));
        
        // 价格调整按钮
        inventory.setItem(PRICE_ADD_10, createItem(Material.GOLD_NUGGET, "§e+10", "§7左键增加10", "§b蹲下+右键 +100"));
        inventory.setItem(PRICE_ADD_100, createItem(Material.GOLD_NUGGET, "§e+100", "§7左键增加100", "§b蹲下+右键 +1000"));
        inventory.setItem(PRICE_ADD_1000, createItem(Material.GOLD_NUGGET, "§e+1000", "§7左键增加1000", "§b蹲下+右键 +10000"));
        inventory.setItem(PRICE_ADD_10000, createItem(Material.GOLD_INGOT, "§6+10000", "§7点击增加10000"));
        inventory.setItem(PRICE_SUB_10, createItem(Material.IRON_NUGGET, "§c-10", "§7左键减少10", "§b蹲下+右键 -100"));
        inventory.setItem(PRICE_SUB_100, createItem(Material.IRON_NUGGET, "§c-100", "§7左键减少100", "§b蹲下+右键 -1000"));
        inventory.setItem(PRICE_CLEAR, createItem(Material.BARRIER, "§c清零", "§7点击清零价格"));
        
        // 货币类型切换
        Material currencyIcon = currencyType.equals("vault") ? Material.GOLD_NUGGET : Material.EMERALD;
        String otherCurrency = currencyType.equals("vault") 
            ? plugin.getConfig().getString("currency.nye-currency-name", "鸽币")
            : plugin.getConfig().getString("currency.vault-currency-name", "金币");
        inventory.setItem(CURRENCY_SLOT, createItem(currencyIcon, "§e货币类型: §f" + currencyName,
            "§7点击切换到: " + otherCurrency));
        
        // 确认和取消按钮
        if (selectedItem != null) {
            inventory.setItem(39, createItem(Material.LIME_WOOL, "§a确认上架",
                "§7上架物品: §f" + selectedItem.getType().name(),
                "§7上架数量: §f" + quantity,
                "§7买家需支付单价: §e" + String.format("%.1f", price) + " " + currencyName,
                "§7结算货币: §f" + currencyName,
                "§7",
                "§a点击确认上架"));
        } else {
            inventory.setItem(39, createItem(Material.GRAY_WOOL, "§7确认上架", "§c请先选择物品"));
        }
        
        inventory.setItem(41, createItem(Material.RED_WOOL, "§c取消", "§7返回货架管理"));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;
        
        int slot = event.getRawSlot();
        
        // 点击玩家背包
        if (slot >= 54) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR) {
                selectedItem = clicked.clone();
                // 左键选择1个，右键选择全部
                if (event.isLeftClick()) {
                    quantity = 1;
                } else if (event.isRightClick()) {
                    quantity = clicked.getAmount();
                } else {
                    quantity = clicked.getAmount();
                }
                selectedItem.setAmount(1);
                refreshDisplay();
            }
            return;
        }
        
        event.setCancelled(true);
        
        switch (slot) {
            case PRICE_DISPLAY_SLOT -> {
                // 点击价格输入自定义价格
                handlePriceInput();
            }
            
            case PRICE_ADD_10 -> {
                if (event.isShiftClick() && event.isRightClick()) {
                    price += 100;
                } else {
                    price += 10;
                }
                refreshDisplay();
            }
            
            case PRICE_ADD_100 -> {
                if (event.isShiftClick() && event.isRightClick()) {
                    price += 1000;
                } else {
                    price += 100;
                }
                refreshDisplay();
            }
            
            case PRICE_ADD_1000 -> {
                if (event.isShiftClick() && event.isRightClick()) {
                    price += 10000;
                } else {
                    price += 1000;
                }
                refreshDisplay();
            }
            
            case PRICE_ADD_10000 -> {
                price += 10000;
                refreshDisplay();
            }
            
            case PRICE_SUB_10 -> {
                if (event.isShiftClick() && event.isRightClick()) {
                    price = Math.max(0, price - 100);
                } else {
                    price = Math.max(0, price - 10);
                }
                refreshDisplay();
            }
            
            case PRICE_SUB_100 -> {
                if (event.isShiftClick() && event.isRightClick()) {
                    price = Math.max(0, price - 1000);
                } else {
                    price = Math.max(0, price - 100);
                }
                refreshDisplay();
            }
            
            case PRICE_CLEAR -> {
                price = 0;
                refreshDisplay();
            }
            
            case CURRENCY_SLOT -> {
                // 切换货币类型
                currencyType = currencyType.equals("vault") ? "nye" : "vault";
                refreshDisplay();
            }
            
            case 39 -> {
                // 确认上架
                if (selectedItem != null) {
                    handleConfirm();
                }
            }
            
            case 41 -> {
                // 取消
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> 
                    new ShelfItemsGUI(plugin, player, shop, shelf).open(), 1L);
            }
        }
    }
    
    private void handlePriceInput() {
        player.closeInventory();
        plugin.getMessageManager().sendRaw(player, "&e请在聊天框输入价格 (输入 &ccancel &e取消):");
        
        // 存储待输入价格的状态
        plugin.getShopManager().setPendingPriceInput(player.getUniqueId(), this);
    }
    
    public void setPrice(double newPrice) {
        this.price = Math.max(0, newPrice);
    }
    
    public void reopenGUI() {
        Bukkit.getScheduler().runTask(plugin, this::open);
    }

    private void handleConfirm() {
        if (selectedItem == null) return;
        
        // 检查玩家背包是否有足够物品
        ItemStack toRemove = selectedItem.clone();
        toRemove.setAmount(quantity);
        
        if (!player.getInventory().containsAtLeast(selectedItem, quantity)) {
            plugin.getMessageManager().sendRaw(player, "&c背包中没有足够的物品!");
            return;
        }
        
        // 从背包移除物品
        player.getInventory().removeItem(toRemove);
        
        // 添加到货架 (使用带货币类型的方法)
        ShopShelf.ShelfItem newItem = new ShopShelf.ShelfItem(selectedItem, price, quantity, currencyType);
        if (shelf.addItemWithCurrency(shelfSlot, newItem)) {
            String currencyName = currencyType.equals("vault") 
                ? plugin.getConfig().getString("currency.vault-currency-name", "金币")
                : plugin.getConfig().getString("currency.nye-currency-name", "鸽币");
            plugin.getMessageManager().sendRaw(player, "&a成功上架商品! &7买家需支付单价: " + String.format("%.1f", price) + " " + currencyName);
            
            // 保存到数据库
            ShopShelf.ShelfItem savedItem = shelf.getItem(shelfSlot);
            if (savedItem != null) {
                plugin.getShopManager().saveShelfItem(shelf, shelfSlot, savedItem);
            }
            
            // 更新全息图
            plugin.getShelfHologramManager().updateHologram(shelf);
        } else {
            // 退还物品
            player.getInventory().addItem(toRemove);
            plugin.getMessageManager().sendRaw(player, "&c上架失败!");
        }
        
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> 
            new ShelfItemsGUI(plugin, player, shop, shelf).open(), 1L);
    }

    private ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines.length > 0) {
                List<String> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(line);
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() != inventory) return;
        if (!event.getPlayer().equals(player)) return;
        
        HandlerList.unregisterAll(this);
    }
}

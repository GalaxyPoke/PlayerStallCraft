package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.Shop;
import com.playerstallcraft.models.ShopShelf;
import com.playerstallcraft.models.StallItem;
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

public class ShopViewGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private final Shop shop;
    private Inventory inventory;
    private final Map<Integer, ShelfItemRef> shelfItemSlots = new java.util.HashMap<>();

    public ShopViewGUI(PlayerStallCraft plugin, Player player, Shop shop) {
        this.plugin = plugin;
        this.player = player;
        this.shop = shop;
    }

    public void open() {
        String ownerName = shop.getOwnerName() != null ? shop.getOwnerName() : "无";
        inventory = Bukkit.createInventory(null, 54, "§6商铺: " + shop.getName() + " §7[" + ownerName + "]");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshDisplay();
        player.openInventory(inventory);
    }

    private void refreshDisplay() {
        inventory.clear();
        shelfItemSlots.clear();
        
        Map<Integer, StallItem> items = plugin.getShopManager().getShopItems(shop.getId());
        
        // 显示普通商品 (前3行, 0-26)
        for (Map.Entry<Integer, StallItem> entry : items.entrySet()) {
            if (entry.getKey() < 27) {
                ItemStack displayItem = createItemDisplay(entry.getValue());
                inventory.setItem(entry.getKey(), displayItem);
            }
        }
        
        // 分隔线 (第4行开头)
        inventory.setItem(27, createItem(Material.GRAY_STAINED_GLASS_PANE, "§7═══ 货架商品 ═══", ""));
        
        // 显示货架商品 (第4-5行, 28-44)
        int shelfSlot = 28;
        for (ShopShelf shelf : shop.getShelves().values()) {
            for (Map.Entry<Integer, ShopShelf.ShelfItem> entry : shelf.getItems().entrySet()) {
                if (shelfSlot > 44) break;
                ShopShelf.ShelfItem shelfItem = entry.getValue();
                ItemStack displayItem = createShelfItemDisplay(shelfItem);
                inventory.setItem(shelfSlot, displayItem);
                shelfItemSlots.put(shelfSlot, new ShelfItemRef(shelf, entry.getKey()));
                shelfSlot++;
            }
            if (shelfSlot > 44) break;
        }
        
        // 底部信息栏
        ItemStack shopInfo = createItem(Material.BOOK, "§6商铺信息",
            "§7名称: §f" + shop.getName(),
            "§7业主: §f" + (shop.getOwnerName() != null ? shop.getOwnerName() : "无"),
            "§7普通商品: §f" + items.size(),
            "§7货架商品: §f" + shelfItemSlots.size());
        inventory.setItem(49, shopInfo);
        
        // 返回
        inventory.setItem(45, createItem(Material.ARROW, "§e返回", "§7返回商铺列表"));
        
        // 关闭
        inventory.setItem(53, createItem(Material.BARRIER, "§c关闭", "§7关闭界面"));
    }

    private ItemStack createItemDisplay(StallItem stallItem) {
        ItemStack item = stallItem.getItemStack().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§7═══════════════════");
            lore.add("§7购买单价 §7(你支付): §e" + plugin.getEconomyManager().formatCurrency(stallItem.getPrice(), stallItem.getCurrencyType()));
            lore.add("§7库存数量: §f" + stallItem.getAmount());
            lore.add("§7═══════════════════");
            lore.add("§a左键: 购买1个 | 右键: 购买全部");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createShelfItemDisplay(ShopShelf.ShelfItem shelfItem) {
        ItemStack item = shelfItem.getItemStack().clone();
        // 显示数量设为1，避免显示异常的堆叠数
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§b【货架商品】");
            lore.add("§7═══════════════════");
            if (shelfItem.hasActiveDiscount()) {
                lore.add("§c原价: §7§m" + plugin.getEconomyManager().formatCurrency(shelfItem.getPrice(), shelfItem.getCurrencyType()));
                lore.add("§a折扣价: §e" + plugin.getEconomyManager().formatCurrency(shelfItem.getEffectivePrice(), shelfItem.getCurrencyType())
                    + " §6(" + (int)(shelfItem.getDiscountRate()*100) + "折)");
                if (shelfItem.getDiscountExpiry() > 0) {
                    long rem = shelfItem.getDiscountExpiry() - System.currentTimeMillis();
                    lore.add("§7促销剩余: §e" + (rem/3600000) + "h " + ((rem%3600000)/60000) + "m");
                }
            } else {
                lore.add("§7购买单价 §7(你支付): §e" + plugin.getEconomyManager().formatCurrency(shelfItem.getPrice(), shelfItem.getCurrencyType()));
            }
            lore.add("§7剩余库存: §f" + shelfItem.getStock());
            lore.add("§7已售出: §f" + shelfItem.getSold());
            lore.add("§7═══════════════════");
            if (shelfItem.getStock() > 0) {
                lore.add("§a左键: 购买1个 | 右键: 购买全部库存");
            } else {
                lore.add("§c已售罄");
            }
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
        
        // 普通商品区域 - 购买 (0-26)
        if (slot < 27) {
            Map<Integer, StallItem> items = plugin.getShopManager().getShopItems(shop.getId());
            StallItem stallItem = items.get(slot);
            if (stallItem != null) {
                int buyAmount = event.isRightClick() ? stallItem.getAmount() : 1;
                purchaseItem(stallItem, buyAmount, slot);
            }
            return;
        }
        
        // 货架商品区域 - 购买 (28-44)
        if (slot >= 28 && slot <= 44) {
            ShelfItemRef ref = shelfItemSlots.get(slot);
            if (ref != null) {
                ShopShelf.ShelfItem shelfItem = ref.shelf.getItem(ref.itemSlot);
                if (shelfItem != null && shelfItem.getStock() > 0) {
                    int buyAmount = event.isRightClick() ? shelfItem.getStock() : 1;
                    purchaseShelfItem(ref.shelf, shelfItem, buyAmount, ref.itemSlot);
                }
            }
            return;
        }
        
        switch (slot) {
            case 45 -> new ShopListGUI(plugin, player).open();
            case 53 -> player.closeInventory();
        }
    }

    private void purchaseItem(StallItem stallItem, int amount, int slot) {
        double unitPrice = stallItem.getPrice() / stallItem.getAmount();
        double totalPrice = unitPrice * amount;
        String currency = stallItem.getCurrencyType();
        
        if (!plugin.getEconomyManager().has(player, totalPrice, currency)) {
            plugin.getMessageManager().sendRaw(player, "&c余额不足!");
            return;
        }
        
        plugin.getEconomyManager().withdraw(player, totalPrice, currency);
        
        // 计算税率
        String taxType = shop.isOwned() ? "owned-shop" : "rented-shop";
        double tax = plugin.getEconomyManager().calculateTax(totalPrice, taxType);
        double sellerReceive = totalPrice - tax;
        
        // 给卖家打钱
        if (shop.getOwnerUuid() != null) {
            plugin.getEconomyManager().depositOffline(shop.getOwnerUuid(), sellerReceive, currency);
        }
        
        // 给买家物品
        ItemStack purchasedItem = stallItem.getItemStack().clone();
        purchasedItem.setAmount(amount);
        player.getInventory().addItem(purchasedItem);
        
        // 更新或移除商品
        if (amount >= stallItem.getAmount()) {
            plugin.getShopManager().removeItemFromShop(shop.getId(), slot);
        }
        
        plugin.getMessageManager().sendRaw(player, "&a购买成功! 花费: " + 
            plugin.getEconomyManager().formatCurrency(totalPrice, currency));
        
        refreshDisplay();
    }

    private void purchaseShelfItem(ShopShelf shelf, ShopShelf.ShelfItem shelfItem, int amount, int itemSlot) {
        if (amount > shelfItem.getStock()) {
            amount = shelfItem.getStock();
        }
        if (amount <= 0) {
            plugin.getMessageManager().sendRaw(player, "&c库存不足!");
            return;
        }
        
        double totalPrice = shelfItem.getEffectivePrice() * amount;
        String currency = "vault";
        
        if (!plugin.getEconomyManager().has(player, totalPrice, currency)) {
            plugin.getMessageManager().sendRaw(player, "&c余额不足!");
            return;
        }
        
        plugin.getEconomyManager().withdraw(player, totalPrice, currency);
        
        // 计算税率
        String taxType = shop.isOwned() ? "owned-shop" : "rented-shop";
        double tax = plugin.getEconomyManager().calculateTax(totalPrice, taxType);
        double sellerReceive = totalPrice - tax;
        
        // 给卖家打钱
        if (shop.getOwnerUuid() != null) {
            plugin.getEconomyManager().depositOffline(shop.getOwnerUuid(), sellerReceive, currency);
        }
        
        // 给买家物品
        ItemStack purchasedItem = shelfItem.getItemStack().clone();
        purchasedItem.setAmount(amount);
        player.getInventory().addItem(purchasedItem);
        
        // 更新库存
        shelfItem.setStock(shelfItem.getStock() - amount);
        shelfItem.addSold(amount);
        
        // 保存到数据库
        plugin.getShopManager().updateShelfItemStock(shelf.getId(), itemSlot, shelfItem.getStock(), shelfItem.getSold());
        
        // 更新全息图
        plugin.getShelfHologramManager().updateHologram(shelf);

        // 写入交易日志（用于排行榜和统计看板）
        if (shop.getOwnerUuid() != null) {
            plugin.getTransactionLogManager().logStallPurchase(
                player.getUniqueId(), player.getName(),
                shop.getOwnerUuid(), shop.getOwnerName() != null ? shop.getOwnerName() : "",
                shelfItem.getItemName(), amount, totalPrice, currency
            );
        }

        plugin.getMessageManager().sendRaw(player, "&a购买成功! 花费: " + 
            plugin.getEconomyManager().formatCurrency(totalPrice, currency));
        
        refreshDisplay();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;
        
        HandlerList.unregisterAll(this);
    }

    // 货架商品引用类
    private record ShelfItemRef(ShopShelf shelf, int itemSlot) {}
}

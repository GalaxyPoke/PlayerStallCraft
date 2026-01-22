package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.managers.MessageManager;
import com.playerstallcraft.models.PlayerStall;
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
import java.util.List;

public class StallViewGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player viewer;
    private final PlayerStall stall;
    private final Inventory inventory;

    public StallViewGUI(PlayerStallCraft plugin, Player viewer, PlayerStall stall) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.stall = stall;
        
        String title = MessageManager.colorize("&6" + stall.getOwnerName() + "的摊位");
        this.inventory = Bukkit.createInventory(null, 27, title);
        
        setupInventory();
    }

    private void setupInventory() {
        // 填充边框
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(18 + i, border);
        }

        // 显示商品
        for (StallItem stallItem : stall.getItems().values()) {
            int displaySlot = 9 + (stallItem.getSlot() % 9);
            if (displaySlot < 18) {
                ItemStack displayItem = stallItem.getItemStack().clone();
                ItemMeta meta = displayItem.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add("");
                    lore.add(MessageManager.colorize("&e价格: &f" + plugin.getEconomyManager().formatCurrency(stallItem.getPrice(), stallItem.getCurrencyType())));
                    lore.add(MessageManager.colorize("&7左键购买1个 | 右键购买全部"));
                    meta.setLore(lore);
                    displayItem.setItemMeta(meta);
                }
                inventory.setItem(displaySlot, displayItem);
            }
        }
    }

    public void open() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 9 || slot >= 18) {
            return;
        }

        int itemSlot = slot - 9;
        StallItem stallItem = null;
        
        // 查找对应的商品
        for (StallItem item : stall.getItems().values()) {
            if (item.getSlot() % 9 == itemSlot) {
                stallItem = item;
                break;
            }
        }

        if (stallItem == null) {
            return;
        }

        // 购买逻辑
        boolean buyAll = event.isRightClick();
        int amount = buyAll ? stallItem.getAmount() : 1;
        double totalPrice = stallItem.getPrice() * amount;

        // 检查货币
        if (!plugin.getEconomyManager().has(player, totalPrice, stallItem.getCurrencyType())) {
            plugin.getMessageManager().send(player, "trade.not-enough-money");
            return;
        }

        // 扣款
        if (!plugin.getEconomyManager().withdraw(player, totalPrice, stallItem.getCurrencyType())) {
            return;
        }

        // 计算税率并给卖家 (根据摊位类型)
        String stallType = "no-license"; // TODO: 根据玩家是否有执照判断
        double tax = plugin.getEconomyManager().calculateTax(totalPrice, stallType);
        double sellerReceive = totalPrice - tax;

        // 给卖家存款（支持离线）
        Player seller = Bukkit.getPlayer(stall.getOwnerUuid());
        if (seller != null && seller.isOnline()) {
            plugin.getEconomyManager().deposit(seller, sellerReceive, stallItem.getCurrencyType());
            plugin.getMessageManager().send(seller, "trade.sale-success", MessageManager.placeholders(
                    "amount", String.valueOf(amount),
                    "item", stallItem.getItemName(),
                    "price", plugin.getEconomyManager().formatCurrency(sellerReceive, stallItem.getCurrencyType()),
                    "tax", plugin.getEconomyManager().formatCurrency(tax, stallItem.getCurrencyType())
            ));
        } else {
            // 卖家离线时也能收款
            plugin.getEconomyManager().depositOffline(stall.getOwnerUuid(), sellerReceive, stallItem.getCurrencyType());
        }

        // 给买家物品
        ItemStack purchasedItem = stallItem.getItemStack().clone();
        purchasedItem.setAmount(amount);
        player.getInventory().addItem(purchasedItem);

        plugin.getMessageManager().send(player, "trade.purchase-success", MessageManager.placeholders(
                "amount", String.valueOf(amount),
                "item", stallItem.getItemName(),
                "price", plugin.getEconomyManager().formatCurrency(totalPrice, stallItem.getCurrencyType())
        ));

        // 记录交易
        plugin.getDatabaseManager().executeAsync(
                "INSERT INTO transaction_logs (seller_uuid, buyer_uuid, item_data, amount, price, currency_type, tax_amount) VALUES (?, ?, ?, ?, ?, ?, ?)",
                stall.getOwnerUuid().toString(),
                player.getUniqueId().toString(),
                stallItem.getItemStack().getType().name(),
                amount,
                totalPrice,
                stallItem.getCurrencyType(),
                tax
        );

        // 更新库存
        if (buyAll) {
            stall.removeItem(stallItem.getSlot());
        } else {
            ItemStack remaining = stallItem.getItemStack();
            remaining.setAmount(remaining.getAmount() - 1);
            if (remaining.getAmount() <= 0) {
                stall.removeItem(stallItem.getSlot());
            }
        }

        // 刷新界面
        setupInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.colorize(name));
            item.setItemMeta(meta);
        }
        return item;
    }
}

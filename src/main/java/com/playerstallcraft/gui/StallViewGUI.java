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
                    String currencyName = plugin.getEconomyManager().getCurrencyName(stallItem.getCurrencyType());
                    lore.add(MessageManager.colorize("&e购买单价 &7(你支付): &f" + plugin.getEconomyManager().formatCurrency(stallItem.getPrice(), stallItem.getCurrencyType())));
                    lore.add(MessageManager.colorize("&7结算货币: &f" + currencyName));
                    lore.add(MessageManager.colorize("&7左键: 购买1个 | 右键: 购买全部"));
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
        int availableStock = stallItem.getAmount();
        
        // 检查库存是否足够
        if (availableStock <= 0) {
            plugin.getMessageManager().send(player, "trade.not-enough-stock");
            setupInventory(); // 刷新界面
            return;
        }
        
        int amount = buyAll ? availableStock : 1;
        // 确保购买数量不超过库存
        amount = Math.min(amount, availableStock);
        
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

        // 计算税率并给卖家 (根据摊主是否持有执照)
        boolean sellerHasLicense = plugin.getLicenseManager().hasLicense(stall.getOwnerUuid());
        String stallType = sellerHasLicense ? "with-license" : "no-license";
        double tax = plugin.getEconomyManager().calculateTax(totalPrice, stallType);
        double sellerReceive = totalPrice - tax;

        // 给卖家存款（支持离线）并发送通知
        Player seller = Bukkit.getPlayer(stall.getOwnerUuid());
        String priceStr = plugin.getEconomyManager().formatCurrency(sellerReceive, stallItem.getCurrencyType());
        if (seller != null && seller.isOnline()) {
            plugin.getEconomyManager().deposit(seller, sellerReceive, stallItem.getCurrencyType());
            plugin.getMessageManager().send(seller, "trade.sale-success", MessageManager.placeholders(
                    "amount", String.valueOf(amount),
                    "item", stallItem.getItemName(),
                    "price", priceStr,
                    "tax", plugin.getEconomyManager().formatCurrency(tax, stallItem.getCurrencyType())
            ));
        } else {
            // 卖家离线时存款并通过 SweetMail 通知
            plugin.getEconomyManager().depositOffline(stall.getOwnerUuid(), sellerReceive, stallItem.getCurrencyType());
            plugin.getSweetMailManager().sendNoticeMail(
                    stall.getOwnerUuid(),
                    "摊位 - 商品已售出",
                    "你的摊位商品 [" + stallItem.getItemName() + "] x" + amount + " 已售出!",
                    "到手金额: " + priceStr,
                    "买家: " + player.getName()
            );
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

        // 累计销售件数
        stall.incrementSoldCount(amount);

        // 更新库存
        if (buyAll || amount >= stallItem.getAmount()) {
            stall.removeItem(stallItem.getSlot());
        } else {
            stallItem.reduceAmount(amount);
            // 同时更新 item_data（重新序列化以嵌入新数量）和 amount，防止重启后从旧字节还原
            plugin.getDatabaseManager().executeAsync(
                    "UPDATE stall_items SET amount = ?, item_data = ? WHERE owner_uuid = ? AND slot = ?",
                    stallItem.getAmount(),
                    stallItem.serialize(),
                    stall.getOwnerUuid().toString(),
                    stallItem.getSlot()
            );
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

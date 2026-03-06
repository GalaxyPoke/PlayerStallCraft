package com.playerstallcraft.listeners;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.gui.ShelfAddItemGUI;
import com.playerstallcraft.gui.ShelfItemsGUI;
import com.playerstallcraft.models.Shop;
import com.playerstallcraft.models.ShopShelf;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * 货架聊天监听器 - 处理重命名和价格输入
 */
public class ShelfRenameListener implements Listener {

    private final PlayerStallCraft plugin;

    public ShelfRenameListener(PlayerStallCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        
        // 检查是否有待修改价格的货架槽位
        ShopShelf priceEditShelf = plugin.getShopManager().getPendingPriceEditShelf(player.getUniqueId());
        if (priceEditShelf != null) {
            handlePriceEdit(event, player, priceEditShelf);
            return;
        }
        
        // 检查是否有待输入的价格
        Object priceInput = plugin.getShopManager().getPendingPriceInput(player.getUniqueId());
        if (priceInput != null) {
            handlePriceInput(event, player, priceInput);
            return;
        }
        
        // 检查是否有待重命名的货架
        ShopShelf shelf = plugin.getShopManager().getPendingShelfRename(player.getUniqueId());
        if (shelf != null) {
            handleRename(event, player, shelf);
        }
    }
    
    private void handlePriceEdit(AsyncPlayerChatEvent event, Player player, ShopShelf shelf) {
        event.setCancelled(true);
        String message = event.getMessage();
        int slot = plugin.getShopManager().getPendingPriceEditSlot(player.getUniqueId());
        
        if (message.equalsIgnoreCase("cancel")) {
            plugin.getShopManager().clearPendingPriceEdit(player.getUniqueId());
            plugin.getMessageManager().sendRaw(player, "&a已取消价格修改");
            Shop shop = plugin.getShopManager().getShop(shelf.getShopId());
            if (shop != null) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    new ShelfItemsGUI(plugin, player, shop, shelf).open());
            }
            return;
        }
        
        try {
            double newPrice = Double.parseDouble(message);
            if (newPrice < 0) {
                plugin.getMessageManager().sendRaw(player, "&c价格不能为负数!");
                return;
            }
            
            ShopShelf.ShelfItem item = shelf.getItem(slot);
            if (item != null) {
                item.setPrice(newPrice);
                plugin.getShopManager().saveShelfItem(shelf, slot, item);
                plugin.getShelfHologramManager().updateHologram(shelf);
                plugin.getMessageManager().sendRaw(player, "&a价格已修改为: " + String.format("%.1f", newPrice));
            }
            
            plugin.getShopManager().clearPendingPriceEdit(player.getUniqueId());
            Shop shop = plugin.getShopManager().getShop(shelf.getShopId());
            if (shop != null) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    new ShelfItemsGUI(plugin, player, shop, shelf).open());
            }
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendRaw(player, "&c请输入有效的数字!");
        }
    }

    private void handlePriceInput(AsyncPlayerChatEvent event, Player player, Object guiObj) {
        event.setCancelled(true);
        String message = event.getMessage();
        
        // 取消输入
        if (message.equalsIgnoreCase("cancel")) {
            plugin.getShopManager().clearPendingPriceInput(player.getUniqueId());
            plugin.getMessageManager().sendRaw(player, "&a已取消价格输入");
            
            if (guiObj instanceof ShelfAddItemGUI gui) {
                gui.reopenGUI();
            }
            return;
        }
        
        // 尝试解析价格
        try {
            double price = Double.parseDouble(message);
            if (price < 0) {
                plugin.getMessageManager().sendRaw(player, "&c价格不能为负数!");
                return;
            }
            
            plugin.getShopManager().clearPendingPriceInput(player.getUniqueId());
            
            if (guiObj instanceof ShelfAddItemGUI gui) {
                gui.setPrice(price);
                plugin.getMessageManager().sendRaw(player, "&a价格已设置为: " + String.format("%.1f", price));
                gui.reopenGUI();
            }
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendRaw(player, "&c请输入有效的数字!");
        }
    }
    
    private void handleRename(AsyncPlayerChatEvent event, Player player, ShopShelf shelf) {
        event.setCancelled(true);
        String message = event.getMessage();
        
        // 取消重命名
        if (message.equalsIgnoreCase("cancel")) {
            plugin.getShopManager().clearPendingShelfRename(player.getUniqueId());
            plugin.getMessageManager().sendRaw(player, "&a已取消重命名");
            
            // 重新打开GUI
            Shop shop = plugin.getShopManager().getShop(shelf.getShopId());
            if (shop != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> 
                    new ShelfItemsGUI(plugin, player, shop, shelf).open());
            }
            return;
        }
        
        // 设置新名称
        String newName = message.replace("&", "§");
        shelf.setDisplayName(newName);
        plugin.getShopManager().updateShelfDisplayName(shelf);
        
        // 更新全息图
        plugin.getShelfHologramManager().updateHologram(shelf);
        
        plugin.getShopManager().clearPendingShelfRename(player.getUniqueId());
        plugin.getMessageManager().sendRaw(player, "&a货架名称已更新为: " + newName);
        
        // 重新打开GUI
        Shop shop = plugin.getShopManager().getShop(shelf.getShopId());
        if (shop != null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> 
                new ShelfItemsGUI(plugin, player, shop, shelf).open());
        }
    }
}

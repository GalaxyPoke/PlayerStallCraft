package com.playerstallcraft.listeners;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.gui.ShelfItemsGUI;
import com.playerstallcraft.models.Shop;
import com.playerstallcraft.models.ShopShelf;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * 货架交互监听器
 * 处理玩家点击木桶货架方块的事件
 */
public class ShelfInteractListener implements Listener {

    private final PlayerStallCraft plugin;

    public ShelfInteractListener(PlayerStallCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 只处理右键点击方块
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Block block = event.getClickedBlock();
        if (block == null) return;
        
        // 只处理木桶
        if (block.getType() != Material.BARREL) return;
        
        Player player = event.getPlayer();
        Location blockLoc = block.getLocation();
        
        // 查找该位置对应的货架
        ShopShelf shelf = findShelfAtLocation(blockLoc);
        if (shelf == null) return;
        
        // 找到货架，取消默认打开木桶行为
        event.setCancelled(true);
        
        // 获取货架所属商铺
        Shop shop = plugin.getShopManager().getShop(shelf.getShopId());
        if (shop == null) return;
        
        // 判断是店主还是顾客
        boolean isOwner = shop.getOwnerUuid() != null && shop.getOwnerUuid().equals(player.getUniqueId());
        if (isOwner) {
            // 店主 - 打开货架管理界面
            new ShelfItemsGUI(plugin, player, shop, shelf).open();
        } else {
            // 顾客 - 也打开货架商品界面（只读浏览/购买模式）
            new ShelfItemsGUI(plugin, player, shop, shelf).open();
        }
    }

    /**
     * 处理方块破坏事件 - 保护货架木桶
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BARREL) return;
        
        Player player = event.getPlayer();
        Location blockLoc = block.getLocation();
        
        ShopShelf shelf = findShelfAtLocation(blockLoc);
        if (shelf == null) return;
        
        Shop shop = plugin.getShopManager().getShop(shelf.getShopId());
        if (shop == null) return;
        
        // 检查权限
        boolean isOwner = shop.getOwnerUuid() != null && shop.getOwnerUuid().equals(player.getUniqueId());
        boolean isAdmin = player.hasPermission("playerstallcraft.admin");
        
        if (!isOwner && !isAdmin) {
            // 非店主非管理员不能破坏
            event.setCancelled(true);
            plugin.getMessageManager().sendRaw(player, "&c你不能破坏这个货架!");
            return;
        }
        
        // 检查货架是否有商品
        if (shelf.getItemCount() > 0) {
            event.setCancelled(true);
            plugin.getMessageManager().sendRaw(player, "&c请先清空货架上的商品后再移除货架!");
            return;
        }
        
        // 允许破坏，清理全息图和数据
        plugin.getShelfHologramManager().removeHologram(shelf.getId());
        shop.removeShelf(shelf.getId());
        plugin.getShopManager().deleteShelf(shelf.getId());
        plugin.getMessageManager().sendRaw(player, "&a货架已移除!");
    }

    /**
     * 在指定位置查找货架
     */
    private ShopShelf findShelfAtLocation(Location location) {
        for (Shop shop : plugin.getShopManager().getAllShops()) {
            for (ShopShelf shelf : shop.getShelves().values()) {
                Location shelfLoc = shelf.getLocation();
                if (shelfLoc != null && 
                    shelfLoc.getWorld() != null &&
                    location.getWorld() != null &&
                    shelfLoc.getWorld().equals(location.getWorld()) &&
                    shelfLoc.getBlockX() == location.getBlockX() &&
                    shelfLoc.getBlockY() == location.getBlockY() &&
                    shelfLoc.getBlockZ() == location.getBlockZ()) {
                    return shelf;
                }
            }
        }
        return null;
    }
}

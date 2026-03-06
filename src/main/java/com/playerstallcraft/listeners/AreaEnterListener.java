package com.playerstallcraft.listeners;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.managers.MessageManager;
import com.playerstallcraft.models.Shop;
import com.playerstallcraft.models.StallRegion;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AreaEnterListener implements Listener {

    private final PlayerStallCraft plugin;
    private final Map<UUID, String> lastStallRegion = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastShop = new ConcurrentHashMap<>();

    public AreaEnterListener(PlayerStallCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        
        if (to == null) return;
        
        if (from.getBlockX() == to.getBlockX() && 
            from.getBlockY() == to.getBlockY() && 
            from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        
        Player player = event.getPlayer();
        checkStallRegion(player, to);
        checkShop(player, to);
    }

    private void checkStallRegion(Player player, Location loc) {
        UUID uuid = player.getUniqueId();
        String lastRegion = lastStallRegion.get(uuid);
        
        StallRegion currentRegion = plugin.getRegionManager().getRegionAt(loc);
        String currentName = currentRegion != null ? currentRegion.getName() : null;
        
        if (currentName != null && !currentName.equals(lastRegion)) {
            plugin.getMessageManager().send(player, "area-enter.stall-region",
                MessageManager.placeholders("name", currentName));
            lastStallRegion.put(uuid, currentName);
        } else if (currentName == null && lastRegion != null) {
            lastStallRegion.remove(uuid);
        }
    }

    private void checkShop(Player player, Location loc) {
        UUID uuid = player.getUniqueId();
        Integer lastShopId = lastShop.get(uuid);
        
        Shop currentShop = getShopAt(loc);
        Integer currentId = currentShop != null ? currentShop.getId() : null;
        
        if (currentId != null && !currentId.equals(lastShopId)) {
            if (currentShop.hasOwner()) {
                plugin.getMessageManager().send(player, "area-enter.shop-owned",
                    MessageManager.placeholders(
                        "name", currentShop.getName(),
                        "owner", currentShop.getOwnerName()
                    ));
            } else {
                plugin.getMessageManager().send(player, "area-enter.shop-available",
                    MessageManager.placeholders("name", currentShop.getName()));
            }
            lastShop.put(uuid, currentId);
        } else if (currentId == null && lastShopId != null) {
            lastShop.remove(uuid);
        }
    }

    private Shop getShopAt(Location loc) {
        for (Shop shop : plugin.getShopManager().getAllShops()) {
            if (shop.isInRegion(loc)) {
                return shop;
            }
        }
        return null;
    }
}

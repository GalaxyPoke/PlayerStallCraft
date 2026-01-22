package com.playerstallcraft.listeners;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.managers.MessageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class RegionSelectListener implements Listener {

    private final PlayerStallCraft plugin;

    public RegionSelectListener(PlayerStallCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        if (!player.hasPermission("stall.region")) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.NETHERITE_HOE) {
            return;
        }

        if (event.getClickedBlock() == null) {
            return;
        }

        Location clickedLocation = event.getClickedBlock().getLocation();
        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_BLOCK) {
            // 左键设置点1
            event.setCancelled(true);
            plugin.getRegionManager().setPos1(player, clickedLocation);
            plugin.getMessageManager().send(player, "region.pos1-set", MessageManager.placeholders(
                    "x", String.valueOf(clickedLocation.getBlockX()),
                    "y", String.valueOf(clickedLocation.getBlockY()),
                    "z", String.valueOf(clickedLocation.getBlockZ())
            ));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            // 右键设置点2
            event.setCancelled(true);
            plugin.getRegionManager().setPos2(player, clickedLocation);
            plugin.getMessageManager().send(player, "region.pos2-set", MessageManager.placeholders(
                    "x", String.valueOf(clickedLocation.getBlockX()),
                    "y", String.valueOf(clickedLocation.getBlockY()),
                    "z", String.valueOf(clickedLocation.getBlockZ())
            ));
        }
    }
}

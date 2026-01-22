package com.playerstallcraft.listeners;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.gui.StallViewGUI;
import com.playerstallcraft.models.PlayerStall;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDismountEvent;

public class PlayerListener implements Listener {

    private final PlayerStallCraft plugin;

    public PlayerListener(PlayerStallCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // 玩家退出时自动收摊
        if (plugin.getStallManager().hasStall(player)) {
            plugin.getStallManager().closeStall(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        // 摆摊中的玩家禁止移动（允许转头）
        if (plugin.getStallNPCManager().isPlayerFrozen(player.getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() 
                    || event.getFrom().getY() != event.getTo().getY() 
                    || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // 摆摊中的玩家禁止离开座位（除非通过收摊命令）
        if (plugin.getStallNPCManager().isPlayerFrozen(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }

        Player player = event.getPlayer();

        // Shift + 右键查看摊位
        if (!player.isSneaking()) {
            return;
        }

        PlayerStall stall = plugin.getStallManager().getStall(target);
        if (stall == null) {
            return;
        }

        event.setCancelled(true);
        
        // 打开摊位查看GUI
        new StallViewGUI(plugin, player, stall).open();
    }
}

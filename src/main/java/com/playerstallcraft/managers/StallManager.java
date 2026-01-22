package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.PlayerStall;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StallManager {

    private final PlayerStallCraft plugin;
    private final Map<UUID, PlayerStall> activeStalls;

    public StallManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        this.activeStalls = new ConcurrentHashMap<>();
    }

    public boolean openStall(Player player, String slogan) {
        UUID uuid = player.getUniqueId();
        
        if (activeStalls.containsKey(uuid)) {
            return false;
        }

        if (!plugin.getRegionManager().isInStallRegion(player.getLocation())) {
            plugin.getMessageManager().send(player, "region.not-in-region");
            return false;
        }

        PlayerStall stall = new PlayerStall(plugin, player, slogan);
        activeStalls.put(uuid, stall);
        stall.start();
        
        // 创建NPC人物模型和全息显示
        plugin.getStallNPCManager().createStallDisplay(stall, player);
        
        return true;
    }

    public boolean closeStall(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStall stall = activeStalls.remove(uuid);
        
        if (stall == null) {
            return false;
        }

        stall.stop();
        
        // 移除NPC人物模型
        plugin.getStallNPCManager().removeStallDisplay(uuid);
        
        return true;
    }

    public PlayerStall getStall(Player player) {
        return activeStalls.get(player.getUniqueId());
    }

    public PlayerStall getStall(UUID uuid) {
        return activeStalls.get(uuid);
    }

    public boolean hasStall(Player player) {
        return activeStalls.containsKey(player.getUniqueId());
    }

    public void closeAllStalls() {
        for (PlayerStall stall : activeStalls.values()) {
            stall.stop();
        }
        activeStalls.clear();
    }

    public Map<UUID, PlayerStall> getActiveStalls() {
        return activeStalls;
    }
}

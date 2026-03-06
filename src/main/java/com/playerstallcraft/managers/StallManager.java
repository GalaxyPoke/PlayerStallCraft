package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.PlayerStall;
import com.playerstallcraft.models.StallItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;

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

        // 将所有未售出物品返还给玩家背包，背包满则尝试邮件投递，邮件不可用才掉落
        boolean anyMailed = false;
        for (StallItem item : new ArrayList<>(stall.getItems().values())) {
            ItemStack returnItem = item.getItemStack();
            java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(returnItem);
            if (!overflow.isEmpty()) {
                for (ItemStack dropped : overflow.values()) {
                    boolean mailed = plugin.getSweetMailManager().sendItemMail(
                            player.getUniqueId(),
                            dropped,
                            "摊位收摊 - 物品返还",
                            "你的摊位收摊，未售出的 [" + item.getItemName() + "] 因背包已满已通过邮件发送，请查收附件。"
                    );
                    if (mailed) {
                        anyMailed = true;
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                    }
                }
            }
        }
        if (anyMailed) {
            plugin.getMessageManager().sendRaw(player, "&e背包已满，部分物品已通过邮件返还，请使用 /mail inbox 查收!");
        }
        stall.clearAllItems();

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

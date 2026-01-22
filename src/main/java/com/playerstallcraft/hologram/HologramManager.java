package com.playerstallcraft.hologram;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.PlayerStall;
import com.playerstallcraft.models.StallItem;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HologramManager {

    private final PlayerStallCraft plugin;
    private final Map<UUID, List<ArmorStand>> holograms;
    private final Map<UUID, BukkitTask> rotationTasks;
    private final Map<UUID, Integer> currentIndex;

    public HologramManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        this.holograms = new HashMap<>();
        this.rotationTasks = new HashMap<>();
        this.currentIndex = new HashMap<>();
    }

    public void createHologram(PlayerStall stall) {
        UUID ownerUuid = stall.getOwnerUuid();
        removeHologram(ownerUuid);

        Location loc = stall.getLocation().clone().add(0, 2.5, 0);
        List<ArmorStand> stands = new ArrayList<>();

        // 创建标题全息
        ArmorStand titleStand = createHologramLine(loc, "§6§l" + stall.getOwnerName() + "的摊位");
        stands.add(titleStand);

        // 创建标语全息
        ArmorStand sloganStand = createHologramLine(loc.clone().subtract(0, 0.3, 0), "§e" + stall.getSlogan());
        stands.add(sloganStand);

        // 创建商品展示全息
        ArmorStand itemStand = createHologramLine(loc.clone().subtract(0, 0.6, 0), "§7加载中...");
        stands.add(itemStand);

        // 创建价格全息
        ArmorStand priceStand = createHologramLine(loc.clone().subtract(0, 0.9, 0), "");
        stands.add(priceStand);

        holograms.put(ownerUuid, stands);
        currentIndex.put(ownerUuid, 0);

        // 启动商品轮播任务
        startRotation(stall);
    }

    private ArmorStand createHologramLine(Location location, String text) {
        ArmorStand stand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setCustomName(text);
        stand.setCustomNameVisible(true);
        stand.setMarker(true);
        stand.setInvulnerable(true);
        stand.setSmall(true);
        return stand;
    }

    private void startRotation(PlayerStall stall) {
        UUID ownerUuid = stall.getOwnerUuid();
        int interval = plugin.getConfigManager().getHologramRefreshInterval();

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            updateHologramDisplay(stall);
        }, 0L, interval);

        rotationTasks.put(ownerUuid, task);
    }

    private void updateHologramDisplay(PlayerStall stall) {
        UUID ownerUuid = stall.getOwnerUuid();
        List<ArmorStand> stands = holograms.get(ownerUuid);
        
        if (stands == null || stands.size() < 4) {
            return;
        }

        Map<Integer, StallItem> items = stall.getItems();
        if (items.isEmpty()) {
            stands.get(2).setCustomName("§7暂无商品");
            stands.get(3).setCustomName("§7Shift+右键查看");
            return;
        }

        // 轮播商品
        StallItem[] itemArray = items.values().toArray(new StallItem[0]);
        int index = currentIndex.getOrDefault(ownerUuid, 0);
        
        if (index >= itemArray.length) {
            index = 0;
        }

        StallItem item = itemArray[index];
        
        // 更新商品名称
        String itemName = item.getItemName();
        if (itemName.length() > 20) {
            itemName = itemName.substring(0, 17) + "...";
        }
        stands.get(2).setCustomName("§f" + itemName + " §7x" + item.getAmount());
        
        // 更新价格
        String priceText = plugin.getEconomyManager().formatCurrency(item.getPrice(), item.getCurrencyType());
        stands.get(3).setCustomName("§a价格: " + priceText);

        // 更新索引
        currentIndex.put(ownerUuid, (index + 1) % itemArray.length);
    }

    public void updateHologram(PlayerStall stall) {
        UUID ownerUuid = stall.getOwnerUuid();
        List<ArmorStand> stands = holograms.get(ownerUuid);
        
        if (stands == null || stands.isEmpty()) {
            createHologram(stall);
            return;
        }

        // 更新标语
        if (stands.size() > 1) {
            stands.get(1).setCustomName("§e" + stall.getSlogan());
        }
    }

    public void removeHologram(UUID ownerUuid) {
        // 取消轮播任务
        BukkitTask task = rotationTasks.remove(ownerUuid);
        if (task != null) {
            task.cancel();
        }

        // 移除全息实体
        List<ArmorStand> stands = holograms.remove(ownerUuid);
        if (stands != null) {
            for (ArmorStand stand : stands) {
                if (stand != null && !stand.isDead()) {
                    stand.remove();
                }
            }
        }

        currentIndex.remove(ownerUuid);
    }

    public void removeAllHolograms() {
        for (UUID uuid : new ArrayList<>(holograms.keySet())) {
            removeHologram(uuid);
        }
    }

    public void teleportHologram(UUID ownerUuid, Location newLocation) {
        List<ArmorStand> stands = holograms.get(ownerUuid);
        if (stands == null) {
            return;
        }

        Location baseLoc = newLocation.clone().add(0, 2.5, 0);
        for (int i = 0; i < stands.size(); i++) {
            ArmorStand stand = stands.get(i);
            if (stand != null && !stand.isDead()) {
                stand.teleport(baseLoc.clone().subtract(0, i * 0.3, 0));
            }
        }
    }
}

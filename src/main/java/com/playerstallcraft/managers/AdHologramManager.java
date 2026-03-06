package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.AdSlot;
import com.playerstallcraft.models.Advertisement;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.event.HologramClickEvent;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 广告全息图管理器
 */
public class AdHologramManager implements Listener {

    private final PlayerStallCraft plugin;
    private final Map<Integer, Hologram> slotHolograms; // slotId -> hologram
    private BukkitTask rotationTask;
    private boolean useDecentHolograms = false;

    public AdHologramManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        this.slotHolograms = new ConcurrentHashMap<>();
    }

    public void init() {
        this.useDecentHolograms = plugin.getServer().getPluginManager().getPlugin("DecentHolograms") != null;
        
        if (useDecentHolograms) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            startRotationTask();
        }
    }

    @EventHandler
    public void onHologramClick(HologramClickEvent event) {
        String holoName = event.getHologram().getName();
        if (!holoName.startsWith("ad_slot_")) return;

        int slotId;
        try {
            slotId = Integer.parseInt(holoName.substring(8));
        } catch (NumberFormatException e) {
            return;
        }

        com.playerstallcraft.models.Advertisement currentAd = plugin.getAdManager().getCurrentAd(slotId);
        if (currentAd == null || currentAd.getShopId() <= 0) return;

        Player player = event.getPlayer();
        var shops = plugin.getShopManager().getPlayerShops(currentAd.getOwnerUuid());
        if (shops == null || shops.isEmpty()) return;

        com.playerstallcraft.models.Shop shop = shops.stream()
                .filter(s -> s.getId() == currentAd.getShopId())
                .findFirst().orElse(null);
        if (shop == null || shop.getLocation() == null) return;

        player.teleport(shop.getLocation());
        plugin.getMessageManager().sendRaw(player, "&a已传送到商铺: &e" + shop.getName());
    }

    private void startRotationTask() {
        int interval = plugin.getConfig().getInt("advertisement.rotation-interval", 100); // 5秒
        
        rotationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<Integer, AdSlot> entry : plugin.getAdManager().getAdSlots().entrySet()) {
                int slotId = entry.getKey();
                AdSlot slot = entry.getValue();
                
                if (slot.isActive()) {
                    plugin.getAdManager().nextAd(slotId);
                    updateSlotHologram(slot);
                }
            }
            // 检查过期广告
            plugin.getAdManager().checkExpiredAds();
        }, interval, interval);
    }

    public void createSlotHologram(AdSlot slot) {
        if (!useDecentHolograms) return;
        if (slot.getLocation() == null) return;

        String holoName = "ad_slot_" + slot.getId();
        
        // 删除旧全息图
        try {
            if (DHAPI.getHologram(holoName) != null) {
                DHAPI.removeHologram(holoName);
            }
        } catch (Exception ignored) {}

        double heightOffset = plugin.getConfig().getDouble("advertisement.hologram-height", 3.0);
        Location holoLoc = slot.getLocation().clone().add(0.5, heightOffset, 0.5);

        List<String> lines = buildHologramLines(slot);
        
        try {
            Hologram hologram = DHAPI.createHologram(holoName, holoLoc, lines);
            slotHolograms.put(slot.getId(), hologram);
        } catch (Exception e) {
            plugin.getLogger().warning("创建广告全息图失败: " + e.getMessage());
        }
    }

    public void updateSlotHologram(AdSlot slot) {
        if (!useDecentHolograms) return;

        Hologram existing = slotHolograms.get(slot.getId());
        if (existing == null) {
            // 首次创建
            createSlotHologram(slot);
            return;
        }

        // 原地更新行内容，不删除/重建，避免闪烁
        List<String> lines = buildHologramLines(slot);
        try {
            DHAPI.setHologramLines(existing, lines);
        } catch (Exception e) {
            // 降级：重建
            removeSlotHologram(slot.getId());
            createSlotHologram(slot);
        }
    }

    public void removeSlotHologram(int slotId) {
        if (!useDecentHolograms) return;

        String holoName = "ad_slot_" + slotId;
        try {
            if (DHAPI.getHologram(holoName) != null) {
                DHAPI.removeHologram(holoName);
            }
        } catch (Exception ignored) {}
        slotHolograms.remove(slotId);
    }

    private List<String> buildHologramLines(AdSlot slot) {
        List<String> lines = new ArrayList<>();
        
        Advertisement currentAd = plugin.getAdManager().getCurrentAd(slot.getId());
        
        if (currentAd == null) {
            // 无广告时显示招租信息
            lines.add("&6&l═══════════════");
            lines.add("&e&l📢 广告位招租");
            lines.add("&6&l═══════════════");
            lines.add("");
            lines.add("&7广告位: &f" + slot.getName());
            lines.add("");
            String currencyName = plugin.getEconomyManager().getCurrencyName(slot.getCurrencyType());
            lines.add("&6价格: &e" + String.format("%.0f", slot.getPricePerHour()) + " " + currencyName + "/小时");
            lines.add("");
            lines.add("&a使用 /ad place " + slot.getId() + " 投放广告");
            lines.add("&6&l═══════════════");
        } else {
            // 显示广告内容
            lines.add("&6&l═══════════════");
            lines.add("&e&l⭐ " + currentAd.getTitle() + " ⭐");
            lines.add("&6&l═══════════════");
            lines.add("");
            
            // 物品图标
            if (currentAd.getIconMaterial() != null && !currentAd.getIconMaterial().isEmpty()) {
                lines.add("#ICON:" + currentAd.getIconMaterial());
            }
            
            // 描述
            if (currentAd.getDescription() != null && !currentAd.getDescription().isEmpty()) {
                lines.add("&f" + currentAd.getDescription());
            }
            
            lines.add("");
            lines.add("&7店主: &f" + currentAd.getOwnerName());
            lines.add("&7剩余: &f" + currentAd.getRemainingTimeString());
            lines.add("");
            
            // 如果关联了商铺，显示传送提示
            if (currentAd.getShopId() > 0) {
                lines.add("&a「点击传送到商铺」");
            }
            
            // 显示广告队列位置
            List<Advertisement> ads = plugin.getAdManager().getSlotAds(slot.getId());
            if (ads.size() > 1) {
                int index = ads.indexOf(currentAd) + 1;
                lines.add("&8[" + index + "/" + ads.size() + "]");
            }
            
            lines.add("&6&l═══════════════");
        }
        
        return lines;
    }

    public void reloadAll() {
        if (!useDecentHolograms) return;
        
        // 清除所有
        for (Integer slotId : new ArrayList<>(slotHolograms.keySet())) {
            removeSlotHologram(slotId);
        }
        
        // 重新创建
        for (AdSlot slot : plugin.getAdManager().getAdSlots().values()) {
            if (slot.isActive()) {
                createSlotHologram(slot);
            }
        }
    }

    public void shutdown() {
        if (rotationTask != null) {
            rotationTask.cancel();
        }
        
        for (Integer slotId : new ArrayList<>(slotHolograms.keySet())) {
            removeSlotHologram(slotId);
        }
        slotHolograms.clear();
    }
}

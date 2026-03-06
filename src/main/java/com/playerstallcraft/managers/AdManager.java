package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.AdSlot;
import com.playerstallcraft.models.Advertisement;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 广告管理器
 * 负责管理广告位和广告投放
 */
public class AdManager {

    private final PlayerStallCraft plugin;
    private final Map<Integer, AdSlot> adSlots; // 广告位
    private final Map<Integer, Advertisement> advertisements; // 当前投放的广告
    private final Map<Integer, List<Advertisement>> slotAds; // 每个广告位的广告队列
    private final Map<Integer, Integer> currentAdIndex; // 每个广告位当前显示的广告索引
    private final AtomicInteger nextSlotId = new AtomicInteger(1);
    private final AtomicInteger nextAdId = new AtomicInteger(1);
    private final Set<Integer> remindedAds = ConcurrentHashMap.newKeySet();

    public AdManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        this.adSlots = new ConcurrentHashMap<>();
        this.advertisements = new ConcurrentHashMap<>();
        this.slotAds = new ConcurrentHashMap<>();
        this.currentAdIndex = new ConcurrentHashMap<>();
        loadData();
    }

    private void loadData() {
        loadAdSlots();
        loadAdvertisements();
    }

    private void loadAdSlots() {
        plugin.getDatabaseManager().queryAsync("SELECT * FROM ad_slots")
            .thenAccept(rs -> {
                try {
                    if (rs != null) {
                        while (rs.next()) {
                            int id = rs.getInt("id");
                            String name = rs.getString("name");
                            String world = rs.getString("world");
                            double x = rs.getDouble("x");
                            double y = rs.getDouble("y");
                            double z = rs.getDouble("z");
                            double pricePerHour = rs.getDouble("price_per_hour");
                            String currencyType = rs.getString("currency_type");
                            boolean active = rs.getBoolean("active");

                            if (Bukkit.getWorld(world) != null) {
                                Location loc = new Location(Bukkit.getWorld(world), x, y, z);
                                AdSlot slot = new AdSlot(id, name, loc);
                                slot.setPricePerHour(pricePerHour);
                                slot.setCurrencyType(currencyType);
                                slot.setActive(active);
                                adSlots.put(id, slot);
                                slotAds.put(id, new ArrayList<>());
                                currentAdIndex.put(id, 0);
                                
                                if (id >= nextSlotId.get()) {
                                    nextSlotId.set(id + 1);
                                }
                            }
                        }
                        rs.close();
                    }
                    plugin.getLogger().info("已加载 " + adSlots.size() + " 个广告位");
                } catch (Exception e) {
                    plugin.getLogger().warning("加载广告位失败: " + e.getMessage());
                }
            });
    }

    private void loadAdvertisements() {
        plugin.getDatabaseManager().queryAsync("SELECT * FROM advertisements WHERE active = 1")
            .thenAccept(rs -> {
                try {
                    if (rs != null) {
                        while (rs.next()) {
                            int id = rs.getInt("id");
                            int slotId = rs.getInt("slot_id");
                            UUID ownerUuid = UUID.fromString(rs.getString("owner_uuid"));
                            String ownerName = rs.getString("owner_name");
                            String title = rs.getString("title");
                            String description = rs.getString("description");
                            String iconMaterial = rs.getString("icon_material");
                            int shopId = rs.getInt("shop_id");
                            long startTime = rs.getLong("start_time");
                            long endTime = rs.getLong("end_time");
                            double price = rs.getDouble("price");
                            String currencyType = rs.getString("currency_type");

                            Advertisement ad = new Advertisement(id, ownerUuid, ownerName, title, description);
                            ad.setIconMaterial(iconMaterial);
                            ad.setShopId(shopId);
                            ad.setStartTime(startTime);
                            ad.setEndTime(endTime);
                            ad.setPrice(price);
                            ad.setCurrencyType(currencyType);

                            // 检查是否过期
                            if (ad.isExpired()) {
                                ad.setActive(false);
                                updateAdStatus(id, false);
                            } else {
                                advertisements.put(id, ad);
                                if (slotAds.containsKey(slotId)) {
                                    slotAds.get(slotId).add(ad);
                                }
                            }

                            if (id >= nextAdId.get()) {
                                nextAdId.set(id + 1);
                            }
                        }
                        rs.close();
                    }
                    plugin.getLogger().info("已加载 " + advertisements.size() + " 个活跃广告");
                    
                    // 初始化全息图
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        plugin.getAdHologramManager().reloadAll();
                    }, 40L);
                    // 启动到期提醒定时任务
                    startExpiryReminderTask();
                } catch (Exception e) {
                    plugin.getLogger().warning("加载广告失败: " + e.getMessage());
                }
            });
    }

    /**
     * 创建广告位 (管理员)
     */
    public AdSlot createAdSlot(String name, Location location, double pricePerHour, String currencyType) {
        int id = nextSlotId.getAndIncrement();
        AdSlot slot = new AdSlot(id, name, location);
        slot.setPricePerHour(pricePerHour);
        slot.setCurrencyType(currencyType);
        
        adSlots.put(id, slot);
        slotAds.put(id, new ArrayList<>());
        currentAdIndex.put(id, 0);
        
        // 保存到数据库
        plugin.getDatabaseManager().executeAsync(
            "INSERT INTO ad_slots (id, name, world, x, y, z, price_per_hour, currency_type, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, name, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
            pricePerHour, currencyType, true
        );
        
        // 创建全息图
        plugin.getAdHologramManager().createSlotHologram(slot);
        
        return slot;
    }

    /**
     * 删除广告位 (管理员)
     */
    public boolean deleteAdSlot(int slotId) {
        AdSlot slot = adSlots.remove(slotId);
        if (slot == null) return false;
        
        // 删除该广告位的所有广告
        List<Advertisement> ads = slotAds.remove(slotId);
        if (ads != null) {
            for (Advertisement ad : ads) {
                advertisements.remove(ad.getId());
            }
        }
        currentAdIndex.remove(slotId);
        
        // 删除全息图
        plugin.getAdHologramManager().removeSlotHologram(slotId);
        
        // 从数据库删除
        plugin.getDatabaseManager().executeAsync("DELETE FROM ad_slots WHERE id = ?", slotId);
        plugin.getDatabaseManager().executeAsync("DELETE FROM advertisements WHERE slot_id = ?", slotId);
        
        return true;
    }

    /**
     * 投放广告 (玩家)
     */
    public boolean placeAdvertisement(Player player, int slotId, String title, String description, 
                                       String iconMaterial, int hours, int shopId) {
        AdSlot slot = adSlots.get(slotId);
        if (slot == null || !slot.isActive()) {
            plugin.getMessageManager().sendRaw(player, "&c该广告位不存在或已关闭");
            return false;
        }

        double totalPrice = slot.getPricePerHour() * hours;
        String currencyType = slot.getCurrencyType();

        // 检查余额
        String currencyName = plugin.getEconomyManager().getCurrencyName(currencyType);
        if (!plugin.getEconomyManager().has(player, totalPrice, currencyType)) {
            double balance = plugin.getEconomyManager().getBalance(player, currencyType);
            plugin.getMessageManager().sendRaw(player, "&c余额不足! 需要 &e" + String.format("%.0f", totalPrice) + " " + currencyName + 
                " &c当前余额: &e" + String.format("%.0f", balance) + " " + currencyName);
            return false;
        }

        // 扣费
        if (!plugin.getEconomyManager().withdraw(player, totalPrice, currencyType)) {
            plugin.getMessageManager().sendRaw(player, "&c扣款失败! 请联系管理员");
            return false;
        }

        int id = nextAdId.getAndIncrement();
        Advertisement ad = new Advertisement(id, player.getUniqueId(), player.getName(), title, description);
        ad.setIconMaterial(iconMaterial);
        ad.setShopId(shopId);
        ad.setStartTime(System.currentTimeMillis());
        ad.setEndTime(System.currentTimeMillis() + (hours * 60L * 60L * 1000L));
        ad.setPrice(totalPrice);
        ad.setCurrencyType(currencyType);

        advertisements.put(id, ad);
        slotAds.get(slotId).add(ad);

        // 保存到数据库
        plugin.getDatabaseManager().executeAsync(
            "INSERT INTO advertisements (id, slot_id, owner_uuid, owner_name, title, description, icon_material, shop_id, start_time, end_time, price, currency_type, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id, slotId, player.getUniqueId().toString(), player.getName(), title, description,
            iconMaterial, shopId, ad.getStartTime(), ad.getEndTime(), totalPrice, currencyType, true
        );

        // 更新全息图
        plugin.getAdHologramManager().updateSlotHologram(slot);

        plugin.getMessageManager().sendRaw(player, "&a广告投放成功! 花费 " + totalPrice + " " + currencyName + "，有效期 " + hours + " 小时");
        return true;
    }

    /**
     * 取消广告 (玩家/管理员)
     */
    public boolean cancelAdvertisement(int adId, Player operator) {
        Advertisement ad = advertisements.get(adId);
        if (ad == null) return false;

        // 检查权限
        if (!operator.hasPermission("playerstallcraft.admin") && !ad.getOwnerUuid().equals(operator.getUniqueId())) {
            plugin.getMessageManager().sendRaw(operator, "&c你没有权限取消这个广告");
            return false;
        }

        ad.setActive(false);
        advertisements.remove(adId);
        
        // 从广告位队列中移除
        for (List<Advertisement> ads : slotAds.values()) {
            ads.removeIf(a -> a.getId() == adId);
        }

        updateAdStatus(adId, false);
        
        // 更新全息图
        for (Map.Entry<Integer, List<Advertisement>> entry : slotAds.entrySet()) {
            if (entry.getValue().stream().anyMatch(a -> a.getId() == adId)) {
                AdSlot slot = adSlots.get(entry.getKey());
                if (slot != null) {
                    plugin.getAdHologramManager().updateSlotHologram(slot);
                }
            }
        }

        plugin.getMessageManager().sendRaw(operator, "&a广告已取消");
        return true;
    }

    /**
     * 广告续期
     */
    public boolean renewAdvertisement(Player player, int adId, int hours) {
        Advertisement ad = advertisements.get(adId);
        if (ad == null || !ad.getOwnerUuid().equals(player.getUniqueId())) {
            plugin.getMessageManager().sendRaw(player, "&c找不到该广告或无权限");
            return false;
        }
        // 找广告所在广告位
        int slotId = findSlotIdForAd(adId);
        if (slotId == -1) { plugin.getMessageManager().sendRaw(player, "&c找不到广告位"); return false; }
        AdSlot slot = adSlots.get(slotId);
        double cost = slot.getPricePerHour() * hours;
        String currencyType = slot.getCurrencyType();
        String currencyName = plugin.getEconomyManager().getCurrencyName(currencyType);
        if (!plugin.getEconomyManager().has(player, cost, currencyType)) {
            plugin.getMessageManager().sendRaw(player, "&c余额不足! 需要 &e" + String.format("%.0f", cost) + " " + currencyName);
            return false;
        }
        if (!plugin.getEconomyManager().withdraw(player, cost, currencyType)) {
            plugin.getMessageManager().sendRaw(player, "&c扣款失败!");
            return false;
        }
        ad.setEndTime(ad.getEndTime() + (hours * 60L * 60L * 1000L));
        plugin.getDatabaseManager().executeAsync(
                "UPDATE advertisements SET end_time = ? WHERE id = ?", ad.getEndTime(), adId);
        // 续期后重置提醒状态
        remindedAds.remove(adId);
        plugin.getMessageManager().sendRaw(player,
                "&a广告续期成功! 花费 &e" + String.format("%.0f", cost) + " " + currencyName + "&a, 延长 &e" + hours + " &a小时");
        return true;
    }

    /**
     * 广告取消（支持按剩余时间比例退款）
     */
    public boolean cancelAdvertisementWithRefund(int adId, Player operator) {
        Advertisement ad = advertisements.get(adId);
        if (ad == null) return false;
        if (!operator.hasPermission("playerstallcraft.admin") && !ad.getOwnerUuid().equals(operator.getUniqueId())) {
            plugin.getMessageManager().sendRaw(operator, "&c你没有权限取消这个广告");
            return false;
        }
        // 计算退款
        long remaining = ad.getEndTime() - System.currentTimeMillis();
        long total = ad.getEndTime() - ad.getStartTime();
        if (remaining > 0 && total > 0 && ad.getPrice() > 0) {
            double ratio = (double) remaining / total;
            double refund = ad.getPrice() * ratio;
            if (refund >= 0.01) {
                plugin.getEconomyManager().deposit(operator, refund, ad.getCurrencyType());
                String currencyName = plugin.getEconomyManager().getCurrencyName(ad.getCurrencyType());
                plugin.getMessageManager().sendRaw(operator,
                        "&a已退款 &e" + String.format("%.2f", refund) + " " + currencyName
                        + " &a（剩余时间 &e" + String.format("%.0f", ratio * 100) + "% &a比例）");
            }
        }
        return cancelAdvertisement(adId, operator);
    }

    /**
     * 切换广告位启用/禁用
     */
    public void toggleAdSlotActive(int slotId) {
        AdSlot slot = adSlots.get(slotId);
        if (slot == null) return;
        slot.setActive(!slot.isActive());
        plugin.getDatabaseManager().executeAsync(
                "UPDATE ad_slots SET active = ? WHERE id = ?", slot.isActive() ? 1 : 0, slotId);
    }

    /**
     * 修改广告位价格
     */
    public void updateAdSlotPrice(int slotId, double newPrice) {
        AdSlot slot = adSlots.get(slotId);
        if (slot == null) return;
        slot.setPricePerHour(newPrice);
        plugin.getDatabaseManager().executeAsync(
                "UPDATE ad_slots SET price_per_hour = ? WHERE id = ?", newPrice, slotId);
    }

    /**
     * 修改广告位货币类型
     */
    public void updateAdSlotCurrency(int slotId, String currencyType) {
        AdSlot slot = adSlots.get(slotId);
        if (slot == null) return;
        slot.setCurrencyType(currencyType);
        plugin.getDatabaseManager().executeAsync(
                "UPDATE ad_slots SET currency_type = ? WHERE id = ?", currencyType, slotId);
    }

    /**
     * 返回每个广告位的统计： slotId -> [活跃广告数, 总收入*100(int)]
     */
    public Map<Integer, long[]> getAdStats() {
        Map<Integer, long[]> stats = new HashMap<>();
        for (Integer slotId : adSlots.keySet()) {
            List<Advertisement> ads = slotAds.getOrDefault(slotId, new ArrayList<>());
            long count = ads.size();
            long revenue = (long) ads.stream().mapToDouble(Advertisement::getPrice).sum();
            stats.put(slotId, new long[]{count, revenue});
        }
        return stats;
    }

    /**
     * 启动广告到期提醒定时任务（每5分钟检查一次，剩余2小时内提醒）
     */
    private void startExpiryReminderTask() {
        long reminderMs = 2L * 60 * 60 * 1000; // 2 hours
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Advertisement ad : advertisements.values()) {
                if (remindedAds.contains(ad.getId())) continue;
                long remaining = ad.getEndTime() - now;
                if (remaining > 0 && remaining <= reminderMs) {
                    remindedAds.add(ad.getId());
                    long hoursLeft = TimeUnit.MILLISECONDS.toHours(remaining);
                    long minsLeft = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60;
                    Player owner = Bukkit.getPlayer(ad.getOwnerUuid());
                    if (owner != null) {
                        plugin.getMessageManager().sendRaw(owner,
                                "&e【广告提醒】&7你的广告 &f" + ad.getTitle()
                                + " &7还有 &e" + hoursLeft + "小时" + minsLeft + "分钟"
                                + " &7到期，请及时续费! &8(/baitan ad)");
                    }
                }
            }
        }, 20L * 60 * 5, 20L * 60 * 5); // every 5 min
    }

    private int findSlotIdForAd(int adId) {
        for (Map.Entry<Integer, List<Advertisement>> entry : slotAds.entrySet()) {
            if (entry.getValue().stream().anyMatch(a -> a.getId() == adId)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    private void updateAdStatus(int adId, boolean active) {
        plugin.getDatabaseManager().executeAsync(
            "UPDATE advertisements SET active = ? WHERE id = ?",
            active, adId
        );
    }

    /**
     * 获取广告位的当前展示广告
     */
    public Advertisement getCurrentAd(int slotId) {
        List<Advertisement> ads = slotAds.get(slotId);
        if (ads == null || ads.isEmpty()) return null;

        // 移除过期广告
        ads.removeIf(ad -> {
            if (ad.isExpired()) {
                ad.setActive(false);
                advertisements.remove(ad.getId());
                updateAdStatus(ad.getId(), false);
                return true;
            }
            return false;
        });

        if (ads.isEmpty()) return null;

        int index = currentAdIndex.getOrDefault(slotId, 0) % ads.size();
        return ads.get(index);
    }

    /**
     * 切换到下一个广告 (轮播)
     */
    public void nextAd(int slotId) {
        List<Advertisement> ads = slotAds.get(slotId);
        if (ads == null || ads.isEmpty()) return;

        int current = currentAdIndex.getOrDefault(slotId, 0);
        currentAdIndex.put(slotId, (current + 1) % ads.size());
    }

    public Map<Integer, AdSlot> getAdSlots() {
        return adSlots;
    }

    public AdSlot getAdSlot(int slotId) {
        return adSlots.get(slotId);
    }

    public List<Advertisement> getSlotAds(int slotId) {
        return slotAds.getOrDefault(slotId, new ArrayList<>());
    }

    public List<Advertisement> getPlayerAds(UUID playerUuid) {
        List<Advertisement> result = new ArrayList<>();
        for (Advertisement ad : advertisements.values()) {
            if (ad.getOwnerUuid().equals(playerUuid)) {
                result.add(ad);
            }
        }
        return result;
    }

    /**
     * 检查并清理过期广告
     */
    public void checkExpiredAds() {
        for (Map.Entry<Integer, List<Advertisement>> entry : slotAds.entrySet()) {
            List<Advertisement> ads = entry.getValue();
            boolean changed = ads.removeIf(ad -> {
                if (ad.isExpired()) {
                    ad.setActive(false);
                    advertisements.remove(ad.getId());
                    updateAdStatus(ad.getId(), false);
                    return true;
                }
                return false;
            });
            
            if (changed) {
                AdSlot slot = adSlots.get(entry.getKey());
                if (slot != null) {
                    plugin.getAdHologramManager().updateSlotHologram(slot);
                }
            }
        }
    }
}

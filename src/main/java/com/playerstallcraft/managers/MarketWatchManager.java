package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MarketWatchManager {

    private final PlayerStallCraft plugin;
    // 内存缓存: playerUuid -> Set<itemType>
    private final Map<UUID, Set<String>> watches = new ConcurrentHashMap<>();

    public MarketWatchManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        loadAll();
    }

    private void loadAll() {
        plugin.getDatabaseManager().queryAsync("SELECT player_uuid, item_type FROM market_watches")
            .thenAccept(rs -> {
                try {
                    if (rs != null) {
                        while (rs.next()) {
                            UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                            String itemType = rs.getString("item_type");
                            watches.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(itemType);
                        }
                        rs.close();
                    }
                    plugin.getLogger().info("已加载 " + watches.values().stream().mapToInt(Set::size).sum() + " 条物品关注记录");
                } catch (Exception e) {
                    plugin.getLogger().warning("加载关注列表失败: " + e.getMessage());
                }
            });
    }

    public boolean isWatching(UUID playerUuid, String itemType) {
        Set<String> set = watches.get(playerUuid);
        return set != null && set.contains(normalize(itemType));
    }

    public boolean addWatch(UUID playerUuid, String itemType) {
        String norm = normalize(itemType);
        Set<String> set = watches.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet());
        if (!set.add(norm)) return false; // already watching
        plugin.getDatabaseManager().executeAsync(
            "INSERT OR IGNORE INTO market_watches (player_uuid, item_type, created_at) VALUES (?, ?, ?)",
            playerUuid.toString(), norm, System.currentTimeMillis()
        );
        return true;
    }

    public boolean removeWatch(UUID playerUuid, String itemType) {
        String norm = normalize(itemType);
        Set<String> set = watches.get(playerUuid);
        if (set == null || !set.remove(norm)) return false;
        plugin.getDatabaseManager().executeAsync(
            "DELETE FROM market_watches WHERE player_uuid = ? AND item_type = ?",
            playerUuid.toString(), norm
        );
        return true;
    }

    public List<String> getWatches(UUID playerUuid) {
        Set<String> set = watches.get(playerUuid);
        if (set == null) return Collections.emptyList();
        return new ArrayList<>(set);
    }

    /**
     * 新商品上架时通知关注该物品的在线玩家
     * @param itemType  上架物品的类型名（Material名 或 cobblemon:xxx）
     * @param itemName  显示名称
     * @param sellerName 卖家名
     * @param unitPrice  单价
     * @param currencyType  货币类型
     */
    public void notifyWatchers(String itemType, String itemName, String sellerName,
                               double unitPrice, String currencyType) {
        String norm = normalize(itemType);
        String currency = currencyType.equals("vault") ? "金币" : "鸽币";
        String priceStr = plugin.getEconomyManager().formatCurrency(unitPrice, currencyType);

        for (Map.Entry<UUID, Set<String>> entry : watches.entrySet()) {
            if (!entry.getValue().contains(norm)) continue;
            Player online = plugin.getServer().getPlayer(entry.getKey());
            if (online == null || !online.isOnline()) continue;
            plugin.getMessageManager().sendRaw(online,
                "&6[市场关注] &e" + itemName + " &7有新上架! " +
                "&7卖家: &f" + sellerName + " &7单价: &e" + priceStr + " " + currency +
                " &7| 输入 &a/baitan market &7查看");
        }
    }

    private String normalize(String itemType) {
        return itemType == null ? "" : itemType.toLowerCase().trim();
    }
}

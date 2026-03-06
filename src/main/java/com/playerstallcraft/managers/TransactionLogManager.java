package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class TransactionLogManager {

    private final PlayerStallCraft plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public TransactionLogManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
    }

    /** 记录全服市场购买交易（异步写库） */
    public void logMarketPurchase(UUID buyerUuid, String buyerName, UUID sellerUuid, String sellerName,
                                   String itemName, int amount, double price, String currencyType) {
        plugin.getDatabaseManager().executeAsync(
            "INSERT INTO transaction_logs (type, seller_uuid, seller_name, buyer_uuid, buyer_name, item_name, amount, price, currency_type, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
            "market", sellerUuid.toString(), sellerName, buyerUuid.toString(), buyerName,
            itemName, amount, price, currencyType, System.currentTimeMillis()
        );
    }

    /** 记录摆摊购买交易（异步写库） */
    public void logStallPurchase(UUID buyerUuid, String buyerName, UUID sellerUuid, String sellerName,
                                  String itemName, int amount, double price, String currencyType) {
        plugin.getDatabaseManager().executeAsync(
            "INSERT INTO transaction_logs (type, seller_uuid, seller_name, buyer_uuid, buyer_name, item_name, amount, price, currency_type, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
            "stall", sellerUuid.toString(), sellerName, buyerUuid.toString(), buyerName,
            itemName, amount, price, currencyType, System.currentTimeMillis()
        );
    }

    /** 异步查询玩家购买记录，回调在主线程执行 */
    public void getPlayerPurchasesAsync(UUID playerUuid, int limit, Consumer<List<String>> callback) {
        plugin.getDatabaseManager().queryAsync(
            "SELECT * FROM transaction_logs WHERE buyer_uuid = ? ORDER BY created_at DESC LIMIT ?",
            playerUuid.toString(), limit
        ).thenAccept(rs -> {
            List<String> records = new ArrayList<>();
            try {
                if (rs != null) {
                    while (rs.next()) {
                        String type = rs.getString("type");
                        String ts = dateFormat.format(new Date(rs.getLong("created_at")));
                        String label = "stall".equals(type) ? "摊位购买" : "市场购买";
                        records.add(String.format("[%s] %s 从 %s 购买 %s x%d，花费 %s",
                            ts, label, rs.getString("seller_name"), rs.getString("item_name"),
                            rs.getInt("amount"),
                            plugin.getEconomyManager().formatCurrency(rs.getDouble("price"), rs.getString("currency_type"))
                        ));
                    }
                    rs.close();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("查询购买记录失败: " + e.getMessage());
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(records));
        });
    }

    /** 异步查询玩家销售记录，回调在主线程执行 */
    public void getPlayerSalesAsync(UUID playerUuid, int limit, Consumer<List<String>> callback) {
        plugin.getDatabaseManager().queryAsync(
            "SELECT * FROM transaction_logs WHERE seller_uuid = ? ORDER BY created_at DESC LIMIT ?",
            playerUuid.toString(), limit
        ).thenAccept(rs -> {
            List<String> records = new ArrayList<>();
            try {
                if (rs != null) {
                    while (rs.next()) {
                        String type = rs.getString("type");
                        String ts = dateFormat.format(new Date(rs.getLong("created_at")));
                        String label = "stall".equals(type) ? "摊位售出" : "市场售出";
                        records.add(String.format("[%s] %s 卖给 %s %s x%d，收入 %s",
                            ts, label, rs.getString("buyer_name"), rs.getString("item_name"),
                            rs.getInt("amount"),
                            plugin.getEconomyManager().formatCurrency(rs.getDouble("price"), rs.getString("currency_type"))
                        ));
                    }
                    rs.close();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("查询销售记录失败: " + e.getMessage());
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(records));
        });
    }

    /** 异步查询物品近期成交单价（用于价格参考），回调在主线程 */
    public void getItemPriceHistoryAsync(String itemName, String currencyType, int limit, Consumer<List<Double>> callback) {
        plugin.getDatabaseManager().queryAsync(
            "SELECT price, amount FROM transaction_logs WHERE item_name LIKE ? AND currency_type = ? ORDER BY created_at DESC LIMIT ?",
            "%" + itemName + "%", currencyType, limit
        ).thenAccept(rs -> {
            List<Double> prices = new ArrayList<>();
            try {
                if (rs != null) {
                    while (rs.next()) {
                        int amt = rs.getInt("amount");
                        if (amt > 0) prices.add(rs.getDouble("price") / amt);
                    }
                    rs.close();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("查询价格历史失败: " + e.getMessage());
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(prices));
        });
    }

    /**
     * 异步查询卖家统计数据（用于统计看板）
     * 返回：totalRevenue, totalOrders, topItems(item->count)
     */
    public void getSellerStatsAsync(UUID sellerUuid, String currencyType, long sinceMs, Consumer<SellerStats> callback) {
        plugin.getDatabaseManager().queryAsync(
            "SELECT item_name, amount, price FROM transaction_logs WHERE seller_uuid = ? AND currency_type = ? AND created_at >= ? ORDER BY created_at DESC",
            sellerUuid.toString(), currencyType, sinceMs
        ).thenAccept(rs -> {
            double totalRevenue = 0;
            int totalOrders = 0;
            Map<String, Integer> itemCounts = new LinkedHashMap<>();
            try {
                if (rs != null) {
                    while (rs.next()) {
                        totalRevenue += rs.getDouble("price");
                        totalOrders++;
                        String item = rs.getString("item_name");
                        int amt = rs.getInt("amount");
                        itemCounts.merge(item, amt, Integer::sum);
                    }
                    rs.close();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("查询卖家统计失败: " + e.getMessage());
            }
            // 取 Top 3 畅销品
            List<Map.Entry<String, Integer>> top = new ArrayList<>(itemCounts.entrySet());
            top.sort((a, b) -> b.getValue() - a.getValue());
            List<Map.Entry<String, Integer>> top3 = top.subList(0, Math.min(3, top.size()));
            SellerStats stats = new SellerStats(totalRevenue, totalOrders, top3);
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(stats));
        });
    }

    /**
     * 异步查询销售额排行榜（前 limit 名），回调在主线程
     * 返回 List<String[]> 每项 [seller_name, totalRevenue, totalOrders, currency_type]
     */
    public void getTopSellersAsync(String currencyType, int limit, long sinceMs, Consumer<List<String[]>> callback) {
        plugin.getDatabaseManager().queryAsync(
            "SELECT seller_name, SUM(price) as revenue, COUNT(*) as orders FROM transaction_logs" +
            " WHERE currency_type = ? AND created_at >= ? GROUP BY seller_uuid ORDER BY revenue DESC LIMIT ?",
            currencyType, sinceMs, limit
        ).thenAccept(rs -> {
            List<String[]> rows = new ArrayList<>();
            try {
                if (rs != null) {
                    while (rs.next()) {
                        rows.add(new String[]{
                            rs.getString("seller_name"),
                            String.valueOf(rs.getDouble("revenue")),
                            String.valueOf(rs.getInt("orders")),
                            currencyType
                        });
                    }
                    rs.close();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("查询排行榜失败: " + e.getMessage());
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(rows));
        });
    }

    /**
     * 异步查询热门物品排行（交易量最高），回调在主线程
     * 返回 List<String[]> 每项 [item_name, total_sold, orders, avg_unit_price, currency_type]
     */
    public void getTopItemsAsync(String currencyType, int limit, long sinceMs, Consumer<List<String[]>> callback) {
        plugin.getDatabaseManager().queryAsync(
            "SELECT item_name, SUM(amount) as total_sold, COUNT(*) as orders, AVG(price * 1.0 / amount) as avg_unit_price" +
            " FROM transaction_logs WHERE currency_type = ? AND created_at >= ? AND amount > 0" +
            " GROUP BY item_name ORDER BY total_sold DESC LIMIT ?",
            currencyType, sinceMs, limit
        ).thenAccept(rs -> {
            List<String[]> rows = new ArrayList<>();
            try {
                if (rs != null) {
                    while (rs.next()) {
                        rows.add(new String[]{
                            rs.getString("item_name"),
                            String.valueOf(rs.getLong("total_sold")),
                            String.valueOf(rs.getInt("orders")),
                            String.format("%.2f", rs.getDouble("avg_unit_price")),
                            currencyType
                        });
                    }
                    rs.close();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("查询热门物品失败: " + e.getMessage());
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(rows));
        });
    }

    /** 卖家统计数据容器 */
    public static class SellerStats {
        public final double totalRevenue;
        public final int totalOrders;
        public final List<Map.Entry<String, Integer>> topItems;

        public SellerStats(double totalRevenue, int totalOrders, List<Map.Entry<String, Integer>> topItems) {
            this.totalRevenue = totalRevenue;
            this.totalOrders = totalOrders;
            this.topItems = topItems;
        }
    }
}

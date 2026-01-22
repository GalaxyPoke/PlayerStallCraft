package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.BuyRequest;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MarketManager {

    private final PlayerStallCraft plugin;
    private final Map<Integer, BuyRequest> buyRequests;
    private final Map<Material, List<Double>> priceHistory;
    private int nextRequestId = 1;

    public MarketManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        this.buyRequests = new ConcurrentHashMap<>();
        this.priceHistory = new HashMap<>();
        loadBuyRequests();
    }

    private void loadBuyRequests() {
        plugin.getDatabaseManager().queryAsync("SELECT * FROM buy_requests WHERE status = 'active'")
            .thenAccept(rs -> {
                try {
                    if (rs != null) {
                        while (rs.next()) {
                            int id = rs.getInt("id");
                            BuyRequest request = new BuyRequest(
                                    id,
                                    UUID.fromString(rs.getString("player_uuid")),
                                    "",
                                    Material.valueOf(rs.getString("item_type")),
                                    rs.getString("item_data"),
                                    rs.getInt("amount"),
                                    rs.getDouble("price"),
                                    rs.getString("currency_type")
                            );
                            buyRequests.put(id, request);
                            if (id >= nextRequestId) {
                                nextRequestId = id + 1;
                            }
                        }
                        rs.close();
                    }
                    plugin.getLogger().info("已加载 " + buyRequests.size() + " 个求购请求");
                } catch (Exception e) {
                    plugin.getLogger().warning("加载求购请求失败: " + e.getMessage());
                }
            });
    }

    public boolean createBuyRequest(Player player, Material itemType, int amount, double price, String currencyType) {
        double totalCost = price * amount;
        
        if (!plugin.getEconomyManager().has(player, totalCost, currencyType)) {
            plugin.getMessageManager().sendRaw(player, "&c余额不足! 需要 " + 
                    plugin.getEconomyManager().formatCurrency(totalCost, currencyType));
            return false;
        }

        if (!plugin.getEconomyManager().withdraw(player, totalCost, currencyType)) {
            return false;
        }

        int id = nextRequestId++;
        BuyRequest request = new BuyRequest(id, player.getUniqueId(), player.getName(), 
                itemType, null, amount, price, currencyType);
        buyRequests.put(id, request);

        plugin.getDatabaseManager().executeAsync(
                "INSERT INTO buy_requests (id, player_uuid, item_type, amount, price, currency_type, status) VALUES (?, ?, ?, ?, ?, ?, 'active')",
                id, player.getUniqueId().toString(), itemType.name(), amount, price, currencyType
        );

        plugin.getMessageManager().sendRaw(player, "&a成功发布求购! ID: " + id);
        plugin.getMessageManager().sendRaw(player, "&7物品: &f" + itemType.name() + " x" + amount);
        plugin.getMessageManager().sendRaw(player, "&7单价: &f" + plugin.getEconomyManager().formatCurrency(price, currencyType));

        return true;
    }

    public boolean cancelBuyRequest(Player player, int requestId) {
        BuyRequest request = buyRequests.get(requestId);
        if (request == null) {
            plugin.getMessageManager().sendRaw(player, "&c求购请求不存在!");
            return false;
        }

        if (!request.getPlayerUuid().equals(player.getUniqueId()) && !player.hasPermission("stall.admin")) {
            plugin.getMessageManager().sendRaw(player, "&c这不是你的求购请求!");
            return false;
        }

        double refund = request.getPrice() * request.getAmount();
        plugin.getEconomyManager().deposit(player, refund, request.getCurrencyType());

        buyRequests.remove(requestId);
        plugin.getDatabaseManager().executeAsync(
                "UPDATE buy_requests SET status = 'cancelled' WHERE id = ?", requestId
        );

        plugin.getMessageManager().sendRaw(player, "&a已取消求购，退还 " + 
                plugin.getEconomyManager().formatCurrency(refund, request.getCurrencyType()));
        return true;
    }

    public List<BuyRequest> getBuyRequestsForItem(Material itemType) {
        List<BuyRequest> result = new ArrayList<>();
        for (BuyRequest request : buyRequests.values()) {
            if (request.getItemType() == itemType && request.isActive()) {
                result.add(request);
            }
        }
        result.sort((a, b) -> Double.compare(b.getPrice(), a.getPrice()));
        return result;
    }

    public List<BuyRequest> getPlayerBuyRequests(UUID playerUuid) {
        List<BuyRequest> result = new ArrayList<>();
        for (BuyRequest request : buyRequests.values()) {
            if (request.getPlayerUuid().equals(playerUuid) && request.isActive()) {
                result.add(request);
            }
        }
        return result;
    }

    public List<BuyRequest> getAllActiveBuyRequests() {
        return new ArrayList<>(buyRequests.values());
    }

    public void recordPrice(Material itemType, double price) {
        priceHistory.computeIfAbsent(itemType, k -> new ArrayList<>()).add(price);
        
        List<Double> prices = priceHistory.get(itemType);
        if (prices.size() > 100) {
            prices.remove(0);
        }
    }

    public double getAveragePrice(Material itemType) {
        List<Double> prices = priceHistory.get(itemType);
        if (prices == null || prices.isEmpty()) {
            return 0;
        }
        return prices.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    public double getLowestPrice(Material itemType) {
        List<Double> prices = priceHistory.get(itemType);
        if (prices == null || prices.isEmpty()) {
            return 0;
        }
        return prices.stream().mapToDouble(Double::doubleValue).min().orElse(0);
    }

    public double getHighestPrice(Material itemType) {
        List<Double> prices = priceHistory.get(itemType);
        if (prices == null || prices.isEmpty()) {
            return 0;
        }
        return prices.stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }

    public int getTransactionCount(Material itemType) {
        List<Double> prices = priceHistory.get(itemType);
        return prices == null ? 0 : prices.size();
    }
}

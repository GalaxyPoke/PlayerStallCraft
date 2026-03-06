package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.BuyRequest;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BuyRequestManager {

    private final PlayerStallCraft plugin;
    private final Map<Integer, BuyRequest> activeRequests = new ConcurrentHashMap<>();

    public BuyRequestManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        loadAllRequests();
    }

    private void loadAllRequests() {
        plugin.getDatabaseManager().queryAsync(
            "SELECT * FROM buy_requests WHERE status = 'active'"
        ).thenAccept(rs -> {
            try {
                while (rs != null && rs.next()) {
                    long createdAt;
                    try {
                        createdAt = rs.getLong("created_at");
                        if (createdAt == 0) {
                            String ts = rs.getString("created_at");
                            createdAt = ts != null ? java.sql.Timestamp.valueOf(ts).getTime() : System.currentTimeMillis();
                        }
                    } catch (Exception ignored) {
                        createdAt = System.currentTimeMillis();
                    }
                    BuyRequest request = new BuyRequest(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("player_uuid")),
                        getPlayerName(rs.getString("player_uuid")),
                        Material.valueOf(rs.getString("item_type")),
                        rs.getString("item_data"),
                        rs.getInt("amount"),
                        rs.getDouble("price"),
                        rs.getString("currency_type"),
                        createdAt
                    );
                    // 加载新字段（兼容旧数据库，捕获列不存在的异常）
                    try { request.setExpireTime(rs.getLong("expire_time")); } catch (Exception ignored) {}
                    try {
                        int rem = rs.getInt("remaining_amount");
                        request.setRemainingAmount(rem > 0 ? rem : request.getAmount());
                    } catch (Exception ignored) {}
                    try { request.setFeatured(rs.getInt("is_featured") == 1); } catch (Exception ignored) {}
                    activeRequests.put(request.getId(), request);
                }
                plugin.getLogger().info("已加载 " + activeRequests.size() + " 条求购信息");
                scheduleExpiryCheck();
            } catch (SQLException e) {
                plugin.getLogger().severe("加载求购信息失败: " + e.getMessage());
            }
        });
    }

    private void scheduleExpiryCheck() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            List<BuyRequest> toExpire = new ArrayList<>();
            for (BuyRequest r : activeRequests.values()) {
                if (r.isExpired()) toExpire.add(r);
            }
            for (BuyRequest r : toExpire) {
                activeRequests.remove(r.getId());
                plugin.getDatabaseManager().executeAsync(
                    "UPDATE buy_requests SET status = 'expired' WHERE id = ?", r.getId());
                double refund = r.getPrice() * r.getRemainingAmount();
                plugin.getEconomyManager().depositOffline(r.getPlayerUuid(), refund, r.getCurrencyType());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Player buyer = plugin.getServer().getPlayer(r.getPlayerUuid());
                    String refundStr = plugin.getEconomyManager().formatCurrency(refund, r.getCurrencyType());
                    if (buyer != null && buyer.isOnline()) {
                        plugin.getMessageManager().sendRaw(buyer, "&e你的求购 [" + r.getItemName() + " x" + r.getRemainingAmount() + "] 已到期，守付款 &f" + refundStr + " &e已退还!");
                    } else {
                        plugin.getSweetMailManager().sendNoticeMail(
                            r.getPlayerUuid(), "求购已到期",
                            "你的求购 [" + r.getItemName() + " x" + r.getRemainingAmount() + "] 已到期自动取消",
                            "守付款 " + refundStr + " 已退还至你的账户");
                    }
                });
            }
        }, 20L * 60, 20L * 60); // 每分钟检查一次
    }

    private String getPlayerName(String uuid) {
        var player = plugin.getServer().getOfflinePlayer(UUID.fromString(uuid));
        return player.getName() != null ? player.getName() : "未知玩家";
    }

    public void createRequest(Player player, ItemStack item, int amount, double price, String currencyType) {
        int expireDays = plugin.getConfigManager().getConfig().getInt("buy-request.default-expire-days", 7);
        long expireTime = expireDays > 0 ? System.currentTimeMillis() + expireDays * 86400000L : 0L;
        String sql = "INSERT INTO buy_requests (player_uuid, item_type, item_data, amount, price, currency_type, status, expire_time, remaining_amount) VALUES (?, ?, ?, ?, ?, ?, 'active', ?, ?)";
        long now = System.currentTimeMillis();
        plugin.getDatabaseManager().executeAsyncGetId(sql,
            player.getUniqueId().toString(),
            item.getType().name(),
            serializeItemData(item),
            amount,
            price,
            currencyType,
            expireTime,
            amount
        ).thenAccept(requestId -> {
            if (requestId <= 0) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.getMessageManager().sendRaw(player, "&c发布求购失败，请重试!"));
                return;
            }
            BuyRequest request = new BuyRequest(
                requestId,
                player.getUniqueId(),
                player.getName(),
                item.getType(),
                serializeItemData(item),
                amount,
                price,
                currencyType,
                now
            );
            request.setExpireTime(expireTime);
            request.setRemainingAmount(amount);
            activeRequests.put(requestId, request);
            double totalPrice = price * amount;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getEconomyManager().withdraw(player, totalPrice, currencyType);
                plugin.getEconomyManager().sendBalanceHint(player, currencyType);
                String zhName = plugin.getGlobalMarketManager().getChineseNamePublic(item.getType());
                String itemDisplayName = zhName != null ? zhName : item.getType().name();
                plugin.getMessageManager().sendRaw(player, "&a成功发布求购信息!");
                plugin.getMessageManager().sendRaw(player, String.format(
                    "&7求购: &e%s x%d &7你的出价: &f%s/个",
                    itemDisplayName, amount,
                    plugin.getEconomyManager().formatCurrency(price, currencyType)
                ));
                String expireInfo = expireDays > 0 ? "&7有效期: &f" + expireDays + " 天" : "&7有效期: &f永久";
                plugin.getMessageManager().sendRaw(player, expireInfo);
                plugin.getMessageManager().sendRaw(player, String.format(
                    "&7已预充 &f%s &7作为守付款，取消求购可全额退还",
                    plugin.getEconomyManager().formatCurrency(totalPrice, currencyType)
                ));
                // 发布后通知有匹配在售商品的求购者（自动匹配提醒反向）
                notifyMatchingBuyRequests(item.getType(), price, currencyType);
            });
        });
    }

    public void modifyPrice(int requestId, Player player, double newPrice) {
        BuyRequest request = activeRequests.get(requestId);
        if (request == null) { plugin.getMessageManager().sendRaw(player, "&c求购不存在!"); return; }
        if (!request.getPlayerUuid().equals(player.getUniqueId())) { plugin.getMessageManager().sendRaw(player, "&c不能修改他人的求购!"); return; }
        double oldTotal = request.getPrice() * request.getRemainingAmount();
        double newTotal = newPrice * request.getRemainingAmount();
        double diff = newTotal - oldTotal;
        if (diff > 0 && !plugin.getEconomyManager().has(player, diff, request.getCurrencyType())) {
            plugin.getMessageManager().sendRaw(player, "&c余额不足! 需要额外补充 " + plugin.getEconomyManager().formatCurrency(diff, request.getCurrencyType()));
            return;
        }
        if (diff > 0) plugin.getEconomyManager().withdraw(player, diff, request.getCurrencyType());
        else if (diff < 0) plugin.getEconomyManager().deposit(player, -diff, request.getCurrencyType());
        plugin.getEconomyManager().sendBalanceHint(player, request.getCurrencyType());
        request.setPrice(newPrice);
        plugin.getDatabaseManager().executeAsync("UPDATE buy_requests SET price = ? WHERE id = ?", newPrice, requestId);
        plugin.getMessageManager().sendRaw(player, "&a出价已修改为 " + plugin.getEconomyManager().formatCurrency(newPrice, request.getCurrencyType()) + "/个");
        if (diff > 0) plugin.getMessageManager().sendRaw(player, "&7已额外扣除 &f" + plugin.getEconomyManager().formatCurrency(diff, request.getCurrencyType()));
        else if (diff < 0) plugin.getMessageManager().sendRaw(player, "&7已退还差额 &f" + plugin.getEconomyManager().formatCurrency(-diff, request.getCurrencyType()));
    }

    public void featureRequest(int requestId, Player player) {
        BuyRequest request = activeRequests.get(requestId);
        if (request == null) { plugin.getMessageManager().sendRaw(player, "&c求购不存在!"); return; }
        if (!request.getPlayerUuid().equals(player.getUniqueId())) { plugin.getMessageManager().sendRaw(player, "&c不能置顶他人的求购!"); return; }
        double cost = plugin.getConfigManager().getConfig().getDouble("buy-request.featured-cost", 500);
        String currency = plugin.getConfigManager().getConfig().getString("buy-request.featured-currency", "vault");
        int hours = plugin.getConfigManager().getConfig().getInt("buy-request.featured-duration-hours", 24);
        if (!plugin.getEconomyManager().has(player, cost, currency)) {
            plugin.getMessageManager().sendRaw(player, "&c余额不足! 置顶需要 " + plugin.getEconomyManager().formatCurrency(cost, currency));
            return;
        }
        plugin.getEconomyManager().withdraw(player, cost, currency);
        long featuredUntil = System.currentTimeMillis() + hours * 3600000L;
        request.setFeatured(true);
        plugin.getDatabaseManager().executeAsync(
            "UPDATE buy_requests SET is_featured = 1, featured_until = ? WHERE id = ?", featuredUntil, requestId);
        plugin.getMessageManager().sendRaw(player, "&a求购已置顶推广 &f" + hours + " 小时!");
        plugin.getMessageManager().sendRaw(player, "&7花费: &c" + plugin.getEconomyManager().formatCurrency(cost, currency));
    }

    public void notifyBuyRequestMatches(Material material, double sellerPrice, String currencyType) {
        for (BuyRequest r : activeRequests.values()) {
            if (r.getItemType() != material) continue;
            if (!r.getCurrencyType().equals(currencyType)) continue;
            if (r.getPrice() < sellerPrice) continue;
            Player buyer = plugin.getServer().getPlayer(r.getPlayerUuid());
            if (buyer != null && buyer.isOnline()) {
                String zhName = plugin.getGlobalMarketManager().getChineseNamePublic(material);
                String name = zhName != null ? zhName : material.name();
                plugin.getMessageManager().sendRaw(buyer,
                    "&6[求购提醒] &e有玩家正在出售你求购的 &f" + name +
                    " &e单价 &f" + plugin.getEconomyManager().formatCurrency(sellerPrice, currencyType) +
                    " &e(你的出价: &f" + plugin.getEconomyManager().formatCurrency(r.getPrice(), currencyType) + "&e)");
                plugin.getMessageManager().sendRaw(buyer, "&e使用 /baitan buy 打开求购市场快速接单!");
            }
        }
    }

    private void notifyMatchingBuyRequests(Material material, double buyerPrice, String currencyType) {
        // 当玩家发布新求购时，检查是否有在售商品匹配，帮助其发现
        long matchCount = plugin.getGlobalMarketManager().searchByMaterial(material).stream()
            .filter(i -> i.getCurrencyType().equals(currencyType) &&
                         (i.getAmount() > 0 ? i.getPrice() / i.getAmount() : i.getPrice()) <= buyerPrice)
            .count();
        if (matchCount > 0) {
            // 通知由调用方在主线程中处理，这里仅记录
            plugin.getLogger().info("新求购匹配到 " + matchCount + " 个在售商品");
        }
    }

    public void cancelRequest(int requestId, Player player) {
        BuyRequest request = activeRequests.get(requestId);
        if (request == null) {
            plugin.getMessageManager().sendRaw(player, "&c求购信息不存在!");
            return;
        }
        
        if (!request.getPlayerUuid().equals(player.getUniqueId()) && !player.hasPermission("stall.admin")) {
            plugin.getMessageManager().sendRaw(player, "&c你没有权限取消这条求购!");
            return;
        }
        
        plugin.getDatabaseManager().executeAsync(
            "UPDATE buy_requests SET status = 'cancelled' WHERE id = ?",
            requestId
        ).thenRun(() -> {
            activeRequests.remove(requestId);
            double refundAmount = request.getPrice() * request.getAmount();
            plugin.getEconomyManager().depositOffline(
                request.getPlayerUuid(), refundAmount, request.getCurrencyType());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getMessageManager().sendRaw(player, "&a成功取消求购!");
                String refundStr = plugin.getEconomyManager().formatCurrency(refundAmount, request.getCurrencyType());
                Player buyer = plugin.getServer().getPlayer(request.getPlayerUuid());
                if (buyer != null && buyer.isOnline()) {
                    plugin.getMessageManager().sendRaw(buyer, "&a守付款 &f" + refundStr + " &a已退还到你的账户!");
                    plugin.getEconomyManager().sendBalanceHint(buyer, request.getCurrencyType());
                } else if (!request.getPlayerUuid().equals(player.getUniqueId())) {
                    plugin.getMessageManager().sendRaw(player, "&7守付款 &f" + refundStr + " &7已退还至求购者账户");
                }
            });
        });
    }

    public void fulfillRequest(int requestId, Player seller) {
        fulfillPartial(requestId, seller, -1);
    }

    public void fulfillPartial(int requestId, Player seller, int provideAmount) {
        BuyRequest request = activeRequests.get(requestId);
        if (request == null) {
            plugin.getMessageManager().sendRaw(seller, "&c求购信息不存在或已被接单!");
            return;
        }
        if (request.getPlayerUuid().equals(seller.getUniqueId())) {
            plugin.getMessageManager().sendRaw(seller, "&c你不能接自己的求购单!");
            return;
        }
        int needed = request.getRemainingAmount();
        int available = countItems(seller, request.getItemType());
        int actualProvide = provideAmount < 0 ? needed : Math.min(provideAmount, needed);
        actualProvide = Math.min(actualProvide, available);
        if (actualProvide <= 0) {
            plugin.getMessageManager().sendRaw(seller, String.format(
                "&c物品数量不足! 还需 %d 个，你只有 %d 个", needed, available));
            return;
        }
        boolean mailAvailable = plugin.getSweetMailManager().isEnabled();
        // 无邮件插件时要求买家在线且背包有空间
        Player buyer = plugin.getServer().getPlayer(request.getPlayerUuid());
        if (!mailAvailable) {
            if (buyer == null || !buyer.isOnline()) {
                plugin.getMessageManager().sendRaw(seller, "&c求购玩家不在线，且服务器未安装邮件插件，无法完成交易!");
                return;
            }
            int space = 0;
            for (ItemStack inv : buyer.getInventory().getContents()) {
                if (inv == null || inv.getType() == Material.AIR) space += request.getItemType().getMaxStackSize();
                else if (inv.getType() == request.getItemType()) space += inv.getMaxStackSize() - inv.getAmount();
                if (space >= actualProvide) break;
            }
            if (space < actualProvide) {
                plugin.getMessageManager().sendRaw(seller, "&c求购玩家背包已满，无法接单!");
                plugin.getMessageManager().sendRaw(buyer, "&c你的背包已满，有人想接你的求购单但失败了，请清理背包!");
                return;
            }
        }
        double unitPrice = request.getPrice();
        double totalPrice = unitPrice * actualProvide;
        double taxRate = plugin.getConfigManager().getConfig().getDouble("stall.tax-rate", 0.05);
        double tax = totalPrice * taxRate;
        double sellerReceive = totalPrice - tax;
        String zhItemName = plugin.getGlobalMarketManager().getChineseNamePublic(request.getItemType());
        String itemDisplayName = zhItemName != null ? zhItemName : request.getItemType().name();
        // 执行交易：扣除卖家物品、发放收益
        plugin.getEconomyManager().deposit(seller, sellerReceive, request.getCurrencyType());
        plugin.getEconomyManager().sendBalanceHint(seller, request.getCurrencyType());
        removeItems(seller, request.getItemType(), actualProvide);
        // 发放物品给买家：优先邮件，无邮件插件则直接给背包
        ItemStack reward = new ItemStack(request.getItemType(), actualProvide);
        if (mailAvailable) {
            plugin.getSweetMailManager().sendItemMail(
                request.getPlayerUuid(), reward,
                "求购成交 - " + itemDisplayName,
                "你的求购单已有人接单！",
                "物品: " + itemDisplayName + " x" + actualProvide,
                "花费: " + plugin.getEconomyManager().formatCurrency(totalPrice, request.getCurrencyType()));
        } else {
            buyer.getInventory().addItem(reward);
        }
        int newRemaining = needed - actualProvide;
        if (newRemaining <= 0) {
            activeRequests.remove(requestId);
            plugin.getDatabaseManager().executeAsync(
                "UPDATE buy_requests SET status = 'completed', remaining_amount = 0 WHERE id = ?", requestId);
        } else {
            request.setRemainingAmount(newRemaining);
            plugin.getDatabaseManager().executeAsync(
                "UPDATE buy_requests SET remaining_amount = ? WHERE id = ?", newRemaining, requestId);
        }
        // 记录交易
        plugin.getDatabaseManager().executeAsync(
            "INSERT INTO transaction_logs (seller_uuid, buyer_uuid, item_data, amount, price, currency_type, tax_amount) VALUES (?, ?, ?, ?, ?, ?, ?)",
            seller.getUniqueId().toString(), request.getPlayerUuid().toString(),
            request.getItemType().name(), actualProvide, totalPrice, request.getCurrencyType(), tax);
        String priceStr = plugin.getEconomyManager().formatCurrency(totalPrice, request.getCurrencyType());
        String receiveStr = plugin.getEconomyManager().formatCurrency(sellerReceive, request.getCurrencyType());
        String deliveryHint = mailAvailable ? "&7(物品已通过邮件发送给买家)" : "&7(物品已直接发放到买家背包)";
        plugin.getMessageManager().sendRaw(seller, "&a交易成功! " + deliveryHint);
        plugin.getMessageManager().sendRaw(seller, String.format(
            "&7出售 &e%s x%d &7获得 &f%s &7(税后)", itemDisplayName, actualProvide, receiveStr));
        if (newRemaining > 0) {
            plugin.getMessageManager().sendRaw(seller, "&7该求购剩余 &f" + newRemaining + " 个&7未完成");
        }
        // 通知买家（在线则直接发消息）
        String remaining = newRemaining > 0 ? "，还需 &f" + newRemaining + " &7个" : "，求购全部完成！";
        String buyerMsg = String.format("&7收到 &e%s x%d &7花费 &f%s%s", itemDisplayName, actualProvide, priceStr, remaining);
        String deliveryNotice = mailAvailable ? "&a物品已发送至你的邮箱，请查收！" : "&a物品已发放到你的背包！";
        if (buyer != null && buyer.isOnline()) {
            plugin.getMessageManager().sendRaw(buyer, "&a你的求购被接单了!");
            plugin.getMessageManager().sendRaw(buyer, buyerMsg);
            plugin.getMessageManager().sendRaw(buyer, deliveryNotice);
        } else if (mailAvailable) {
            // 买家不在线，发送通知邮件（物品邮件已在上方发送）
            if (newRemaining <= 0) {
                plugin.getSweetMailManager().sendNoticeMail(
                    request.getPlayerUuid(), "求购已全部完成",
                    "你的求购 [" + itemDisplayName + " x" + request.getAmount() + "] 已全部完成!",
                    "物品 x" + actualProvide + " 已发送至邮箱。",
                    "花费总额: " + plugin.getEconomyManager().formatCurrency(request.getPrice() * request.getAmount(), request.getCurrencyType()));
            }
        }
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
    }

    private String serializeItemData(ItemStack item) {
        // 简单序列化，只保存类型
        return item.getType().name();
    }

    public Collection<BuyRequest> getActiveRequests() {
        return activeRequests.values();
    }

    public List<BuyRequest> getRequestsByPlayer(UUID playerUuid) {
        return activeRequests.values().stream()
            .filter(r -> r.getPlayerUuid().equals(playerUuid))
            .collect(Collectors.toList());
    }

    public List<BuyRequest> getSortedRequests(String sortBy, String currencyFilter, String searchKeyword) {
        List<BuyRequest> list = new ArrayList<>(activeRequests.values());
        if (currencyFilter != null && !currencyFilter.isEmpty()) {
            list.removeIf(r -> !r.getCurrencyType().equals(currencyFilter));
        }
        if (searchKeyword != null && !searchKeyword.isEmpty()) {
            String kw = searchKeyword.toLowerCase();
            list.removeIf(r -> {
                String name = plugin.getGlobalMarketManager().getChineseNamePublic(r.getItemType());
                return !r.getItemType().name().toLowerCase().contains(kw) &&
                       (name == null || !name.contains(kw));
            });
        }
        switch (sortBy == null ? "" : sortBy) {
            case "price_asc"  -> list.sort(Comparator.comparingDouble(BuyRequest::getPrice));
            case "price_desc" -> list.sort(Comparator.comparingDouble(BuyRequest::getPrice).reversed());
            case "time_asc"   -> list.sort(Comparator.comparingLong(BuyRequest::getCreatedAt));
            case "time_desc"  -> list.sort(Comparator.comparingLong(BuyRequest::getCreatedAt).reversed());
            default           -> list.sort(Comparator.comparingLong(BuyRequest::getCreatedAt).reversed());
        }
        // 置顶的排在最前面
        list.sort(Comparator.comparingInt(r -> (r.isFeatured() ? 0 : 1)));
        return list;
    }

    public void queryHistory(UUID playerUuid, java.util.function.Consumer<List<BuyRequest>> callback) {
        plugin.getDatabaseManager().queryAsync(
            "SELECT * FROM buy_requests WHERE player_uuid = ? AND status IN ('completed','cancelled','expired') ORDER BY created_at DESC LIMIT 50",
            playerUuid.toString()
        ).thenAccept(rs -> {
            List<BuyRequest> history = new ArrayList<>();
            try {
                while (rs != null && rs.next()) {
                    long createdAt;
                    try { createdAt = rs.getLong("created_at"); if (createdAt == 0) { String ts = rs.getString("created_at"); createdAt = ts != null ? java.sql.Timestamp.valueOf(ts).getTime() : 0; } } catch (Exception ignored) { createdAt = 0; }
                    BuyRequest r = new BuyRequest(
                        rs.getInt("id"),
                        playerUuid,
                        getPlayerName(playerUuid.toString()),
                        Material.valueOf(rs.getString("item_type")),
                        rs.getString("item_data"),
                        rs.getInt("amount"),
                        rs.getDouble("price"),
                        rs.getString("currency_type"),
                        createdAt
                    );
                    r.setStatus(rs.getString("status"));
                    history.add(r);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("查询求购历史失败: " + e.getMessage());
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(history));
        });
    }

    public BuyRequest getRequest(int id) {
        return activeRequests.get(id);
    }

    public int getPlayerRequestCount(UUID playerUuid) {
        return (int) activeRequests.values().stream()
            .filter(r -> r.getPlayerUuid().equals(playerUuid))
            .count();
    }

    public int getMaxRequestsPerPlayer() {
        return plugin.getConfigManager().getConfig().getInt("buy-request.max-per-player", 10);
    }

    public boolean requiresLicense() {
        return plugin.getConfigManager().getConfig().getBoolean("buy-request.require-license", false);
    }

    public void reload() {
        activeRequests.clear();
        loadAllRequests();
    }
}

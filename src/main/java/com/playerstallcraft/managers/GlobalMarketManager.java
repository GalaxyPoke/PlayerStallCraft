package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class GlobalMarketManager {

    private final PlayerStallCraft plugin;
    private final Map<Integer, GlobalMarketItem> listings;
    private final AtomicInteger nextListingId = new AtomicInteger(1);

    public GlobalMarketManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        this.listings = new ConcurrentHashMap<>();
        loadListings();
        startExpiryTask();
    }

    private void startExpiryTask() {
        // 每5分钟检查一次过期商品，退还给卖家
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            List<GlobalMarketItem> expired = new ArrayList<>();
            for (GlobalMarketItem item : listings.values()) {
                if (item.isReallyExpired(now)) {
                    expired.add(item);
                }
            }
            if (expired.isEmpty()) return;

            for (GlobalMarketItem listing : expired) {
                listings.remove(listing.getId());
                plugin.getDatabaseManager().executeAsync(
                        "UPDATE global_market SET status = 'expired' WHERE id = ?", listing.getId()
                );
                ItemStack item = deserializeItem(listing.getItemData());
                if (item == null) continue;
                item.setAmount(listing.getAmount());
                final ItemStack finalItem = item;
                // 退还给卖家（可能离线），通过SweetMail
                boolean mailSent = plugin.getSweetMailManager().sendItemMail(
                        listing.getSellerUuid(),
                        finalItem,
                        "全服市场 - 商品已过期退还",
                        "你的商品 [" + listing.getItemName() + "] x" + listing.getAmount() + " 上架已到期，物品已退还。"
                );
                if (!mailSent) {
                    // SweetMail 未安装，尝试直接给在线玩家
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(listing.getSellerUuid());
                        if (online != null && online.isOnline()) {
                            online.getInventory().addItem(finalItem);
                            plugin.getMessageManager().sendRaw(online,
                                    "&e你在全服市场上架的 &f" + listing.getItemName() +
                                    " &ex" + listing.getAmount() + " &e已过期，物品已退还到背包!");
                        }
                    });
                }
            }
            plugin.getLogger().info("已清理 " + expired.size() + " 个过期全服市场商品");
        }, 20L * 60 * 5, 20L * 60 * 5); // 每5分钟
    }

    private void loadListings() {
        plugin.getDatabaseManager().queryAsync("SELECT * FROM global_market WHERE status = 'active'")
            .thenAccept(rs -> {
                try {
                    if (rs != null) {
                        while (rs.next()) {
                            int id = rs.getInt("id");
                            GlobalMarketItem item = new GlobalMarketItem(
                                    id,
                                    UUID.fromString(rs.getString("seller_uuid")),
                                    rs.getString("seller_name"),
                                    rs.getString("item_data"),
                                    rs.getInt("amount"),
                                    rs.getDouble("price"),
                                    rs.getString("currency_type"),
                                    rs.getLong("expire_time")
                            );
                            listings.put(id, item);
                            if (id >= nextListingId.get()) {
                                nextListingId.set(id + 1);
                            }
                        }
                        rs.close();
                    }
                    plugin.getLogger().info("已加载 " + listings.size() + " 个全服市场商品");
                } catch (Exception e) {
                    plugin.getLogger().warning("加载全服市场失败: " + e.getMessage());
                }
            });
    }

    public boolean listItem(Player seller, ItemStack item, double price, String currencyType, int durationHours) {
        if (item == null || item.getType().isAir()) {
            plugin.getMessageManager().sendRaw(seller, "&c请手持要上架的物品!");
            return false;
        }

        double listingFee = plugin.getConfigManager().getConfig().getDouble("global-market.listing-fee", 100);
        // 使用选择的货币类型扣除上架费用
        if (!plugin.getEconomyManager().has(seller, listingFee, currencyType)) {
            plugin.getMessageManager().sendRaw(seller, "&c上架费用不足! 需要 " + 
                    plugin.getEconomyManager().formatCurrency(listingFee, currencyType));
            return false;
        }

        plugin.getEconomyManager().withdraw(seller, listingFee, currencyType);

        int id = nextListingId.getAndIncrement();
        long expireTime = System.currentTimeMillis() + (durationHours * 60L * 60 * 1000);
        String itemData = serializeItem(item);
        
        // 获取物品名称用于搜索
        String itemName = getItemDisplayName(item);
        String itemType = item.getType().name();

        GlobalMarketItem listing = new GlobalMarketItem(
                id, seller.getUniqueId(), seller.getName(),
                itemData, item.getAmount(), price, currencyType, expireTime
        );
        listing.setItemName(itemName);
        listing.setItemType(itemType);
        listings.put(id, listing);

        plugin.getDatabaseManager().executeAsync(
                "INSERT INTO global_market (id, seller_uuid, seller_name, item_data, item_name, item_type, amount, price, currency_type, expire_time, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'active')",
                id, seller.getUniqueId().toString(), seller.getName(),
                itemData, itemName, itemType, item.getAmount(), price, currencyType, expireTime
        );

        seller.getInventory().setItemInMainHand(null);
        plugin.getMessageManager().sendRaw(seller, "&a成功上架到全服市场! ID: " + id);

        // 自动匹配提醒：通知有对应求购单的玩家
        double unitPrice = item.getAmount() > 0 ? price / item.getAmount() : price;
        plugin.getBuyRequestManager().notifyBuyRequestMatches(item.getType(), unitPrice, currencyType);

        return true;
    }

    public boolean purchaseItem(Player buyer, int listingId) {
        GlobalMarketItem listing = listings.get(listingId);
        if (listing == null || !listing.isActive()) {
            plugin.getMessageManager().sendRaw(buyer, "&c该商品不存在或已下架!");
            return false;
        }

        // 立即标记为不可购买，防止两个玩家同时购买同一件商品（双花）
        listing.setActive(false);

        if (listing.getSellerUuid().equals(buyer.getUniqueId())) {
            listing.setActive(true); // 回滚
            plugin.getMessageManager().sendRaw(buyer, "&c不能购买自己的商品!");
            return false;
        }

        if (!plugin.getEconomyManager().has(buyer, listing.getPrice(), listing.getCurrencyType())) {
            listing.setActive(true); // 回滚
            plugin.getMessageManager().sendRaw(buyer, "&c余额不足!");
            return false;
        }

        if (!plugin.getEconomyManager().withdraw(buyer, listing.getPrice(), listing.getCurrencyType())) {
            listing.setActive(true); // 回滚
            plugin.getMessageManager().sendRaw(buyer, "&c扣款失败! 请联系管理员");
            return false;
        }
        plugin.getEconomyManager().sendBalanceHint(buyer, listing.getCurrencyType());

        double tax = plugin.getEconomyManager().calculateTax(listing.getPrice(), "global-market");
        double sellerReceive = listing.getPrice() - tax;
        plugin.getEconomyManager().depositOffline(listing.getSellerUuid(), sellerReceive, listing.getCurrencyType());

        ItemStack item = deserializeItem(listing.getItemData());
        String itemName = "未知物品";
        if (item != null) {
            item.setAmount(listing.getAmount());
            itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName()
                    : item.getType().name();

            java.util.Map<Integer, ItemStack> overflow = buyer.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                // 背包已满，通过 SweetMail 投递剩余物品
                boolean mailSent = false;
                for (ItemStack overflowItem : overflow.values()) {
                    if (plugin.getSweetMailManager().sendItemMail(
                            buyer.getUniqueId(),
                            overflowItem,
                            "全服市场 - 购买成功",
                            "你从全服市场购买的物品 [" + itemName + "] 因背包已满已通过邮件送达，请查收附件。",
                            "卖家: " + listing.getSellerName(),
                            "成交价: " + plugin.getEconomyManager().formatCurrency(listing.getPrice(), listing.getCurrencyType())
                    )) {
                        mailSent = true;
                    } else {
                        // SweetMail 未安装，直接掉落
                        buyer.getWorld().dropItemNaturally(buyer.getLocation(), overflowItem);
                    }
                }
                if (mailSent) {
                    plugin.getMessageManager().sendRaw(buyer, "&e背包已满，物品已通过邮件送达，请查收!");
                }
            }
        }

        // 通知卖家成交（离线时通过邮件）
        org.bukkit.entity.Player sellerOnline = org.bukkit.Bukkit.getPlayer(listing.getSellerUuid());
        String priceStr = plugin.getEconomyManager().formatCurrency(sellerReceive, listing.getCurrencyType());
        if (sellerOnline != null && sellerOnline.isOnline()) {
            plugin.getMessageManager().sendRaw(sellerOnline,
                    "&a你在全服市场上架的 &e" + itemName + " &ax" + listing.getAmount() +
                    " &a已售出! 到手: &e" + priceStr);
            plugin.getEconomyManager().sendBalanceHint(sellerOnline, listing.getCurrencyType());
        } else {
            plugin.getSweetMailManager().sendNoticeMail(
                    listing.getSellerUuid(),
                    "全服市场 - 商品已售出",
                    "你的商品 [" + itemName + "] x" + listing.getAmount() + " 已售出!",
                    "到手金额: " + priceStr,
                    "买家: " + buyer.getName()
            );
        }

        // 记录交易日志
        plugin.getTransactionLogManager().logMarketPurchase(
                buyer.getUniqueId(), buyer.getName(),
                listing.getSellerUuid(), listing.getSellerName(),
                itemName, listing.getAmount(), listing.getPrice(), listing.getCurrencyType()
        );

        listings.remove(listingId);
        plugin.getDatabaseManager().executeAsync(
                "UPDATE global_market SET status = 'sold' WHERE id = ?", listingId
        );

        plugin.getMessageManager().sendRaw(buyer, "&a购买成功!");
        return true;
    }

    public boolean cancelListing(Player seller, int listingId) {
        GlobalMarketItem listing = listings.get(listingId);
        if (listing == null) {
            plugin.getMessageManager().sendRaw(seller, "&c该商品不存在!");
            return false;
        }

        if (!listing.getSellerUuid().equals(seller.getUniqueId()) && !seller.hasPermission("stall.admin")) {
            plugin.getMessageManager().sendRaw(seller, "&c这不是你的商品!");
            return false;
        }

        ItemStack item = deserializeItem(listing.getItemData());
        if (item != null) {
            item.setAmount(listing.getAmount());
            java.util.Map<Integer, ItemStack> overflow = seller.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                for (ItemStack overflowItem : overflow.values()) {
                    if (!plugin.getSweetMailManager().sendItemMail(
                            seller.getUniqueId(),
                            overflowItem,
                            "全服市场 - 下架退还",
                            "你从全服市场下架的物品因背包已满已通过邮件送达，请查收附件。"
                    )) {
                        seller.getWorld().dropItemNaturally(seller.getLocation(), overflowItem);
                    }
                }
                plugin.getMessageManager().sendRaw(seller, "&e背包已满，下架物品已通过邮件退还，请查收!");
            }
        }

        listings.remove(listingId);
        plugin.getDatabaseManager().executeAsync(
                "UPDATE global_market SET status = 'cancelled' WHERE id = ?", listingId
        );

        plugin.getMessageManager().sendRaw(seller, "&a已下架商品!");
        return true;
    }

    public List<GlobalMarketItem> searchByMaterial(Material material) {
        List<GlobalMarketItem> result = new ArrayList<>();
        for (GlobalMarketItem item : listings.values()) {
            if (item.isActive()) {
                ItemStack stack = deserializeItem(item.getItemData());
                if (stack != null && stack.getType() == material) {
                    result.add(item);
                }
            }
        }
        result.sort(Comparator.comparingDouble(GlobalMarketItem::getPrice));
        return result;
    }

    public List<GlobalMarketItem> getSellerListings(UUID sellerUuid) {
        List<GlobalMarketItem> result = new ArrayList<>();
        for (GlobalMarketItem item : listings.values()) {
            if (item.getSellerUuid().equals(sellerUuid) && item.isActive()) {
                result.add(item);
            }
        }
        return result;
    }

    public void getSellerSoldHistoryAsync(UUID sellerUuid, int limit, java.util.function.Consumer<List<GlobalMarketItem>> callback) {
        plugin.getDatabaseManager().queryAsync(
            "SELECT * FROM global_market WHERE seller_uuid = ? AND status = 'sold' ORDER BY id DESC LIMIT ?",
            sellerUuid.toString(), limit
        ).thenAccept(rs -> {
            List<GlobalMarketItem> result = new ArrayList<>();
            try {
                if (rs != null) {
                    while (rs.next()) {
                        GlobalMarketItem item = new GlobalMarketItem(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("seller_uuid")),
                            rs.getString("seller_name"),
                            rs.getString("item_data"),
                            rs.getInt("amount"),
                            rs.getDouble("price"),
                            rs.getString("currency_type"),
                            rs.getLong("expire_time")
                        );
                        result.add(item);
                    }
                    rs.close();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("查询售出记录失败: " + e.getMessage());
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }

    public List<GlobalMarketItem> getAllActiveListings() {
        List<GlobalMarketItem> result = new ArrayList<>();
        for (GlobalMarketItem item : listings.values()) {
            if (item.isActive()) {
                result.add(item);
            }
        }
        return result;
    }

    private String serializeItem(ItemStack item) {
        try {
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            org.bukkit.util.io.BukkitObjectOutputStream dataOutput = new org.bukkit.util.io.BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return java.util.Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private ItemStack deserializeItem(String data) {
        return deserializeItemPublic(data);
    }

    public ItemStack deserializeItemPublic(String data) {
        try {
            java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(data));
            org.bukkit.util.io.BukkitObjectInputStream dataInput = new org.bukkit.util.io.BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) {
            return null;
        }
    }
    
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName().replaceAll("§[0-9a-fklmnor]", "");
        }
        String chineseName = getChineseName(item.getType());
        return chineseName != null ? chineseName : item.getType().name().toLowerCase().replace("_", " ");
    }
    
    private Map<String, String> itemNamesZh = new HashMap<>();
    
    public void loadItemNames() {
        itemNamesZh.clear();
        int count = 0;
        
        // 加载原版物品中文名
        try {
            java.io.InputStream is = plugin.getResource("item_names_zh.yml");
            if (is != null) {
                org.bukkit.configuration.file.YamlConfiguration config = 
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                for (String key : config.getKeys(false)) {
                    itemNamesZh.put(key, config.getString(key));
                    count++;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("加载原版物品中文名失败: " + e.getMessage());
        }
        
        // 加载Cobblemon物品中文名
        try {
            java.io.InputStream is = plugin.getResource("cobblemon_items_zh.yml");
            if (is != null) {
                org.bukkit.configuration.file.YamlConfiguration config = 
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                for (String key : config.getKeys(false)) {
                    itemNamesZh.put(key, config.getString(key));
                    count++;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("加载Cobblemon物品中文名失败: " + e.getMessage());
        }
        
        plugin.getLogger().info("已加载 " + count + " 个物品中文名");
    }
    
    private String getChineseName(Material material) {
        // 1. 先用 NamespacedKey 直接查（如 cobblemon:power_belt → 力量腰带）
        try {
            String nsKey = material.getKey().toString();
            String r = itemNamesZh.get(nsKey);
            if (r != null) return r;
        } catch (Exception ignored) {}
        // 2. 用大写 Material 名查（如 STONE → 石头）
        String name = material.name();
        String result = itemNamesZh.get(name);
        if (result != null) return result;
        // 3. COBBLEMON_XXX → cobblemon:xxx 兜底
        if (name.startsWith("COBBLEMON_")) {
            String nsKey = "cobblemon:" + name.substring("COBBLEMON_".length()).toLowerCase();
            result = itemNamesZh.get(nsKey);
        }
        return result;
    }
    
    public String getChineseNamePublic(Material material) {
        return getChineseName(material);
    }

    public static class GlobalMarketItem {
        private final int id;
        private final UUID sellerUuid;
        private final String sellerName;
        private final String itemData;
        private final int amount;
        private final double price;
        private final String currencyType;
        private final long expireTime;
        private String itemName = "";
        private String itemType = "";
        private volatile boolean active = true;

        public GlobalMarketItem(int id, UUID sellerUuid, String sellerName, String itemData,
                                int amount, double price, String currencyType, long expireTime) {
            this.id = id;
            this.sellerUuid = sellerUuid;
            this.sellerName = sellerName;
            this.itemData = itemData;
            this.amount = amount;
            this.price = price;
            this.currencyType = currencyType;
            this.expireTime = expireTime;
        }

        public int getId() { return id; }
        public UUID getSellerUuid() { return sellerUuid; }
        public String getSellerName() { return sellerName; }
        public String getItemData() { return itemData; }
        public int getAmount() { return amount; }
        public double getPrice() { return price; }
        public String getCurrencyType() { return currencyType; }
        public long getExpireTime() { return expireTime; }
        public boolean isActive() { return active && System.currentTimeMillis() < expireTime; }
        public boolean isReallyExpired(long now) { return active && now >= expireTime; }
        public void setActive(boolean active) { this.active = active; }
        
        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public String getItemType() { return itemType; }
        public void setItemType(String itemType) { this.itemType = itemType; }
    }
}

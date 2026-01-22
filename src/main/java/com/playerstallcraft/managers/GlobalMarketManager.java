package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalMarketManager {

    private final PlayerStallCraft plugin;
    private final Map<Integer, GlobalMarketItem> listings;
    private int nextListingId = 1;

    public GlobalMarketManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        this.listings = new ConcurrentHashMap<>();
        loadListings();
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
                            if (id >= nextListingId) {
                                nextListingId = id + 1;
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
        if (!plugin.getEconomyManager().has(seller, listingFee, "vault")) {
            plugin.getMessageManager().sendRaw(seller, "&c上架费用不足! 需要 " + 
                    plugin.getEconomyManager().formatCurrency(listingFee, "vault"));
            return false;
        }

        plugin.getEconomyManager().withdraw(seller, listingFee, "vault");

        int id = nextListingId++;
        long expireTime = System.currentTimeMillis() + (durationHours * 60L * 60 * 1000);
        String itemData = serializeItem(item);

        GlobalMarketItem listing = new GlobalMarketItem(
                id, seller.getUniqueId(), seller.getName(),
                itemData, item.getAmount(), price, currencyType, expireTime
        );
        listings.put(id, listing);

        plugin.getDatabaseManager().executeAsync(
                "INSERT INTO global_market (id, seller_uuid, seller_name, item_data, amount, price, currency_type, expire_time, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'active')",
                id, seller.getUniqueId().toString(), seller.getName(),
                itemData, item.getAmount(), price, currencyType, expireTime
        );

        seller.getInventory().setItemInMainHand(null);
        plugin.getMessageManager().sendRaw(seller, "&a成功上架到全服市场! ID: " + id);

        return true;
    }

    public boolean purchaseItem(Player buyer, int listingId) {
        GlobalMarketItem listing = listings.get(listingId);
        if (listing == null || !listing.isActive()) {
            plugin.getMessageManager().sendRaw(buyer, "&c该商品不存在或已下架!");
            return false;
        }

        if (listing.getSellerUuid().equals(buyer.getUniqueId())) {
            plugin.getMessageManager().sendRaw(buyer, "&c不能购买自己的商品!");
            return false;
        }

        if (!plugin.getEconomyManager().has(buyer, listing.getPrice(), listing.getCurrencyType())) {
            plugin.getMessageManager().sendRaw(buyer, "&c余额不足!");
            return false;
        }

        plugin.getEconomyManager().withdraw(buyer, listing.getPrice(), listing.getCurrencyType());

        double tax = plugin.getEconomyManager().calculateTax(listing.getPrice(), "global-market");
        double sellerReceive = listing.getPrice() - tax;
        plugin.getEconomyManager().depositOffline(listing.getSellerUuid(), sellerReceive, listing.getCurrencyType());

        ItemStack item = deserializeItem(listing.getItemData());
        if (item != null) {
            item.setAmount(listing.getAmount());
            buyer.getInventory().addItem(item);
        }

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
            seller.getInventory().addItem(item);
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

    public static class GlobalMarketItem {
        private final int id;
        private final UUID sellerUuid;
        private final String sellerName;
        private final String itemData;
        private final int amount;
        private final double price;
        private final String currencyType;
        private final long expireTime;

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
        public boolean isActive() { return System.currentTimeMillis() < expireTime; }
    }
}

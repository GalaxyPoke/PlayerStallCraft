package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.Shop;
import com.playerstallcraft.models.ShopShelf;
import com.playerstallcraft.models.StallItem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ShopManager {

    private final PlayerStallCraft plugin;
    private final Map<Integer, Shop> shops = new ConcurrentHashMap<>();
    private final Map<Integer, Map<Integer, StallItem>> shopItems = new ConcurrentHashMap<>();
    private final AtomicInteger nextShopId = new AtomicInteger(1);
    
    // 已发送过到期预警的商铺ID（避免重复提醒，重启后重置）
    private final Set<Integer> warnedShopIds = ConcurrentHashMap.newKeySet();

    // 待移动/重命名的货架
    private final Map<UUID, ShopShelf> pendingShelfMoves = new ConcurrentHashMap<>();
    private final Map<UUID, ShopShelf> pendingShelfRenames = new ConcurrentHashMap<>();
    private final Map<UUID, Object> pendingPriceInputs = new ConcurrentHashMap<>();
    // 待修改价格的货架槽位
    private final Map<UUID, int[]> pendingPriceEditSlots = new ConcurrentHashMap<>(); // [shelfId, slot]
    private final Map<UUID, ShopShelf> pendingPriceEditShelves = new ConcurrentHashMap<>();

    public ShopManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        loadShops();
    }

    private void loadShops() {
        plugin.getDatabaseManager().queryAsync("SELECT * FROM shops")
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
                            int shelfCount = rs.getInt("shelf_count");
                            
                            Location loc = new Location(Bukkit.getWorld(world), x, y, z);
                            Shop shop = new Shop(id, name, loc, shelfCount);
                            
                            String ownerUuid = rs.getString("owner_uuid");
                            if (ownerUuid != null && !ownerUuid.isEmpty()) {
                                shop.setOwnerUuid(UUID.fromString(ownerUuid));
                                shop.setOwnerName(rs.getString("owner_name"));
                                shop.setRented(rs.getBoolean("is_rented"));
                                shop.setOwned(rs.getBoolean("is_owned"));
                                shop.setRentExpireTime(rs.getLong("rent_expire_time"));
                                shop.setShelfDurability(rs.getInt("shelf_durability"));
                            }
                            
                            shops.put(id, shop);
                            if (id >= nextShopId.get()) {
                                nextShopId.set(id + 1);
                            }
                        }
                        rs.close();
                    }
                    plugin.getLogger().info("已加载 " + shops.size() + " 个商铺");
                    loadShopItems();
                    loadShelves();
                } catch (Exception e) {
                    plugin.getLogger().warning("加载商铺失败: " + e.getMessage());
                }
            });
    }

    private void loadShelves() {
        plugin.getDatabaseManager().queryAsync("SELECT * FROM shop_shelves")
            .thenAccept(rs -> {
                try {
                    int count = 0;
                    if (rs != null) {
                        while (rs.next()) {
                            int shelfId = rs.getInt("id");
                            int shopId = rs.getInt("shop_id");
                            String world = rs.getString("world");
                            double x = rs.getDouble("x");
                            double y = rs.getDouble("y");
                            double z = rs.getDouble("z");
                            
                            Shop shop = shops.get(shopId);
                            if (shop != null && Bukkit.getWorld(world) != null) {
                                Location loc = new Location(Bukkit.getWorld(world), x, y, z);
                                ShopShelf shelf = new ShopShelf(shelfId, shopId, loc);
                                
                                // 加载自定义显示名称
                                String displayName = rs.getString("display_name");
                                if (displayName != null && !displayName.isEmpty()) {
                                    shelf.setDisplayName(displayName);
                                }
                                
                                shop.getShelves().put(shelfId, shelf);
                                count++;
                            }
                        }
                        rs.close();
                    }
                    plugin.getLogger().info("已加载 " + count + " 个货架");
                    loadShelfItems();
                } catch (Exception e) {
                    plugin.getLogger().warning("加载货架失败: " + e.getMessage());
                }
            });
    }

    private void loadShelfItems() {
        plugin.getDatabaseManager().queryAsync("SELECT * FROM shelf_items")
            .thenAccept(rs -> {
                try {
                    int count = 0;
                    if (rs != null) {
                        while (rs.next()) {
                            int shelfId = rs.getInt("shelf_id");
                            int slot = rs.getInt("slot");
                            String itemData = rs.getString("item_data");
                            double price = rs.getDouble("price");
                            int stock = rs.getInt("stock");
                            // 读取货币类型
                            String currencyType = "nye";
                            try {
                                currencyType = rs.getString("currency_type");
                                if (currencyType == null) currencyType = "nye";
                            } catch (Exception ignored) {}
                            
                            // 读取折扣字段（兼容旧数据库）
                            double discountRate = 1.0;
                            long discountExpiry = 0;
                            try { discountRate = rs.getDouble("discount_rate"); } catch (Exception ignored) {}
                            try { discountExpiry = rs.getLong("discount_expiry"); } catch (Exception ignored) {}

                            // 找到对应的货架
                            for (Shop shop : shops.values()) {
                                ShopShelf shelf = shop.getShelf(shelfId);
                                if (shelf != null) {
                                    ItemStack item = deserializeItem(itemData);
                                    if (item != null) {
                                        ShopShelf.ShelfItem shelfItem = new ShopShelf.ShelfItem(item, price, stock, currencyType);
                                        shelfItem.setDiscountRate(discountRate);
                                        shelfItem.setDiscountExpiry(discountExpiry);
                                        shelf.getItems().put(slot, shelfItem);
                                        count++;
                                    }
                                    break;
                                }
                            }
                        }
                        rs.close();
                    }
                    plugin.getLogger().info("已加载 " + count + " 个货架商品");
                } catch (Exception e) {
                    plugin.getLogger().warning("加载货架商品失败: " + e.getMessage());
                }
            });
    }

    private void loadShopItems() {
        plugin.getDatabaseManager().queryAsync("SELECT * FROM shop_items")
            .thenAccept(rs -> {
                try {
                    if (rs != null) {
                        while (rs.next()) {
                            int shopId = rs.getInt("shop_id");
                            int slot = rs.getInt("slot");
                            String itemData = rs.getString("item_data");
                            double price = rs.getDouble("price");
                            String currencyType = rs.getString("currency_type");
                            
                            StallItem item = StallItem.deserialize(itemData, slot, price, currencyType);
                            if (item != null) {
                                shopItems.computeIfAbsent(shopId, k -> new ConcurrentHashMap<>())
                                    .put(slot, item);
                            }
                        }
                        rs.close();
                    }
                    plugin.getLogger().info("已加载商铺商品数据");
                } catch (Exception e) {
                    plugin.getLogger().warning("加载商铺商品失败: " + e.getMessage());
                }
            });
    }

    public boolean createShop(String name, Location location, int shelfCount) {
        int id = nextShopId.getAndIncrement();
        Shop shop = new Shop(id, name, location, shelfCount);
        shops.put(id, shop);
        
        plugin.getDatabaseManager().executeAsync(
            "INSERT INTO shops (id, name, world, x, y, z, shelf_count) VALUES (?, ?, ?, ?, ?, ?, ?)",
            id, name, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), shelfCount
        );
        
        return true;
    }

    public boolean deleteShop(int shopId) {
        Shop shop = shops.remove(shopId);
        if (shop == null) return false;
        
        // 清理所有货架的全息图和木桶
        for (ShopShelf shelf : shop.getShelves().values()) {
            // 移除全息图
            plugin.getShelfHologramManager().removeHologram(shelf.getId());
            
            // 移除木桶方块
            if (shelf.getLocation() != null && shelf.getLocation().getWorld() != null) {
                org.bukkit.block.Block block = shelf.getLocation().getBlock();
                if (block.getType() == org.bukkit.Material.BARREL) {
                    block.setType(org.bukkit.Material.AIR);
                }
            }
            
            // 删除货架数据
            plugin.getDatabaseManager().executeAsync("DELETE FROM shop_shelves WHERE id = ?", shelf.getId());
            plugin.getDatabaseManager().executeAsync("DELETE FROM shelf_items WHERE shelf_id = ?", shelf.getId());
        }
        
        shopItems.remove(shopId);
        plugin.getDatabaseManager().executeAsync("DELETE FROM shops WHERE id = ?", shopId);
        plugin.getDatabaseManager().executeAsync("DELETE FROM shop_items WHERE shop_id = ?", shopId);
        
        return true;
    }

    public boolean rentShop(Player player, int shopId, int days) {
        return rentShop(player, shopId, days, "vault");
    }

    public boolean rentShop(Player player, int shopId, int amount, String currencyType, String unit) {
        // 计算价格和到期时间
        boolean nye = currencyType.equals("nye");
        double pricePerUnit;
        long durationMs;
        switch (unit) {
            case "minute" -> {
                pricePerUnit = plugin.getConfig().getDouble(nye ? "shop.nye-rent-per-minute" : "shop.rent-per-minute", nye ? 5 : 1);
                durationMs = amount * 60L * 1000;
            }
            case "hour" -> {
                pricePerUnit = plugin.getConfig().getDouble(nye ? "shop.nye-rent-per-hour" : "shop.rent-per-hour", nye ? 150 : 30);
                durationMs = amount * 3600L * 1000;
            }
            case "month" -> {
                pricePerUnit = plugin.getConfig().getDouble(nye ? "shop.nye-rent-per-month" : "shop.rent-per-month", nye ? 60000 : 12000);
                durationMs = amount * 30L * 86400L * 1000;
            }
            default -> { // "day"
                pricePerUnit = plugin.getConfig().getDouble(nye ? "shop.nye-rent-per-day" : "shop.rent-per-day", nye ? 2500 : 500);
                durationMs = amount * 86400L * 1000;
            }
        }
        double rentPrice = pricePerUnit * amount;
        // 折扣（按等效天数）
        double equivDays = durationMs / 86400000.0;
        int tier2Days = plugin.getConfig().getInt("shop.rent-discount.tier2-days", 90);
        int tier1Days = plugin.getConfig().getInt("shop.rent-discount.tier1-days", 30);
        double tier2Rate = plugin.getConfig().getDouble("shop.rent-discount.tier2-rate", 0.90);
        double tier1Rate = plugin.getConfig().getDouble("shop.rent-discount.tier1-rate", 0.95);
        if (tier2Days > 0 && equivDays >= tier2Days) rentPrice *= tier2Rate;
        else if (tier1Days > 0 && equivDays >= tier1Days) rentPrice *= tier1Rate;

        Shop shop = shops.get(shopId);
        if (shop == null || !shop.isAvailable()) {
            plugin.getMessageManager().sendRaw(player, "&c该商铺不可租赁!");
            return false;
        }
        if (!plugin.getLicenseManager().hasLicense(player)) {
            plugin.getMessageManager().sendRaw(player, "&c租赁商铺需要营业执照!");
            return false;
        }
        if (!plugin.getEconomyManager().has(player, rentPrice, currencyType)) {
            plugin.getMessageManager().sendRaw(player, "&c余额不足! 租金: " +
                plugin.getEconomyManager().formatCurrency(rentPrice, currencyType));
            return false;
        }
        plugin.getEconomyManager().withdraw(player, rentPrice, currencyType);
        plugin.getEconomyManager().sendBalanceHint(player, currencyType);
        shop.setOwnerUuid(player.getUniqueId());
        shop.setOwnerName(player.getName());
        shop.setRented(true);
        shop.setOwned(false);
        shop.setRentExpireTime(System.currentTimeMillis() + durationMs);
        shop.setShelfDurability(100);
        saveShop(shop);
        String unitLabel = switch (unit) { case "minute" -> "分钟"; case "hour" -> "小时"; case "month" -> "月"; default -> "天"; };
        plugin.getMessageManager().sendRaw(player, "&a成功租赁商铺 &e" + shop.getName() + " &a" + amount + " " + unitLabel + "!");
        return true;
    }

    public boolean rentShop(Player player, int shopId, int days, String currencyType) {
        Shop shop = shops.get(shopId);
        if (shop == null || !shop.isAvailable()) {
            plugin.getMessageManager().sendRaw(player, "&c该商铺不可租赁!");
            return false;
        }
        
        if (!plugin.getLicenseManager().hasLicense(player)) {
            plugin.getMessageManager().sendRaw(player, "&c租赁商铺需要营业执照!");
            return false;
        }
        
        double rentPrice;
        if (currencyType.equals("nye")) {
            rentPrice = plugin.getConfig().getDouble("shop.nye-rent-per-day", 2500) * days;
        } else {
            rentPrice = plugin.getConfigManager().getConfig().getDouble("shop.rent-per-day", 500) * days;
        }
        // 长租折扣
        int tier2Days = plugin.getConfig().getInt("shop.rent-discount.tier2-days", 90);
        int tier1Days = plugin.getConfig().getInt("shop.rent-discount.tier1-days", 30);
        double tier2Rate = plugin.getConfig().getDouble("shop.rent-discount.tier2-rate", 0.90);
        double tier1Rate = plugin.getConfig().getDouble("shop.rent-discount.tier1-rate", 0.95);
        if (tier2Days > 0 && days >= tier2Days) rentPrice *= tier2Rate;
        else if (tier1Days > 0 && days >= tier1Days) rentPrice *= tier1Rate;
        
        if (!plugin.getEconomyManager().has(player, rentPrice, currencyType)) {
            plugin.getMessageManager().sendRaw(player, "&c余额不足! 租金: " + 
                plugin.getEconomyManager().formatCurrency(rentPrice, currencyType));
            return false;
        }
        
        plugin.getEconomyManager().withdraw(player, rentPrice, currencyType);
        plugin.getEconomyManager().sendBalanceHint(player, currencyType);
        
        shop.setOwnerUuid(player.getUniqueId());
        shop.setOwnerName(player.getName());
        shop.setRented(true);
        shop.setOwned(false);
        shop.setRentExpireTime(System.currentTimeMillis() + (days * 24L * 60 * 60 * 1000));
        shop.setShelfDurability(100);
        
        saveShop(shop);
        
        plugin.getMessageManager().sendRaw(player, "&a成功租赁商铺 &e" + shop.getName() + " &a" + days + "天!");
        return true;
    }

    public boolean buyShop(Player player, int shopId) {
        return buyShop(player, shopId, "vault");
    }

    public boolean buyShop(Player player, int shopId, String currencyType) {
        Shop shop = shops.get(shopId);
        if (shop == null || !shop.isAvailable()) {
            plugin.getMessageManager().sendRaw(player, "&c该商铺不可购买!");
            return false;
        }
        
        if (!plugin.getLicenseManager().hasLicense(player)) {
            plugin.getMessageManager().sendRaw(player, "&c购买商铺需要营业执照!");
            return false;
        }
        
        double buyPrice;
        if (currencyType.equals("nye")) {
            buyPrice = plugin.getConfig().getDouble("shop.nye-buy-price", 500000);
        } else {
            buyPrice = plugin.getConfigManager().getConfig().getDouble("shop.buy-price", 100000);
        }
        
        if (!plugin.getEconomyManager().has(player, buyPrice, currencyType)) {
            plugin.getMessageManager().sendRaw(player, "&c余额不足! 购买价: " + 
                plugin.getEconomyManager().formatCurrency(buyPrice, currencyType));
            return false;
        }
        
        plugin.getEconomyManager().withdraw(player, buyPrice, currencyType);
        plugin.getEconomyManager().sendBalanceHint(player, currencyType);
        
        shop.setOwnerUuid(player.getUniqueId());
        shop.setOwnerName(player.getName());
        shop.setRented(false);
        shop.setOwned(true);
        shop.setRentExpireTime(0);
        shop.setShelfDurability(100);
        
        saveShop(shop);
        
        plugin.getMessageManager().sendRaw(player, "&a成功购买商铺 &e" + shop.getName() + "&a!");
        return true;
    }

    public boolean renewRent(Player player, int shopId, int days) {
        Shop shop = shops.get(shopId);
        if (shop == null || !shop.isRented()) {
            plugin.getMessageManager().sendRaw(player, "&c该商铺未被租赁!");
            return false;
        }
        
        if (!shop.getOwnerUuid().equals(player.getUniqueId())) {
            plugin.getMessageManager().sendRaw(player, "&c你不是该商铺的租户!");
            return false;
        }
        
        double rentPrice = plugin.getConfigManager().getConfig().getDouble("shop.rent-per-day", 500) * days;
        if (!plugin.getEconomyManager().has(player, rentPrice, "vault")) {
            plugin.getMessageManager().sendRaw(player, "&c余额不足!");
            return false;
        }
        
        plugin.getEconomyManager().withdraw(player, rentPrice, "vault");
        shop.setRentExpireTime(shop.getRentExpireTime() + (days * 24L * 60 * 60 * 1000));
        saveShop(shop);
        
        plugin.getMessageManager().sendRaw(player, "&a成功续租 " + days + " 天!");
        return true;
    }

    public boolean addItemToShop(int shopId, StallItem item) {
        Shop shop = shops.get(shopId);
        if (shop == null) return false;
        
        Map<Integer, StallItem> items = shopItems.computeIfAbsent(shopId, k -> new ConcurrentHashMap<>());
        if (items.size() >= shop.getShelfCount() * 27) {
            return false;
        }
        
        int slot = getNextAvailableSlot(shopId);
        if (slot == -1) return false;
        
        StallItem newItem = new StallItem(slot, item.getItemStack(), item.getPrice(), item.getCurrencyType());
        items.put(slot, newItem);
        
        plugin.getDatabaseManager().executeAsync(
            "INSERT INTO shop_items (shop_id, slot, item_data, price, currency_type) VALUES (?, ?, ?, ?, ?)",
            shopId, slot, newItem.serialize(), newItem.getPrice(), newItem.getCurrencyType()
        );
        
        return true;
    }

    public boolean removeItemFromShop(int shopId, int slot) {
        Map<Integer, StallItem> items = shopItems.get(shopId);
        if (items == null) return false;
        
        StallItem removed = items.remove(slot);
        if (removed == null) return false;
        
        plugin.getDatabaseManager().executeAsync(
            "DELETE FROM shop_items WHERE shop_id = ? AND slot = ?", shopId, slot
        );
        
        return true;
    }

    private int getNextAvailableSlot(int shopId) {
        Map<Integer, StallItem> items = shopItems.getOrDefault(shopId, new HashMap<>());
        Shop shop = shops.get(shopId);
        if (shop == null) return -1;
        
        int maxSlots = shop.getShelfCount() * 27;
        for (int i = 0; i < maxSlots; i++) {
            if (!items.containsKey(i)) {
                return i;
            }
        }
        return -1;
    }

    private void saveShop(Shop shop) {
        plugin.getDatabaseManager().executeAsync(
            "UPDATE shops SET owner_uuid = ?, owner_name = ?, is_rented = ?, is_owned = ?, rent_expire_time = ?, shelf_durability = ? WHERE id = ?",
            shop.getOwnerUuid() != null ? shop.getOwnerUuid().toString() : null,
            shop.getOwnerName(),
            shop.isRented(),
            shop.isOwned(),
            shop.getRentExpireTime(),
            shop.getShelfDurability(),
            shop.getId()
        );
    }

    public void setShopRegion(int shopId, Location min, Location max) {
        Shop shop = shops.get(shopId);
        if (shop == null) return;
        
        shop.setMinCorner(min);
        shop.setMaxCorner(max);
        
        plugin.getDatabaseManager().executeAsync(
            "UPDATE shops SET min_x = ?, min_y = ?, min_z = ?, max_x = ?, max_y = ?, max_z = ? WHERE id = ?",
            min.getX(), min.getY(), min.getZ(),
            max.getX(), max.getY(), max.getZ(),
            shopId
        );
    }

    public Shop getShop(int id) {
        return shops.get(id);
    }

    public List<Shop> getAllShops() {
        return new ArrayList<>(shops.values());
    }

    public List<Shop> getAvailableShops() {
        List<Shop> available = new ArrayList<>();
        for (Shop shop : shops.values()) {
            if (shop.isAvailable()) {
                available.add(shop);
            }
        }
        return available;
    }

    public List<Shop> getPlayerShops(UUID playerUuid) {
        List<Shop> playerShops = new ArrayList<>();
        for (Shop shop : shops.values()) {
            if (playerUuid.equals(shop.getOwnerUuid())) {
                playerShops.add(shop);
            }
        }
        return playerShops;
    }

    public Map<Integer, StallItem> getShopItems(int shopId) {
        return shopItems.getOrDefault(shopId, new HashMap<>());
    }

    public void checkExpiredRents() {
        for (Shop shop : shops.values()) {
            if (!shop.isRented() || !shop.isRentExpired()) continue;
            UUID ownerUuid = shop.getOwnerUuid();
            String shopName = shop.getName();
            Location dropLoc = shop.getLocation();

            // 1. 返还货架商品
            for (ShopShelf shelf : shop.getShelves().values()) {
                for (ShopShelf.ShelfItem si : shelf.getItems().values()) {
                    ItemStack stack = si.getItemStack();
                    stack.setAmount(si.getStock());
                    if (stack.getAmount() <= 0) continue;
                    boolean returned = false;
                    // a. 尝试 SweetMail
                    if (ownerUuid != null) {
                        returned = plugin.getSweetMailManager().sendItemMail(
                            ownerUuid, stack, "商铺货架物品返还",
                            "你的商铺 [" + shopName + "] 租约到期，货架商品已返还。");
                    }
                    if (!returned && ownerUuid != null) {
                        // b. 玩家在线且背包有空间
                        Player online = Bukkit.getPlayer(ownerUuid);
                        if (online != null && online.isOnline()) {
                            Map<Integer, ItemStack> leftOver = online.getInventory().addItem(stack);
                            returned = leftOver.isEmpty();
                            // c. 背包满 → 掉落剩余
                            if (!leftOver.isEmpty() && dropLoc != null && dropLoc.getWorld() != null) {
                                for (ItemStack drop : leftOver.values()) {
                                    dropLoc.getWorld().dropItemNaturally(dropLoc, drop);
                                }
                            }
                            returned = true;
                        }
                    }
                    // d. SweetMail不可用且离线 → 掉落
                    if (!returned && dropLoc != null && dropLoc.getWorld() != null) {
                        dropLoc.getWorld().dropItemNaturally(dropLoc, stack);
                    }
                }
                shelf.getItems().clear();
                plugin.getDatabaseManager().executeAsync(
                    "DELETE FROM shelf_items WHERE shelf_id = ?", shelf.getId()
                );
                plugin.getShelfHologramManager().updateHologram(shelf);
            }

            // 2. 清除货架结构
            plugin.getDatabaseManager().executeAsync(
                "DELETE FROM shop_shelves WHERE shop_id = ?", shop.getId()
            );
            for (ShopShelf shelf : shop.getShelves().values()) {
                plugin.getShelfHologramManager().removeHologram(shelf.getId());
            }
            shop.getShelves().clear();

            // 3. 退还额外货架槽位费用（按比例）
            int defaultSlots = plugin.getConfig().getInt("shop.default-shelf-slots", 1);
            int extraSlots = shop.getUnlockedShelfSlots() - defaultSlots;
            if (extraSlots > 0 && ownerUuid != null) {
                double unlockPrice = plugin.getConfig().getDouble("shop.shelf-unlock-price", 5000);
                double refundRate = plugin.getConfig().getDouble("shop.shelf-unlock-refund-rate", 0.8);
                double refund = extraSlots * unlockPrice * refundRate;
                if (refund > 0) {
                    plugin.getEconomyManager().depositOffline(ownerUuid, refund, "vault");
                    Player onlineP = Bukkit.getPlayer(ownerUuid);
                    String refundStr = plugin.getEconomyManager().formatCurrency(refund, "vault");
                    if (onlineP != null && onlineP.isOnline()) {
                        plugin.getMessageManager().sendRaw(onlineP,
                            "&a[商铺] &7额外货架槽位已按 &e" + (int)(refundRate*100) + "% &7退款: &f" + refundStr);
                    } else {
                        plugin.getSweetMailManager().sendNoticeMail(ownerUuid, "货架槽位退款",
                            "你的商铺 [" + shopName + "] 租约到期，",
                            "额外购买的 " + extraSlots + " 个货架槽位已按 " + (int)(refundRate*100) + "% 退款: " + refundStr);
                    }
                }
            }
            shop.setUnlockedShelfSlots(defaultSlots);

            // 4. 清除商铺主数据
            shop.clearOwner();
            saveShop(shop);
            shopItems.remove(shop.getId());
            warnedShopIds.remove(shop.getId());
            plugin.getDatabaseManager().executeAsync(
                "DELETE FROM shop_items WHERE shop_id = ?", shop.getId()
            );
            plugin.getLogger().info("商铺 " + shopName + " 租约已到期，已清空");

            // 4. 通知业主
            if (ownerUuid != null) {
                Player online = Bukkit.getPlayer(ownerUuid);
                if (online != null && online.isOnline()) {
                    plugin.getMessageManager().sendRaw(online,
                        "&c[商铺] &7你的商铺 &e" + shopName + " &7租约已到期，商铺已被收回！");
                    plugin.getMessageManager().sendRaw(online,
                        "&7使用 &e/baitan shop &7可重新租赁。");
                } else {
                    plugin.getSweetMailManager().sendNoticeMail(ownerUuid, "商铺租约到期",
                        "你的商铺 [" + shopName + "] 租约已到期，商铺已被收回。",
                        "如需继续使用，请重新租赁：/baitan shop");
                }
            }
        }
    }

    public void checkRentWarnings() {
        int warnMinutes = plugin.getConfig().getInt("shop.rent-expiry-warning-minutes", 30);
        if (warnMinutes <= 0) return;
        long warnMs = warnMinutes * 60_000L;
        long now = System.currentTimeMillis();
        for (Shop shop : shops.values()) {
            if (!shop.isRented() || shop.isRentExpired()) continue;
            if (warnedShopIds.contains(shop.getId())) continue;
            long remaining = shop.getRentExpireTime() - now;
            if (remaining > warnMs) continue;
            // 首次进入预警窗口
            warnedShopIds.add(shop.getId());
            if (shop.getOwnerUuid() == null) continue;
            Player online = Bukkit.getPlayer(shop.getOwnerUuid());
            if (online == null || !online.isOnline()) continue;
            plugin.getMessageManager().sendRaw(online,
                "&e[商铺提醒] &7你的商铺 &e" + shop.getName() +
                " &7租期仅剩 &c" + shop.getRemainingRentTime() +
                " &7，请尽快续租！(&e/baitan shop&7)");
        }
    }

    // ============ 货架相关方法 ============

    public void saveShelf(ShopShelf shelf) {
        Location loc = shelf.getLocation();
        if (loc == null || loc.getWorld() == null) return;
        
        plugin.getDatabaseManager().executeAsync(
            "INSERT OR REPLACE INTO shop_shelves (id, shop_id, world, x, y, z) VALUES (?, ?, ?, ?, ?, ?)",
            shelf.getId(), shelf.getShopId(), loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ()
        );
    }

    public void deleteShelf(int shelfId) {
        plugin.getDatabaseManager().executeAsync("DELETE FROM shop_shelves WHERE id = ?", shelfId);
        plugin.getDatabaseManager().executeAsync("DELETE FROM shelf_items WHERE shelf_id = ?", shelfId);
    }

    public void saveShelfItem(ShopShelf shelf, int slot, ShopShelf.ShelfItem item) {
        String itemData = serializeItem(item.getItemStack());
        
        plugin.getDatabaseManager().executeAsync(
            "INSERT OR REPLACE INTO shelf_items (shelf_id, slot, item_data, price, stock, sold, currency_type, discount_rate, discount_expiry) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            shelf.getId(), slot, itemData, item.getPrice(), item.getStock(), item.getSold(), item.getCurrencyType(),
            item.getDiscountRate(), item.getDiscountExpiry()
        );
    }

    public void updateShelfItemDiscount(int shelfId, int slot, double discountRate, long discountExpiry) {
        plugin.getDatabaseManager().executeAsync(
            "UPDATE shelf_items SET discount_rate = ?, discount_expiry = ? WHERE shelf_id = ? AND slot = ?",
            discountRate, discountExpiry, shelfId, slot
        );
    }

    public void deleteShelfItem(int shelfId, int slot) {
        plugin.getDatabaseManager().executeAsync(
            "DELETE FROM shelf_items WHERE shelf_id = ? AND slot = ?", shelfId, slot
        );
    }

    public void updateShelfItemStock(int shelfId, int slot, int newStock, int sold) {
        plugin.getDatabaseManager().executeAsync(
            "UPDATE shelf_items SET stock = ?, sold = ? WHERE shelf_id = ? AND slot = ?",
            newStock, sold, shelfId, slot
        );
    }

    // ============ 物品序列化 ============

    private String serializeItem(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warning("序列化物品失败: " + e.getMessage());
            return null;
        }
    }

    private ItemStack deserializeItem(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) {
            plugin.getLogger().warning("反序列化物品失败: " + e.getMessage());
            return null;
        }
    }

    public void saveShopUnlockedSlots(Shop shop) {
        plugin.getDatabaseManager().executeAsync(
            "UPDATE shops SET unlocked_shelf_slots = ? WHERE id = ?",
            shop.getUnlockedShelfSlots(), shop.getId()
        );
    }

    // 货架移动相关方法
    public void setPendingShelfMove(UUID playerUuid, ShopShelf shelf) {
        pendingShelfMoves.put(playerUuid, shelf);
    }

    public ShopShelf getPendingShelfMove(UUID playerUuid) {
        return pendingShelfMoves.get(playerUuid);
    }

    public void clearPendingShelfMove(UUID playerUuid) {
        pendingShelfMoves.remove(playerUuid);
    }

    // 货架重命名相关方法
    public void setPendingShelfRename(UUID playerUuid, ShopShelf shelf) {
        pendingShelfRenames.put(playerUuid, shelf);
    }

    public ShopShelf getPendingShelfRename(UUID playerUuid) {
        return pendingShelfRenames.get(playerUuid);
    }

    public void clearPendingShelfRename(UUID playerUuid) {
        pendingShelfRenames.remove(playerUuid);
    }

    // 更新货架位置
    public void updateShelfLocation(ShopShelf shelf) {
        Location loc = shelf.getLocation();
        if (loc == null || loc.getWorld() == null) return;
        
        plugin.getDatabaseManager().executeAsync(
            "UPDATE shop_shelves SET world = ?, x = ?, y = ?, z = ? WHERE id = ?",
            loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), shelf.getId()
        );
    }

    // 更新货架显示名称
    public void updateShelfDisplayName(ShopShelf shelf) {
        plugin.getDatabaseManager().executeAsync(
            "UPDATE shop_shelves SET display_name = ? WHERE id = ?",
            shelf.getDisplayName(), shelf.getId()
        );
    }

    // 价格输入相关方法
    public void setPendingPriceInput(UUID playerUuid, Object gui) {
        pendingPriceInputs.put(playerUuid, gui);
    }

    public Object getPendingPriceInput(UUID playerUuid) {
        return pendingPriceInputs.get(playerUuid);
    }

    public void clearPendingPriceInput(UUID playerUuid) {
        pendingPriceInputs.remove(playerUuid);
    }
    
    // 货架价格修改相关方法
    public void setPendingPriceEdit(UUID playerUuid, ShopShelf shelf, int slot) {
        pendingPriceEditShelves.put(playerUuid, shelf);
        pendingPriceEditSlots.put(playerUuid, new int[]{shelf.getId(), slot});
    }

    public ShopShelf getPendingPriceEditShelf(UUID playerUuid) {
        return pendingPriceEditShelves.get(playerUuid);
    }

    public int getPendingPriceEditSlot(UUID playerUuid) {
        int[] data = pendingPriceEditSlots.get(playerUuid);
        return data != null ? data[1] : -1;
    }

    public void clearPendingPriceEdit(UUID playerUuid) {
        pendingPriceEditShelves.remove(playerUuid);
        pendingPriceEditSlots.remove(playerUuid);
    }

    /**
     * 清理玩家所有待处理数据 (玩家退出时调用，防止内存泄漏)
     */
    public void clearPlayerPendingData(UUID playerUuid) {
        pendingShelfMoves.remove(playerUuid);
        pendingShelfRenames.remove(playerUuid);
        pendingPriceInputs.remove(playerUuid);
        pendingPriceEditShelves.remove(playerUuid);
        pendingPriceEditSlots.remove(playerUuid);
    }
}

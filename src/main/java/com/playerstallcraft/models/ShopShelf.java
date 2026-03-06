package com.playerstallcraft.models;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 商铺货架模型
 * 每个货架最多存放16个商品
 */
public class ShopShelf {

    private final int id;
    private final int shopId;
    private Location location;
    private final Map<Integer, ShelfItem> items; // slot -> item (0-15)
    private int currentDisplayIndex = 0; // 当前轮播显示的商品索引
    private String displayName; // 自定义全息图显示名称

    public static final int MAX_SLOTS = 16;

    public ShopShelf(int id, int shopId, Location location) {
        this.id = id;
        this.shopId = shopId;
        this.location = location;
        this.items = new ConcurrentHashMap<>();
        this.displayName = "货架 #" + id; // 默认名称
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getId() {
        return id;
    }

    public int getShopId() {
        return shopId;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Map<Integer, ShelfItem> getItems() {
        return items;
    }

    public ShelfItem getItem(int slot) {
        return items.get(slot);
    }

    public boolean addItem(int slot, ItemStack item, double price, int stock) {
        if (slot < 0 || slot >= MAX_SLOTS) return false;
        if (items.containsKey(slot)) return false;
        
        items.put(slot, new ShelfItem(item, price, stock));
        return true;
    }

    public boolean addItemWithCurrency(int slot, ShelfItem item) {
        if (slot < 0 || slot >= MAX_SLOTS) return false;
        if (items.containsKey(slot)) return false;
        
        items.put(slot, item);
        return true;
    }

    public boolean removeItem(int slot) {
        return items.remove(slot) != null;
    }

    public boolean hasItem(int slot) {
        return items.containsKey(slot);
    }

    public int getItemCount() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public boolean isFull() {
        return items.size() >= MAX_SLOTS;
    }

    /**
     * 获取下一个轮播显示的商品
     */
    public ShelfItem getNextDisplayItem() {
        if (items.isEmpty()) return null;
        
        Integer[] slots = items.keySet().toArray(new Integer[0]);
        if (slots.length == 0) return null;
        
        currentDisplayIndex = (currentDisplayIndex + 1) % slots.length;
        return items.get(slots[currentDisplayIndex]);
    }

    /**
     * 获取当前轮播显示的商品槽位
     */
    public int getCurrentDisplaySlot() {
        if (items.isEmpty()) return -1;
        
        Integer[] slots = items.keySet().toArray(new Integer[0]);
        if (slots.length == 0) return -1;
        
        return slots[currentDisplayIndex % slots.length];
    }

    /**
     * 获取当前轮播显示的商品
     */
    public ShelfItem getCurrentDisplayItem() {
        int slot = getCurrentDisplaySlot();
        if (slot < 0) return null;
        return items.get(slot);
    }

    /**
     * 货架商品数据类
     */
    public static class ShelfItem {
        private final ItemStack itemStack;
        private double price;
        private int stock;
        private int sold = 0;
        private String currencyType = "nye"; // "vault" 或 "nye"
        private double discountRate = 1.0;  // 1.0 = 无折扣, 0.8 = 八折
        private long discountExpiry = 0;   // 0 = 无效

        public ShelfItem(ItemStack itemStack, double price, int stock) {
            this.itemStack = itemStack.clone();
            this.price = price;
            this.stock = stock;
        }

        public ShelfItem(ItemStack itemStack, double price, int stock, String currencyType) {
            this.itemStack = itemStack.clone();
            this.price = price;
            this.stock = stock;
            this.currencyType = currencyType != null ? currencyType : "nye";
        }

        public String getCurrencyType() {
            return currencyType;
        }

        public void setCurrencyType(String currencyType) {
            this.currencyType = currencyType;
        }

        public ItemStack getItemStack() {
            return itemStack.clone();
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        public int getStock() {
            return stock;
        }

        public void setStock(int stock) {
            this.stock = stock;
        }

        public int getSold() {
            return sold;
        }

        public void addSold(int amount) {
            this.sold += amount;
        }

        public boolean purchase(int amount) {
            if (stock < amount) return false;
            stock -= amount;
            sold += amount;
            return true;
        }

        public boolean isAvailable() {
            return stock > 0;
        }

        public double getDiscountRate() { return discountRate; }
        public void setDiscountRate(double rate) { this.discountRate = Math.max(0.01, Math.min(1.0, rate)); }

        public long getDiscountExpiry() { return discountExpiry; }
        public void setDiscountExpiry(long expiry) { this.discountExpiry = expiry; }

        /** 返回当前是否有效折扣 */
        public boolean hasActiveDiscount() {
            return discountRate < 1.0 && (discountExpiry == 0 || System.currentTimeMillis() < discountExpiry);
        }

        /** 返回实际成交价格（折后） */
        public double getEffectivePrice() {
            return hasActiveDiscount() ? price * discountRate : price;
        }

        public String getItemName() {
            if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                return itemStack.getItemMeta().getDisplayName();
            }
            return itemStack.getType().name().toLowerCase().replace("_", " ");
        }
    }
}

package com.playerstallcraft.models;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Shop {

    private final int id;
    private final String name;
    private final Location location;
    private int unlockedShelfSlots; // 已解锁的货架位置数
    private final Map<Integer, ShopShelf> shelves; // 货架ID -> 货架
    private Location minCorner;  // 区域最小角
    private Location maxCorner;  // 区域最大角
    private UUID ownerUuid;
    private String ownerName;
    private boolean isRented;
    private boolean isOwned;
    private long rentExpireTime;
    private int shelfDurability;

    public Shop(int id, String name, Location location, int defaultShelfSlots) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.unlockedShelfSlots = defaultShelfSlots;
        this.shelves = new ConcurrentHashMap<>();
        this.isRented = false;
        this.isOwned = false;
        this.shelfDurability = 100;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public int getShelfCount() {
        return shelves.size();
    }

    public int getUnlockedShelfSlots() {
        return unlockedShelfSlots;
    }

    public void setUnlockedShelfSlots(int slots) {
        this.unlockedShelfSlots = slots;
    }

    public boolean unlockShelfSlot() {
        unlockedShelfSlots++;
        return true;
    }

    public Map<Integer, ShopShelf> getShelves() {
        return shelves;
    }

    public ShopShelf getShelf(int shelfId) {
        return shelves.get(shelfId);
    }

    public boolean addShelf(ShopShelf shelf) {
        if (shelves.size() >= unlockedShelfSlots) return false;
        shelves.put(shelf.getId(), shelf);
        return true;
    }

    public boolean removeShelf(int shelfId) {
        return shelves.remove(shelfId) != null;
    }

    public boolean canAddMoreShelves() {
        return shelves.size() < unlockedShelfSlots;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public boolean isRented() {
        return isRented;
    }

    public void setRented(boolean rented) {
        isRented = rented;
    }

    public boolean isOwned() {
        return isOwned;
    }

    public void setOwned(boolean owned) {
        isOwned = owned;
    }

    public long getRentExpireTime() {
        return rentExpireTime;
    }

    public void setRentExpireTime(long rentExpireTime) {
        this.rentExpireTime = rentExpireTime;
    }

    public boolean isRentExpired() {
        return isRented && System.currentTimeMillis() > rentExpireTime;
    }

    public int getRemainingRentDays() {
        if (!isRented) return 0;
        long remaining = rentExpireTime - System.currentTimeMillis();
        return (int) Math.max(0, remaining / (1000 * 60 * 60 * 24));
    }

    public String getRemainingRentTime() {
        if (!isRented) return "0 分钟";
        long remaining = rentExpireTime - System.currentTimeMillis();
        if (remaining <= 0) return "已到期";
        long days    = remaining / (1000L * 60 * 60 * 24);
        long hours   = (remaining % (1000L * 60 * 60 * 24)) / (1000L * 60 * 60);
        long minutes = (remaining % (1000L * 60 * 60)) / (1000L * 60);
        if (days > 0) {
            if (hours > 0) return days + " 天 " + hours + " 小时 " + minutes + " 分";
            if (minutes > 0) return days + " 天 " + minutes + " 分";
            return days + " 天";
        }
        if (hours > 0) return hours + " 小时 " + minutes + " 分";
        return minutes + " 分钟";
    }

    public int getShelfDurability() {
        return shelfDurability;
    }

    public void setShelfDurability(int shelfDurability) {
        this.shelfDurability = Math.max(0, Math.min(100, shelfDurability));
    }

    public void decreaseShelfDurability(int amount) {
        this.shelfDurability = Math.max(0, this.shelfDurability - amount);
    }

    public boolean isAvailable() {
        return !isRented && !isOwned;
    }

    public boolean hasOwner() {
        return ownerUuid != null;
    }

    public void clearOwner() {
        this.ownerUuid = null;
        this.ownerName = null;
        this.isRented = false;
        this.isOwned = false;
        this.rentExpireTime = 0;
    }

    public Location getMinCorner() {
        return minCorner;
    }

    public void setMinCorner(Location minCorner) {
        this.minCorner = minCorner;
    }

    public Location getMaxCorner() {
        return maxCorner;
    }

    public void setMaxCorner(Location maxCorner) {
        this.maxCorner = maxCorner;
    }

    public boolean hasRegion() {
        return minCorner != null && maxCorner != null;
    }

    public boolean isInRegion(Location loc) {
        if (!hasRegion()) return false;
        if (!loc.getWorld().equals(minCorner.getWorld())) return false;
        
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        
        return x >= minCorner.getX() && x <= maxCorner.getX() &&
               y >= minCorner.getY() && y <= maxCorner.getY() &&
               z >= minCorner.getZ() && z <= maxCorner.getZ();
    }
}

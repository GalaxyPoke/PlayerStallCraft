package com.playerstallcraft.models;

import org.bukkit.Location;

import java.util.UUID;

public class Shop {

    private final int id;
    private final String name;
    private final Location location;
    private final int shelfCount;
    private UUID ownerUuid;
    private String ownerName;
    private boolean isRented;
    private boolean isOwned;
    private long rentExpireTime;
    private int shelfDurability;

    public Shop(int id, String name, Location location, int shelfCount) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.shelfCount = shelfCount;
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
        return shelfCount;
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
}

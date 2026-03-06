package com.playerstallcraft.models;

import org.bukkit.Location;

import java.util.UUID;

/**
 * 广告数据模型
 */
public class Advertisement {

    private int id;
    private UUID ownerUuid;
    private String ownerName;
    private String title;
    private String description;
    private String iconMaterial; // 显示的物品图标
    private int shopId; // 关联的商铺ID (可选)
    private Location targetLocation; // 点击传送目标位置
    private long startTime;
    private long endTime;
    private double price; // 支付的广告费
    private String currencyType;
    private boolean active;

    public Advertisement(int id, UUID ownerUuid, String ownerName, String title, String description) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.title = title;
        this.description = description;
        this.iconMaterial = "GOLD_INGOT";
        this.active = true;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIconMaterial() {
        return iconMaterial;
    }

    public void setIconMaterial(String iconMaterial) {
        this.iconMaterial = iconMaterial;
    }

    public int getShopId() {
        return shopId;
    }

    public void setShopId(int shopId) {
        this.shopId = shopId;
    }

    public Location getTargetLocation() {
        return targetLocation;
    }

    public void setTargetLocation(Location targetLocation) {
        this.targetLocation = targetLocation;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getCurrencyType() {
        return currencyType;
    }

    public void setCurrencyType(String currencyType) {
        this.currencyType = currencyType;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > endTime;
    }

    public long getRemainingTime() {
        return Math.max(0, endTime - System.currentTimeMillis());
    }

    public String getRemainingTimeString() {
        long remaining = getRemainingTime();
        if (remaining <= 0) return "已过期";
        
        long hours = remaining / (1000 * 60 * 60);
        long minutes = (remaining % (1000 * 60 * 60)) / (1000 * 60);
        
        if (hours > 24) {
            long days = hours / 24;
            return days + "天";
        } else if (hours > 0) {
            return hours + "小时" + minutes + "分钟";
        } else {
            return minutes + "分钟";
        }
    }
}

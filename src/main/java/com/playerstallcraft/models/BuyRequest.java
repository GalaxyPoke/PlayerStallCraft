package com.playerstallcraft.models;

import org.bukkit.Material;

import java.util.UUID;

public class BuyRequest {

    private final int id;
    private final UUID playerUuid;
    private final String playerName;
    private final Material itemType;
    private final String itemData;
    private final int amount;
    private double price;
    private final String currencyType;
    private String status;
    private final long createdAt;
    private long expireTime;
    private int remainingAmount;
    private boolean featured;

    public BuyRequest(int id, UUID playerUuid, String playerName, Material itemType,
                      String itemData, int amount, double price, String currencyType) {
        this(id, playerUuid, playerName, itemType, itemData, amount, price, currencyType, System.currentTimeMillis());
    }

    public BuyRequest(int id, UUID playerUuid, String playerName, Material itemType,
                      String itemData, int amount, double price, String currencyType, long createdAt) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.itemType = itemType;
        this.itemData = itemData;
        this.amount = amount;
        this.remainingAmount = amount;
        this.price = price;
        this.currencyType = currencyType;
        this.status = "active";
        this.createdAt = createdAt;
        this.expireTime = 0;
        this.featured = false;
    }

    public int getId() {
        return id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Material getItemType() {
        return itemType;
    }

    public String getItemData() {
        return itemData;
    }

    public int getAmount() {
        return amount;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    public boolean isExpired() {
        return expireTime > 0 && System.currentTimeMillis() > expireTime;
    }

    public int getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(int remainingAmount) {
        this.remainingAmount = remainingAmount;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    public boolean isActive() {
        return "active".equals(status);
    }

    public String getItemName() {
        return itemType.name().toLowerCase().replace("_", " ");
    }
}

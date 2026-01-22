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
    private final double price;
    private final String currencyType;
    private String status;
    private final long createdAt;

    public BuyRequest(int id, UUID playerUuid, String playerName, Material itemType, 
                      String itemData, int amount, double price, String currencyType) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.itemType = itemType;
        this.itemData = itemData;
        this.amount = amount;
        this.price = price;
        this.currencyType = currencyType;
        this.status = "active";
        this.createdAt = System.currentTimeMillis();
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

    public boolean isActive() {
        return "active".equals(status);
    }

    public String getItemName() {
        return itemType.name().toLowerCase().replace("_", " ");
    }
}

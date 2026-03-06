package com.playerstallcraft.models;

import org.bukkit.Location;

/**
 * 广告位模型
 * 管理员设置的固定广告展示位置
 */
public class AdSlot {

    private int id;
    private String name;
    private Location location;
    private double pricePerHour; // 每小时价格
    private String currencyType;
    private boolean active;

    public AdSlot(int id, String name, Location location) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.pricePerHour = 100;
        this.currencyType = "nye";
        this.active = true;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public double getPricePerHour() {
        return pricePerHour;
    }

    public void setPricePerHour(double pricePerHour) {
        this.pricePerHour = pricePerHour;
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
}

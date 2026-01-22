package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final PlayerStallCraft plugin;
    private FileConfiguration config;

    public ConfigManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public int getMaxSlots() {
        return config.getInt("stall.max-slots", 27);
    }

    public int getHologramRefreshInterval() {
        return config.getInt("stall.hologram-refresh-interval", 100);
    }

    public double getHologramHeightOffset() {
        return config.getDouble("stall.hologram-height-offset", 2.5);
    }

    public double getTaxRate(String type) {
        return switch (type) {
            case "no-license" -> config.getDouble("tax.no-license", 15.0);
            case "with-license" -> config.getDouble("tax.with-license", 8.0);
            case "rented-shop" -> config.getDouble("tax.rented-shop", 3.0);
            case "owned-shop" -> config.getDouble("tax.owned-shop", 1.0);
            default -> config.getDouble("tax.no-license", 15.0);
        };
    }

    public double getLicensePrice() {
        return config.getDouble("license.price", 10000);
    }

    public int getLicenseDuration() {
        return config.getInt("license.duration", 30);
    }

    public double getShopRentPerDay() {
        return config.getDouble("shop.rent-per-day", 500);
    }

    public double getShopBuyPrice() {
        return config.getDouble("shop.buy-price", 100000);
    }

    public int getShelfDurationDays() {
        return config.getInt("shop.shelf.duration-days", 30);
    }

    public int getShelfTransactionLimit() {
        return config.getInt("shop.shelf.transaction-limit", 1000);
    }

    public String getMessagePrefix() {
        return config.getString("messages.prefix", "&6[摆摊] &r");
    }

    public FileConfiguration getConfig() {
        return config;
    }
}

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
        return config.getDouble("stall.hologram-height-offset", 4.5);
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

    // ==================== 圈地粒子效果配置 ====================

    public String getPos1Particle() {
        return config.getString("region-particles.pos1.particle", "FLAME");
    }

    public int getPos1ParticleCount() {
        return config.getInt("region-particles.pos1.count", 20);
    }

    public double getPos1ParticleSpread() {
        return config.getDouble("region-particles.pos1.spread", 0.3);
    }

    public String getPos2Particle() {
        return config.getString("region-particles.pos2.particle", "SOUL_FIRE_FLAME");
    }

    public int getPos2ParticleCount() {
        return config.getInt("region-particles.pos2.count", 20);
    }

    public double getPos2ParticleSpread() {
        return config.getDouble("region-particles.pos2.spread", 0.3);
    }

    public String getBorderParticle() {
        return config.getString("region-particles.border.particle", "END_ROD");
    }

    public double getBorderStep() {
        return config.getDouble("region-particles.border.step", 1.0);
    }

    public int getBorderDuration() {
        return config.getInt("region-particles.border.duration", 5);
    }

    public int getBorderRefreshInterval() {
        return config.getInt("region-particles.border.refresh-interval", 10);
    }

    // ==================== 摆摊区域专属粒子配置 ====================

    public String getStallRegionBorderParticle() {
        return config.getString("stall-region-particles.border.particle", "FLAME");
    }

    public double getStallRegionBorderStep() {
        return config.getDouble("stall-region-particles.border.step", 1.0);
    }

    public int getStallRegionBorderDuration() {
        return config.getInt("stall-region-particles.border.duration", 3);
    }

    public int getStallRegionBorderRefreshInterval() {
        return config.getInt("stall-region-particles.border.refresh-interval", 10);
    }

    // ==================== 商铺区域专属粒子配置 ====================

    public String getShopRegionBorderParticle() {
        return config.getString("shop-region-particles.border.particle", "SOUL_FIRE_FLAME");
    }

    public double getShopRegionBorderStep() {
        return config.getDouble("shop-region-particles.border.step", 1.0);
    }

    public int getShopRegionBorderDuration() {
        return config.getInt("shop-region-particles.border.duration", 3);
    }

    public int getShopRegionBorderRefreshInterval() {
        return config.getInt("shop-region-particles.border.refresh-interval", 10);
    }
}

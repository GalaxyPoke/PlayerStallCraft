package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.License;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LicenseManager {

    private final PlayerStallCraft plugin;
    private final Map<UUID, License> licenses;

    public LicenseManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        this.licenses = new ConcurrentHashMap<>();
        loadLicenses();
    }

    private void loadLicenses() {
        plugin.getDatabaseManager().queryAsync("SELECT * FROM licenses WHERE active = 1")
            .thenAccept(rs -> {
                try {
                    if (rs != null) {
                        while (rs.next()) {
                            UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                            License license = new License(
                                    rs.getInt("id"),
                                    uuid,
                                    rs.getString("player_name"),
                                    rs.getLong("purchase_time"),
                                    rs.getLong("expire_time")
                            );
                            if (!license.isExpired()) {
                                licenses.put(uuid, license);
                            }
                        }
                        rs.close();
                    }
                    plugin.getLogger().info("已加载 " + licenses.size() + " 个营业执照");
                } catch (Exception e) {
                    plugin.getLogger().warning("加载营业执照失败: " + e.getMessage());
                }
            });
    }

    public boolean hasLicense(Player player) {
        return hasLicense(player.getUniqueId());
    }

    public boolean hasLicense(UUID uuid) {
        License license = licenses.get(uuid);
        return license != null && license.isActive();
    }

    public License getLicense(UUID uuid) {
        return licenses.get(uuid);
    }

    public boolean purchaseLicense(Player player) {
        if (hasLicense(player)) {
            return false;
        }

        double price = plugin.getConfigManager().getLicensePrice();
        if (!plugin.getEconomyManager().has(player, price, "vault")) {
            plugin.getMessageManager().send(player, "license.not-enough-money");
            return false;
        }

        if (!plugin.getEconomyManager().withdraw(player, price, "vault")) {
            return false;
        }

        int durationDays = plugin.getConfigManager().getLicenseDuration();
        long now = System.currentTimeMillis();
        long expireTime = now + (durationDays * 24L * 60 * 60 * 1000);

        plugin.getDatabaseManager().executeAsync(
                "INSERT INTO licenses (player_uuid, player_name, purchase_time, expire_time, active) VALUES (?, ?, ?, ?, 1)",
                player.getUniqueId().toString(),
                player.getName(),
                now,
                expireTime
        );

        License license = new License(0, player.getUniqueId(), player.getName(), now, expireTime);
        licenses.put(player.getUniqueId(), license);

        plugin.getMessageManager().send(player, "license.purchase-success", MessageManager.placeholders(
                "days", String.valueOf(durationDays),
                "price", plugin.getEconomyManager().formatCurrency(price, "vault")
        ));

        return true;
    }

    public boolean renewLicense(Player player) {
        License license = licenses.get(player.getUniqueId());
        if (license == null) {
            plugin.getMessageManager().send(player, "license.no-license");
            return false;
        }

        double price = plugin.getConfigManager().getLicensePrice();
        if (!plugin.getEconomyManager().has(player, price, "vault")) {
            plugin.getMessageManager().send(player, "license.not-enough-money");
            return false;
        }

        if (!plugin.getEconomyManager().withdraw(player, price, "vault")) {
            return false;
        }

        int durationDays = plugin.getConfigManager().getLicenseDuration();
        long newExpireTime = license.getExpireTime() + (durationDays * 24L * 60 * 60 * 1000);

        plugin.getDatabaseManager().executeAsync(
                "UPDATE licenses SET expire_time = ? WHERE player_uuid = ?",
                newExpireTime,
                player.getUniqueId().toString()
        );

        License newLicense = new License(license.getId(), player.getUniqueId(), player.getName(), 
                license.getPurchaseTime(), newExpireTime);
        licenses.put(player.getUniqueId(), newLicense);

        plugin.getMessageManager().send(player, "license.renew-success", MessageManager.placeholders(
                "days", String.valueOf(durationDays)
        ));

        return true;
    }

    public String getStallType(UUID uuid) {
        if (hasLicense(uuid)) {
            return "with-license";
        }
        return "no-license";
    }

    public void checkExpiredLicenses() {
        licenses.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}

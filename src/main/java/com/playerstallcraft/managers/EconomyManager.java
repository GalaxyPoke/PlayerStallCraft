package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

public class EconomyManager {

    private final PlayerStallCraft plugin;
    private Object nyeEconomy; // NYEconomy插件实例

    public EconomyManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        setupNYE();
    }

    private void setupNYE() {
        try {
            if (Bukkit.getPluginManager().getPlugin("NYEconomy") != null) {
                nyeEconomy = Bukkit.getPluginManager().getPlugin("NYEconomy");
                plugin.getLogger().info("NYEconomy 已连接!");
            }
        } catch (Exception e) {
            nyeEconomy = null;
        }
    }

    public boolean hasVault() {
        return plugin.getVaultEconomy() != null;
    }

    public boolean hasNYE() {
        return nyeEconomy != null;
    }

    public double getBalance(Player player, String currencyType) {
        if (currencyType.equalsIgnoreCase("nye") && hasNYE()) {
            return getNYEBalance(player.getUniqueId());
        }
        if (currencyType.equalsIgnoreCase("vault") && hasVault()) {
            return plugin.getVaultEconomy().getBalance(player);
        }
        return 0;
    }

    public boolean withdraw(Player player, double amount, String currencyType) {
        if (currencyType.equalsIgnoreCase("nye") && hasNYE()) {
            return withdrawNYE(player.getUniqueId(), amount);
        }
        if (currencyType.equalsIgnoreCase("vault") && hasVault()) {
            Economy economy = plugin.getVaultEconomy();
            if (economy.has(player, amount)) {
                EconomyResponse response = economy.withdrawPlayer(player, amount);
                return response.transactionSuccess();
            }
        }
        return false;
    }

    public boolean deposit(Player player, double amount, String currencyType) {
        if (currencyType.equalsIgnoreCase("nye") && hasNYE()) {
            return depositNYE(player.getUniqueId(), amount);
        }
        if (currencyType.equalsIgnoreCase("vault") && hasVault()) {
            EconomyResponse response = plugin.getVaultEconomy().depositPlayer(player, amount);
            return response.transactionSuccess();
        }
        return false;
    }

    public boolean depositOffline(UUID playerUuid, double amount, String currencyType) {
        if (currencyType.equalsIgnoreCase("nye") && hasNYE()) {
            return depositNYE(playerUuid, amount);
        }
        if (currencyType.equalsIgnoreCase("vault") && hasVault()) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
            EconomyResponse response = plugin.getVaultEconomy().depositPlayer(offlinePlayer, amount);
            return response.transactionSuccess();
        }
        return false;
    }

    public boolean has(Player player, double amount, String currencyType) {
        if (currencyType.equalsIgnoreCase("nye") && hasNYE()) {
            return getNYEBalance(player.getUniqueId()) >= amount;
        }
        if (currencyType.equalsIgnoreCase("vault") && hasVault()) {
            return plugin.getVaultEconomy().has(player, amount);
        }
        return false;
    }

    public String formatCurrency(double amount, String currencyType) {
        if (currencyType.equalsIgnoreCase("nye")) {
            return String.format("%.0f NYE", amount);
        }
        if (currencyType.equalsIgnoreCase("vault") && hasVault()) {
            return plugin.getVaultEconomy().format(amount);
        }
        return String.format("%.2f", amount);
    }

    // NYE货币操作方法 (通过反射调用)
    private double getNYEBalance(UUID playerUuid) {
        try {
            Class<?> apiClass = Class.forName("com.nye.economy.api.NYEconomyAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            return (double) apiClass.getMethod("getBalance", UUID.class).invoke(api, playerUuid);
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean withdrawNYE(UUID playerUuid, double amount) {
        try {
            Class<?> apiClass = Class.forName("com.nye.economy.api.NYEconomyAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            return (boolean) apiClass.getMethod("withdraw", UUID.class, double.class).invoke(api, playerUuid, amount);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean depositNYE(UUID playerUuid, double amount) {
        try {
            Class<?> apiClass = Class.forName("com.nye.economy.api.NYEconomyAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            return (boolean) apiClass.getMethod("deposit", UUID.class, double.class).invoke(api, playerUuid, amount);
        } catch (Exception e) {
            return false;
        }
    }

    public double calculateTax(double amount, String stallType) {
        double taxRate = plugin.getConfigManager().getTaxRate(stallType) / 100.0;
        return amount * taxRate;
    }

    public double getAfterTaxAmount(double amount, String stallType) {
        return amount - calculateTax(amount, stallType);
    }
}

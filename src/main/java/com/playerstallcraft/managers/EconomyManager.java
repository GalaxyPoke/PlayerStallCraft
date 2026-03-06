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
    private boolean nyeAvailable = false;
    private String nyeCurrencyName; // NyEconomy货币名称
    // 缓存反射方法，避免每次调用都查找
    private java.lang.reflect.Method nyeGetBalance;
    private java.lang.reflect.Method nyeWithdraw;
    private java.lang.reflect.Method nyeDeposit;
    private Object nyeApi;

    public EconomyManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        setupNYE();
    }

    private void setupNYE() {
        try {
            if (Bukkit.getPluginManager().getPlugin("NyEconomy") != null) {
                nyeCurrencyName = plugin.getConfigManager().getConfig().getString("currency.nye-currency-name", "鸽币");
                Class<?> mainClass = Class.forName("com.mc9y.nyeconomy.Main");
                nyeApi = mainClass.getMethod("getNyEconomyAPI").invoke(null);
                if (nyeApi != null) {
                    nyeGetBalance = nyeApi.getClass().getMethod("getBalance", String.class, UUID.class);
                    nyeWithdraw   = nyeApi.getClass().getMethod("withdraw",   String.class, UUID.class, int.class);
                    nyeDeposit    = nyeApi.getClass().getMethod("deposit",    String.class, UUID.class, int.class);
                    nyeAvailable = true;
                    plugin.getLogger().info("NyEconomy 已连接! 使用货币: " + nyeCurrencyName);
                }
            }
        } catch (Exception e) {
            nyeAvailable = false;
            plugin.getLogger().warning("NyEconomy 初始化失败: " + e.getMessage());
        }
    }

    public boolean hasVault() {
        return plugin.getVaultEconomy() != null;
    }

    public boolean hasNYE() {
        return nyeAvailable;
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
            return String.format("%.0f %s", amount, nyeCurrencyName != null ? nyeCurrencyName : "鸽币");
        }
        if (currencyType.equalsIgnoreCase("vault") && hasVault()) {
            return plugin.getVaultEconomy().format(amount);
        }
        return String.format("%.2f", amount);
    }

    public String getCurrencyName(String currencyType) {
        if (currencyType.equalsIgnoreCase("nye")) {
            return nyeCurrencyName != null ? nyeCurrencyName : "鸽币";
        }
        if (currencyType.equalsIgnoreCase("vault")) {
            return plugin.getConfig().getString("currency.vault-currency-name", "金币");
        }
        return "货币";
    }

    // NyEconomy货币操作方法 (通过反射调用 mc9y/NyEconomy)
    // API: Main.getNyEconomyAPI() 返回 NyEconomyAPI 实例
    // 方法: getBalance(String type, UUID uuid) -> int
    //       withdraw(String type, UUID uuid, int amount)
    //       deposit(String type, UUID uuid, int amount)
    private double getNYEBalance(UUID playerUuid) {
        try {
            Object result = nyeGetBalance.invoke(nyeApi, nyeCurrencyName, playerUuid);
            return result != null ? ((Number) result).doubleValue() : 0;
        } catch (Exception e) {
            plugin.getLogger().warning("NyEconomy getBalance 失败: " + e.getMessage());
            return 0;
        }
    }

    private boolean withdrawNYE(UUID playerUuid, double amount) {
        try {
            double balance = getNYEBalance(playerUuid);
            if (balance < amount) return false;
            nyeWithdraw.invoke(nyeApi, nyeCurrencyName, playerUuid, (int) amount);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("NyEconomy withdraw 失败: " + e.getMessage());
            return false;
        }
    }

    private boolean depositNYE(UUID playerUuid, double amount) {
        try {
            nyeDeposit.invoke(nyeApi, nyeCurrencyName, playerUuid, (int) amount);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("NyEconomy deposit 失败: " + e.getMessage());
            return false;
        }
    }

    public void sendBalanceHint(Player player, String currencyType) {
        double balance = getBalance(player, currencyType);
        plugin.getMessageManager().sendRaw(player,
            "&8当前余额: &7" + formatCurrency(balance, currencyType));
    }

    public double calculateTax(double amount, String stallType) {
        double taxRate = plugin.getConfigManager().getTaxRate(stallType) / 100.0;
        return amount * taxRate;
    }

    public double getAfterTaxAmount(double amount, String stallType) {
        return amount - calculateTax(amount, stallType);
    }
}

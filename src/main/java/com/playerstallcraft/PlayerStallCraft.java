package com.playerstallcraft;

import com.playerstallcraft.commands.BaitanCommand;
import com.playerstallcraft.commands.PriceCommand;
import com.playerstallcraft.database.DatabaseManager;
import com.playerstallcraft.listeners.PlayerListener;
import com.playerstallcraft.listeners.RegionSelectListener;
import com.playerstallcraft.managers.ConfigManager;
import com.playerstallcraft.managers.MessageManager;
import com.playerstallcraft.managers.RegionManager;
import com.playerstallcraft.managers.StallManager;
import com.playerstallcraft.managers.EconomyManager;
import com.playerstallcraft.managers.LicenseManager;
import com.playerstallcraft.managers.MarketManager;
import com.playerstallcraft.managers.GlobalMarketManager;
import com.playerstallcraft.hologram.HologramManager;
import com.playerstallcraft.npc.StallNPCManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerStallCraft extends JavaPlugin {

    private static PlayerStallCraft instance;
    private DatabaseManager databaseManager;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private RegionManager regionManager;
    private StallManager stallManager;
    private EconomyManager economyManager;
    private HologramManager hologramManager;
    private LicenseManager licenseManager;
    private MarketManager marketManager;
    private GlobalMarketManager globalMarketManager;
    private StallNPCManager stallNPCManager;
    private Economy vaultEconomy;

    @Override
    public void onEnable() {
        instance = this;

        // 保存默认配置
        saveDefaultConfig();
        saveResource("messages.yml", false);

        // 初始化管理器
        this.configManager = new ConfigManager(this);
        this.messageManager = new MessageManager(this);

        // 初始化数据库
        this.databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("数据库初始化失败! 插件将被禁用.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 初始化经济系统
        if (!setupVaultEconomy()) {
            getLogger().warning("未找到Vault经济插件, 部分功能可能不可用!");
        }
        this.economyManager = new EconomyManager(this);

        // 初始化其他管理器
        this.regionManager = new RegionManager(this);
        this.hologramManager = new HologramManager(this);
        this.licenseManager = new LicenseManager(this);
        this.marketManager = new MarketManager(this);
        this.globalMarketManager = new GlobalMarketManager(this);
        this.stallNPCManager = new StallNPCManager(this);
        this.stallManager = new StallManager(this);

        // 注册命令
        getCommand("baitan").setExecutor(new BaitanCommand(this));
        getCommand("price").setExecutor(new PriceCommand(this));

        // 注册监听器
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new RegionSelectListener(this), this);

        getLogger().info("PlayerStallCraft 已启用!");
    }

    @Override
    public void onDisable() {
        // 关闭所有摊位
        if (stallManager != null) {
            stallManager.closeAllStalls();
        }

        // 移除所有全息显示
        if (hologramManager != null) {
            hologramManager.removeAllHolograms();
        }

        // 关闭数据库连接
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("PlayerStallCraft 已禁用!");
    }

    private boolean setupVaultEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        vaultEconomy = rsp.getProvider();
        return vaultEconomy != null;
    }

    public static PlayerStallCraft getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public RegionManager getRegionManager() {
        return regionManager;
    }

    public StallManager getStallManager() {
        return stallManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public Economy getVaultEconomy() {
        return vaultEconomy;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public LicenseManager getLicenseManager() {
        return licenseManager;
    }

    public MarketManager getMarketManager() {
        return marketManager;
    }

    public GlobalMarketManager getGlobalMarketManager() {
        return globalMarketManager;
    }

    public StallNPCManager getStallNPCManager() {
        return stallNPCManager;
    }
}

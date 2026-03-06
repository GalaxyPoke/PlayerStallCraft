package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 物品命名工具类（保留原名以兼容其他模块调用）。全球市场交易功能已移除。 */
public class GlobalMarketManager {

    private final PlayerStallCraft plugin;
    private final Map<String, String> itemNamesZh = new HashMap<>();

    public GlobalMarketManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
    }

    public void loadItemNames() {
        itemNamesZh.clear();
        int count = 0;
        try {
            java.io.InputStream is = plugin.getResource("item_names_zh.yml");
            if (is != null) {
                org.bukkit.configuration.file.YamlConfiguration config =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                for (String key : config.getKeys(false)) { itemNamesZh.put(key, config.getString(key)); count++; }
            }
        } catch (Exception e) { plugin.getLogger().warning("加载原版物品中文名失败: " + e.getMessage()); }
        try {
            java.io.InputStream is = plugin.getResource("cobblemon_items_zh.yml");
            if (is != null) {
                org.bukkit.configuration.file.YamlConfiguration config =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8));
                for (String key : config.getKeys(false)) { itemNamesZh.put(key, config.getString(key)); count++; }
            }
        } catch (Exception e) { plugin.getLogger().warning("加载Cobblemon物品中文名失败: " + e.getMessage()); }
        plugin.getLogger().info("已加载 " + count + " 个物品中文名");
    }

    public String getChineseNamePublic(Material material) {
        try {
            String nsKey = material.getKey().toString();
            String r = itemNamesZh.get(nsKey);
            if (r != null) return r;
        } catch (Exception ignored) {}
        String name = material.name();
        String result = itemNamesZh.get(name);
        if (result != null) return result;
        if (name.startsWith("COBBLEMON_")) {
            String nsKey = "cobblemon:" + name.substring("COBBLEMON_".length()).toLowerCase();
            result = itemNamesZh.get(nsKey);
        }
        return result;
    }

    /** 全服市场已移除，始终返回空列表 */
    public List<GlobalMarketItem> getAllActiveListings() {
        return Collections.emptyList();
    }

    /** 全服市场已移除，始终返回空列表 */
    public List<GlobalMarketItem> searchByMaterial(Material material) {
        return Collections.emptyList();
    }

    /** 保留内部类以兼容引用 GlobalMarketItem 的其他模块 */
    public static class GlobalMarketItem {
        private final int id;
        private final UUID sellerUuid;
        private final String sellerName;
        private final String itemData;
        private final int amount;
        private final double price;
        private final String currencyType;
        private final long expireTime;
        private String itemName = "";
        private String itemType = "";

        public GlobalMarketItem(int id, UUID sellerUuid, String sellerName, String itemData,
                                int amount, double price, String currencyType, long expireTime) {
            this.id = id; this.sellerUuid = sellerUuid; this.sellerName = sellerName;
            this.itemData = itemData; this.amount = amount; this.price = price;
            this.currencyType = currencyType; this.expireTime = expireTime;
        }

        public int getId() { return id; }
        public UUID getSellerUuid() { return sellerUuid; }
        public String getSellerName() { return sellerName; }
        public String getItemData() { return itemData; }
        public int getAmount() { return amount; }
        public double getPrice() { return price; }
        public String getCurrencyType() { return currencyType; }
        public long getExpireTime() { return expireTime; }
        public boolean isActive() { return System.currentTimeMillis() < expireTime; }
        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public String getItemType() { return itemType; }
        public void setItemType(String itemType) { this.itemType = itemType; }
    }
}
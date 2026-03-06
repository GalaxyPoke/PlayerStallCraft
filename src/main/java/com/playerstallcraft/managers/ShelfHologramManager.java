package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.Shop;
import com.playerstallcraft.models.ShopShelf;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 货架全息图管理器
 * 负责在货架上方显示轮播商品信息
 */
public class ShelfHologramManager {

    private final PlayerStallCraft plugin;
    private final Map<Integer, Hologram> shelfHolograms; // shelfId -> hologram
    private boolean decentHologramsChecked = false;
    private boolean useDecentHolograms = false;
    private int rotationTaskId = -1;

    public ShelfHologramManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        this.shelfHolograms = new ConcurrentHashMap<>();
    }
    
    /**
     * 延迟检测DecentHolograms插件（在onEnable后调用）
     */
    public void init() {
        this.useDecentHolograms = plugin.getServer().getPluginManager().getPlugin("DecentHolograms") != null;
        this.decentHologramsChecked = true;
        
        if (useDecentHolograms) {
            plugin.getLogger().info("DecentHolograms 已检测到，启用全息图功能");
            startRotationTask();
        } else {
            plugin.getLogger().warning("未检测到 DecentHolograms 插件，全息图功能将不可用");
        }
    }

    private void startRotationTask() {
        if (rotationTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(rotationTaskId);
        }
        long intervalTicks = plugin.getConfig().getLong("shop.shelf.hologram-rotate-interval", 100L);
        rotationTaskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Shop shop : plugin.getShopManager().getAllShops()) {
                for (ShopShelf shelf : shop.getShelves().values()) {
                    if (shelf.getItemCount() > 1 && shelf.getLocation() != null) {
                        shelf.getNextDisplayItem();
                        updateHologram(shelf);
                    }
                }
            }
        }, intervalTicks, intervalTicks).getTaskId();
    }
    
    private boolean isDecentHologramsAvailable() {
        if (!decentHologramsChecked) {
            init();
        }
        return useDecentHolograms;
    }


    /**
     * 为货架创建全息图
     */
    public void createHologram(ShopShelf shelf) {
        if (!isDecentHologramsAvailable()) return;
        if (shelf.getLocation() == null) return;

        String holoName = "shelf_" + shelf.getId();
        
        // 删除可能存在的旧全息图
        try {
            if (DHAPI.getHologram(holoName) != null) {
                DHAPI.removeHologram(holoName);
            }
        } catch (Exception ignored) {}

        double heightOffset = plugin.getConfig().getDouble("shop.shelf.hologram-height", 2.0);
        Location holoLoc = shelf.getLocation().clone().add(0.5, heightOffset, 0.5);

        List<String> lines = buildHologramLines(shelf);
        
        try {
            Hologram hologram = DHAPI.createHologram(holoName, holoLoc, lines);
            shelfHolograms.put(shelf.getId(), hologram);
        } catch (Exception e) {
            plugin.getLogger().warning("创建全息图失败: " + e.getMessage());
        }
    }

    /**
     * 更新货架全息图（原地更新，避免闪烁）
     */
    public void updateHologram(ShopShelf shelf) {
        if (!isDecentHologramsAvailable()) return;

        String holoName = "shelf_" + shelf.getId();
        List<String> lines = buildHologramLines(shelf);
        try {
            Hologram existing = DHAPI.getHologram(holoName);
            if (existing != null) {
                DHAPI.setHologramLines(existing, lines);
                return;
            }
        } catch (Exception ignored) {}
        // 不存在时才创建
        createHologram(shelf);
    }

    /**
     * 删除货架全息图
     */
    public void removeHologram(int shelfId) {
        if (!isDecentHologramsAvailable()) return;

        String holoName = "shelf_" + shelfId;
        try {
            if (DHAPI.getHologram(holoName) != null) {
                DHAPI.removeHologram(holoName);
            }
        } catch (Exception ignored) {}
        shelfHolograms.remove(shelfId);
    }


    /**
     * 构建全息图内容
     */
    private List<String> buildHologramLines(ShopShelf shelf) {
        List<String> lines = new ArrayList<>();

        if (shelf.isEmpty()) {
            lines.add("&7═══════════════");
            lines.add("&e" + shelf.getDisplayName());
            lines.add("&7暂无商品");
            lines.add("&7═══════════════");
            return lines;
        }

        ShopShelf.ShelfItem currentItem = shelf.getCurrentDisplayItem();
        int currentSlot = shelf.getCurrentDisplaySlot();
        
        if (currentItem == null) {
            lines.add("&7暂无商品");
            return lines;
        }

        // 优先查中文名，找不到时才用 getItemName()
        String itemName;
        String zhName = plugin.getGlobalMarketManager().getChineseNamePublic(currentItem.getItemStack().getType());
        if (zhName != null && !zhName.isEmpty()) {
            itemName = zhName;
        } else {
            itemName = currentItem.getItemName();
        }
        String currencyName = plugin.getConfig().getString("currency.nye-currency-name", "鸽币");

        lines.add("&7═══════════════");
        lines.add("&e" + shelf.getDisplayName());
        lines.add("&7───────────────");
        
        // 商品图标
        String iconLine = "#ICON:" + currentItem.getItemStack().getType().name();
        lines.add(iconLine);
        
        // 商品名称
        lines.add("&f" + itemName);
        
        // 价格
        lines.add("&6价格: &e" + String.format("%.1f", currentItem.getPrice()) + " " + currencyName);
        
        // 库存
        if (currentItem.getStock() > 0) {
            lines.add("&a库存: &f" + currentItem.getStock());
        } else {
            lines.add("&c已售罄");
        }
        
        // 商品索引提示
        lines.add("&8[" + (currentSlot + 1) + "/" + shelf.getItemCount() + "]");
        
        lines.add("&7───────────────");
        lines.add("&e右键点击购买");
        lines.add("&7═══════════════");

        return lines;
    }

    /**
     * 为商铺的所有货架创建全息图
     */
    public void createShopHolograms(Shop shop) {
        for (ShopShelf shelf : shop.getShelves().values()) {
            createHologram(shelf);
        }
    }

    /**
     * 删除商铺的所有货架全息图
     */
    public void removeShopHolograms(Shop shop) {
        for (ShopShelf shelf : shop.getShelves().values()) {
            removeHologram(shelf.getId());
        }
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        if (rotationTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(rotationTaskId);
            rotationTaskId = -1;
        }
        // 删除所有全息图
        for (Integer shelfId : new ArrayList<>(shelfHolograms.keySet())) {
            removeHologram(shelfId);
        }
        shelfHolograms.clear();
    }

    /**
     * 重新加载所有货架全息图
     */
    public void reloadAll() {
        if (!isDecentHologramsAvailable()) {
            plugin.getLogger().warning("DecentHolograms 未启用，跳过全息图加载");
            return;
        }
        
        // 先清除所有
        for (Integer shelfId : new ArrayList<>(shelfHolograms.keySet())) {
            removeHologram(shelfId);
        }
        
        // 重新创建所有有货架的商铺
        int count = 0;
        for (Shop shop : plugin.getShopManager().getAllShops()) {
            for (ShopShelf shelf : shop.getShelves().values()) {
                if (shelf.getLocation() != null) {
                    createHologram(shelf);
                    count++;
                }
            }
        }
        plugin.getLogger().info("已为 " + count + " 个货架创建全息图");
    }
}

package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.Shop;
import com.playerstallcraft.models.ShopShelf;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShopManageGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private final Shop shop;
    private Inventory inventory;
    private final Map<Integer, ShelfSlotInfo> shelfSlotMap = new java.util.HashMap<>();

    public ShopManageGUI(PlayerStallCraft plugin, Player player, Shop shop) {
        this.plugin = plugin;
        this.player = player;
        this.shop = shop;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 54, "§b管理商铺: " + shop.getName());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshDisplay();
        player.openInventory(inventory);
    }

    private void refreshDisplay() {
        inventory.clear();
        shelfSlotMap.clear();
        
        // 显示货架槽位 (从第一行开始, 0-44)
        displayShelfSlots();
        
        // 底部控制栏
        // 商铺信息
        ItemStack shopInfo = createItem(Material.BOOK, "§6商铺信息",
            "§7名称: §f" + shop.getName(),
            "§7状态: " + (shop.isOwned() ? "§b已购买" : "§e租赁中"),
            shop.isRented() ? "§7剩余租期: §f" + shop.getRemainingRentTime() : "",
            "§7货架: §f" + shop.getShelves().size() + "/" + shop.getUnlockedShelfSlots());
        inventory.setItem(45, shopInfo);
        
        // 续租 (仅租赁商铺)
        if (shop.isRented()) {
            ItemStack renewItem = createItem(Material.CLOCK, "§e续租",
                "§7当前剩余: §f" + shop.getRemainingRentTime(),
                "§a点击续租商铺");
            inventory.setItem(49, renewItem);
        }
        
        // 传送到商铺
        ItemStack tpItem = createItem(Material.ENDER_PEARL, "§d传送到商铺",
            "§7点击传送到商铺位置");
        inventory.setItem(51, tpItem);
        
        // 交易统计
        inventory.setItem(47, createItem(Material.GOLD_NUGGET, "§6交易统计",
            "§7查看本店销售额、成交数",
            "§7及畅销商品排行",
            "",
            "§e点击打开统计看板"));
        
        // 返回
        inventory.setItem(52, createItem(Material.ARROW, "§e返回", "§7返回商铺列表"));
        
        // 关闭
        inventory.setItem(53, createItem(Material.BARRIER, "§c关闭", "§7关闭界面"));
    }

    private void displayShelfSlots() {
        int maxSlots = plugin.getConfig().getInt("shop.max-shelf-slots", 10);
        int unlockedSlots = shop.getUnlockedShelfSlots();
        double unlockPrice = plugin.getConfig().getDouble("shop.shelf-unlock-price", 5000);
        
        int slot = 0;  // 从第一个槽位开始
        int shelfIndex = 0;
        
        // 显示已创建的货架
        for (ShopShelf shelf : shop.getShelves().values()) {
            if (slot >= 45) break;  // 留出底部控制栏
            inventory.setItem(slot, createShelfDisplayItem(shelf));
            shelfSlotMap.put(slot, new ShelfSlotInfo(ShelfSlotType.SHELF, shelf.getId()));
            slot++;
            shelfIndex++;
        }
        
        // 显示空货架位 (已解锁但未创建)
        while (shelfIndex < unlockedSlots && slot < 45) {
            inventory.setItem(slot, createEmptySlotItem());
            shelfSlotMap.put(slot, new ShelfSlotInfo(ShelfSlotType.EMPTY, -1));
            slot++;
            shelfIndex++;
        }
        
        // 显示未解锁的货架位
        while (shelfIndex < maxSlots && slot < 45) {
            inventory.setItem(slot, createLockedSlotItem(unlockPrice));
            shelfSlotMap.put(slot, new ShelfSlotInfo(ShelfSlotType.LOCKED, -1));
            slot++;
            shelfIndex++;
        }
    }

    private ItemStack createShelfDisplayItem(ShopShelf shelf) {
        Material material = shelf.isEmpty() ? Material.CHEST : Material.ENDER_CHEST;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e货架 #" + shelf.getId());
            List<String> lore = new ArrayList<>();
            lore.add("§7───────────────");
            lore.add("§f商品数量: §e" + shelf.getItemCount() + "/" + ShopShelf.MAX_SLOTS);
            if (!shelf.isEmpty()) {
                lore.add("");
                lore.add("§f商品列表:");
                int count = 0;
                for (ShopShelf.ShelfItem shelfItem : shelf.getItems().values()) {
                    if (count >= 3) {
                        lore.add("§8... 还有 " + (shelf.getItemCount() - 3) + " 件");
                        break;
                    }
                    String sName = plugin.getGlobalMarketManager().getChineseNamePublic(shelfItem.getItemStack().getType());
                    if (sName == null || sName.isEmpty()) sName = shelfItem.getItemName();
                    lore.add("§7- §f" + sName + " §7x" + shelfItem.getStock());
                    count++;
                }
            }
            lore.add("§7───────────────");
            lore.add("§a左键 §7- 管理商品");
            lore.add("§bShift+右键 §7- 传送到货架");
            lore.add("§c右键 §7- 删除货架");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createEmptySlotItem() {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a空货架位");
            List<String> lore = new ArrayList<>();
            lore.add("§7此位置已解锁");
            lore.add("");
            lore.add("§e点击放置货架");
            lore.add("§7(需要站在商铺区域内)");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createLockedSlotItem(double price) {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c未解锁");
            String currencyName = plugin.getConfig().getString("currency.nye-currency-name", "鸽币");
            List<String> lore = new ArrayList<>();
            lore.add("§7此位置需要解锁");
            lore.add("");
            lore.add("§6解锁价格: §e" + String.format("%.0f", price) + " " + currencyName);
            lore.add("");
            lore.add("§e点击解锁");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            for (String line : loreLines) {
                if (!line.isEmpty()) lore.add(line);
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;
        
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        
        // 货架槽位区域 (0-44)
        if (slot < 45) {
            ShelfSlotInfo info = shelfSlotMap.get(slot);
            if (info != null) {
                handleShelfSlotClick(info, event.getClick());
            }
            return;
        }
        
        switch (slot) {
            case 49 -> {
                if (shop.isRented()) {
                    new ShopRentGUI(plugin, player, shop).open();
                }
            }
            case 51 -> {
                player.teleport(shop.getLocation());
                player.closeInventory();
                plugin.getMessageManager().sendRaw(player, "&a已传送到商铺!");
            }
            case 47 -> new ShopStatsGUI(plugin, player).open();
            case 52 -> new ShopListGUI(plugin, player).open();
            case 53 -> player.closeInventory();
        }
    }

    private void handleShelfSlotClick(ShelfSlotInfo info, ClickType click) {
        switch (info.type) {
            case SHELF -> {
                ShopShelf shelf = shop.getShelf(info.shelfId);
                if (shelf != null) {
                    if (click == ClickType.RIGHT) {
                        // 删除货架
                        new ConfirmDeleteGUI(plugin, player, "删除货架 #" + shelf.getId(), () -> {
                            // 移除货架方块
                            if (shelf.getLocation() != null) {
                                org.bukkit.block.Block block = shelf.getLocation().getBlock();
                                if (block.getType() == Material.BARREL) {
                                    block.setType(Material.AIR);
                                }
                            }
                            shop.removeShelf(shelf.getId());
                            plugin.getShopManager().deleteShelf(shelf.getId());
                            plugin.getShelfHologramManager().removeHologram(shelf.getId());
                            plugin.getMessageManager().sendRaw(player, "&a已删除货架 #" + shelf.getId());
                            new ShopManageGUI(plugin, player, shop).open();
                        }, () -> new ShopManageGUI(plugin, player, shop).open()).open();
                    } else if (click == ClickType.SHIFT_RIGHT) {
                        // 传送到货架
                        if (shelf.getLocation() != null) {
                            player.closeInventory();
                            player.teleport(shelf.getLocation().clone().add(0.5, 1, 0.5));
                            plugin.getMessageManager().sendRaw(player, "&a已传送到货架 #" + shelf.getId());
                        } else {
                            plugin.getMessageManager().sendRaw(player, "&c货架位置无效!");
                        }
                    } else {
                        // 管理商品
                        new ShelfItemsGUI(plugin, player, shop, shelf).open();
                    }
                }
            }
            case EMPTY -> {
                // 创建新货架
                handleCreateShelf();
            }
            case LOCKED -> {
                // 解锁货架位
                handleUnlockSlot();
            }
        }
    }

    private void handleCreateShelf() {
        if (!shop.canAddMoreShelves()) {
            plugin.getMessageManager().sendRaw(player, "&c货架位已满，请先解锁更多位置!");
            return;
        }
        
        if (!shop.hasRegion()) {
            plugin.getMessageManager().sendRaw(player, "&c该商铺尚未设置区域! 请联系管理员设置商铺区域。");
            return;
        }
        
        if (!shop.isInRegion(player.getLocation())) {
            plugin.getMessageManager().sendRaw(player, "&c请先站在商铺区域内再创建货架!");
            return;
        }
        
        // 获取玩家脚下的方块位置
        org.bukkit.Location shelfLoc = player.getLocation().getBlock().getLocation();
        
        // 检查该位置是否可以放置货架方块
        org.bukkit.block.Block targetBlock = shelfLoc.getBlock();
        if (!targetBlock.getType().isAir() && targetBlock.getType() != Material.WATER) {
            plugin.getMessageManager().sendRaw(player, "&c该位置已有方块，请站在空地上!");
            return;
        }
        
        int newId = generateShelfId();
        ShopShelf shelf = new ShopShelf(newId, shop.getId(), shelfLoc);
        
        if (shop.addShelf(shelf)) {
            // 放置货架方块 (使用木桶作为货架外观)
            targetBlock.setType(Material.BARREL);
            
            plugin.getShopManager().saveShelf(shelf);
            plugin.getShelfHologramManager().createHologram(shelf);
            plugin.getMessageManager().sendRaw(player, "&a成功创建货架 #" + newId + " &7(木桶已放置)");
            refreshDisplay();
        } else {
            plugin.getMessageManager().sendRaw(player, "&c创建货架失败!");
        }
    }

    private void handleUnlockSlot() {
        int maxSlots = plugin.getConfig().getInt("shop.max-shelf-slots", 10);
        if (shop.getUnlockedShelfSlots() >= maxSlots) {
            plugin.getMessageManager().sendRaw(player, "&c已达到最大货架数量!");
            return;
        }
        
        double price = plugin.getConfig().getDouble("shop.shelf-unlock-price", 5000);
        String currencyName = plugin.getConfig().getString("currency.nye-currency-name", "鸽币");
        
        if (plugin.getEconomyManager().withdraw(player, price, "nye")) {
            shop.unlockShelfSlot();
            plugin.getShopManager().saveShopUnlockedSlots(shop);
            plugin.getMessageManager().sendRaw(player, "&a成功解锁新货架位! &7(-" + String.format("%.0f", price) + " " + currencyName + ")");
            refreshDisplay();
        } else {
            plugin.getMessageManager().sendRaw(player, "&c余额不足! 需要 " + String.format("%.0f", price) + " " + currencyName);
        }
    }

    private int generateShelfId() {
        int maxId = 0;
        for (ShopShelf shelf : shop.getShelves().values()) {
            if (shelf.getId() > maxId) {
                maxId = shelf.getId();
            }
        }
        return maxId + 1;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;
        
        HandlerList.unregisterAll(this);
    }

    // 货架槽位类型
    private enum ShelfSlotType {
        SHELF, EMPTY, LOCKED
    }

    // 货架槽位信息
    private record ShelfSlotInfo(ShelfSlotType type, int shelfId) {}
}

package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class ShopCreateGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private final Location pos1;
    private final Location pos2;
    private Inventory inventory;
    
    private String shopName = "";
    private int shelfCount = 1;
    private boolean waitingForNameInput = false;
    private boolean isCreating = false;

    public ShopCreateGUI(PlayerStallCraft plugin, Player player, Location pos1, Location pos2) {
        this.plugin = plugin;
        this.player = player;
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    public void open() {
        // 先注销之前可能存在的监听器，防止重复注册
        HandlerList.unregisterAll(this);
        
        inventory = Bukkit.createInventory(null, 45, "§b创建商铺");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        refreshDisplay();
        player.openInventory(inventory);
    }

    private void refreshDisplay() {
        inventory.clear();
        
        // 填充背景
        ItemStack bgItem = createItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, bgItem);
        }

        // 计算选区信息
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        // 区域预览
        inventory.setItem(4, createItem(Material.MAP, "§e商铺区域预览",
            "§7═══════════════════",
            "§7世界: §f" + pos1.getWorld().getName(),
            "§7范围: §f(" + minX + "," + minY + "," + minZ + ")",
            "§7      §f(" + maxX + "," + maxY + "," + maxZ + ")",
            "§7大小: §f" + (maxX - minX + 1) + " x " + (maxY - minY + 1) + " x " + (maxZ - minZ + 1),
            "§7═══════════════════"
        ));

        // 商铺名称设置
        String displayName = shopName.isEmpty() ? "§c未设置" : "§a" + shopName;
        inventory.setItem(20, createItem(Material.NAME_TAG, "§6商铺名称: " + displayName,
            "§7═══════════════════",
            "§f点击设置商铺名称",
            "",
            "§7当前: " + displayName,
            "§7═══════════════════",
            "",
            "§e点击修改"
        ));

        // 货架数量设置
        inventory.setItem(22, createItem(Material.CHEST, "§6货架数量: §a" + shelfCount,
            "§7═══════════════════",
            "§f每个货架可存放27件商品",
            "",
            "§7当前货架: §f" + shelfCount,
            "§7可容纳: §f" + (shelfCount * 27) + " 件商品",
            "§7═══════════════════",
            "",
            "§a左键 +1  §c右键 -1",
            "§aShift+左键 +5  §cShift+右键 -5",
            "§bShift+右键 +10"
        ));

        // 价格信息
        double rentPrice = plugin.getConfigManager().getConfig().getDouble("shop.rent-per-day", 500);
        double buyPrice = plugin.getConfigManager().getConfig().getDouble("shop.buy-price", 100000);
        inventory.setItem(24, createItem(Material.GOLD_INGOT, "§6价格信息",
            "§7═══════════════════",
            "§7租金: §f" + plugin.getEconomyManager().formatCurrency(rentPrice, "vault") + "/天",
            "§7购买价: §f" + plugin.getEconomyManager().formatCurrency(buyPrice, "vault"),
            "§7═══════════════════",
            "",
            "§7价格在config.yml中配置"
        ));

        // 确认创建按钮
        Material confirmMaterial = shopName.isEmpty() ? Material.GRAY_CONCRETE : Material.LIME_CONCRETE;
        String confirmStatus = shopName.isEmpty() ? "§c请先设置商铺名称" : "§a点击确认创建";
        inventory.setItem(40, createItem(confirmMaterial, "§a确认创建",
            "§7═══════════════════",
            "§7商铺名称: " + displayName,
            "§7货架数量: §f" + shelfCount,
            "§7═══════════════════",
            "",
            confirmStatus
        ));

        // 返回按钮
        inventory.setItem(36, createItem(Material.ARROW, "§e返回",
            "§7返回选择区域类型"
        ));

        // 取消按钮
        inventory.setItem(44, createItem(Material.BARRIER, "§c取消",
            "§7取消创建商铺"
        ));
    }

    private ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines.length > 0) {
                meta.setLore(Arrays.asList(loreLines));
            }
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
        if (slot < 0 || slot >= 45) return;

        switch (slot) {
            case 20 -> {
                // 设置商铺名称
                waitingForNameInput = true;
                player.closeInventory();
                plugin.getMessageManager().sendRaw(player, "§a请在聊天栏输入商铺名称 §7(输入 cancel 取消)");
            }
            case 22 -> {
                // 调整货架数量
                if (event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                    shelfCount = Math.min(10, shelfCount + 10);
                } else {
                    int change = event.isShiftClick() ? 5 : 1;
                    if (event.isLeftClick()) {
                        shelfCount = Math.min(10, shelfCount + change);
                    } else if (event.isRightClick()) {
                        shelfCount = Math.max(1, shelfCount - change);
                    }
                }
                refreshDisplay();
            }
            case 36 -> {
                // 返回
                player.closeInventory();
                new RegionTypeSelectGUI(plugin, player, pos1, pos2).open();
            }
            case 40 -> {
                // 确认创建
                if (shopName.isEmpty()) {
                    plugin.getMessageManager().sendRaw(player, "§c请先设置商铺名称!");
                    return;
                }
                if (isCreating) return;
                isCreating = true;
                createShop();
            }
            case 44 -> {
                // 取消
                player.closeInventory();
                plugin.getMessageManager().sendRaw(player, "§e已取消创建商铺");
            }
        }
    }

    private void createShop() {
        // 计算商铺位置（区域中心点）
        double centerX = (pos1.getX() + pos2.getX()) / 2;
        double centerY = Math.min(pos1.getY(), pos2.getY());
        double centerZ = (pos1.getZ() + pos2.getZ()) / 2;
        Location shopLoc = new Location(pos1.getWorld(), centerX, centerY, centerZ);

        // 创建商铺
        if (plugin.getShopManager().createShop(shopName, shopLoc, shelfCount)) {
            // 获取刚创建的商铺ID
            int shopId = plugin.getShopManager().getAllShops().stream()
                .filter(s -> s.getName().equals(shopName))
                .mapToInt(s -> s.getId())
                .max()
                .orElse(-1);

            if (shopId > 0) {
                // 设置商铺区域
                Location min = new Location(pos1.getWorld(),
                    Math.min(pos1.getX(), pos2.getX()),
                    Math.min(pos1.getY(), pos2.getY()),
                    Math.min(pos1.getZ(), pos2.getZ()));
                Location max = new Location(pos1.getWorld(),
                    Math.max(pos1.getX(), pos2.getX()),
                    Math.max(pos1.getY(), pos2.getY()),
                    Math.max(pos1.getZ(), pos2.getZ()));
                
                plugin.getShopManager().setShopRegion(shopId, min, max);
            }

            player.closeInventory();
            plugin.getMessageManager().sendRaw(player, "§a成功创建商铺: §e" + shopName);
            plugin.getMessageManager().sendRaw(player, "§7货架数量: §f" + shelfCount + " (可容纳 " + (shelfCount * 27) + " 件商品)");
            plugin.getMessageManager().sendRaw(player, "§7商铺ID: §f" + shopId);
        } else {
            plugin.getMessageManager().sendRaw(player, "§c创建商铺失败!");
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (!waitingForNameInput) return;

        event.setCancelled(true);
        waitingForNameInput = false;
        String input = event.getMessage().trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (input.equalsIgnoreCase("cancel")) {
                plugin.getMessageManager().sendRaw(player, "§e已取消输入");
                open();
                return;
            }

            shopName = input;
            plugin.getMessageManager().sendRaw(player, "§a商铺名称已设置为: §e" + shopName);
            open();
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;

        if (!waitingForNameInput) {
            HandlerList.unregisterAll(this);
        }
    }
}

package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.Shop;
import com.playerstallcraft.models.StallRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import java.util.Arrays;
import java.util.List;

public class RegionManageGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private Inventory inventory;
    private int currentPage = 0;
    private boolean showStallRegions = true; // true=摆摊区域, false=商铺
    private static final int ITEMS_PER_PAGE = 36;

    public RegionManageGUI(PlayerStallCraft plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 54, "§6区域管理 - " + (showStallRegions ? "摆摊区域" : "商铺"));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshDisplay();
        player.openInventory(inventory);
    }

    private void refreshDisplay() {
        inventory.clear();

        // 填充底部背景
        ItemStack bgItem = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 36; i < 54; i++) {
            inventory.setItem(i, bgItem);
        }

        // 获取区域列表
        List<Object> regions = new ArrayList<>();
        if (showStallRegions) {
            regions.addAll(plugin.getRegionManager().getRegions().values());
        } else {
            regions.addAll(plugin.getShopManager().getAllShops());
        }

        // 分页显示
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, regions.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            Object region = regions.get(i);

            if (region instanceof StallRegion stallRegion) {
                inventory.setItem(slot, createStallRegionItem(stallRegion));
            } else if (region instanceof Shop shop) {
                inventory.setItem(slot, createShopItem(shop));
            }
        }

        // 切换类型按钮
        inventory.setItem(45, createItem(
            showStallRegions ? Material.CAMPFIRE : Material.BARREL,
            showStallRegions ? "§6当前: 摆摊区域" : "§b当前: 商铺",
            "§7点击切换查看类型",
            "",
            showStallRegions ? "§e点击查看商铺" : "§e点击查看摆摊区域"
        ));

        // 创建新区域按钮
        inventory.setItem(47, createItem(Material.LIME_CONCRETE, "§a创建新区域",
            "§7使用下界合金锄圈地后",
            "§7点击此处打开创建界面",
            "",
            "§e点击创建"
        ));

        // 分页信息
        int totalPages = (int) Math.ceil(regions.size() / (double) ITEMS_PER_PAGE);
        inventory.setItem(49, createItem(Material.PAPER, "§e第 " + (currentPage + 1) + "/" + Math.max(1, totalPages) + " 页",
            "§7共 " + regions.size() + " 个" + (showStallRegions ? "摆摊区域" : "商铺")
        ));

        // 上一页
        if (currentPage > 0) {
            inventory.setItem(48, createItem(Material.ARROW, "§e上一页"));
        }

        // 下一页
        if (endIndex < regions.size()) {
            inventory.setItem(50, createItem(Material.ARROW, "§e下一页"));
        }

        // 关闭按钮
        inventory.setItem(53, createItem(Material.BARRIER, "§c关闭"));
    }

    private ItemStack createStallRegionItem(StallRegion region) {
        return createItem(Material.CAMPFIRE, "§6" + region.getName(),
            "§7═══════════════════",
            "§7ID: §f" + region.getId(),
            "§7世界: §f" + region.getWorldName(),
            "§7范围: §f(" + region.getX1() + "," + region.getY1() + "," + region.getZ1() + ")",
            "§7      §f(" + region.getX2() + "," + region.getY2() + "," + region.getZ2() + ")",
            "§7大小: §f" + getRegionSize(region) + " 方块",
            "§7═══════════════════",
            "",
            "§a左键/Shift+右键 §7- 传送到区域",
            "§c右键 §7- 删除区域",
            "§eShift+左键 §7- 显示边框粒子"
        );
    }

    private ItemStack createShopItem(Shop shop) {
        String status = shop.isOwned() ? "§a已购买" : (shop.isRented() ? "§e已租赁" : "§7空置");
        String owner = shop.hasOwner() ? shop.getOwnerName() : "无";
        
        return createItem(Material.BARREL, "§b" + shop.getName(),
            "§7═══════════════════",
            "§7ID: §f" + shop.getId(),
            "§7状态: " + status,
            "§7所有者: §f" + owner,
            "§7位置: §f" + formatLocation(shop.getLocation()),
            "§7货架: §f" + shop.getShelfCount(),
            shop.hasRegion() ? "§7区域: §a已设置" : "§7区域: §c未设置",
            "§7═══════════════════",
            "",
            "§a左键/Shift+右键 §7- 传送到商铺",
            "§c右键 §7- 删除商铺",
            "§eShift+左键 §7- 显示边框粒子"
        );
    }

    private String formatLocation(Location loc) {
        if (loc == null) return "未知";
        return String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private Location getRegionCenter(StallRegion region) {
        org.bukkit.World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) return null;
        return new Location(world,
            (region.getX1() + region.getX2()) / 2.0,
            region.getY1(),
            (region.getZ1() + region.getZ2()) / 2.0
        );
    }

    private int getRegionSize(StallRegion region) {
        return (Math.abs(region.getX2() - region.getX1()) + 1) *
               (Math.abs(region.getY2() - region.getY1()) + 1) *
               (Math.abs(region.getZ2() - region.getZ1()) + 1);
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
        if (slot < 0 || slot >= 54) return;

        ClickType click = event.getClick();

        // 区域项目点击 (0-35)
        if (slot < 36) {
            handleRegionClick(slot, click);
            return;
        }

        // 功能按钮
        switch (slot) {
            case 45 -> {
                // 切换类型
                showStallRegions = !showStallRegions;
                currentPage = 0;
                inventory = Bukkit.createInventory(null, 54, "§6区域管理 - " + (showStallRegions ? "摆摊区域" : "商铺"));
                refreshDisplay();
                player.openInventory(inventory);
            }
            case 47 -> {
                // 创建新区域
                if (!plugin.getRegionManager().hasSelection(player)) {
                    plugin.getMessageManager().sendRaw(player, "§c请先用下界合金锄选择区域!");
                    return;
                }
                player.closeInventory();
                Location[] selection = plugin.getRegionManager().getSelection(player);
                new RegionTypeSelectGUI(plugin, player, selection[0], selection[1]).open();
            }
            case 48 -> {
                // 上一页
                if (currentPage > 0) {
                    currentPage--;
                    refreshDisplay();
                }
            }
            case 50 -> {
                // 下一页
                currentPage++;
                refreshDisplay();
            }
            case 53 -> {
                // 关闭
                player.closeInventory();
            }
        }
    }

    private void handleRegionClick(int slot, ClickType click) {
        List<Object> regions = new ArrayList<>();
        if (showStallRegions) {
            regions.addAll(plugin.getRegionManager().getRegions().values());
        } else {
            regions.addAll(plugin.getShopManager().getAllShops());
        }

        int index = currentPage * ITEMS_PER_PAGE + slot;
        if (index >= regions.size()) return;

        Object region = regions.get(index);

        if (region instanceof StallRegion stallRegion) {
            handleStallRegionClick(stallRegion, click);
        } else if (region instanceof Shop shop) {
            handleShopClick(shop, click);
        }
    }

    private void handleStallRegionClick(StallRegion region, ClickType click) {
        if (click == ClickType.LEFT || click == ClickType.SHIFT_RIGHT) {
            // 传送
            Location center = getRegionCenter(region);
            if (center != null) {
                player.closeInventory();
                player.teleport(center.add(0, 1, 0));
                plugin.getMessageManager().sendRaw(player, "§a已传送到区域: §e" + region.getName());
            } else {
                plugin.getMessageManager().sendRaw(player, "§c无法传送，世界不存在!");
            }
        } else if (click == ClickType.RIGHT) {
            // 删除
            player.closeInventory();
            new ConfirmDeleteGUI(plugin, player, "摆摊区域", region.getName(), () -> {
                plugin.getRegionManager().deleteRegion(region.getName());
                plugin.getMessageManager().sendRaw(player, "§a已删除区域: §e" + region.getName());
                open();
            }, this::open).open();
        } else if (click == ClickType.SHIFT_LEFT) {
            // 显示粒子边框
            showRegionParticles(region);
            plugin.getMessageManager().sendRaw(player, "§a正在显示区域边框: §e" + region.getName());
        }
    }

    private void handleShopClick(Shop shop, ClickType click) {
        if (click == ClickType.LEFT || click == ClickType.SHIFT_RIGHT) {
            // 传送
            Location loc = shop.getLocation();
            if (loc != null) {
                player.closeInventory();
                player.teleport(loc.add(0, 1, 0));
                plugin.getMessageManager().sendRaw(player, "§a已传送到商铺: §e" + shop.getName());
            } else {
                plugin.getMessageManager().sendRaw(player, "§c无法传送，位置无效!");
            }
        } else if (click == ClickType.RIGHT) {
            // 删除
            player.closeInventory();
            new ConfirmDeleteGUI(plugin, player, "商铺", shop.getName(), () -> {
                plugin.getShopManager().deleteShop(shop.getId());
                plugin.getMessageManager().sendRaw(player, "§a已删除商铺: §e" + shop.getName());
                open();
            }, this::open).open();
        } else if (click == ClickType.SHIFT_LEFT) {
            // 显示粒子边框
            if (shop.hasRegion()) {
                showShopParticles(shop);
                plugin.getMessageManager().sendRaw(player, "§a正在显示商铺边框: §e" + shop.getName());
            } else {
                plugin.getMessageManager().sendRaw(player, "§c该商铺未设置区域!");
            }
        }
    }

    private void showRegionParticles(StallRegion region) {
        org.bukkit.World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) return;

        Location pos1 = new Location(world, region.getX1(), region.getY1(), region.getZ1());
        Location pos2 = new Location(world, region.getX2(), region.getY2(), region.getZ2());
        
        // 使用RegionSelectListener中的方法显示边框
        drawBorderParticles(pos1, pos2, org.bukkit.Particle.FLAME);
    }

    private void showShopParticles(Shop shop) {
        if (!shop.hasRegion()) return;
        
        Location min = shop.getMinCorner();
        Location max = shop.getMaxCorner();
        
        drawBorderParticles(min, max, org.bukkit.Particle.SOUL_FIRE_FLAME);
    }

    private void drawBorderParticles(Location pos1, Location pos2, org.bukkit.Particle particle) {
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX()) + 1;
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY()) + 1;
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ()) + 1;

        double step = 1.0;

        // 底部4条线
        for (double x = minX; x <= maxX; x += step) {
            player.spawnParticle(particle, x, minY, minZ, 1, 0, 0, 0, 0);
            player.spawnParticle(particle, x, minY, maxZ, 1, 0, 0, 0, 0);
        }
        for (double z = minZ; z <= maxZ; z += step) {
            player.spawnParticle(particle, minX, minY, z, 1, 0, 0, 0, 0);
            player.spawnParticle(particle, maxX, minY, z, 1, 0, 0, 0, 0);
        }

        // 顶部4条线
        for (double x = minX; x <= maxX; x += step) {
            player.spawnParticle(particle, x, maxY, minZ, 1, 0, 0, 0, 0);
            player.spawnParticle(particle, x, maxY, maxZ, 1, 0, 0, 0, 0);
        }
        for (double z = minZ; z <= maxZ; z += step) {
            player.spawnParticle(particle, minX, maxY, z, 1, 0, 0, 0, 0);
            player.spawnParticle(particle, maxX, maxY, z, 1, 0, 0, 0, 0);
        }

        // 垂直4条线
        for (double y = minY; y <= maxY; y += step) {
            player.spawnParticle(particle, minX, y, minZ, 1, 0, 0, 0, 0);
            player.spawnParticle(particle, maxX, y, minZ, 1, 0, 0, 0, 0);
            player.spawnParticle(particle, minX, y, maxZ, 1, 0, 0, 0, 0);
            player.spawnParticle(particle, maxX, y, maxZ, 1, 0, 0, 0, 0);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;

        HandlerList.unregisterAll(this);
    }
}

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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class RegionTypeSelectGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private final Location pos1;
    private final Location pos2;
    private Inventory inventory;

    public RegionTypeSelectGUI(PlayerStallCraft plugin, Player player, Location pos1, Location pos2) {
        this.plugin = plugin;
        this.player = player;
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 27, "§6选择区域类型");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        setupDisplay();
        player.openInventory(inventory);
    }

    private void setupDisplay() {
        // 填充背景
        ItemStack bgItem = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, bgItem);
        }

        // 计算选区信息
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());
        int volume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);

        // 区域信息（中间上方）
        inventory.setItem(4, createItem(Material.MAP, "§e当前选区信息",
            "§7═══════════════════",
            "§7世界: §f" + pos1.getWorld().getName(),
            "§7点1: §f(" + pos1.getBlockX() + ", " + pos1.getBlockY() + ", " + pos1.getBlockZ() + ")",
            "§7点2: §f(" + pos2.getBlockX() + ", " + pos2.getBlockY() + ", " + pos2.getBlockZ() + ")",
            "§7范围: §f" + (maxX - minX + 1) + " x " + (maxY - minY + 1) + " x " + (maxZ - minZ + 1),
            "§7体积: §f" + volume + " 方块",
            "§7═══════════════════"
        ));

        // 摆摊区域选项（左边）
        inventory.setItem(11, createItem(Material.CAMPFIRE, "§6摆摊区域 §7(流动摊位)",
            "§7═══════════════════",
            "§f创建一个公共摆摊区域",
            "§f玩家可以在此区域内摆摊",
            "",
            "§7特点:",
            "§e• §f多个玩家可同时使用",
            "§e• §f需要玩家在线摆摊",
            "§e• §f税率: 15% (持证8%)",
            "§7═══════════════════",
            "",
            "§a点击创建摆摊区域"
        ));

        // 商铺选项（右边）
        inventory.setItem(15, createItem(Material.BARREL, "§b商铺 §7(固定店铺)",
            "§7═══════════════════",
            "§f创建一个商铺位置",
            "§f玩家可租赁或购买此商铺",
            "",
            "§7特点:",
            "§e• §f支持离线售卖",
            "§e• §f需要营业执照",
            "§e• §f税率: 租3% / 购1%",
            "§7═══════════════════",
            "",
            "§a点击创建商铺"
        ));

        // 取消按钮（底部中间）
        inventory.setItem(22, createItem(Material.BARRIER, "§c取消",
            "§7取消创建，保留选区"
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
        if (slot < 0 || slot >= 27) return;

        switch (slot) {
            case 11 -> {
                // 创建摆摊区域
                player.closeInventory();
                new RegionNameInputGUI(plugin, player, pos1, pos2, RegionType.STALL_ZONE).open();
            }
            case 15 -> {
                // 创建商铺
                player.closeInventory();
                new ShopCreateGUI(plugin, player, pos1, pos2).open();
            }
            case 22 -> {
                // 取消
                player.closeInventory();
                plugin.getMessageManager().sendRaw(player, "§e已取消，选区已保留，可重新选择类型");
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;

        HandlerList.unregisterAll(this);
    }

    public enum RegionType {
        STALL_ZONE,
        SHOP
    }
}

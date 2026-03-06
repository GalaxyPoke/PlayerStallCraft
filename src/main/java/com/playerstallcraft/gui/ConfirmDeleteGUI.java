package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import org.bukkit.Bukkit;
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

public class ConfirmDeleteGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private final String type;
    private final String name;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private Inventory inventory;

    public ConfirmDeleteGUI(PlayerStallCraft plugin, Player player, String type, String name, 
                            Runnable onConfirm, Runnable onCancel) {
        this.plugin = plugin;
        this.player = player;
        this.type = type;
        this.name = name;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    public ConfirmDeleteGUI(PlayerStallCraft plugin, Player player, String displayName,
                            Runnable onConfirm, Runnable onCancel) {
        this(plugin, player, "", displayName, onConfirm, onCancel);
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 27, "§c确认删除 - " + name);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        setupDisplay();
        player.openInventory(inventory);
    }

    private void setupDisplay() {
        // 填充背景
        ItemStack bgItem = createItem(Material.RED_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, bgItem);
        }

        // 警告信息
        inventory.setItem(4, createItem(Material.TNT, "§c§l警告!",
            "§7═══════════════════",
            "§f你即将删除:",
            "§e" + type + ": §c" + name,
            "§7═══════════════════",
            "",
            "§c此操作不可撤销!"
        ));

        // 确认按钮
        inventory.setItem(11, createItem(Material.LIME_CONCRETE, "§a确认删除",
            "§7点击确认删除",
            "",
            "§c警告: 此操作不可恢复!"
        ));

        // 取消按钮
        inventory.setItem(15, createItem(Material.RED_CONCRETE, "§c取消",
            "§7点击取消操作"
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

        if (slot == 11) {
            // 确认删除
            player.closeInventory();
            if (onConfirm != null) {
                onConfirm.run();
            }
        } else if (slot == 15) {
            // 取消
            player.closeInventory();
            if (onCancel != null) {
                onCancel.run();
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;

        HandlerList.unregisterAll(this);
    }
}

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

public class RegionNameInputGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private final Location pos1;
    private final Location pos2;
    private Inventory inventory;
    private boolean waitingForInput = false;

    public RegionNameInputGUI(PlayerStallCraft plugin, Player player, Location pos1, Location pos2, 
                              RegionTypeSelectGUI.RegionType regionType) {
        this.plugin = plugin;
        this.player = player;
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 27, "§6输入摆摊区域名称");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        setupDisplay();
        player.openInventory(inventory);
    }

    private void setupDisplay() {
        // 填充背景
        ItemStack bgItem = createItem(Material.ORANGE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, bgItem);
        }

        // 提示信息
        inventory.setItem(13, createItem(Material.NAME_TAG, "§e点击输入区域名称",
            "§7═══════════════════",
            "§f点击此处后在聊天栏输入名称",
            "",
            "§7名称要求:",
            "§e• §f不能包含空格",
            "§e• §f不能与现有区域重名",
            "§e• §f建议使用中英文均可",
            "§7═══════════════════",
            "",
            "§a点击开始输入"
        ));

        // 返回按钮
        inventory.setItem(18, createItem(Material.ARROW, "§e返回",
            "§7返回选择区域类型"
        ));

        // 取消按钮
        inventory.setItem(26, createItem(Material.BARRIER, "§c取消",
            "§7取消创建"
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
            case 13 -> {
                // 开始输入名称
                waitingForInput = true;
                player.closeInventory();
                plugin.getMessageManager().sendRaw(player, "§a请在聊天栏输入区域名称 §7(输入 cancel 取消)");
            }
            case 18 -> {
                // 返回
                player.closeInventory();
                new RegionTypeSelectGUI(plugin, player, pos1, pos2).open();
            }
            case 26 -> {
                // 取消
                player.closeInventory();
                plugin.getMessageManager().sendRaw(player, "§e已取消创建");
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (!waitingForInput) return;

        event.setCancelled(true);
        waitingForInput = false;
        String input = event.getMessage().trim();

        // 在主线程执行
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (input.equalsIgnoreCase("cancel")) {
                plugin.getMessageManager().sendRaw(player, "§e已取消输入");
                new RegionTypeSelectGUI(plugin, player, pos1, pos2).open();
                return;
            }

            if (input.contains(" ")) {
                plugin.getMessageManager().sendRaw(player, "§c区域名称不能包含空格!");
                plugin.getMessageManager().sendRaw(player, "§a请重新输入 §7(输入 cancel 取消)");
                waitingForInput = true;
                return;
            }

            if (plugin.getRegionManager().getRegions().containsKey(input)) {
                plugin.getMessageManager().sendRaw(player, "§c该名称已被使用!");
                plugin.getMessageManager().sendRaw(player, "§a请重新输入 §7(输入 cancel 取消)");
                waitingForInput = true;
                return;
            }

            // 保存区域
            plugin.getRegionManager().setPos1(player, pos1);
            plugin.getRegionManager().setPos2(player, pos2);
            plugin.getRegionManager().saveRegion(input, player);
            
            plugin.getMessageManager().sendRaw(player, "§a成功创建摆摊区域: §e" + input);
            plugin.getMessageManager().sendRaw(player, "§7玩家现在可以在此区域内使用 /baitan on 摆摊");
            
            HandlerList.unregisterAll(this);
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;

        if (!waitingForInput) {
            HandlerList.unregisterAll(this);
        }
    }
}

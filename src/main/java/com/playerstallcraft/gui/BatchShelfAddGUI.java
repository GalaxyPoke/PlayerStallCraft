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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量上架货架 GUI
 * 左侧为已选商品预览（最多 9 格），右侧显示操作说明；
 * 底部两排镜像玩家背包，点击物品加入待上架列表，设置统一单价后一键确认。
 */
public class BatchShelfAddGUI implements Listener {

    private static final int QUEUE_SIZE = 9;  // 待上架队列槽位数
    private static final int INV_START   = 27; // GUI 底部两排对应背包起始槽

    private final PlayerStallCraft plugin;
    private final Player player;
    private final Shop shop;
    private final ShopShelf shelf;
    private Inventory inventory;

    /** 已选待上架：GUI槽(0-8) -> 物品 */
    private final Map<Integer, ItemStack> queuedItems = new LinkedHashMap<>();
    private double defaultPrice = 100.0;
    private String currencyType = "nye";
    private boolean waitingForPrice = false;
    private boolean isRefreshing = false;

    public BatchShelfAddGUI(PlayerStallCraft plugin, Player player, Shop shop, ShopShelf shelf) {
        this.plugin = plugin;
        this.player = player;
        this.shop = shop;
        this.shelf = shelf;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 54, "§6批量上架 - 货架#" + shelf.getId());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshDisplay();
        player.openInventory(inventory);
    }

    private void refreshDisplay() {
        isRefreshing = true;
        inventory.clear();

        // ── 背景 ──
        ItemStack bg = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inventory.setItem(i, bg);

        // ── 队列展示（行0，slot 0-8）──
        for (int i = 0; i < QUEUE_SIZE; i++) {
            ItemStack queued = queuedItems.get(i);
            if (queued != null) {
                ItemStack display = queued.clone();
                ItemMeta meta = display.getItemMeta();
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("§7───────────");
                lore.add("§7单价: §e" + plugin.getEconomyManager().formatCurrency(defaultPrice, currencyType));
                lore.add("§c点击从列表移除");
                meta.setLore(lore);
                display.setItemMeta(meta);
                inventory.setItem(i, display);
            } else {
                ItemStack empty = createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7空槽位", "§7点击背包中的物品加入");
                inventory.setItem(i, empty);
            }
        }

        // ── 操作区（行1-2，slot 9-26）──
        inventory.setItem(11, createItem(Material.GOLD_INGOT,
            "§6设置单价: §e" + plugin.getEconomyManager().formatCurrency(defaultPrice, currencyType),
            "§7点击后在聊天中输入新价格",
            "§7适用于队列中所有商品"));

        inventory.setItem(13, createItem(
            currencyType.equals("vault") ? Material.GOLD_NUGGET : Material.PAPER,
            "§b货币: §f" + plugin.getEconomyManager().getCurrencyName(currencyType),
            "§7点击切换金币/鸽币"));

        int freeSlots = ShopShelf.MAX_SLOTS - shelf.getItemCount();
        int canList = Math.min(queuedItems.size(), freeSlots);
        boolean canConfirm = !queuedItems.isEmpty() && freeSlots > 0;

        inventory.setItem(15, createItem(
            canConfirm ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
            canConfirm ? "§a§l确认上架 (" + canList + "/" + QUEUE_SIZE + ")" : "§c§l无法上架",
            "§7货架剩余槽位: §f" + freeSlots,
            "§7待上架数量: §f" + queuedItems.size(),
            "",
            canConfirm ? "§a点击一键上架所有商品" : (queuedItems.isEmpty() ? "§c请先选择物品" : "§c货架已满")));

        inventory.setItem(24, createItem(Material.ARROW, "§e返回", "§7返回货架管理"));
        inventory.setItem(26, createItem(Material.BARRIER, "§c取消", "§7关闭并取消批量上架"));

        // ── 背包镜像（行3-4，slot 27-44）──
        ItemStack[] pInv = player.getInventory().getContents();
        for (int i = 0; i < 18 && i < pInv.length; i++) {
            ItemStack item = pInv[i];
            if (item != null && item.getType() != Material.AIR) {
                inventory.setItem(INV_START + i, item.clone());
            }
        }

        // ── 背包分隔说明 ──
        inventory.setItem(45, createItem(Material.GREEN_STAINED_GLASS_PANE, "§a背包（点击物品加入队列）"));
        isRefreshing = false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getWhoClicked().equals(player)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0) return;

        // 点击队列槽位（0-8）→ 从队列移除
        if (slot >= 0 && slot < QUEUE_SIZE) {
            if (queuedItems.containsKey(slot)) {
                ItemStack removed = queuedItems.remove(slot);
                // 归还到背包
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(removed);
                if (!leftover.isEmpty()) {
                    leftover.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
                }
                compactQueue();
                refreshDisplay();
            }
            return;
        }

        // 设置单价
        if (slot == 11) {
            waitingForPrice = true;
            player.closeInventory();
            plugin.getMessageManager().sendRaw(player, "§e请在聊天中输入商品单价 (输入 cancel 取消):");
            return;
        }

        // 切换货币
        if (slot == 13) {
            currencyType = currencyType.equals("vault") ? "nye" : "vault";
            refreshDisplay();
            return;
        }

        // 确认上架
        if (slot == 15) {
            confirmListing();
            return;
        }

        // 返回
        if (slot == 24) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                new ShelfItemsGUI(plugin, player, shop, shelf).open(), 1L);
            return;
        }

        // 取消
        if (slot == 26) {
            player.closeInventory();
            return;
        }

        // 点击背包镜像区（27-44）→ 从背包取出物品加入队列
        if (slot >= INV_START && slot < INV_START + 18) {
            int invIndex = slot - INV_START;
            ItemStack[] pInv = player.getInventory().getContents();
            if (invIndex >= pInv.length) return;
            ItemStack item = pInv[invIndex];
            if (item == null || item.getType() == Material.AIR) return;

            if (queuedItems.size() >= QUEUE_SIZE) {
                plugin.getMessageManager().sendRaw(player, "§c待上架队列已满（最多 " + QUEUE_SIZE + " 件）!");
                return;
            }
            // 从背包中取出（取全部该格的物品）
            ItemStack toQueue = item.clone();
            player.getInventory().setItem(invIndex, null);
            int nextSlot = nextQueueSlot();
            queuedItems.put(nextSlot, toQueue);
            refreshDisplay();
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player) || !waitingForPrice) return;
        event.setCancelled(true);
        waitingForPrice = false;
        String input = event.getMessage().trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!input.equalsIgnoreCase("cancel")) {
                try {
                    double p = Double.parseDouble(input);
                    if (p <= 0) {
                        plugin.getMessageManager().sendRaw(player, "§c价格必须大于0!");
                    } else {
                        defaultPrice = p;
                    }
                } catch (NumberFormatException e) {
                    plugin.getMessageManager().sendRaw(player, "§c请输入有效的数字!");
                }
            }
            open();
        });
    }

    private void confirmListing() {
        if (queuedItems.isEmpty()) return;
        int listed = 0;
        List<ItemStack> failed = new ArrayList<>();

        for (ItemStack item : queuedItems.values()) {
            // 找下一个空货架槽
            int targetSlot = -1;
            for (int s = 0; s < ShopShelf.MAX_SLOTS; s++) {
                if (!shelf.hasItem(s)) { targetSlot = s; break; }
            }
            if (targetSlot == -1) {
                failed.add(item);
                continue;
            }
            ShopShelf.ShelfItem shelfItem = new ShopShelf.ShelfItem(item, defaultPrice, item.getAmount(), currencyType);
            shelf.getItems().put(targetSlot, shelfItem);
            plugin.getShopManager().saveShelfItem(shelf, targetSlot, shelfItem);
            listed++;
        }

        // 货架已满时剩余物品归还背包
        for (ItemStack leftover : failed) {
            Map<Integer, ItemStack> remain = player.getInventory().addItem(leftover);
            remain.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
        }

        plugin.getShelfHologramManager().updateHologram(shelf);
        queuedItems.clear();

        plugin.getMessageManager().sendRaw(player, "§a成功批量上架 §e" + listed + " §a件商品!");
        if (!failed.isEmpty()) {
            plugin.getMessageManager().sendRaw(player, "§c货架空间不足，§e" + failed.size() + " §c件物品已返还背包。");
        }

        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () ->
            new ShelfItemsGUI(plugin, player, shop, shelf).open(), 1L);
    }

    private void compactQueue() {
        List<ItemStack> items = new ArrayList<>(queuedItems.values());
        queuedItems.clear();
        for (int i = 0; i < items.size(); i++) queuedItems.put(i, items.get(i));
    }

    private int nextQueueSlot() {
        for (int i = 0; i < QUEUE_SIZE; i++) { if (!queuedItems.containsKey(i)) return i; }
        return -1;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;
        if (waitingForPrice || isRefreshing) return;
        // 未确认时将队列物品归还背包
        if (!queuedItems.isEmpty()) {
            for (ItemStack item : queuedItems.values()) {
                Map<Integer, ItemStack> remain = player.getInventory().addItem(item);
                remain.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
            }
            queuedItems.clear();
        }
        HandlerList.unregisterAll(this);
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}

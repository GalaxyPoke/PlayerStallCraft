package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.managers.MessageManager;
import com.playerstallcraft.models.AdSlot;
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
import java.util.List;
import java.util.Map;

/**
 * 管理员专用广告位管理 GUI
 * 功能：创建/删除、启用/禁用、修改价格/货币、统计查看
 */
public class AdAdminGUI implements Listener {

    private enum Page { LIST, EDIT, STATS }
    private enum ChatStep { NONE, NAME, PRICE, CURRENCY, EDIT_PRICE, EDIT_CURRENCY }

    private final PlayerStallCraft plugin;
    private final Player player;
    private Inventory inventory;
    private Page currentPage = Page.LIST;
    private ChatStep chatStep = ChatStep.NONE;

    // 创建流程
    private String pendingName;
    private double pendingPrice;
    // 编辑流程
    private int editingSlotId = -1;

    public AdAdminGUI(PlayerStallCraft plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        openListPage();
    }

    // ─────────────────────────────── LIST PAGE ────────────────────────────────

    private void openListPage() {
        currentPage = Page.LIST;
        inventory = Bukkit.createInventory(null, 54, "§c管理员 - 广告位管理");

        List<AdSlot> slots = new ArrayList<>(plugin.getAdManager().getAdSlots().values());
        for (int i = 0; i < Math.min(slots.size(), 45); i++) {
            inventory.setItem(i, buildSlotListItem(slots.get(i)));
        }

        if (slots.isEmpty()) {
            inventory.setItem(22, buildItem(Material.GRAY_STAINED_GLASS_PANE,
                    "§7暂无广告位", "§7点击下方按钮创建第一个广告位"));
        }

        inventory.setItem(45, buildItem(Material.ARROW, "§e返回广告列表", "§7返回玩家广告界面"));
        inventory.setItem(47, buildItem(Material.EMERALD_BLOCK, "§a创建新广告位",
                "§7在你当前站立的位置创建广告位", "", "§a▶ 点击开始创建"));
        inventory.setItem(49, buildItem(Material.BARRIER, "§c关闭", "§7关闭界面"));
        inventory.setItem(51, buildItem(Material.BOOK, "§b统计查看", "§7查看各广告位收入与广告数"));
        player.openInventory(inventory);
    }

    private ItemStack buildSlotListItem(AdSlot slot) {
        String currency = plugin.getEconomyManager().getCurrencyName(slot.getCurrencyType());
        int adCount = plugin.getAdManager().getSlotAds(slot.getId()).size();
        Material mat = slot.isActive() ? Material.LIME_TERRACOTTA : Material.RED_TERRACOTTA;
        List<String> lore = new ArrayList<>();
        lore.add("§7ID: §f" + slot.getId());
        lore.add("§7状态: " + (slot.isActive() ? "§a启用" : "§c禁用"));
        lore.add("§7每小时价格: §e" + String.format("%.0f", slot.getPricePerHour()) + " " + currency);
        lore.add("§7货币类型: §f" + slot.getCurrencyType());
        lore.add("§7当前广告数: §f" + adCount);
        lore.add("");
        lore.add("§a▶ 左键编辑");
        lore.add("§c▶ 右键删除");
        return buildItemLore(mat, "§f" + slot.getName(), lore);
    }

    // ─────────────────────────────── EDIT PAGE ────────────────────────────────

    private void openEditPage(AdSlot slot) {
        currentPage = Page.EDIT;
        editingSlotId = slot.getId();
        inventory = Bukkit.createInventory(null, 54, "§e编辑广告位: " + slot.getName());

        String currency = plugin.getEconomyManager().getCurrencyName(slot.getCurrencyType());

        // Toggle active
        Material toggleMat = slot.isActive() ? Material.LIME_DYE : Material.GRAY_DYE;
        inventory.setItem(20, buildItem(toggleMat,
                slot.isActive() ? "§a§l当前: 启用 (点击禁用)" : "§c§l当前: 禁用 (点击启用)",
                "§7切换广告位启用/禁用状态"));

        // Edit price
        inventory.setItem(22, buildItem(Material.GOLD_NUGGET, "§e修改每小时价格",
                "§7当前: §e" + String.format("%.0f", slot.getPricePerHour()) + " " + currency,
                "", "§e▶ 点击输入新价格"));

        // Edit currency
        inventory.setItem(24, buildItem(Material.SUNFLOWER, "§6修改货币类型",
                "§7当前货币: §f" + slot.getCurrencyType(),
                "", "§6▶ 点击输入新货币类型"));

        inventory.setItem(45, buildItem(Material.ARROW, "§e返回列表", "§7返回广告位列表"));
        inventory.setItem(49, buildItem(Material.BARRIER, "§c关闭", "§7关闭界面"));
        player.openInventory(inventory);
    }

    // ─────────────────────────────── STATS PAGE ───────────────────────────────

    private void openStatsPage() {
        currentPage = Page.STATS;
        inventory = Bukkit.createInventory(null, 54, "§b广告位统计");

        Map<Integer, long[]> stats = plugin.getAdManager().getAdStats();
        List<AdSlot> slots = new ArrayList<>(plugin.getAdManager().getAdSlots().values());
        int i = 0;
        for (AdSlot slot : slots) {
            if (i >= 45) break;
            long[] data = stats.getOrDefault(slot.getId(), new long[]{0, 0});
            String currency = plugin.getEconomyManager().getCurrencyName(slot.getCurrencyType());
            inventory.setItem(i++, buildItemLore(Material.GOLD_INGOT,
                    "§e" + slot.getName(),
                    List.of(
                            "§7广告位 ID: §f" + slot.getId(),
                            "§7活跃广告数: §f" + data[0],
                            "§7当前总收入: §e" + String.format("%.0f", (double) data[1]) + " " + currency,
                            "§7状态: " + (slot.isActive() ? "§a启用" : "§c禁用")
                    )));
        }

        if (slots.isEmpty()) {
            inventory.setItem(22, buildItem(Material.GRAY_STAINED_GLASS_PANE, "§7暂无广告位", ""));
        }

        inventory.setItem(45, buildItem(Material.ARROW, "§e返回列表", "§7返回广告位列表"));
        inventory.setItem(49, buildItem(Material.BARRIER, "§c关闭", "§7关闭界面"));
        player.openInventory(inventory);
    }

    // ─────────────────────────────── EVENTS ─────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        switch (currentPage) {
            case LIST -> handleListClick(slot, event.isRightClick());
            case EDIT -> handleEditClick(slot);
            case STATS -> handleStatsClick(slot);
        }
    }

    private void handleListClick(int slot, boolean rightClick) {
        if (slot == 45) { HandlerList.unregisterAll(this); new AdGUI(plugin, player).open(); return; }
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 51) { openStatsPage(); return; }
        if (slot == 47) {
            chatStep = ChatStep.NAME;
            player.closeInventory();
            plugin.getMessageManager().sendRaw(player, "&c【管理员】&7请输入新广告位名称 &8(输入 cancel 取消):");
            return;
        }
        if (slot < 45) {
            List<AdSlot> slots = new ArrayList<>(plugin.getAdManager().getAdSlots().values());
            if (slot < slots.size()) {
                AdSlot adSlot = slots.get(slot);
                if (rightClick) {
                    boolean deleted = plugin.getAdManager().deleteAdSlot(adSlot.getId());
                    plugin.getMessageManager().sendRaw(player,
                            deleted ? "&a广告位 [" + adSlot.getName() + "] 已删除" : "&c删除失败");
                    openListPage();
                } else {
                    openEditPage(adSlot);
                }
            }
        }
    }

    private void handleEditClick(int slot) {
        if (slot == 45) { openListPage(); return; }
        if (slot == 49) { player.closeInventory(); return; }
        AdSlot adSlot = plugin.getAdManager().getAdSlot(editingSlotId);
        if (adSlot == null) { openListPage(); return; }
        if (slot == 20) {
            plugin.getAdManager().toggleAdSlotActive(editingSlotId);
            openEditPage(plugin.getAdManager().getAdSlot(editingSlotId));
            return;
        }
        if (slot == 22) {
            chatStep = ChatStep.EDIT_PRICE;
            player.closeInventory();
            plugin.getMessageManager().sendRaw(player,
                    "&e【编辑】&7请输入新的每小时价格 &8(输入 cancel 取消):");
            return;
        }
        if (slot == 24) {
            chatStep = ChatStep.EDIT_CURRENCY;
            player.closeInventory();
            plugin.getMessageManager().sendRaw(player,
                    "&e【编辑】&7请输入货币类型（如 &fnye&7、&fvault&7）&8(输入 cancel 取消):");
        }
    }

    private void handleStatsClick(int slot) {
        if (slot == 45) { openListPage(); return; }
        if (slot == 49) { player.closeInventory(); return; }
    }

    // ─────────────────────────────── CHAT ─────────────────────────────────────

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (chatStep == ChatStep.NONE) return;

        event.setCancelled(true);
        String input = event.getMessage().trim();

        // 原子性地抢占当前步骤，防止两个 async 线程同时通过检查
        final ChatStep step;
        synchronized (this) {
            if (chatStep == ChatStep.NONE) return;
            step = chatStep;
            chatStep = ChatStep.NONE;
        }

        if (input.equalsIgnoreCase("cancel")) {
            boolean wasEditing = step == ChatStep.EDIT_PRICE || step == ChatStep.EDIT_CURRENCY;
            plugin.getMessageManager().sendRaw(player, "&c已取消");
            if (wasEditing) {
                final int slotId = editingSlotId;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    AdSlot s = plugin.getAdManager().getAdSlot(slotId);
                    if (s != null) openEditPage(s); else openListPage();
                });
            } else {
                Bukkit.getScheduler().runTask(plugin, this::openListPage);
            }
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (step) {
                case NAME -> {
                    pendingName = input;
                    chatStep = ChatStep.PRICE;
                    plugin.getMessageManager().sendRaw(player, "&c【管理员】&7请输入每小时价格（如 &f100&7）:");
                }
                case PRICE -> {
                    try {
                        double price = Double.parseDouble(input);
                        if (price < 0) {
                            chatStep = ChatStep.PRICE;
                            plugin.getMessageManager().sendRaw(player, "&c价格不能为负数，请重新输入:");
                            return;
                        }
                        pendingPrice = price;
                        chatStep = ChatStep.CURRENCY;
                        plugin.getMessageManager().sendRaw(player,
                                "&c【管理员】&7请输入货币类型（如 &fnye&7、&fvault&7，输入 skip 默认 nye）:");
                    } catch (NumberFormatException e) {
                        chatStep = ChatStep.PRICE;
                        plugin.getMessageManager().sendRaw(player, "&c请输入有效的数字价格:");
                    }
                }
                case CURRENCY -> {
                    String currency = input.equalsIgnoreCase("skip") ? "nye" : input;
                    plugin.getAdManager().createAdSlot(pendingName, player.getLocation(), pendingPrice, currency);
                    plugin.getMessageManager().sendRaw(player, "&a广告位 [" + pendingName + "] 创建成功!");
                    openListPage();
                }
                case EDIT_PRICE -> {
                    try {
                        double price = Double.parseDouble(input);
                        if (price < 0) {
                            chatStep = ChatStep.EDIT_PRICE;
                            plugin.getMessageManager().sendRaw(player, "&c价格不能为负数，请重新输入:");
                            return;
                        }
                        final int slotId = editingSlotId;
                        plugin.getAdManager().updateAdSlotPrice(slotId, price);
                        plugin.getMessageManager().sendRaw(player, "&a价格已更新为 &e" + String.format("%.0f", price));
                        AdSlot updated = plugin.getAdManager().getAdSlot(slotId);
                        if (updated != null) openEditPage(updated); else openListPage();
                    } catch (NumberFormatException e) {
                        chatStep = ChatStep.EDIT_PRICE;
                        plugin.getMessageManager().sendRaw(player, "&c请输入有效的数字价格:");
                    }
                }
                case EDIT_CURRENCY -> {
                    final int slotId = editingSlotId;
                    plugin.getAdManager().updateAdSlotCurrency(slotId, input);
                    plugin.getMessageManager().sendRaw(player, "&a货币类型已更新为 &f" + input);
                    AdSlot updated = plugin.getAdManager().getAdSlot(slotId);
                    if (updated != null) openEditPage(updated); else openListPage();
                }
                default -> {}
            }
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;
        if (chatStep != ChatStep.NONE) return;
        HandlerList.unregisterAll(this);
    }

    // ─────────────────────────────── HELPERS ──────────────────────────────────

    private ItemStack buildItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.colorize(name));
            List<String> list = new ArrayList<>();
            for (String l : lore) list.add(MessageManager.colorize(l));
            meta.setLore(list);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildItemLore(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.colorize(name));
            List<String> colored = new ArrayList<>();
            for (String l : lore) colored.add(MessageManager.colorize(l));
            meta.setLore(colored);
            item.setItemMeta(meta);
        }
        return item;
    }
}

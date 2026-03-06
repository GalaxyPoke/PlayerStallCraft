package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.managers.MessageManager;
import com.playerstallcraft.models.AdSlot;
import com.playerstallcraft.models.Advertisement;
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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AdGUI implements Listener {

    private enum Page { SLOTS, MY_ADS, SLOT_DETAIL, CONFIRM }
    private enum ChatStep { NONE, TITLE, DESCRIPTION, HOURS, RENEW_HOURS }

    private final PlayerStallCraft plugin;
    private final Player player;
    private Inventory inventory;
    private Page currentPage = Page.SLOTS;
    private ChatStep chatStep = ChatStep.NONE;

    // 投放流程中间状态
    private AdSlot selectedSlot;
    private String pendingTitle;
    private String pendingDescription;
    private int pendingHours;
    private String pendingIconMaterial = "PAPER";
    // 续期流程
    private int pendingRenewAdId = -1;
    // 确认页：是否关联商铺
    private boolean pendingLinkShop = false;
    public AdGUI(PlayerStallCraft plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        openSlotsPage();
    }

    // ─────────────────────────────── SLOTS PAGE ────────────────────────────────

    private void openSlotsPage() {
        currentPage = Page.SLOTS;
        inventory = Bukkit.createInventory(null, 54, "§6广告位列表");

        Collection<AdSlot> slots = plugin.getAdManager().getAdSlots().values();
        int i = 0;
        for (AdSlot slot : slots) {
            if (i >= 45) break;
            inventory.setItem(i++, buildSlotItem(slot));
        }

        // 底栏
        inventory.setItem(45, buildItem(Material.PAPER, "§b我的广告", "§7查看并管理你投放的广告"));
        inventory.setItem(49, buildItem(Material.BARRIER, "§c关闭", "§7关闭广告界面"));
        if (player.hasPermission("playerstallcraft.admin")) {
            inventory.setItem(53, buildItem(Material.COMMAND_BLOCK, "§c管理员管理", "§7创建/删除广告位"));
        }

        player.openInventory(inventory);
    }

    private ItemStack buildSlotItem(AdSlot slot) {
        List<Advertisement> ads = plugin.getAdManager().getSlotAds(slot.getId());
        boolean occupied = !ads.isEmpty();
        String currency = plugin.getEconomyManager().getCurrencyName(slot.getCurrencyType());

        List<String> lore = new ArrayList<>();
        lore.add("§7广告位 ID: §f" + slot.getId());
        lore.add("§7每小时价格: §e" + String.format("%.0f", slot.getPricePerHour()) + " " + currency);
        lore.add("§7状态: " + (occupied ? "§c已有 " + ads.size() + " 个广告" : "§a空闲"));
        lore.add("");
        if (slot.isActive()) {
            lore.add("§a▶ 左键点击投放广告");
            lore.add("§e▶ 右键查看当前广告");
        } else {
            lore.add("§c该广告位已关闭");
        }

        Material mat = occupied ? Material.FILLED_MAP : Material.MAP;
        return buildItemLore(mat, (occupied ? "§e" : "§a") + slot.getName(), lore);
    }

    // ─────────────────────────────── MY ADS PAGE ───────────────────────────────

    private void openMyAdsPage() {
        currentPage = Page.MY_ADS;
        inventory = Bukkit.createInventory(null, 54, "§b我的广告");

        List<Advertisement> myAds = plugin.getAdManager().getPlayerAds(player.getUniqueId());
        int i = 0;
        for (Advertisement ad : myAds) {
            if (i >= 45) break;
            inventory.setItem(i++, buildAdItem(ad));
        }

        if (myAds.isEmpty()) {
            inventory.setItem(22, buildItem(Material.GRAY_STAINED_GLASS_PANE, "§7暂无广告", "§7你还没有投放任何广告"));
        }

        inventory.setItem(45, buildItem(Material.ARROW, "§e返回", "§7返回广告位列表"));
        inventory.setItem(49, buildItem(Material.BARRIER, "§c关闭", "§7关闭广告界面"));
        player.openInventory(inventory);
    }

    private ItemStack buildAdItem(Advertisement ad) {
        long remaining = ad.getEndTime() - System.currentTimeMillis();
        long hours = TimeUnit.MILLISECONDS.toHours(remaining);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60;

        List<String> lore = new ArrayList<>();
        lore.add("§7标题: §f" + ad.getTitle());
        lore.add("§7描述: §f" + (ad.getDescription() != null && !ad.getDescription().isEmpty() ? ad.getDescription() : "无"));
        lore.add("§7剩余时间: §e" + hours + "小时" + minutes + "分钟");
        lore.add("§7花费: §e" + String.format("%.0f", ad.getPrice()) + " " + ad.getCurrencyType());
        lore.add("");
        lore.add("§a▶ 左键续期广告");
        lore.add("§c▶ 右键取消广告（按剩余时间退款）");

        Material mat;
        try {
            mat = Material.valueOf(ad.getIconMaterial());
        } catch (Exception e) {
            mat = Material.PAPER;
        }
        return buildItemLore(mat, "§e" + ad.getTitle(), lore);
    }

    // ──────────────────────────── SLOT DETAIL (VIEW ADS) ──────────────────────

    private void openSlotDetailPage(AdSlot slot) {
        currentPage = Page.SLOT_DETAIL;
        selectedSlot = slot;
        inventory = Bukkit.createInventory(null, 54, "§e广告位: " + slot.getName());

        List<Advertisement> ads = plugin.getAdManager().getSlotAds(slot.getId());
        int i = 0;
        for (Advertisement ad : ads) {
            if (i >= 36) break;
            inventory.setItem(i++, buildAdItem(ad));
        }

        if (ads.isEmpty()) {
            inventory.setItem(22, buildItem(Material.GRAY_STAINED_GLASS_PANE, "§7暂无广告", "§7该广告位当前没有广告"));
        }

        String currency = plugin.getEconomyManager().getCurrencyName(slot.getCurrencyType());
        inventory.setItem(45, buildItem(Material.ARROW, "§e返回", "§7返回广告位列表"));
        inventory.setItem(47, buildItem(Material.EMERALD, "§a投放广告",
                "§7广告位: §f" + slot.getName(),
                "§7每小时价格: §e" + String.format("%.0f", slot.getPricePerHour()) + " " + currency,
                "",
                "§a点击开始投放"));
        inventory.setItem(49, buildItem(Material.BARRIER, "§c关闭", "§7关闭广告界面"));
        player.openInventory(inventory);
    }

    // ─────────────────────────────── EVENTS ────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        switch (currentPage) {
            case SLOTS -> handleSlotsClick(slot, event.isRightClick());
            case MY_ADS -> handleMyAdsClick(slot, event.isRightClick());
            case SLOT_DETAIL -> handleSlotDetailClick(slot);
            case CONFIRM -> handleConfirmClick(slot);
        }
    }

    private void handleSlotsClick(int slot, boolean rightClick) {
        if (slot == 45) { openMyAdsPage(); return; }
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 53 && player.hasPermission("playerstallcraft.admin")) {
            HandlerList.unregisterAll(this);
            new AdAdminGUI(plugin, player).open();
            return;
        }

        if (slot < 45) {
            List<AdSlot> slots = new ArrayList<>(plugin.getAdManager().getAdSlots().values());
            if (slot < slots.size()) {
                AdSlot adSlot = slots.get(slot);
                if (rightClick) {
                    openSlotDetailPage(adSlot);
                } else if (adSlot.isActive()) {
                    selectedSlot = adSlot;
                    startPlacementFlow();
                }
            }
        }
    }

    private void handleMyAdsClick(int slot, boolean rightClick) {
        if (slot == 45) { openSlotsPage(); return; }
        if (slot == 49) { player.closeInventory(); return; }

        if (slot < 45) {
            List<Advertisement> myAds = plugin.getAdManager().getPlayerAds(player.getUniqueId());
            if (slot < myAds.size()) {
                Advertisement ad = myAds.get(slot);
                if (rightClick) {
                    // 右键取消广告（退款）
                    plugin.getAdManager().cancelAdvertisementWithRefund(ad.getId(), player);
                    openMyAdsPage();
                } else {
                    // 左键续期
                    pendingRenewAdId = ad.getId();
                    chatStep = ChatStep.RENEW_HOURS;
                    player.closeInventory();
                    plugin.getMessageManager().sendRaw(player,
                            "&a【广告续期】&7请输入续期时长（小时） &8(输入 cancel 取消):");
                }
            }
        }
    }

    private void handleSlotDetailClick(int slot) {
        if (slot == 45) { openSlotsPage(); return; }
        if (slot == 47) { startPlacementFlow(); return; }
        if (slot == 49) { player.closeInventory(); return; }
    }

    // ─────────────────────────── CONFIRM PAGE ──────────────────────────────

    private void openConfirmPage() {
        currentPage = Page.CONFIRM;
        inventory = Bukkit.createInventory(null, 54, "§a确认广告投放");

        String currency = plugin.getEconomyManager().getCurrencyName(selectedSlot.getCurrencyType());
        double totalCost = selectedSlot.getPricePerHour() * pendingHours;

        // 图标预览（点击可更换）
        Material iconMat;
        try { iconMat = Material.valueOf(pendingIconMaterial); } catch (Exception e) { iconMat = Material.PAPER; }
        inventory.setItem(13, buildItemLore(iconMat, "§e广告图标",
                List.of("§7点击更换为手持物品图标", "§7当前: §f" + pendingIconMaterial)));

        // 关联商铺开关
        var playerShops = plugin.getShopManager().getPlayerShops(player.getUniqueId());
        boolean hasShop = playerShops != null && !playerShops.isEmpty();
        if (hasShop) {
            String shopName = playerShops.get(0).getName();
            Material toggleMat = pendingLinkShop ? Material.LIME_DYE : Material.GRAY_DYE;
            String toggleLabel = pendingLinkShop ? "§a关联商铺: §l开" : "§7关联商铺: §l关";
            inventory.setItem(22, buildItemLore(toggleMat, toggleLabel,
                    List.of(
                            "§7商铺: §f" + shopName,
                            "",
                            pendingLinkShop ? "§7玩家点击全息图可传送到你的商铺" : "§7广告不会关联商铺",
                            "",
                            "§e▶ 点击切换"
                    )));
        } else {
            inventory.setItem(22, buildItem(Material.BARRIER, "§7无商铺可关联",
                    "§7你还没有开设商铺"));
        }

        // 广告信息预览
        inventory.setItem(31, buildItemLore(Material.FILLED_MAP, "§b广告预览",
                List.of(
                        "§7标题: §f" + pendingTitle,
                        "§7描述: §f" + (pendingDescription.isEmpty() ? "无" : pendingDescription),
                        "§7时长: §e" + pendingHours + " §7小时",
                        "§7广告位: §f" + selectedSlot.getName(),
                        "§7关联商铺: " + (hasShop && pendingLinkShop ? "§a是" : "§7否"),
                        "",
                        "§7待支付: §c" + String.format("%.0f", totalCost) + " " + currency
                )));

        inventory.setItem(45, buildItem(Material.ARROW, "§e重新输入", "§7返回重新输入广告信息"));
        inventory.setItem(47, buildItem(Material.EMERALD, "§a确认投放",
                "§7花费 §c" + String.format("%.0f", totalCost) + " " + currency,
                "",
                "§a点击确认扣费并投放"));
        inventory.setItem(49, buildItem(Material.BARRIER, "§c取消", "§7放弃返回广告位列表"));
        player.openInventory(inventory);
    }

    private void handleConfirmClick(int slot) {
        if (slot == 45) { startPlacementFlow(); return; } // 重新输入
        if (slot == 49) { openSlotsPage(); return; }    // 取消
        if (slot == 22) {
            // 切换关联商铺开关
            var playerShops = plugin.getShopManager().getPlayerShops(player.getUniqueId());
            if (playerShops != null && !playerShops.isEmpty()) {
                pendingLinkShop = !pendingLinkShop;
                openConfirmPage();
            }
            return;
        }
        if (slot == 47) {
            // 确认投放
            int shopId = 0;
            if (pendingLinkShop) {
                var playerShops = plugin.getShopManager().getPlayerShops(player.getUniqueId());
                if (playerShops != null && !playerShops.isEmpty()) {
                    shopId = playerShops.get(0).getId();
                }
            }
            plugin.getAdManager().placeAdvertisement(
                    player, selectedSlot.getId(),
                    pendingTitle, pendingDescription,
                    pendingIconMaterial, pendingHours, shopId
            );
            openSlotsPage();
            return;
        }
        if (slot == 13) {
            // 点击图标格——更换为手持物品
            Material held = player.getInventory().getItemInMainHand().getType();
            if (held != Material.AIR) {
                pendingIconMaterial = held.name();
                openConfirmPage();
            }
        }
    }

    // ─────────────────────────── PLACEMENT FLOW (CHAT) ─────────────────────────

    private void startPlacementFlow() {
        chatStep = ChatStep.TITLE;
        player.closeInventory();
        plugin.getMessageManager().sendRaw(player,
                "&e【广告投放】&7请在聊天栏输入广告标题 &8(输入 cancel 取消):");
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
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
            if (step == ChatStep.RENEW_HOURS) {
                plugin.getMessageManager().sendRaw(player, "&c已取消续期");
                Bukkit.getScheduler().runTask(plugin, this::openMyAdsPage);
            } else {
                plugin.getMessageManager().sendRaw(player, "&c已取消广告投放");
                Bukkit.getScheduler().runTask(plugin, this::openSlotsPage);
            }
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (step) {
                case TITLE -> {
                    pendingTitle = input;
                    chatStep = ChatStep.DESCRIPTION;
                    plugin.getMessageManager().sendRaw(player,
                            "&e【广告投放】&7请输入广告描述 &8(输入 skip 跳过):");
                }
                case DESCRIPTION -> {
                    pendingDescription = input.equalsIgnoreCase("skip") ? "" : input;
                    chatStep = ChatStep.HOURS;
                    plugin.getMessageManager().sendRaw(player,
                            "&e【广告投放】&7请输入投放时长（小时，如 &f24&7）:");
                }
                case HOURS -> {
                    try {
                        int hours = Integer.parseInt(input);
                        if (hours <= 0 || hours > 720) {
                            chatStep = ChatStep.HOURS;
                            plugin.getMessageManager().sendRaw(player, "&c时长必须在 1~720 小时之间，请重新输入:");
                            return;
                        }
                        pendingHours = hours;
                        // 主线程安全：获取手持物品
                        Material held = player.getInventory().getItemInMainHand().getType();
                        pendingIconMaterial = (held != Material.AIR) ? held.name() : "PAPER";
                        openConfirmPage();
                    } catch (NumberFormatException e) {
                        chatStep = ChatStep.HOURS;
                        plugin.getMessageManager().sendRaw(player, "&c请输入有效的数字（小时），如 &f24&c:");
                    }
                }
                case RENEW_HOURS -> {
                    try {
                        int hours = Integer.parseInt(input);
                        if (hours <= 0 || hours > 720) {
                            chatStep = ChatStep.RENEW_HOURS;
                            plugin.getMessageManager().sendRaw(player, "&c时长必须在 1~720 小时之间，请重新输入:");
                            return;
                        }
                        final int adId = pendingRenewAdId;
                        plugin.getAdManager().renewAdvertisement(player, adId, hours);
                        openMyAdsPage();
                    } catch (NumberFormatException e) {
                        chatStep = ChatStep.RENEW_HOURS;
                        plugin.getMessageManager().sendRaw(player, "&c请输入有效的数字（小时）:");
                    }
                }
                default -> {}
            }
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;
        if (chatStep != ChatStep.NONE) return; // 等待聊天输入时不注销
        HandlerList.unregisterAll(this);
    }

    // ─────────────────────────────── HELPERS ───────────────────────────────────

    private ItemStack buildItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.colorize(name));
            List<String> loreList = new ArrayList<>();
            for (String l : lore) loreList.add(MessageManager.colorize(l));
            meta.setLore(loreList);
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

package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.BuyRequest;
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

import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.text.SimpleDateFormat;
import java.util.*;

public class MyBuyRequestsGUI implements Listener {

    private static final int TAB_ACTIVE  = 0;
    private static final int TAB_HISTORY = 1;

    private final PlayerStallCraft plugin;
    private final Player player;
    private Inventory inventory;
    private List<BuyRequest> myRequests;
    private List<BuyRequest> historyList = new ArrayList<>();
    private int tab = TAB_ACTIVE;

    private boolean waitingForPrice = false;
    private int editingRequestId = -1;

    public MyBuyRequestsGUI(PlayerStallCraft plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.myRequests = new ArrayList<>(plugin.getBuyRequestManager().getRequestsByPlayer(player.getUniqueId()));
        this.inventory = Bukkit.createInventory(null, 54, "§b§l我的求购");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        fillInventory();
    }

    private void fillInventory() {
        inventory.clear();

        ItemStack border = createItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(45 + i, border);
        }
        for (int i = 9; i < 45; i += 9) {
            inventory.setItem(i, border);
            inventory.setItem(i + 8, border);
        }

        // Tab 切换按鈕
        inventory.setItem(2, createItem(
            tab == TAB_ACTIVE ? Material.LIME_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE,
            tab == TAB_ACTIVE ? "§a§l[进行中]求购" : "§7[进行中]求购",
            "§7点击切换到进行中列表"));
        inventory.setItem(6, createItem(
            tab == TAB_HISTORY ? Material.YELLOW_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE,
            tab == TAB_HISTORY ? "§e§l[历史记录]求购" : "§7[历史记录]求购",
            "§7已完成/已取消/已到期的求购", "§7点击切换"));

        if (tab == TAB_ACTIVE) {
            int maxCount = plugin.getBuyRequestManager().getMaxRequestsPerPlayer();
            inventory.setItem(4, createItem(Material.PAPER, "§e§l求购统计",
                "", "§7进行中: §f" + myRequests.size() + "/" + maxCount,
                "§7剩余额度: §f" + (maxCount - myRequests.size()),
                "", "§8操作: §7左键改价 §c右键取消 §e中键置顶"));
            int slot = 10;
            for (BuyRequest request : myRequests) {
                if (slot % 9 == 0) slot++;
                if (slot % 9 == 8) slot += 2;
                if (slot >= 44) break;
                inventory.setItem(slot, createRequestItem(request));
                slot++;
            }
        } else {
            inventory.setItem(4, createItem(Material.BOOK, "§e§l求购历史",
                "", "§7展示最近50条历史记录"));
            if (historyList.isEmpty()) {
                inventory.setItem(22, createItem(Material.GRAY_STAINED_GLASS_PANE, "§7暫无历史记录"));
            } else {
                int slot = 10;
                for (BuyRequest r : historyList) {
                    if (slot % 9 == 0) slot++;
                    if (slot % 9 == 8) slot += 2;
                    if (slot >= 44) break;
                    inventory.setItem(slot, createHistoryItem(r));
                    slot++;
                }
            }
        }

        inventory.setItem(48, createItem(Material.ARROW, "§7返回求购市场"));
        inventory.setItem(50, createItem(Material.EMERALD, "§a§l发布新求购"));
        inventory.setItem(53, createItem(Material.BARRIER, "§c关闭"));
    }

    private ItemStack createRequestItem(BuyRequest request) {
        ItemStack item;
        try { item = new ItemStack(request.getItemType()); } catch (Exception e) { item = new ItemStack(Material.PAPER); }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String zhName = plugin.getGlobalMarketManager().getChineseNamePublic(request.getItemType());
        String displayName = zhName != null ? zhName : request.getItemName();
        String prefix = request.isFeatured() ? "§6★ §e§l" : "§e§l";
        meta.setDisplayName(prefix + displayName);

        int remaining = request.getRemainingAmount();
        double lockedTotal = request.getPrice() * remaining;

        List<String> lore = new ArrayList<>();
        if (request.isFeatured()) lore.add("§6★ §7置顶推广中");
        lore.add("");
        lore.add("§7求购数量: §f" + request.getAmount() + " 个");
        if (remaining < request.getAmount()) {
            lore.add("§7已收到: §a" + (request.getAmount() - remaining) + " 个");
            lore.add("§7剩余需求: §e" + remaining + " 个");
        }
        lore.add("§7你的出价: §a" + plugin.getEconomyManager().formatCurrency(request.getPrice(), request.getCurrencyType()) + "§7/个");
        lore.add("§7已预付金额: §c" + plugin.getEconomyManager().formatCurrency(lockedTotal, request.getCurrencyType()));
        lore.add("");
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
        lore.add("§7发布时间: §f" + sdf.format(new Date(request.getCreatedAt())));
        if (request.getExpireTime() > 0) {
            long left = request.getExpireTime() - System.currentTimeMillis();
            if (left > 0) {
                long days = left / 86400000L;
                lore.add("§7剩余有效期: §f" + days + " 天");
            } else {
                lore.add("§c已到期");
            }
        }
        lore.add("");
        lore.add("§a左键 » 修改出价（自动补扣/退还差额）");
        lore.add("§c右键 » 取消求购（全额退还）");
        if (!request.isFeatured()) {
            double cost = plugin.getConfigManager().getConfig().getDouble("buy-request.featured-cost", 500);
            lore.add("§e中键 » 置顶推广 (花费 " + plugin.getEconomyManager().formatCurrency(cost, plugin.getConfigManager().getConfig().getString("buy-request.featured-currency", "vault")) + ")");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHistoryItem(BuyRequest r) {
        ItemStack item;
        try { item = new ItemStack(r.getItemType()); } catch (Exception e) { item = new ItemStack(Material.PAPER); }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String zhName = plugin.getGlobalMarketManager().getChineseNamePublic(r.getItemType());
        String displayName = zhName != null ? zhName : r.getItemName();
        String statusColor = switch (r.getStatus()) {
            case "completed" -> "§a[已完成] ";
            case "cancelled" -> "§c[已取消] ";
            case "expired"   -> "§7[已到期] ";
            default          -> "§7";
        };
        meta.setDisplayName(statusColor + "§f" + displayName);
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
        meta.setLore(Arrays.asList(
            "§7数量: §f" + r.getAmount() + " 个",
            "§7出价: §f" + plugin.getEconomyManager().formatCurrency(r.getPrice(), r.getCurrencyType()) + "/个",
            "§7状态: " + statusColor.trim(),
            "§7发布时间: §f" + sdf.format(new Date(r.getCreatedAt()))
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open() {
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Tab 切换
        if (slot == 2 && tab != TAB_ACTIVE) { tab = TAB_ACTIVE; fillInventory(); return; }
        if (slot == 6 && tab != TAB_HISTORY) {
            tab = TAB_HISTORY;
            plugin.getBuyRequestManager().queryHistory(player.getUniqueId(), list -> {
                historyList = list;
                fillInventory();
            });
            return;
        }

        if (slot == 48) { player.closeInventory(); new BuyRequestGUI(plugin, player).open(); return; }
        if (slot == 50) { player.closeInventory(); new CreateBuyRequestGUI(plugin, player).open(); return; }
        if (slot == 53) { player.closeInventory(); return; }

        if (isItemSlot(slot) && tab == TAB_ACTIVE) {
            int index = getRequestIndex(slot);
            if (index >= 0 && index < myRequests.size()) {
                BuyRequest request = myRequests.get(index);
                if (event.isRightClick()) {
                    // 取消求购
                    plugin.getBuyRequestManager().cancelRequest(request.getId(), player);
                    myRequests.remove(index);
                    fillInventory();
                } else if (event.isLeftClick()) {
                    // 修改出价
                    editingRequestId = request.getId();
                    waitingForPrice = true;
                    player.closeInventory();
                    plugin.getMessageManager().sendRaw(player,
                        "&e请在聊天中输入新的出价 (输入 cancel 取消): &7当前出价: &f" +
                        plugin.getEconomyManager().formatCurrency(request.getPrice(), request.getCurrencyType()) + "/个");
                } else if (event.getClick().name().equals("MIDDLE")) {
                    // 置顶推广
                    plugin.getBuyRequestManager().featureRequest(request.getId(), player);
                    myRequests.set(index, plugin.getBuyRequestManager().getRequest(request.getId()) != null
                        ? plugin.getBuyRequestManager().getRequest(request.getId()) : request);
                    fillInventory();
                }
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (!waitingForPrice) return;
        event.setCancelled(true);
        waitingForPrice = false;
        String msg = event.getMessage().trim();
        if (msg.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, this::open);
            return;
        }
        try {
            double newPrice = Double.parseDouble(msg);
            if (newPrice <= 0) {
                plugin.getMessageManager().sendRaw(player, "&c出价必须大亇0!");
                Bukkit.getScheduler().runTask(plugin, this::open);
                return;
            }
            int rid = editingRequestId;
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getBuyRequestManager().modifyPrice(rid, player, newPrice);
                myRequests.clear();
                myRequests.addAll(plugin.getBuyRequestManager().getRequestsByPlayer(player.getUniqueId()));
                open();
            });
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendRaw(player, "&c无效的价格格式!");
            Bukkit.getScheduler().runTask(plugin, this::open);
        }
    }

    private boolean isItemSlot(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        return row >= 1 && row <= 4 && col >= 1 && col <= 7;
    }

    private int getRequestIndex(int slot) {
        int row = slot / 9 - 1;
        int col = slot % 9 - 1;
        return row * 7 + col;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory) && event.getPlayer().equals(player)) {
            HandlerList.unregisterAll(this);
        }
    }
}

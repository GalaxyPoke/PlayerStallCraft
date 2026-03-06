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

public class BuyRequestGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private Inventory inventory;
    private List<BuyRequest> requests;
    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 28;

    private String sortMode = "time_desc"; // price_asc / price_desc / time_asc / time_desc
    private String currencyFilter = "";    // "" = all, "vault", "nye"
    private String searchKeyword = "";
    private boolean waitingForSearch = false;

    public BuyRequestGUI(PlayerStallCraft plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.requests = plugin.getBuyRequestManager().getSortedRequests(sortMode, currencyFilter, searchKeyword);
        this.inventory = Bukkit.createInventory(null, 54, "§6§l求购市场 §7第" + (currentPage + 1) + "页");
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        fillInventory();
    }

    private void fillInventory() {
        inventory.clear();
        
        // 边框
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(45 + i, border);
        }
        for (int i = 9; i < 45; i += 9) {
            inventory.setItem(i, border);
            inventory.setItem(i + 8, border);
        }
        
        // 填充求购信息
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int slot = 10;
        
        for (int i = startIndex; i < Math.min(startIndex + ITEMS_PER_PAGE, requests.size()); i++) {
            if (slot % 9 == 0) slot++;
            if (slot % 9 == 8) slot += 2;
            if (slot >= 44) break;
            
            BuyRequest request = requests.get(i);
            inventory.setItem(slot, createRequestItem(request));
            slot++;
        }
        
        // 底部按钮
        // 上一页
        if (currentPage > 0) inventory.setItem(45, createItem(Material.ARROW, "§a上一页"));

        // 搜索
        String searchLabel = searchKeyword.isEmpty() ? "§e搜索求购" : "§e搜索: §f" + searchKeyword;
        inventory.setItem(46, createItem(Material.COMPASS, searchLabel,
            "§7左键: 输入关键词搜索", "§7右键: 清除搜索"));

        // 排序方式
        String sortLabel = switch (sortMode) {
            case "price_asc"  -> "§a出价 ↑ 低→高";
            case "price_desc" -> "§c出价 ↓ 高→低";
            case "time_asc"   -> "§f时间 ↑ 旧→新";
            default           -> "§b时间 ↓ 新→旧";
        };
        inventory.setItem(47, createItem(Material.HOPPER, "§e排序: " + sortLabel,
            "§7左键: 切换排序方式"));

        // 货币类型过滤
        String currLabel = currencyFilter.isEmpty() ? "§f全部" :
            (currencyFilter.equals("vault") ? "§e金币" : "§a鸽币");
        inventory.setItem(48, createItem(Material.GOLD_NUGGET, "§e货币: " + currLabel,
            "§7左键: 切换过滤 (全部/金币/鸽币)"));

        // 我的求购
        inventory.setItem(49, createMyRequestsItem());
        
        // 发布求购
        inventory.setItem(50, createItem(Material.EMERALD, "§a§l发布求购",
            "§7点击发布新的求购信息", "", "§e左键点击发布"));
        
        // 刷新
        inventory.setItem(51, createItem(Material.SUNFLOWER, "§e刷新列表"));
        
        // 下一页
        int maxPage = requests.isEmpty() ? 0 : (requests.size() - 1) / ITEMS_PER_PAGE;
        if (currentPage < maxPage) inventory.setItem(52, createItem(Material.ARROW, "§a下一页"));
        
        // 关闭
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
        double total = request.getPrice() * remaining;
        double taxRate = plugin.getConfigManager().getConfig().getDouble("stall.tax-rate", 0.05);
        double afterTax = total * (1 - taxRate);

        List<String> lore = new ArrayList<>();
        if (request.isFeatured()) lore.add("§6★ §7置顶推广中");
        lore.add("");
        lore.add("§7求购者: §f" + request.getPlayerName());
        lore.add("§7需求数量: §f" + request.getAmount() + " 个");
        if (remaining < request.getAmount()) {
            lore.add("§7已收到: §a" + (request.getAmount() - remaining) + " 个");
            lore.add("§7剩余需求: §e" + remaining + " 个");
        }
        boolean isSelf = request.getPlayerUuid().equals(player.getUniqueId());
        if (isSelf) {
            lore.add("§7你的收购出价: §a" + plugin.getEconomyManager().formatCurrency(request.getPrice(), request.getCurrencyType()) + "§7/个");
            lore.add("§7已预付金额: §c" + plugin.getEconomyManager().formatCurrency(total, request.getCurrencyType()));
        } else {
            lore.add("§7买家出价: §a" + plugin.getEconomyManager().formatCurrency(request.getPrice(), request.getCurrencyType()) + "§7/个");
            lore.add("§7全量卖出可得: §a" + plugin.getEconomyManager().formatCurrency(total, request.getCurrencyType()));
            lore.add("§7扣税后实得: §e" + plugin.getEconomyManager().formatCurrency(afterTax, request.getCurrencyType()));
        }
        lore.add("");

        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
        lore.add("§7发布时间: §f" + sdf.format(new Date(request.getCreatedAt())));
        if (request.getExpireTime() > 0) {
            long left = request.getExpireTime() - System.currentTimeMillis();
            if (left > 0) {
                long days = left / 86400000L;
                long hours = (left % 86400000L) / 3600000L;
                lore.add("§7剩余有效期: §f" + (days > 0 ? days + "天" : "") + hours + "小时");
            } else {
                lore.add("§c已到期");
            }
        }
        lore.add("");

        if (request.getPlayerUuid().equals(player.getUniqueId())) {
            lore.add("§c右键 » 取消求购（全额退还）");
        } else {
            lore.add("§a左键 » 全量接单（" + remaining + " 个）");
            lore.add("§e右键 » 指定数量批量接单");
        }
        lore.add("§8§o#" + request.getId());
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMyRequestsItem() {
        int myCount = plugin.getBuyRequestManager().getPlayerRequestCount(player.getUniqueId());
        int maxCount = plugin.getBuyRequestManager().getMaxRequestsPerPlayer();
        
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§l我的求购");
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7已发布: §f" + myCount + "/" + maxCount);
            lore.add("");
            lore.add("§e点击查看我的求购");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
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
        
        if (slot == 45 && currentPage > 0) { currentPage--; updateTitle(); return; }

        // 搜索
        if (slot == 46) {
            if (event.isRightClick()) {
                searchKeyword = "";
                refreshList();
            } else {
                waitingForSearch = true;
                player.closeInventory();
                plugin.getMessageManager().sendRaw(player, "&e请输入搜索关键词 (输入 cancel 取消):");
            }
            return;
        }

        // 排序切换
        if (slot == 47) {
            sortMode = switch (sortMode) {
                case "time_desc"  -> "price_desc";
                case "price_desc" -> "price_asc";
                case "price_asc"  -> "time_asc";
                default           -> "time_desc";
            };
            refreshList();
            return;
        }

        // 货币类型过滤切换
        if (slot == 48) {
            currencyFilter = switch (currencyFilter) {
                case ""     -> "vault";
                case "vault"-> "nye";
                default     -> "";
            };
            refreshList();
            return;
        }

        if (slot == 49) { player.closeInventory(); new MyBuyRequestsGUI(plugin, player).open(); return; }
        if (slot == 50) { player.closeInventory(); new CreateBuyRequestGUI(plugin, player).open(); return; }

        if (slot == 51) { refreshList(); plugin.getMessageManager().sendRaw(player, "&a已刷新求购列表!"); return; }

        if (slot == 52) {
            int maxPage = requests.isEmpty() ? 0 : (requests.size() - 1) / ITEMS_PER_PAGE;
            if (currentPage < maxPage) { currentPage++; updateTitle(); }
            return;
        }

        if (slot == 53) { player.closeInventory(); return; }

        // 点击求购项
        if (isItemSlot(slot)) {
            int index = getRequestIndex(slot);
            if (index >= 0 && index < requests.size()) {
                BuyRequest request = requests.get(index);
                if (event.isRightClick() && request.getPlayerUuid().equals(player.getUniqueId())) {
                    plugin.getBuyRequestManager().cancelRequest(request.getId(), player);
                    refreshList();
                } else if (event.isLeftClick() && !request.getPlayerUuid().equals(player.getUniqueId())) {
                    plugin.getBuyRequestManager().fulfillRequest(request.getId(), player);
                    refreshList();
                } else if (event.isRightClick() && !request.getPlayerUuid().equals(player.getUniqueId())) {
                    // 指定数量批量接单
                    player.closeInventory();
                    new BatchFulfillGUI(plugin, player, request.getId()).open();
                }
            }
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
        return currentPage * ITEMS_PER_PAGE + row * 7 + col;
    }

    private void refreshList() {
        currentPage = 0;
        requests = plugin.getBuyRequestManager().getSortedRequests(sortMode, currencyFilter, searchKeyword);
        updateTitle();
    }

    private void updateTitle() {
        inventory = Bukkit.createInventory(null, 54, "§6§l求购市场 §7第" + (currentPage + 1) + "页");
        fillInventory();
        player.openInventory(inventory);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (!waitingForSearch) return;
        event.setCancelled(true);
        waitingForSearch = false;
        String msg = event.getMessage().trim();
        if (!msg.equalsIgnoreCase("cancel") && !msg.isEmpty()) searchKeyword = msg;
        Bukkit.getScheduler().runTask(plugin, this::refreshList);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory) && event.getPlayer().equals(player)) {
            HandlerList.unregisterAll(this);
        }
    }
}

package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.managers.GlobalMarketManager.GlobalMarketItem;
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
import java.util.Comparator;
import java.util.List;

public class GlobalMarketGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private Inventory inventory;
    private int currentPage = 0;
    private List<GlobalMarketItem> currentListings;
    private String searchFilter = null;
    private boolean waitingForSearch = false;
    private SortMode sortMode = SortMode.TIME_DESC;
    private CategoryFilter categoryFilter = CategoryFilter.ALL;
    private static final int ITEMS_PER_PAGE = 45;

    private enum SortMode {
        TIME_DESC("§e最新上架", Material.CLOCK),
        UNIT_PRICE_ASC("§a单价↑低到高", Material.GREEN_DYE),
        UNIT_PRICE_DESC("§c单价↓高到低", Material.RED_DYE),
        AMOUNT_DESC("§b数量↓多到少", Material.CHEST);
        final String label; final Material icon;
        SortMode(String label, Material icon) { this.label = label; this.icon = icon; }
        SortMode next() { return values()[(ordinal()+1) % values().length]; }
    }

    private enum CategoryFilter {
        ALL("§f全部分类", Material.COMPASS),
        BLOCK("§a建筑方块", Material.BRICKS),
        TOOL("§6工具/武器", Material.IRON_SWORD),
        FOOD("§e食物", Material.APPLE),
        COBBLEMON("§bCobblemon道具", Material.POTION),
        OTHER("§7其他", Material.PAPER);
        final String label; final Material icon;
        CategoryFilter(String label, Material icon) { this.label = label; this.icon = icon; }
        CategoryFilter next() { return values()[(ordinal()+1) % values().length]; }
    }

    public GlobalMarketGUI(PlayerStallCraft plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.currentListings = plugin.getGlobalMarketManager().getAllActiveListings();
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 54, "§6全服交易市场 §7第" + (currentPage + 1) + "页");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        applyFiltersAndSort();
        refreshDisplay();
        player.openInventory(inventory);
    }

    private void applyFiltersAndSort() {
        List<GlobalMarketItem> base = plugin.getGlobalMarketManager().getAllActiveListings();
        // 搜索过滤
        if (searchFilter != null) {
            String lowerName = searchFilter.toLowerCase().replace(" ", "").replace("_", "");
            List<GlobalMarketItem> filtered = new ArrayList<>();
            for (GlobalMarketItem l : base) if (matchesSearchFast(l, lowerName)) filtered.add(l);
            base = filtered;
        }
        // 分类过滤
        if (categoryFilter != CategoryFilter.ALL) {
            List<GlobalMarketItem> filtered = new ArrayList<>();
            for (GlobalMarketItem l : base) if (matchesCategory(l, categoryFilter)) filtered.add(l);
            base = filtered;
        }
        // 排序
        currentListings = sortListings(base);
    }

    private List<GlobalMarketItem> sortListings(List<GlobalMarketItem> list) {
        List<GlobalMarketItem> sorted = new ArrayList<>(list);
        switch (sortMode) {
            case UNIT_PRICE_ASC -> sorted.sort(Comparator.comparingDouble(l -> l.getPrice() / Math.max(1, l.getAmount())));
            case UNIT_PRICE_DESC -> sorted.sort(Comparator.comparingDouble((GlobalMarketItem l) -> l.getPrice() / Math.max(1, l.getAmount())).reversed());
            case TIME_DESC -> sorted.sort(Comparator.comparingInt(GlobalMarketItem::getId).reversed());
            case AMOUNT_DESC -> sorted.sort(Comparator.comparingInt(GlobalMarketItem::getAmount).reversed());
        }
        return sorted;
    }

    private boolean matchesCategory(GlobalMarketItem listing, CategoryFilter cat) {
        String itemType = listing.getItemType();
        if (itemType == null || itemType.isEmpty()) return cat == CategoryFilter.OTHER;
        if (cat == CategoryFilter.COBBLEMON) return itemType.startsWith("cobblemon:") || itemType.contains("COBBLEMON");
        try {
            Material mat = Material.matchMaterial(itemType.contains(":") ? itemType.split(":")[1].toUpperCase() : itemType);
            if (mat == null) return cat == CategoryFilter.OTHER;
            switch (cat) {
                case BLOCK -> { return mat.isBlock() && mat.isSolid(); }
                case TOOL -> { String n = mat.name(); return n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || n.endsWith("_BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT") || n.equals("SHIELD"); }
                case FOOD -> { return mat.isEdible(); }
                case OTHER -> { return !mat.isBlock() && !mat.isEdible() && !mat.name().endsWith("_SWORD") && !mat.name().endsWith("_AXE") && !mat.name().endsWith("_PICKAXE") && !mat.name().endsWith("_SHOVEL") && !mat.name().endsWith("_HOE"); }
                default -> { return true; }
            }
        } catch (Exception e) { return cat == CategoryFilter.OTHER; }
    }

    private void refreshDisplay() {
        inventory.clear();
        
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, currentListings.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            GlobalMarketItem listing = currentListings.get(i);
            ItemStack displayItem = createListingDisplay(listing);
            inventory.setItem(i - startIndex, displayItem);
        }
        
        // 底部控制栏
        // 上一页
        if (currentPage > 0) {
            ItemStack prevPage = createControlItem(Material.ARROW, "§a上一页", "§7点击翻到上一页");
            inventory.setItem(45, prevPage);
        }
        
        // 搜索
        ItemStack searchItem = createControlItem(Material.COMPASS, "§e搜索物品", 
            searchFilter != null ? "§7当前搜索: §f" + searchFilter : "§7点击输入物品名称搜索",
            "§7左键: 搜索",
            "§7右键: 清除搜索");
        inventory.setItem(47, searchItem);
        
        // 我的上架
        ItemStack myListings = createControlItem(Material.CHEST, "§b我的上架", "§7查看我在市场上架的物品");
        inventory.setItem(48, myListings);
        
        // 上架物品
        ItemStack listItem = createControlItem(Material.EMERALD, "§a上架物品", 
            "§7将手中物品上架到全服市场",
            "§7需要支付上架费用");
        inventory.setItem(49, listItem);
        
        // 刷新
        ItemStack refresh = createControlItem(Material.SUNFLOWER, "§e刷新", "§7刷新市场列表");
        inventory.setItem(50, refresh);
        
        // 关闭
        ItemStack close = createControlItem(Material.BARRIER, "§c关闭", "§7关闭市场界面");
        inventory.setItem(51, close);

        // 排序
        ItemStack sortItem = createControlItem(sortMode.icon, "§e排序: " + sortMode.label,
            "§7左键: 切换排序方式",
            "§7当前: §f" + sortMode.label);
        inventory.setItem(52, sortItem);

        // 分类筛选
        ItemStack catItem = createControlItem(categoryFilter.icon, "§e分类: " + categoryFilter.label,
            "§7左键: 切换分类筛选",
            "§7当前: §f" + categoryFilter.label);
        inventory.setItem(46, catItem);
        
        // 下一页
        int totalPages = (int) Math.ceil((double) currentListings.size() / ITEMS_PER_PAGE);
        if (currentPage < totalPages - 1) {
            ItemStack nextPage = createControlItem(Material.ARROW, "§a下一页", "§7点击翻到下一页");
            inventory.setItem(53, nextPage);
        }
        
        // 余额信息整合到刷新按钮的lore中
        double vaultBalance = plugin.getEconomyManager().getBalance(player, "vault");
        double nyeBalance = plugin.getEconomyManager().getBalance(player, "nye");
        List<String> refreshLore = new ArrayList<>();
        refreshLore.add("§7刷新市场列表");
        refreshLore.add("");
        refreshLore.add("§6══ 我的余额 ══");
        refreshLore.add("§7金币: §e" + plugin.getEconomyManager().formatCurrency(vaultBalance, "vault"));
        if (plugin.getEconomyManager().hasNYE()) {
            refreshLore.add("§7鸽币: §a" + plugin.getEconomyManager().formatCurrency(nyeBalance, "nye"));
        }
        refreshLore.add("");
        refreshLore.add("§7共 §f" + currentListings.size() + " §7件商品  第 §f" + (currentPage + 1) + "/" + Math.max(1, totalPages) + " §7页");
        ItemStack refreshWithInfo = createControlItem(Material.SUNFLOWER, "§e刷新",
            refreshLore.toArray(new String[0]));
        inventory.setItem(50, refreshWithInfo);
    }

    private ItemStack createListingDisplay(GlobalMarketItem listing) {
        ItemStack item = plugin.getGlobalMarketManager().deserializeItemPublic(listing.getItemData());
        if (item == null) {
            item = new ItemStack(Material.BARRIER);
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§7═══════════════════");
            lore.add("§7卖家: §f" + listing.getSellerName());
            lore.add("§7购买数量: §f" + listing.getAmount());
            lore.add("§7买入总价 §7(你支付): §e" + plugin.getEconomyManager().formatCurrency(listing.getPrice(), listing.getCurrencyType()));
            double unitPrice = listing.getPrice() / listing.getAmount();
            lore.add("§7买入单价: §e" + String.format("%.2f", unitPrice) + " " + (listing.getCurrencyType().equals("vault") ? "金币" : "鸽币") + "/个");
            
            long remainingTime = listing.getExpireTime() - System.currentTimeMillis();
            if (remainingTime > 0) {
                long hours = remainingTime / (1000 * 60 * 60);
                long minutes = (remainingTime % (1000 * 60 * 60)) / (1000 * 60);
                lore.add("§7剩余时间: §f" + hours + "小时" + minutes + "分钟");
            }
            
            lore.add("§7═══════════════════");
            if (listing.getSellerUuid().equals(player.getUniqueId())) {
                lore.add("§c右键点击下架");
            } else {
                lore.add("§a左键点击购买");
            }
            lore.add("§7商品ID: " + listing.getId());
            boolean watching = plugin.getMarketWatchManager().isWatching(
                player.getUniqueId(), listing.getItemType());
            lore.add(watching ? "§e★ 已关注 | Shift+左键取消关注" : "§7☆ Shift+左键 关注此类物品新上架提醒");
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    private ItemStack createControlItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(loreLines));
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
        
        // 商品区域 (0-44)
        if (slot < 45) {
            int listingIndex = currentPage * ITEMS_PER_PAGE + slot;
            if (listingIndex < currentListings.size()) {
                GlobalMarketItem listing = currentListings.get(listingIndex);
                
                // Shift+左键：切换关注状态
                if (event.isShiftClick() && event.isLeftClick()) {
                    String itemType = listing.getItemType();
                    if (itemType != null && !itemType.isEmpty()) {
                        if (plugin.getMarketWatchManager().isWatching(player.getUniqueId(), itemType)) {
                            plugin.getMarketWatchManager().removeWatch(player.getUniqueId(), itemType);
                            plugin.getMessageManager().sendRaw(player, "&7已取消关注: &f" + listing.getItemName());
                        } else {
                            plugin.getMarketWatchManager().addWatch(player.getUniqueId(), itemType);
                            plugin.getMessageManager().sendRaw(player, "&e★ 已关注: &f" + listing.getItemName() + " &7(有新上架时会提醒你)");
                        }
                        refreshDisplay();
                    }
                    return;
                }
                if (listing.getSellerUuid().equals(player.getUniqueId())) {
                    // 自己的商品，右键下架
                    if (event.isRightClick()) {
                        plugin.getGlobalMarketManager().cancelListing(player, listing.getId());
                        applyFiltersAndSort();
                        refreshDisplay();
                    }
                } else {
                    // 他人商品，左键购买
                    if (event.isLeftClick()) {
                        if (plugin.getGlobalMarketManager().purchaseItem(player, listing.getId())) {
                            applyFiltersAndSort();
                            refreshDisplay();
                        }
                    }
                }
            }
            return;
        }
        
        // 控制栏
        switch (slot) {
            case 45 -> { // 上一页
                if (currentPage > 0) {
                    currentPage--;
                    refreshDisplay();
                }
            }
            case 47 -> { // 搜索
                if (event.isRightClick()) {
                    // 清除搜索
                    searchFilter = null;
                    currentPage = 0;
                    applyFiltersAndSort();
                    refreshDisplay();
                } else {
                    // 开始搜索
                    waitingForSearch = true;
                    player.closeInventory();
                    plugin.getMessageManager().sendRaw(player, "&e请在聊天中输入要搜索的物品名称 (输入 'cancel' 取消):");
                }
            }
            case 48 -> { // 我的上架
                new MyGlobalListingsGUI(plugin, player).open();
            }
            case 49 -> { // 上架物品
                new ListToMarketGUI(plugin, player).open();
            }
            case 46 -> { // 分类筛选
                categoryFilter = categoryFilter.next();
                currentPage = 0;
                applyFiltersAndSort();
                refreshDisplay();
            }
            case 50 -> { // 刷新
                applyFiltersAndSort();
                refreshDisplay();
                plugin.getMessageManager().sendRaw(player, "&a已刷新市场列表!");
            }
            case 51 -> player.closeInventory(); // 关闭
            case 52 -> { // 排序
                sortMode = sortMode.next();
                applyFiltersAndSort();
                refreshDisplay();
            }
            case 53 -> { // 下一页
                int totalPages = (int) Math.ceil((double) currentListings.size() / ITEMS_PER_PAGE);
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    refreshDisplay();
                }
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (!waitingForSearch) return;
        
        event.setCancelled(true);
        waitingForSearch = false;
        
        String input = event.getMessage().trim();
        
        if (input.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, this::open);
            return;
        }
        
        searchFilter = input;
        currentPage = 0;
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            applyFiltersAndSort();
            open();
            plugin.getMessageManager().sendRaw(player, "&a搜索 \"" + input + "\" 找到 " + currentListings.size() + " 个结果");
        });
    }

    
    private boolean matchesSearchFast(GlobalMarketItem listing, String searchTerm) {
        // 1. 检查已存储的物品显示名（无需反序列化）
        String itemName = listing.getItemName();
        if (itemName != null && !itemName.isEmpty()) {
            if (itemName.toLowerCase().replace(" ", "").replace("_", "").contains(searchTerm)) return true;
        }
        
        // 2. 检查已存储的物品类型名
        String itemType = listing.getItemType();
        if (itemType != null && !itemType.isEmpty()) {
            String normalizedType = itemType.toLowerCase().replace("_", "").replace(" ", "");
            if (normalizedType.contains(searchTerm)) return true;
            // 去除命名空间前缀（如 cobblemon:power_belt → powerbelt）
            int colonIdx = normalizedType.indexOf(':');
            if (colonIdx >= 0 && normalizedType.substring(colonIdx + 1).contains(searchTerm)) return true;
        }
        
        // 3. 卖家名（支持搜索卖家）
        if (listing.getSellerName().toLowerCase().contains(searchTerm)) return true;
        
        return false;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;
        if (waitingForSearch) return;
        
        HandlerList.unregisterAll(this);
    }
}

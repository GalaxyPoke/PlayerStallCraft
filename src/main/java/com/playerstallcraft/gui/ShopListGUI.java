package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.Shop;
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

import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ShopListGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private Inventory inventory;
    private Inventory licenseInventory;
    private Inventory confirmInventory;
    private boolean inLicensePage = false;
    private boolean inConfirmPage = false;
    private String pendingCurrencyType = null;
    private boolean transacting = false;
    private int currentPage = 0;
    private List<Shop> shopList;
    private boolean showAvailableOnly = false;
    private static final int ITEMS_PER_PAGE = 45;
    private BukkitTask refreshTask;
    private String searchFilter = null;
    private boolean waitingForSearch = false;

    public ShopListGUI(PlayerStallCraft plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.shopList = plugin.getShopManager().getAllShops();
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 54, "§6商铺列表 §7第" + (currentPage + 1) + "页");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshDisplay();
        player.openInventory(inventory);
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().equals(inventory)) {
                refreshDisplay();
            }
        }, 200L, 200L);
    }

    private void refreshDisplay() {
        inventory.clear();
        
        List<Shop> displayShops = showAvailableOnly ? 
            plugin.getShopManager().getAvailableShops() : shopList;
        
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, displayShops.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            Shop shop = displayShops.get(i);
            ItemStack displayItem = createShopDisplay(shop);
            inventory.setItem(i - startIndex, displayItem);
        }
        
        // 底部控制栏（9格均匀分布）
        // 45: 上一页  46: 搜索  47: 筛选  48: 我的商铺  49: 执照  50: 商铺信息  51: 刷新  52: 关闭  53: 下一页
        if (currentPage > 0) {
            inventory.setItem(45, createControlItem(Material.ARROW, "§a上一页", "§7点击翻到上一页"));
        } else {
            inventory.setItem(45, createControlItem(Material.GRAY_STAINED_GLASS_PANE, "§7"));
        }

        // 搜索
        inventory.setItem(46, createControlItem(Material.COMPASS,
            searchFilter != null ? "§e搜索: §f" + searchFilter : "§e搜索商铺",
            searchFilter != null ? "§7左键: 重新搜索  右键: 清除" : "§7点击输入商铺名称搜索"));

        // 筛选
        inventory.setItem(47, createControlItem(
            showAvailableOnly ? Material.LIME_DYE : Material.GRAY_DYE,
            "§e筛选: " + (showAvailableOnly ? "§a仅空置" : "§7全部"),
            "§7点击切换显示模式"));

        // 我的商铺
        inventory.setItem(48, createControlItem(Material.CHEST, "§b我的商铺", "§7查看我租赁/购买的商铺"));

        // 营业执照（中央）
        inventory.setItem(49, createLicenseItem());

        // 商铺信息
        double rentPrice = plugin.getConfigManager().getConfig().getDouble("shop.rent-per-day", 500);
        double buyPrice = plugin.getConfigManager().getConfig().getDouble("shop.buy-price", 100000);
        inventory.setItem(50, createControlItem(Material.BOOK, "§6商铺信息",
            "§7总商铺数: §f" + shopList.size(),
            "§7空置商铺: §f" + plugin.getShopManager().getAvailableShops().size(),
            "",
            "§7租金: §f" + plugin.getEconomyManager().formatCurrency(rentPrice, "vault") + "/天",
            "§7购买价: §f" + plugin.getEconomyManager().formatCurrency(buyPrice, "vault")));

        // 刷新
        inventory.setItem(51, createControlItem(Material.SUNFLOWER, "§e刷新", "§7刷新商铺列表"));

        // 关闭
        inventory.setItem(52, createControlItem(Material.BARRIER, "§c关闭", "§7关闭界面"));

        int totalPages = (int) Math.ceil((double) displayShops.size() / ITEMS_PER_PAGE);
        if (currentPage < totalPages - 1) {
            inventory.setItem(53, createControlItem(Material.ARROW, "§a下一页", "§7点击翻到下一页"));
        } else {
            inventory.setItem(53, createControlItem(Material.GRAY_STAINED_GLASS_PANE, "§7"));
        }
    }

    private ItemStack createShopDisplay(Shop shop) {
        Material material;
        String status;
        
        if (shop.isOwned()) {
            material = Material.DIAMOND_BLOCK;
            status = "§b已购买";
        } else if (shop.isRented()) {
            material = Material.GOLD_BLOCK;
            status = "§e已租赁";
        } else {
            material = Material.IRON_BLOCK;
            status = "§a空置中";
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6" + shop.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add("§7═══════════════════");
            lore.add("§7状态: " + status);
            
            if (shop.hasOwner()) {
                lore.add("§7业主: §f" + shop.getOwnerName());
                if (shop.isRented()) {
                    lore.add("§7剩余租期: §f" + shop.getRemainingRentTime());
                }
                lore.add("§7货架耐久: §f" + shop.getShelfDurability() + "%");
            }
            
            lore.add("§7货架数量: §f" + shop.getShelfCount());
            lore.add("§7位置: §f" + formatLocation(shop.getLocation()));
            lore.add("§7═══════════════════");
            
            if (shop.isAvailable()) {
                lore.add("§a左键: 租赁商铺");
                lore.add("§a右键: 购买商铺");
            } else if (shop.getOwnerUuid() != null && shop.getOwnerUuid().equals(player.getUniqueId())) {
                lore.add("§e点击: 管理商铺");
            } else {
                lore.add("§7点击: 查看商铺");
            }
            lore.add("§bShift+右键: 传送到商铺");
            
            lore.add("§7商铺ID: " + shop.getId());
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        
        return item;
    }

    private ItemStack createLicenseItem() {
        boolean has = plugin.getLicenseManager().hasLicense(player);
        double vaultPrice = plugin.getConfigManager().getLicensePrice();
        double nyePrice = plugin.getConfig().getDouble("license.nye-price", 50000);
        String nyeName = plugin.getConfig().getString("currency.nye-currency-name", "鸽币");
        int days = plugin.getConfigManager().getLicenseDuration();

        List<String> lore = new ArrayList<>();
        lore.add("§7═══════════════════");
        if (has) {
            var lic = plugin.getLicenseManager().getLicense(player.getUniqueId());
            lore.add("§7状态: §a有效");
            lore.add("§7剩余: §f" + lic.getRemainingDays() + " 天");
            lore.add("§7税率优惠: §f8% §8(原15%)");
            lore.add("§7═══════════════════");
            lore.add("§7金币续期: §f" + plugin.getEconomyManager().formatCurrency(vaultPrice, "vault") + " / " + days + "天");
            lore.add("§7" + nyeName + "续期: §f" + String.format("%.0f", nyePrice) + " " + nyeName + " / " + days + "天");
            lore.add("");
            lore.add("§e点击续期");
        } else {
            lore.add("§7状态: §c未持有");
            lore.add("§7持有执照可享受 §f8% §7税率 §8(无执照15%)");
            lore.add("§7═══════════════════");
            lore.add("§7金币价格: §f" + plugin.getEconomyManager().formatCurrency(vaultPrice, "vault") + " / " + days + "天");
            lore.add("§7" + nyeName + "价格: §f" + String.format("%.0f", nyePrice) + " " + nyeName + " / " + days + "天");
            lore.add("");
            lore.add("§a点击购买");
        }
        return createControlItem(
                has ? Material.PAPER : Material.WRITABLE_BOOK,
                has ? "§6§l营业执照 §a✔" : "§6营业执照 §c✘",
                lore.toArray(new String[0]));
    }

    private String formatLocation(org.bukkit.Location loc) {
        return String.format("%s (%.0f, %.0f, %.0f)", 
            loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
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
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;

        // 二次确认页面
        if (inConfirmPage && event.getInventory().equals(confirmInventory)) {
            event.setCancelled(true);
            int s = event.getRawSlot();
            if (s < 0 || s >= 27) return;
            switch (s) {
                case 11 -> {
                    if (transacting) return;
                    transacting = true;
                    inConfirmPage = false;
                    boolean has = plugin.getLicenseManager().hasLicense(player);
                    if (has) plugin.getLicenseManager().renewLicense(player, pendingCurrencyType);
                    else plugin.getLicenseManager().purchaseLicense(player, pendingCurrencyType);
                    pendingCurrencyType = null;
                    transacting = false;
                    openLicensePage();
                }
                case 15 -> {
                    // 取消，回到执照页
                    inConfirmPage = false;
                    pendingCurrencyType = null;
                    openLicensePage();
                }
            }
            return;
        }

        // 执照子页面
        if (inLicensePage && event.getInventory().equals(licenseInventory)) {
            event.setCancelled(true);
            int s = event.getRawSlot();
            if (s < 0 || s >= 27) return;
            switch (s) {
                case 11 -> openConfirmPage("vault");
                case 15 -> openConfirmPage("nye");
                case 22 -> {
                    inLicensePage = false;
                    refreshDisplay();
                    player.openInventory(inventory);
                }
            }
            return;
        }

        if (!event.getInventory().equals(inventory)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        
        // 商铺区域
        if (slot < 45) {
            List<Shop> displayShops = showAvailableOnly ? 
                plugin.getShopManager().getAvailableShops() : shopList;
            int shopIndex = currentPage * ITEMS_PER_PAGE + slot;
            
            if (shopIndex < displayShops.size()) {
                Shop shop = displayShops.get(shopIndex);
                
                // Shift+左键传送到商铺
                if (event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                    player.closeInventory();
                    player.teleport(shop.getLocation());
                    plugin.getMessageManager().sendRaw(player, "§a已传送到商铺: §e" + shop.getName());
                    return;
                }
                
                if (shop.isAvailable()) {
                    if (event.isLeftClick()) {
                        // 租赁
                        new ShopRentGUI(plugin, player, shop).open();
                    } else if (event.isRightClick()) {
                        // 购买
                        new ShopBuyGUI(plugin, player, shop).open();
                    }
                } else if (shop.getOwnerUuid() != null && shop.getOwnerUuid().equals(player.getUniqueId())) {
                    // 管理自己的商铺
                    new ShopManageGUI(plugin, player, shop).open();
                } else {
                    // 查看他人商铺
                    new ShopViewGUI(plugin, player, shop).open();
                }
            }
            return;
        }
        
        // 控制栏（对应新槽位布局）
        switch (slot) {
            case 45 -> {
                if (currentPage > 0) { currentPage--; refreshDisplay(); }
            }
            case 46 -> {
                if (event.isRightClick() && searchFilter != null) {
                    searchFilter = null;
                    shopList = plugin.getShopManager().getAllShops();
                    currentPage = 0;
                    refreshDisplay();
                } else {
                    waitingForSearch = true;
                    cancelRefreshTask();
                    player.closeInventory();
                    plugin.getMessageManager().sendRaw(player, "§e请在聊天中输入商铺名称进行搜索 (输入 'cancel' 取消):");
                }
            }
            case 47 -> {
                showAvailableOnly = !showAvailableOnly;
                currentPage = 0;
                refreshDisplay();
            }
            case 48 -> new MyShopsGUI(plugin, player).open();
            case 49 -> openLicensePage();
            case 50 -> { /* 商铺信息，纯展示，无操作 */ }
            case 51 -> {
                shopList = plugin.getShopManager().getAllShops();
                refreshDisplay();
            }
            case 52 -> player.closeInventory();
            case 53 -> {
                List<Shop> displayShops = showAvailableOnly ?
                    plugin.getShopManager().getAvailableShops() : shopList;
                int totalPages = (int) Math.ceil((double) displayShops.size() / ITEMS_PER_PAGE);
                if (currentPage < totalPages - 1) { currentPage++; refreshDisplay(); }
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player) || !waitingForSearch) return;
        event.setCancelled(true);
        waitingForSearch = false;
        String input = event.getMessage().trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!input.equalsIgnoreCase("cancel")) {
                searchFilter = input;
                List<com.playerstallcraft.models.Shop> all = plugin.getShopManager().getAllShops();
                final String lower = input.toLowerCase();
                shopList = all.stream()
                    .filter(s -> s.getName().toLowerCase().contains(lower))
                    .collect(Collectors.toList());
                currentPage = 0;
            }
            open();
        });
    }

    // ───────────────────── 执照子页面 ─────────────────────

    private void openConfirmPage(String currencyType) {
        pendingCurrencyType = currencyType;
        inLicensePage = false;
        inConfirmPage = true;
        boolean has = plugin.getLicenseManager().hasLicense(player);
        String action = has ? "续期" : "购买";
        double price = currencyType.equals("nye")
            ? plugin.getConfig().getDouble("license.nye-price", 50000)
            : plugin.getConfigManager().getLicensePrice();
        String nyeName = plugin.getConfig().getString("currency.nye-currency-name", "鸽币");
        String priceStr = currencyType.equals("nye")
            ? String.format("%.0f", price) + " " + nyeName
            : plugin.getEconomyManager().formatCurrency(price, "vault");
        int days = plugin.getConfigManager().getLicenseDuration();

        confirmInventory = Bukkit.createInventory(null, 27, "§6营业执照 - 确认" + action);
        // 信息展示
        confirmInventory.setItem(4, createControlItem(Material.WRITABLE_BOOK, "§e确认" + action + "营业执照",
            "§7货币: §f" + (currencyType.equals("nye") ? nyeName : "金币"),
            "§7费用: §c" + priceStr,
            "§7有效期: §f" + days + " 天",
            "",
            "§c请确认操作，扣款后不退还"));
        // 确认按钮
        confirmInventory.setItem(11, createControlItem(Material.EMERALD_BLOCK,
            "§a§l确认" + action,
            "§7点击确认，立即扣款"));
        // 取消按钮
        confirmInventory.setItem(15, createControlItem(Material.REDSTONE_BLOCK,
            "§c§l取消",
            "§7点击返回执照页面"));
        player.openInventory(confirmInventory);
    }

    private void openLicensePage() {
        inLicensePage = true;
        boolean has = plugin.getLicenseManager().hasLicense(player);
        double vaultPrice = plugin.getConfigManager().getLicensePrice();
        double nyePrice = plugin.getConfig().getDouble("license.nye-price", 50000);
        String nyeName = plugin.getConfig().getString("currency.nye-currency-name", "鸽币");
        int days = plugin.getConfigManager().getLicenseDuration();
        String action = has ? "续期" : "购买";

        licenseInventory = Bukkit.createInventory(null, 27,
                "§6营业执照 - " + (has ? "续期" : "购买"));

        // 状态展示
        List<String> statusLore = new ArrayList<>();
        if (has) {
            var lic = plugin.getLicenseManager().getLicense(player.getUniqueId());
            statusLore.add("§7状态: §a有效");
            statusLore.add("§7剩余: §f" + lic.getRemainingDays() + " 天");
        } else {
            statusLore.add("§7状态: §c未持有");
            statusLore.add("§7持有执照可享受 §f8%§7 税率 §8(无执照15%)");
        }
        licenseInventory.setItem(4, createControlItem(
                has ? Material.PAPER : Material.WRITABLE_BOOK,
                has ? "§6§l营业执照 §a✔" : "§6营业执照 §c✘",
                statusLore.toArray(new String[0])));

        // 金币按鈕
        double vaultBalance = plugin.getEconomyManager().getBalance(player, "vault");
        boolean vaultOk = vaultBalance >= vaultPrice;
        String vaultBalanceLine = vaultOk
            ? "§7你的余额: §a" + plugin.getEconomyManager().formatCurrency(vaultBalance, "vault") + "§7，点击" + action
            : "§7你的余额: §c" + plugin.getEconomyManager().formatCurrency(vaultBalance, "vault") + "§c，余额不足";
        licenseInventory.setItem(11, createControlItem(vaultOk ? Material.GOLD_INGOT : Material.IRON_INGOT,
                "§e金币" + action,
                "§7价格: §f" + plugin.getEconomyManager().formatCurrency(vaultPrice, "vault"),
                "§7有效期: §f" + days + " 天",
                "",
                vaultBalanceLine));

        // 鸽币按鈕
        double nyeBalance = plugin.getEconomyManager().getBalance(player, "nye");
        boolean nyeOk = nyeBalance >= nyePrice;
        String nyeBalanceLine = nyeOk
            ? "§7你的余额: §a" + String.format("%.0f", nyeBalance) + " " + nyeName + "§7，点击" + action
            : "§7你的余额: §c" + String.format("%.0f", nyeBalance) + " " + nyeName + "§c，余额不足";
        licenseInventory.setItem(15, createControlItem(nyeOk ? Material.EMERALD : Material.GLASS,
                "§b" + nyeName + action,
                "§7价格: §f" + String.format("%.0f", nyePrice) + " " + nyeName,
                "§7有效期: §f" + days + " 天",
                "",
                nyeBalanceLine));

        // 返回
        licenseInventory.setItem(22, createControlItem(Material.ARROW, "§7返回", "§7返回商铺列表"));

        player.openInventory(licenseInventory);
    }

    private void cancelRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getPlayer().equals(player)) return;
        Inventory closed = event.getInventory();
        if (closed.equals(inventory)) {
            // 主页关闭：正在跳到执照页/确认页时不注销
            if (!inLicensePage && !inConfirmPage) {
                cancelRefreshTask();
                HandlerList.unregisterAll(this);
            }
        } else if (closed.equals(licenseInventory)) {
            // 执照子页关闭：跳到确认页或返回主页时不注销
            if (inLicensePage) inLicensePage = false; // 只清标志，不注销
        } else if (confirmInventory != null && closed.equals(confirmInventory)) {
            // 确认页被 Esc 关闭（未点按钮）才注销
            if (inConfirmPage) {
                inConfirmPage = false;
                cancelRefreshTask();
                HandlerList.unregisterAll(this);
            }
        }
    }
}

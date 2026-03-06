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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ShopRentGUI implements Listener {

    // 租赁单位
    private enum RentUnit {
        MINUTE("分钟", "分", 1, 10, 1, 1440),
        HOUR("小时", "时", 1, 6, 1, 168),
        DAY("天", "天", 1, 7, 1, 365),
        MONTH("月", "月", 1, 3, 1, 12);

        final String label;
        final String shortLabel;
        final int smallStep;
        final int bigStep;
        final int min;
        final int max;

        RentUnit(String label, String shortLabel, int smallStep, int bigStep, int min, int max) {
            this.label = label;
            this.shortLabel = shortLabel;
            this.smallStep = smallStep;
            this.bigStep = bigStep;
            this.min = min;
            this.max = max;
        }

        RentUnit next() {
            RentUnit[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }

        double toEquivDays(int amount) {
            return switch (this) {
                case MINUTE -> amount / 1440.0;
                case HOUR   -> amount / 24.0;
                case DAY    -> amount;
                case MONTH  -> amount * 30.0;
            };
        }

    }

    private final PlayerStallCraft plugin;
    private final Player player;
    private final Shop shop;
    private Inventory inventory;
    private int selectedAmount = 7;
    private RentUnit rentUnit = RentUnit.DAY;
    private String currencyType = "vault";
    private boolean transacting = false;
    private boolean inConfirmPage = false;
    private boolean isRefreshing = false;
    private Inventory confirmInventory;

    public ShopRentGUI(PlayerStallCraft plugin, Player player, Shop shop) {
        this.plugin = plugin;
        this.player = player;
        this.shop = shop;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 27, "§6租赁商铺: " + shop.getName());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshDisplay();
        player.openInventory(inventory);
    }

    private double getRentPerUnit() {
        boolean nye = currencyType.equals("nye");
        return switch (rentUnit) {
            case MINUTE -> plugin.getConfig().getDouble(nye ? "shop.nye-rent-per-minute" : "shop.rent-per-minute", nye ? 5 : 1);
            case HOUR   -> plugin.getConfig().getDouble(nye ? "shop.nye-rent-per-hour"   : "shop.rent-per-hour",   nye ? 150 : 30);
            case DAY    -> plugin.getConfig().getDouble(nye ? "shop.nye-rent-per-day"    : "shop.rent-per-day",    nye ? 2500 : 500);
            case MONTH  -> plugin.getConfig().getDouble(nye ? "shop.nye-rent-per-month"  : "shop.rent-per-month",  nye ? 60000 : 12000);
        };
    }

    private double calcDiscount(double totalRent, double equivDays) {
        int tier2Days = plugin.getConfig().getInt("shop.rent-discount.tier2-days", 90);
        int tier1Days = plugin.getConfig().getInt("shop.rent-discount.tier1-days", 30);
        double tier2Rate = plugin.getConfig().getDouble("shop.rent-discount.tier2-rate", 0.90);
        double tier1Rate = plugin.getConfig().getDouble("shop.rent-discount.tier1-rate", 0.95);
        if (tier2Days > 0 && equivDays >= tier2Days) return totalRent * tier2Rate;
        if (tier1Days > 0 && equivDays >= tier1Days) return totalRent * tier1Rate;
        return totalRent;
    }

    private void refreshDisplay() {
        isRefreshing = true;
        inventory.clear();

        double rentPerUnit = getRentPerUnit();
        double totalRent = rentPerUnit * selectedAmount;
        double equivDays = rentUnit.toEquivDays(selectedAmount);
        double finalRent = calcDiscount(totalRent, equivDays);
        boolean discounted = finalRent < totalRent;

        String nyeName = plugin.getConfig().getString("currency.nye-currency-name", "鸽币");

        // 商铺信息
        inventory.setItem(4, createItem(Material.IRON_BLOCK, "§6" + shop.getName(),
            "§7支持摆放货架: §f" + shop.getUnlockedShelfSlots() + " 个",
            "§7位置: §f" + formatLocation(shop.getLocation())));

        // 单位切换按钮
        inventory.setItem(2, createItem(Material.COMPARATOR, "§b计时单位: §f" + rentUnit.label,
            "§7点击切换: 分钟 → 小时 → 天 → 月",
            "§8当前: §e" + rentUnit.label));

        // 货币类型切换
        Material currencyMaterial = currencyType.equals("nye") ? Material.EMERALD : Material.GOLD_INGOT;
        String currencyName = currencyType.equals("nye") ? nyeName : "金币";
        inventory.setItem(8, createItem(currencyMaterial, "§e当前货币: §f" + currencyName, "", "§7点击切换货币类型"));

        // 减少按钮
        inventory.setItem(10, createItem(Material.RED_STAINED_GLASS_PANE,
            "§c-" + rentUnit.bigStep + rentUnit.shortLabel, "§7点击减少"));
        inventory.setItem(11, createItem(Material.ORANGE_STAINED_GLASS_PANE,
            "§c-" + rentUnit.smallStep + rentUnit.shortLabel, "§7点击减少"));

        // 租期显示
        List<String> daysLore = new ArrayList<>();
        daysLore.add("§7每" + rentUnit.label + "租金: §f" + plugin.getEconomyManager().formatCurrency(rentPerUnit, currencyType));
        if (discounted) {
            daysLore.add("§7原价: §8" + plugin.getEconomyManager().formatCurrency(totalRent, currencyType));
            int discount = (int)Math.round((1 - finalRent / totalRent) * 100);
            daysLore.add("§7折后: §a" + plugin.getEconomyManager().formatCurrency(finalRent, currencyType) + " §8(-" + discount + "%)");
        } else {
            daysLore.add("§7总租金: §f" + plugin.getEconomyManager().formatCurrency(finalRent, currencyType));
        }
        inventory.setItem(13, createItem(Material.CLOCK,
            "§e租期: §f" + selectedAmount + " " + rentUnit.label,
            daysLore.toArray(new String[0])));

        // 增加按钮
        inventory.setItem(15, createItem(Material.LIME_STAINED_GLASS_PANE,
            "§a+" + rentUnit.smallStep + rentUnit.shortLabel, "§7点击增加"));
        inventory.setItem(16, createItem(Material.GREEN_STAINED_GLASS_PANE,
            "§a+" + rentUnit.bigStep + rentUnit.shortLabel, "§7点击增加"));

        isRefreshing = false;

        // 确认按钮
        boolean hasLicense = plugin.getLicenseManager().hasLicense(player);
        double balance = plugin.getEconomyManager().getBalance(player, currencyType);
        boolean canAfford = balance >= finalRent;

        ItemStack confirmItem;
        if (!hasLicense) {
            confirmItem = createItem(Material.BARRIER, "§c无法租赁", "§c需要营业执照!");
        } else {
            String balanceLine = canAfford
                ? "§7你的余额: §a" + plugin.getEconomyManager().formatCurrency(balance, currencyType) + "§7，点击确认租赁"
                : "§7你的余额: §c" + plugin.getEconomyManager().formatCurrency(balance, currencyType) + "§c，余额不足";
            List<String> lore = new ArrayList<>();
            lore.add("§7租期: §f" + selectedAmount + " " + rentUnit.label);
            if (discounted) {
                lore.add("§7原价: §8" + plugin.getEconomyManager().formatCurrency(totalRent, currencyType));
                lore.add("§7折后: §a" + plugin.getEconomyManager().formatCurrency(finalRent, currencyType));
            } else {
                lore.add("§7总租金: §f" + plugin.getEconomyManager().formatCurrency(finalRent, currencyType));
                int t1 = plugin.getConfig().getInt("shop.rent-discount.tier1-days", 30);
                if (t1 > 0 && rentUnit == RentUnit.DAY) lore.add("§8租满" + t1 + "天可享折扣");
            }
            lore.add("");
            lore.add(balanceLine);
            confirmItem = createItem(canAfford ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
                canAfford ? "§a确认租赁" : "§c余额不足",
                lore.toArray(new String[0]));
        }
        inventory.setItem(22, confirmItem);

        inventory.setItem(18, createItem(Material.ARROW, "§e返回", "§7返回商铺列表"));
        inventory.setItem(26, createItem(Material.BARRIER, "§c取消", "§7关闭界面"));
    }

    private void openConfirmPage() {
        inConfirmPage = true;
        double rentPerUnit = getRentPerUnit();
        double totalRent = rentPerUnit * selectedAmount;
        double equivDays = rentUnit.toEquivDays(selectedAmount);
        double finalRent = calcDiscount(totalRent, equivDays);
        boolean discounted = finalRent < totalRent;
        String nyeName = plugin.getConfig().getString("currency.nye-currency-name", "鸽币");
        String currencyName = currencyType.equals("nye") ? nyeName : "金币";
        String priceStr = plugin.getEconomyManager().formatCurrency(finalRent, currencyType);

        confirmInventory = Bukkit.createInventory(null, 27, "§6确认租赁: " + shop.getName());
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7商铺: §f" + shop.getName());
        infoLore.add("§7租期: §f" + selectedAmount + " " + rentUnit.label);
        infoLore.add("§7货币: §f" + currencyName);
        if (discounted) {
            infoLore.add("§7原价: §8" + plugin.getEconomyManager().formatCurrency(totalRent, currencyType));
            infoLore.add("§7折后总租金: §a" + priceStr);
        } else {
            infoLore.add("§7总租金: §c" + priceStr);
        }
        infoLore.add("");
        infoLore.add("§c请确认操作，扣款后不退还");
        confirmInventory.setItem(4, createItem(Material.IRON_BLOCK, "§e确认租赁商铺", infoLore.toArray(new String[0])));
        confirmInventory.setItem(11, createItem(Material.EMERALD_BLOCK, "§a§l确认租赁", "§7点击确认，立即扣款"));
        confirmInventory.setItem(15, createItem(Material.REDSTONE_BLOCK, "§c§l取消", "§7点击返回租赁界面"));
        player.openInventory(confirmInventory);
    }

    private String formatLocation(org.bukkit.Location loc) {
        return String.format("%s (%.0f, %.0f, %.0f)",
            loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines.length > 0) meta.setLore(Arrays.asList(loreLines));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;
        if (event.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) { event.setCancelled(true); return; }

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
                    if (plugin.getShopManager().rentShop(player, shop.getId(), selectedAmount, currencyType, rentUnit.name().toLowerCase())) {
                        player.closeInventory();
                    } else {
                        transacting = false;
                        player.openInventory(inventory);
                    }
                }
                case 15 -> { inConfirmPage = false; player.openInventory(inventory); }
            }
            return;
        }

        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();

        switch (slot) {
            case 10 -> { selectedAmount = Math.max(rentUnit.min, selectedAmount - rentUnit.bigStep); refreshDisplay(); }
            case 11 -> { selectedAmount = Math.max(rentUnit.min, selectedAmount - rentUnit.smallStep); refreshDisplay(); }
            case 15 -> { selectedAmount = Math.min(rentUnit.max, selectedAmount + rentUnit.smallStep); refreshDisplay(); }
            case 16 -> { selectedAmount = Math.min(rentUnit.max, selectedAmount + rentUnit.bigStep); refreshDisplay(); }
            case 2 -> {
                rentUnit = rentUnit.next();
                selectedAmount = rentUnit.min;
                refreshDisplay();
            }
            case 8 -> { currencyType = currencyType.equals("vault") ? "nye" : "vault"; refreshDisplay(); }
            case 18 -> new ShopListGUI(plugin, player).open();
            case 22 -> {
                if (!plugin.getLicenseManager().hasLicense(player)) {
                    plugin.getMessageManager().sendRaw(player, "&c需要营业执照才能租赁商铺!");
                    return;
                }
                openConfirmPage();
            }
            case 26 -> player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getPlayer().equals(player)) return;
        Inventory closed = event.getInventory();
        if (closed.equals(inventory)) {
            // 主页关闭：若正在过渡到确认页则不注销
            if (!inConfirmPage && !isRefreshing) HandlerList.unregisterAll(this);
        } else if (confirmInventory != null && closed.equals(confirmInventory)) {
            if (inConfirmPage) {
                // Esc 关闭确认页（未点按钮）
                inConfirmPage = false;
                HandlerList.unregisterAll(this);
            } else if (transacting) {
                // 成功扰款后关闭
                HandlerList.unregisterAll(this);
            }
            // else: cancel 返回主页，保留监听器
        }
    }
}

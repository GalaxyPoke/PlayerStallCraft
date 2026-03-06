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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class ListToMarketGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private Inventory inventory;
    
    private ItemStack selectedItem = null;
    private double price = 0;
    private String currencyType = "vault";
    private int duration = 24; // 默认24小时
    
    private boolean waitingForPrice = false;
    private boolean waitingForDuration = false;
    private boolean registered = false;

    public ListToMarketGUI(PlayerStallCraft plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 54, "§a上架到全服市场");
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        refreshDisplay();
        player.openInventory(inventory);
    }

    private void refreshDisplay() {
        inventory.clear();
        
        // 物品放置区域提示
        ItemStack itemSlotHint = createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7放入要上架的物品", 
            "§7将物品放在此处");
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, itemSlotHint);
        }
        
        // 如果有选择的物品，显示在中间
        if (selectedItem != null) {
            inventory.setItem(4, selectedItem.clone());
        } else {
            inventory.setItem(4, createItem(Material.HOPPER, "§e点击放入物品",
                "§7左键: §f1个",
                "§7右键: §f半组",
                "§7Shift+点击: §f全部"));
        }
        
        // 分隔线
        ItemStack separator = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 9; i < 18; i++) {
            inventory.setItem(i, separator);
        }
        
        // 设置区域
        // 价格设置
        ItemStack priceItem = createItem(Material.GOLD_INGOT, "§e设置售出价格", 
            "§7商品售价 §7(买家支付你): §f" + (price > 0 ? plugin.getEconomyManager().formatCurrency(price, currencyType) : "未设置"),
            "",
            "§a点击设置售价");
        inventory.setItem(20, priceItem);
        
        // 货币类型
        ItemStack currencyItem = createItem(
            currencyType.equals("vault") ? Material.GOLD_NUGGET : Material.EMERALD,
            "§e货币类型",
            "§7当前: §f" + (currencyType.equals("vault") ? "金币(Vault)" : "NYE代币"),
            "",
            "§a点击切换货币类型");
        inventory.setItem(22, currencyItem);
        
        // 上架时长
        ItemStack durationItem = createItem(Material.CLOCK, "§e上架时长",
            "§7当前: §f" + duration + " 小时",
            "",
            "§a左键: +6小时",
            "§a右键: -6小时",
            "§aShift+左键: 自定义时长");
        inventory.setItem(24, durationItem);
        
        // 上架费用提示 (根据货币类型显示)
        double listingFee = plugin.getConfigManager().getConfig().getDouble("global-market.listing-fee", 100);
        ItemStack feeInfo = createItem(Material.PAPER, "§6上架费用",
            "§7基础费用: §f" + plugin.getEconomyManager().formatCurrency(listingFee, currencyType),
            "§7使用: §f" + (currencyType.equals("vault") ? "金币" : "鸽币") + " §7支付",
            "§7(每次上架都需支付)");
        inventory.setItem(31, feeInfo);
        
        // 底部控制栏
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, separator);
        }
        
        // 返回市场
        ItemStack backItem = createItem(Material.ARROW, "§e返回市场", "§7返回全服交易市场");
        inventory.setItem(45, backItem);
        
        // 确认上架
        boolean canList = selectedItem != null && price > 0;
        ItemStack confirmItem;
        if (canList) {
            confirmItem = createItem(Material.EMERALD_BLOCK, "§a确认上架",
                "§7════════════════",
                "§7商品售价 §7(买家支付你): §e" + plugin.getEconomyManager().formatCurrency(price, currencyType),
                "§7上架手续费 §7(你展加支付): §c" + plugin.getEconomyManager().formatCurrency(listingFee, currencyType),
                "§7上架时长: §f" + duration + " 小时",
                "§7════════════════",
                "§8(上架成功后买家购买，你将获得扣税后收入)",
                "",
                "§a点击确认上架");
        } else {
            confirmItem = createItem(Material.GRAY_STAINED_GLASS_PANE, "§7确认上架",
                "§c请先设置物品和价格");
        }
        inventory.setItem(49, confirmItem);
        
        // 关闭
        ItemStack closeItem = createItem(Material.BARRIER, "§c取消", "§7关闭界面");
        inventory.setItem(53, closeItem);
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
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;
        
        // 点击自己背包中的物品
        if (event.getClickedInventory() != null && 
            event.getClickedInventory().equals(player.getInventory()) &&
            event.getCurrentItem() != null && 
            !event.getCurrentItem().getType().isAir()) {
            
            if (inventory.getViewers().contains(player)) {
                event.setCancelled(true);
                ItemStack clicked = event.getCurrentItem();
                selectedItem = clicked.clone();
                if (event.isShiftClick()) {
                    // Shift+点击：全部
                } else if (event.isRightClick()) {
                    // 右键：半组（至少1个）
                    selectedItem.setAmount(Math.max(1, clicked.getAmount() / 2));
                } else {
                    // 左键：1个
                    selectedItem.setAmount(1);
                }
                refreshDisplay();
                return;
            }
        }
        
        if (!event.getInventory().equals(inventory)) return;
        
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        
        switch (slot) {
            case 4 -> {
                // 清除已选物品
                if (selectedItem != null) {
                    selectedItem = null;
                    refreshDisplay();
                }
            }
            case 20 -> {
                // 设置价格
                waitingForPrice = true;
                player.closeInventory();
                plugin.getMessageManager().sendRaw(player, "&e请输入价格 (输入 'cancel' 取消):");
            }
            case 22 -> {
                // 切换货币类型
                currencyType = currencyType.equals("vault") ? "nye" : "vault";
                refreshDisplay();
            }
            case 24 -> {
                // 上架时长
                if (event.isShiftClick() && event.isLeftClick()) {
                    waitingForDuration = true;
                    player.closeInventory();
                    plugin.getMessageManager().sendRaw(player, "&e请输入上架时长(小时) (输入 'cancel' 取消):");
                } else if (event.isLeftClick()) {
                    duration = Math.min(168, duration + 6); // 最多7天
                    refreshDisplay();
                } else if (event.isRightClick()) {
                    duration = Math.max(6, duration - 6); // 最少6小时
                    refreshDisplay();
                }
            }
            case 45 -> new GlobalMarketGUI(plugin, player).open();
            case 49 -> {
                // 确认上架
                if (selectedItem != null && price > 0) {
                    confirmListing();
                }
            }
            case 53 -> player.closeInventory();
        }
    }

    private void confirmListing() {
        // 检查玩家是否还有这个物品
        if (!player.getInventory().containsAtLeast(selectedItem, selectedItem.getAmount())) {
            plugin.getMessageManager().sendRaw(player, "&c你的背包中没有足够的物品!");
            return;
        }
        
        // 从背包中移除物品
        player.getInventory().removeItem(selectedItem);
        
        // 临时设置手持物品以便上架
        ItemStack originalMainHand = player.getInventory().getItemInMainHand();
        player.getInventory().setItemInMainHand(selectedItem);
        
        boolean success = plugin.getGlobalMarketManager().listItem(player, selectedItem, price, currencyType, duration);
        
        // 恢复手持物品
        player.getInventory().setItemInMainHand(originalMainHand);
        
        if (success) {
            player.closeInventory();
        } else {
            // 上架失败，返还物品
            player.getInventory().addItem(selectedItem);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (!waitingForPrice && !waitingForDuration) return;
        
        event.setCancelled(true);
        String input = event.getMessage().trim();
        
        if (input.equalsIgnoreCase("cancel")) {
            waitingForPrice = false;
            waitingForDuration = false;
            Bukkit.getScheduler().runTask(plugin, this::open);
            return;
        }
        
        if (waitingForPrice) {
            waitingForPrice = false;
            try {
                double inputPrice = Double.parseDouble(input);
                if (inputPrice <= 0) {
                    plugin.getMessageManager().sendRaw(player, "&c价格必须大于0!");
                } else {
                    price = inputPrice;
                }
            } catch (NumberFormatException e) {
                plugin.getMessageManager().sendRaw(player, "&c无效的价格!");
            }
            Bukkit.getScheduler().runTask(plugin, this::open);
        } else if (waitingForDuration) {
            waitingForDuration = false;
            try {
                int inputDuration = Integer.parseInt(input);
                if (inputDuration < 1 || inputDuration > 168) {
                    plugin.getMessageManager().sendRaw(player, "&c时长必须在1-168小时之间!");
                } else {
                    duration = inputDuration;
                }
            } catch (NumberFormatException e) {
                plugin.getMessageManager().sendRaw(player, "&c无效的时长!");
            }
            Bukkit.getScheduler().runTask(plugin, this::open);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!event.getPlayer().equals(player)) return;
        if (waitingForPrice || waitingForDuration) return;
        
        HandlerList.unregisterAll(this);
    }
}

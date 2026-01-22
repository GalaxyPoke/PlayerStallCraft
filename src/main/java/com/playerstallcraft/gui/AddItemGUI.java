package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.managers.MessageManager;
import com.playerstallcraft.models.PlayerStall;
import com.playerstallcraft.models.StallItem;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AddItemGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private final PlayerStall stall;
    private final Inventory inventory;
    
    // 存储玩家正在设置价格的状态
    private static final Map<UUID, AddItemGUI> awaitingPrice = new HashMap<>();
    
    // 当前选择的物品和货币
    private ItemStack selectedItem;
    private String currencyType = "vault";
    private double price = 0;
    private boolean awaitingPriceInput = false;

    public AddItemGUI(PlayerStallCraft plugin, Player player, PlayerStall stall) {
        this.plugin = plugin;
        this.player = player;
        this.stall = stall;
        
        String title = MessageManager.colorize("&6&l上架商品");
        this.inventory = Bukkit.createInventory(null, 54, title);
        
        setupInventory();
    }

    private void setupInventory() {
        inventory.clear();
        
        // 顶部装饰边框
        ItemStack topBorder = createItem(Material.YELLOW_STAINED_GLASS_PANE, "&e&l▬▬▬▬▬▬▬▬▬");
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, topBorder);
        }
        
        // 中间信息区域
        ItemStack sideBorder = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        inventory.setItem(9, sideBorder);
        inventory.setItem(17, sideBorder);
        inventory.setItem(18, sideBorder);
        inventory.setItem(26, sideBorder);
        inventory.setItem(27, sideBorder);
        inventory.setItem(35, sideBorder);
        
        // 放置物品区域 (槽位 13)
        if (selectedItem == null) {
            ItemStack placeHolder = createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, 
                    "&f&l放入商品", 
                    "&7点击这里",
                    "&7然后将背包中的物品",
                    "&7拖放到此处");
            inventory.setItem(13, placeHolder);
        } else {
            ItemStack displayItem = selectedItem.clone();
            ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(MessageManager.colorize("&a✓ 已选择此物品"));
                lore.add(MessageManager.colorize("&7点击移除"));
                meta.setLore(lore);
                displayItem.setItemMeta(meta);
            }
            inventory.setItem(13, displayItem);
        }
        
        // 价格设置按钮
        List<String> priceLore = new ArrayList<>();
        priceLore.add("");
        if (price > 0) {
            priceLore.add(MessageManager.colorize("&a当前价格: &f" + plugin.getEconomyManager().formatCurrency(price, currencyType)));
        } else {
            priceLore.add(MessageManager.colorize("&c尚未设置价格"));
        }
        priceLore.add("");
        priceLore.add(MessageManager.colorize("&e点击设置价格"));
        ItemStack priceButton = createItem(Material.GOLD_INGOT, "&6&l设置价格", priceLore);
        inventory.setItem(20, priceButton);
        
        // 快捷价格按钮
        inventory.setItem(28, createItem(Material.GOLD_NUGGET, "&e+10", "&7点击增加10"));
        inventory.setItem(29, createItem(Material.GOLD_NUGGET, "&e+100", "&7点击增加100"));
        inventory.setItem(30, createItem(Material.GOLD_NUGGET, "&e+1000", "&7点击增加1000"));
        inventory.setItem(37, createItem(Material.IRON_NUGGET, "&c-10", "&7点击减少10"));
        inventory.setItem(38, createItem(Material.IRON_NUGGET, "&c-100", "&7点击减少100"));
        inventory.setItem(39, createItem(Material.IRON_NUGGET, "&c清零", "&7点击清零价格"));
        
        // 货币类型选择
        boolean isVault = currencyType.equals("vault");
        List<String> vaultLore = new ArrayList<>();
        vaultLore.add("");
        vaultLore.add(MessageManager.colorize(isVault ? "&a● 已选择" : "&7○ 点击选择"));
        ItemStack vaultButton = createItem(isVault ? Material.SUNFLOWER : Material.DANDELION, 
                "&f&lVault货币", vaultLore);
        inventory.setItem(23, vaultButton);
        
        List<String> nyeLore = new ArrayList<>();
        nyeLore.add("");
        nyeLore.add(MessageManager.colorize(!isVault ? "&a● 已选择" : "&7○ 点击选择"));
        ItemStack nyeButton = createItem(!isVault ? Material.EMERALD : Material.LIME_DYE, 
                "&a&lNYE点券", nyeLore);
        inventory.setItem(24, nyeButton);
        
        // 底部操作栏
        ItemStack bottomBorder = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, bottomBorder);
        }
        
        // 确认上架按钮
        boolean canConfirm = selectedItem != null && price > 0;
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add("");
        if (selectedItem != null) {
            String itemName = selectedItem.hasItemMeta() && selectedItem.getItemMeta().hasDisplayName() 
                    ? selectedItem.getItemMeta().getDisplayName() 
                    : selectedItem.getType().name();
            confirmLore.add(MessageManager.colorize("&7物品: &f" + itemName));
            confirmLore.add(MessageManager.colorize("&7数量: &f" + selectedItem.getAmount()));
        } else {
            confirmLore.add(MessageManager.colorize("&c请先放入物品"));
        }
        if (price > 0) {
            confirmLore.add(MessageManager.colorize("&7价格: &f" + plugin.getEconomyManager().formatCurrency(price, currencyType)));
        } else {
            confirmLore.add(MessageManager.colorize("&c请设置价格"));
        }
        confirmLore.add("");
        confirmLore.add(MessageManager.colorize(canConfirm ? "&a点击确认上架" : "&c条件不满足"));
        
        ItemStack confirmButton = createItem(canConfirm ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE, 
                canConfirm ? "&a&l确认上架" : "&7&l确认上架", confirmLore);
        inventory.setItem(49, confirmButton);
        
        // 取消按钮
        ItemStack cancelButton = createItem(Material.RED_CONCRETE, "&c&l取消", 
                "", "&7点击关闭界面", "&7物品将返回背包");
        inventory.setItem(45, cancelButton);
        
        // 帮助按钮
        ItemStack helpButton = createItem(Material.BOOK, "&b&l帮助", 
                "",
                "&71. 将物品放入中间格子",
                "&72. 设置价格（点击或输入）",
                "&73. 选择货币类型",
                "&74. 点击确认上架");
        inventory.setItem(53, helpButton);
    }

    public void open() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player clicker) || !clicker.equals(player)) {
            return;
        }

        int slot = event.getRawSlot();
        
        // 允许在背包区域和物品槽之间移动物品
        if (slot == 13) {
            // 物品槽
            if (selectedItem != null) {
                // 移除已选物品，返回背包
                player.getInventory().addItem(selectedItem);
                selectedItem = null;
                setupInventory();
                event.setCancelled(true);
            } else if (event.getCursor() != null && !event.getCursor().getType().isAir()) {
                // 放入物品
                selectedItem = event.getCursor().clone();
                event.getWhoClicked().setItemOnCursor(null);
                setupInventory();
                event.setCancelled(true);
            }
            return;
        }
        
        // 其他槽位都取消
        event.setCancelled(true);
        
        // 从背包点击物品
        if (slot >= 54 && event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {
            if (selectedItem == null) {
                selectedItem = event.getCurrentItem().clone();
                event.setCurrentItem(null);
                setupInventory();
            }
            return;
        }
        
        switch (slot) {
            case 20 -> {
                // 设置价格 - 打开聊天输入
                awaitingPriceInput = true;
                awaitingPrice.put(player.getUniqueId(), this);
                player.closeInventory();
                plugin.getMessageManager().sendRaw(player, "&6请在聊天框中输入价格（输入 cancel 取消）:");
            }
            case 28 -> { // +10
                price += 10;
                setupInventory();
            }
            case 29 -> { // +100
                price += 100;
                setupInventory();
            }
            case 30 -> { // +1000
                price += 1000;
                setupInventory();
            }
            case 37 -> { // -10
                price = Math.max(0, price - 10);
                setupInventory();
            }
            case 38 -> { // -100
                price = Math.max(0, price - 100);
                setupInventory();
            }
            case 39 -> { // 清零
                price = 0;
                setupInventory();
            }
            case 23 -> { // Vault货币
                currencyType = "vault";
                setupInventory();
            }
            case 24 -> { // NYE点券
                currencyType = "nye";
                setupInventory();
            }
            case 45 -> { // 取消
                if (selectedItem != null) {
                    player.getInventory().addItem(selectedItem);
                    selectedItem = null;
                }
                player.closeInventory();
            }
            case 49 -> { // 确认上架
                if (selectedItem != null && price > 0) {
                    confirmAddItem();
                }
            }
        }
    }
    
    private void confirmAddItem() {
        int maxSlots = plugin.getConfigManager().getConfig().getInt("stall.max-slots", 27);
        
        if (stall.getItemCount() >= maxSlots) {
            plugin.getMessageManager().sendRaw(player, "&c摊位已满! 最多可上架 " + maxSlots + " 件商品");
            return;
        }

        int slot = stall.getNextAvailableSlot();
        if (slot < 0) {
            plugin.getMessageManager().sendRaw(player, "&c没有可用的槽位!");
            return;
        }
        
        StallItem stallItem = new StallItem(slot, selectedItem.clone(), price, currencyType);
        stall.addItem(stallItem);
        
        String itemName = selectedItem.hasItemMeta() && selectedItem.getItemMeta().hasDisplayName() 
                ? selectedItem.getItemMeta().getDisplayName() 
                : selectedItem.getType().name();
        plugin.getMessageManager().sendRaw(player, String.format(
                "&a成功上架 &e%s x%d &a价格: &f%s",
                itemName, selectedItem.getAmount(),
                plugin.getEconomyManager().formatCurrency(price, currencyType)
        ));
        
        selectedItem = null;
        price = 0;
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory) && !awaitingPriceInput) {
            // 返还物品
            if (selectedItem != null) {
                player.getInventory().addItem(selectedItem);
                selectedItem = null;
            }
            HandlerList.unregisterAll(this);
            awaitingPrice.remove(player.getUniqueId());
        }
    }
    
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        AddItemGUI gui = awaitingPrice.get(event.getPlayer().getUniqueId());
        if (gui == null || gui != this) {
            return;
        }
        
        event.setCancelled(true);
        String message = event.getMessage();
        
        if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("取消")) {
            awaitingPrice.remove(event.getPlayer().getUniqueId());
            awaitingPriceInput = false;
            Bukkit.getScheduler().runTask(plugin, this::open);
            return;
        }
        
        try {
            double inputPrice = Double.parseDouble(message);
            if (inputPrice <= 0) {
                plugin.getMessageManager().sendRaw(event.getPlayer(), "&c价格必须大于0! 请重新输入:");
                return;
            }
            price = inputPrice;
            awaitingPrice.remove(event.getPlayer().getUniqueId());
            awaitingPriceInput = false;
            Bukkit.getScheduler().runTask(plugin, this::open);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendRaw(event.getPlayer(), "&c无效的价格格式! 请输入数字:");
        }
    }

    private ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.colorize(name));
            if (loreLines.length > 0) {
                List<String> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(MessageManager.colorize(line));
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageManager.colorize(name));
            meta.setLore(loreLines);
            item.setItemMeta(meta);
        }
        return item;
    }
}

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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class MyItemsGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private final PlayerStall stall;
    private Inventory inventory;
    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 28; // 4行x7列

    public MyItemsGUI(PlayerStallCraft plugin, Player player, PlayerStall stall) {
        this.plugin = plugin;
        this.player = player;
        this.stall = stall;
        
        createInventory();
    }

    private void createInventory() {
        String title = MessageManager.colorize("&6&l我的商品 &7第" + (currentPage + 1) + "页");
        this.inventory = Bukkit.createInventory(null, 54, title);
        setupInventory();
    }

    private void setupInventory() {
        inventory.clear();
        
        // 顶部装饰
        ItemStack topBorder = createItem(Material.YELLOW_STAINED_GLASS_PANE, "&e&l═══ 我的商品 ═══");
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, topBorder);
        }
        
        // 左右边框
        ItemStack sideBorder = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int row = 1; row < 5; row++) {
            inventory.setItem(row * 9, sideBorder);
            inventory.setItem(row * 9 + 8, sideBorder);
        }
        
        // 底部操作栏
        ItemStack bottomBorder = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, bottomBorder);
        }
        
        // 显示商品 (槽位 10-16, 19-25, 28-34, 37-43)
        int[] itemSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };
        
        var stallItems = stall.getItems().values().toArray(new StallItem[0]);
        int startIndex = currentPage * ITEMS_PER_PAGE;
        
        for (int i = 0; i < itemSlots.length; i++) {
            int itemIndex = startIndex + i;
            if (itemIndex < stallItems.length) {
                StallItem stallItem = stallItems[itemIndex];
                ItemStack displayItem = stallItem.getItemStack().clone();
                ItemMeta meta = displayItem.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add("");
                    lore.add(MessageManager.colorize("&e价格: &f" + plugin.getEconomyManager().formatCurrency(stallItem.getPrice(), stallItem.getCurrencyType())));
                    lore.add(MessageManager.colorize("&7数量: &f" + stallItem.getAmount()));
                    lore.add("");
                    lore.add(MessageManager.colorize("&c左键下架"));
                    lore.add(MessageManager.colorize("&e右键修改价格"));
                    meta.setLore(lore);
                    displayItem.setItemMeta(meta);
                }
                inventory.setItem(itemSlots[i], displayItem);
            } else {
                // 空槽位显示为可上架
                inventory.setItem(itemSlots[i], createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, 
                        "&a&l+ 上架商品", "&7点击此处上架新商品"));
            }
        }
        
        // 上一页按钮
        if (currentPage > 0) {
            inventory.setItem(45, createItem(Material.ARROW, "&a上一页", "&7点击返回上一页"));
        }
        
        // 添加商品按钮
        inventory.setItem(49, createItem(Material.EMERALD, "&a&l上架新商品", 
                "", "&7点击打开上架界面", "&7从背包选择物品上架"));
        
        // 下一页按钮
        int totalPages = (int) Math.ceil((double) stallItems.length / ITEMS_PER_PAGE);
        if (currentPage < totalPages - 1) {
            inventory.setItem(53, createItem(Material.ARROW, "&a下一页", "&7点击前往下一页"));
        }
        
        // 统计信息
        int maxSlots = plugin.getConfigManager().getConfig().getInt("stall.max-slots", 27);
        inventory.setItem(47, createItem(Material.BOOK, "&b&l商品统计", 
                "", 
                "&7已上架: &f" + stallItems.length + "/" + maxSlots,
                "&7总价值: &f" + calculateTotalValue()));
        
        // 关闭按钮
        inventory.setItem(51, createItem(Material.BARRIER, "&c&l关闭", "&7点击关闭界面"));
    }
    
    private String calculateTotalValue() {
        double total = 0;
        for (StallItem item : stall.getItems().values()) {
            total += item.getPrice() * item.getAmount();
        }
        return plugin.getEconomyManager().formatCurrency(total, "vault");
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

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player clicker) || !clicker.equals(player)) {
            return;
        }

        int slot = event.getRawSlot();
        
        // 商品槽位
        int[] itemSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };
        
        for (int i = 0; i < itemSlots.length; i++) {
            if (slot == itemSlots[i]) {
                var stallItems = stall.getItems().values().toArray(new StallItem[0]);
                int itemIndex = currentPage * ITEMS_PER_PAGE + i;
                
                if (itemIndex < stallItems.length) {
                    // 点击已有商品
                    StallItem stallItem = stallItems[itemIndex];
                    if (event.getClick() == ClickType.LEFT) {
                        // 下架商品
                        removeItem(stallItem);
                    } else if (event.getClick() == ClickType.RIGHT) {
                        // 修改价格 - 打开上架界面重新设置
                        plugin.getMessageManager().sendRaw(player, "&e暂不支持修改价格，请下架后重新上架");
                    }
                } else {
                    // 点击空槽位 - 打开上架界面
                    openAddItemGUI();
                }
                return;
            }
        }
        
        switch (slot) {
            case 45 -> { // 上一页
                if (currentPage > 0) {
                    currentPage--;
                    createInventory();
                    player.openInventory(inventory);
                }
            }
            case 49 -> { // 上架新商品
                openAddItemGUI();
            }
            case 51 -> { // 关闭
                player.closeInventory();
            }
            case 53 -> { // 下一页
                var stallItems = stall.getItems().values().toArray(new StallItem[0]);
                int totalPages = (int) Math.ceil((double) stallItems.length / ITEMS_PER_PAGE);
                if (currentPage < totalPages - 1) {
                    currentPage++;
                    createInventory();
                    player.openInventory(inventory);
                }
            }
        }
    }
    
    private void removeItem(StallItem stallItem) {
        // 返还物品到玩家背包
        ItemStack returnItem = stallItem.getItemStack().clone();
        player.getInventory().addItem(returnItem);
        
        // 从摊位移除
        stall.removeItem(stallItem.getSlot());
        
        String itemName = stallItem.getItemName();
        plugin.getMessageManager().sendRaw(player, "&a已下架: &e" + itemName + " x" + stallItem.getAmount());
        
        // 刷新界面
        setupInventory();
    }
    
    private void openAddItemGUI() {
        HandlerList.unregisterAll(this);
        new AddItemGUI(plugin, player, stall).open();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
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
}

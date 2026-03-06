package com.playerstallcraft.gui;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.Shop;
import com.playerstallcraft.models.ShopShelf;
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
import java.util.List;
import java.util.Map;

/**
 * 货架商品管理GUI
 * 显示货架中的所有商品，支持上架、下架、修改价格
 */
public class ShelfItemsGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private final Shop shop;
    private final ShopShelf shelf;
    private Inventory inventory;
    private static final String GUI_TITLE = "§6货架商品";

    private boolean waitingForDiscount = false;

    public ShelfItemsGUI(PlayerStallCraft plugin, Player player, Shop shop, ShopShelf shelf) {
        this.plugin = plugin;
        this.player = player;
        this.shop = shop;
        this.shelf = shelf;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, 36, GUI_TITLE + " #" + shelf.getId());
        
        // 填充背景
        ItemStack background = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 36; i++) {
            inventory.setItem(i, background);
        }

        // 显示商品槽位 (前两行, 16个槽位)
        for (int slot = 0; slot < ShopShelf.MAX_SLOTS; slot++) {
            int invSlot = slot < 9 ? slot : slot + 9; // 第一行0-8, 第二行18-26
            if (slot >= 9) {
                invSlot = slot - 9 + 9; // 9-15 -> 9-15
            }
            
            // 简化：使用0-8和9-16的槽位
            invSlot = slot;
            if (slot >= 9) {
                invSlot = slot + 9; // 跳过中间行
            }
            
            ShopShelf.ShelfItem item = shelf.getItem(slot);
            if (item != null) {
                inventory.setItem(invSlot, createShelfItemDisplay(slot, item));
            } else {
                inventory.setItem(invSlot, createEmptySlotItem(slot));
            }
        }

        // 底部按钮
        inventory.setItem(27, createItem(Material.ARROW, "§c返回", "§7返回商铺管理"));
        inventory.setItem(28, createItem(Material.CHEST, "§a批量上架",
            "§7从背包选取多件物品",
            "§7统一定价后一键上架",
            "",
            "§e点击打开批量上架"));
        inventory.setItem(29, createItem(Material.COMPASS, "§e移动货架", "§7点击后站到新位置", "§7然后确认移动"));
        inventory.setItem(31, createInfoItem());
        inventory.setItem(33, createItem(Material.NAME_TAG, "§e修改名称", "§7修改货架全息图显示名称", "§7当前: §f" + shelf.getDisplayName()));
        inventory.setItem(35, createItem(Material.NETHER_STAR, "§6设置促销折扣",
            "§7为货架所有商品设置限时折扣",
            "§7输入折扣率(如 0.8=八折)和时长(小时)",
            "",
            "§e点击设置，§c右键清除所有折扣"));

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        player.openInventory(inventory);
    }

    private ItemStack createShelfItemDisplay(int slot, ShopShelf.ShelfItem shelfItem) {
        ItemStack display = shelfItem.getItemStack().clone();
        display.setAmount(1);
        ItemMeta meta = display.getItemMeta();
        
        String currencyName = plugin.getEconomyManager().getCurrencyName(shelfItem.getCurrencyType());
        
        List<String> lore = new ArrayList<>();
        if (meta.hasLore()) {
            lore.addAll(meta.getLore());
        }
        lore.add("§7───────────────");
        if (shelfItem.hasActiveDiscount()) {
            lore.add("§c原价: §7§m" + String.format("%.1f", shelfItem.getPrice()) + " " + currencyName);
            lore.add("§a折扣价: §e" + String.format("%.1f", shelfItem.getEffectivePrice()) + " " + currencyName
                + " §6(" + (int)(shelfItem.getDiscountRate() * 100) + "折)");
            if (shelfItem.getDiscountExpiry() > 0) {
                long remaining = shelfItem.getDiscountExpiry() - System.currentTimeMillis();
                long hours = remaining / 3600000;
                long mins = (remaining % 3600000) / 60000;
                lore.add("§7折扣剩余: §e" + hours + "h " + mins + "m");
            }
        } else {
            lore.add("§6价格: §e" + String.format("%.1f", shelfItem.getPrice()) + " " + currencyName);
        }
        lore.add("§b货币: §f" + currencyName);
        lore.add("§a库存: §f" + shelfItem.getStock());
        lore.add("§7已售: §f" + shelfItem.getSold());
        lore.add("§7───────────────");
        lore.add("§a左键 §7- 补充库存");
        lore.add("§e蹲下+右键 §7- 修改价格");
        lore.add("§c右键 §7- 下架商品");
        
        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack createEmptySlotItem(int slot) {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7空槽位 #" + (slot + 1));
        
        List<String> lore = new ArrayList<>();
        lore.add("§7");
        lore.add("§a点击上架商品");
        lore.add("§7将物品放入此槽位");
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6货架信息");
        
        List<String> lore = new ArrayList<>();
        lore.add("§7───────────────");
        lore.add("§f货架ID: §e#" + shelf.getId());
        lore.add("§f商品数量: §e" + shelf.getItemCount() + "/" + ShopShelf.MAX_SLOTS);
        lore.add("§7───────────────");
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) {
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(line);
            }
            meta.setLore(loreList);
        }
        item.setItemMeta(meta);
        return item;
    }

    private boolean isOwnerOrAdmin() {
        // 检查是否是商铺拥有者或管理员
        if (player.hasPermission("playerstallcraft.admin")) {
            return true;
        }
        return shop.hasOwner() && shop.getOwnerUuid().equals(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;
        
        event.setCancelled(true);
        
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 36) return;
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        // 返回按钮
        if (slot == 27) {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                new ShopManageGUI(plugin, player, shop).open();
            }, 1L);
            return;
        }
        
        // 批量上架按钮
        if (slot == 28) {
            if (!isOwnerOrAdmin()) {
                plugin.getMessageManager().sendRaw(player, "&c你不是这个商铺的主人，无法上架商品!");
                return;
            }
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                new BatchShelfAddGUI(plugin, player, shop, shelf).open(), 1L);
            return;
        }

        // 移动货架按钮
        if (slot == 29) {
            if (!isOwnerOrAdmin()) {
                plugin.getMessageManager().sendRaw(player, "&c你不是这个商铺的主人，无法移动货架!");
                return;
            }
            handleMoveShelf();
            return;
        }
        
        // 修改名称按钮
        if (slot == 33) {
            if (!isOwnerOrAdmin()) {
                plugin.getMessageManager().sendRaw(player, "&c你不是这个商铺的主人，无法修改名称!");
                return;
            }
            handleRenameShelf();
            return;
        }

        // 折扣按钮
        if (slot == 35) {
            if (!isOwnerOrAdmin()) return;
            if (event.isRightClick()) {
                // 清除所有折扣
                for (Map.Entry<Integer, ShopShelf.ShelfItem> entry : shelf.getItems().entrySet()) {
                    entry.getValue().setDiscountRate(1.0);
                    entry.getValue().setDiscountExpiry(0);
                    plugin.getShopManager().updateShelfItemDiscount(shelf.getId(), entry.getKey(), 1.0, 0);
                }
                plugin.getMessageManager().sendRaw(player, "&a已清除货架所有折扣!");
                refreshInventory();
            } else {
                handleSetDiscount();
            }
            return;
        }
        
        // 商品槽位点击 (0-8, 18-26)
        int shelfSlot = -1;
        if (slot >= 0 && slot <= 8) {
            shelfSlot = slot;
        } else if (slot >= 18 && slot <= 26) {
            shelfSlot = slot - 9;
        }
        
        if (shelfSlot >= 0 && shelfSlot < ShopShelf.MAX_SLOTS) {
            // 检查权限 - 只有店主或管理员才能管理货架
            if (!isOwnerOrAdmin()) {
                plugin.getMessageManager().sendRaw(player, "&c你不是这个商铺的主人，无法管理货架!");
                return;
            }
            
            Material type = clicked.getType();
            
            if (type == Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
                // 点击空槽位 - 上架商品
                handleAddItem(shelfSlot);
            } else if (type != Material.GRAY_STAINED_GLASS_PANE) {
                // 点击已有商品
                if (event.isShiftClick() && event.isRightClick()) {
                    // 蹲下+右键 修改价格
                    handleEditPrice(shelfSlot);
                } else if (event.isRightClick()) {
                    // 下架商品
                    handleRemoveItem(shelfSlot);
                } else if (event.isLeftClick()) {
                    // 补充库存
                    handleRestockItem(shelfSlot);
                }
            }
        }
    }

    private void handleAddItem(int shelfSlot) {
        // 打开上架物品GUI，让玩家选择物品并设置价格
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> 
            new ShelfAddItemGUI(plugin, player, shop, shelf, shelfSlot).open(), 1L);
    }

    private void handleRemoveItem(int shelfSlot) {
        ShopShelf.ShelfItem item = shelf.getItem(shelfSlot);
        if (item == null) {
            plugin.getMessageManager().sendRaw(player, "&c该槽位没有商品!");
            return;
        }
        
        // 返还物品给玩家
        ItemStack returnItem = item.getItemStack();
        returnItem.setAmount(item.getStock());
        
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(returnItem);
            shelf.removeItem(shelfSlot);
            plugin.getShopManager().deleteShelfItem(shelf.getId(), shelfSlot);
            plugin.getMessageManager().sendRaw(player, "&a已下架商品并返还库存!");
            
            // 更新全息图
            plugin.getShelfHologramManager().updateHologram(shelf);
            
            // 刷新界面
            refreshInventory();
        } else {
            plugin.getMessageManager().sendRaw(player, "&c背包已满，无法下架!");
        }
    }

    private void handleRestockItem(int shelfSlot) {
        ShopShelf.ShelfItem item = shelf.getItem(shelfSlot);
        if (item == null) return;
        
        // 检查玩家手持的物品是否与货架商品相同
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem == null || handItem.getType() == Material.AIR) {
            plugin.getMessageManager().sendRaw(player, "&c请手持相同物品补充库存!");
            return;
        }
        
        // 检查物品类型是否匹配
        if (!handItem.isSimilar(item.getItemStack())) {
            plugin.getMessageManager().sendRaw(player, "&c物品类型不匹配!");
            return;
        }
        
        // 补充库存
        int addAmount = handItem.getAmount();
        item.setStock(item.getStock() + addAmount);
        player.getInventory().setItemInMainHand(null);
        
        // 保存到数据库
        plugin.getShopManager().updateShelfItemStock(shelf.getId(), shelfSlot, item.getStock(), item.getSold());
        
        plugin.getMessageManager().sendRaw(player, "&a成功补充库存 +" + addAmount + "! &7当前库存: " + item.getStock());
        
        // 更新全息图
        plugin.getShelfHologramManager().updateHologram(shelf);
        
        // 刷新界面
        refreshInventory();
    }

    private void refreshInventory() {
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, this::open, 1L);
    }

    private void handleMoveShelf() {
        player.closeInventory();
        plugin.getMessageManager().sendRaw(player, "&e请移动到新位置，然后输入 &a/baitan confirmMove &e确认移动货架");
        plugin.getMessageManager().sendRaw(player, "&7输入 &c/baitan cancelMove &7取消移动");
        
        // 存储待移动的货架信息
        plugin.getShopManager().setPendingShelfMove(player.getUniqueId(), shelf);
    }

    private void handleEditPrice(int shelfSlot) {
        ShopShelf.ShelfItem item = shelf.getItem(shelfSlot);
        if (item == null) return;
        
        player.closeInventory();
        plugin.getMessageManager().sendRaw(player, "§e请在聊天栏输入新的价格:");
        plugin.getMessageManager().sendRaw(player, "§7输入 §ccancel §7取消修改");
        
        // 存储待修改价格的信息
        plugin.getShopManager().setPendingPriceEdit(player.getUniqueId(), shelf, shelfSlot);
    }

    private void handleRenameShelf() {
        player.closeInventory();
        plugin.getMessageManager().sendRaw(player, "&e请在聊天栏输入新的货架名称:");
        plugin.getMessageManager().sendRaw(player, "&7输入 &ccancel &7取消重命名");
        plugin.getShopManager().setPendingShelfRename(player.getUniqueId(), shelf);
    }

    private void handleSetDiscount() {
        if (shelf.getItems().isEmpty()) {
            plugin.getMessageManager().sendRaw(player, "&c货架上没有商品!");
            return;
        }
        waitingForDiscount = true;
        player.closeInventory();
        plugin.getMessageManager().sendRaw(player, "&e请输入折扣信息，格式: &f<折扣率> <小时数>");
        plugin.getMessageManager().sendRaw(player, "&7示例: &f0.8 24 &7= 八折持续24小时");
        plugin.getMessageManager().sendRaw(player, "&7输入 &ccancel &7取消");
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player) || !waitingForDiscount) return;
        event.setCancelled(true);
        waitingForDiscount = false;
        String input = event.getMessage().trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (input.equalsIgnoreCase("cancel")) { open(); return; }
            String[] parts = input.split("\\s+");
            if (parts.length < 2) {
                plugin.getMessageManager().sendRaw(player, "&c格式错误！示例: 0.8 24");
                open(); return;
            }
            try {
                double rate = Double.parseDouble(parts[0]);
                double hours = Double.parseDouble(parts[1]);
                if (rate <= 0 || rate >= 1.0) {
                    plugin.getMessageManager().sendRaw(player, "&c折扣率必须在 0.01 ~ 0.99 之间!");
                    open(); return;
                }
                long expiry = hours <= 0 ? 0 : System.currentTimeMillis() + (long)(hours * 3600000);
                for (Map.Entry<Integer, ShopShelf.ShelfItem> entry : shelf.getItems().entrySet()) {
                    entry.getValue().setDiscountRate(rate);
                    entry.getValue().setDiscountExpiry(expiry);
                    plugin.getShopManager().updateShelfItemDiscount(shelf.getId(), entry.getKey(), rate, expiry);
                }
                plugin.getShelfHologramManager().updateHologram(shelf);
                plugin.getMessageManager().sendRaw(player,
                    "&a已设置 &e" + (int)(rate*100) + "折&a 促销"
                    + (expiry > 0 ? "，持续 &e" + (int)hours + "&a 小时" : "（永久）") + "!");
            } catch (NumberFormatException e) {
                plugin.getMessageManager().sendRaw(player, "&c输入无效，请输入数字!");
            }
            open();
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() != inventory) return;
        if (!event.getPlayer().equals(player)) return;
        
        HandlerList.unregisterAll(this);
    }
}

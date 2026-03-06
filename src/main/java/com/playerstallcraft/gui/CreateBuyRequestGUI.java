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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;

public class CreateBuyRequestGUI implements Listener {

    private final PlayerStallCraft plugin;
    private final Player player;
    private Inventory inventory;
    
    private ItemStack selectedItem = null;
    private int amount = 1;
    private double price = 0;
    private String currencyType = "vault";
    
    private boolean waitingForPrice = false;
    private boolean waitingForAmount = false;
    private boolean navigatingAway = false;
    private List<Double> priceHistoryCache = Collections.emptyList();

    public CreateBuyRequestGUI(PlayerStallCraft plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, 54, "§a§l发布求购");
        
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
        
        // 选择物品区域
        inventory.setItem(13, createItem(Material.HOPPER, "§e§l选择求购物品",
            "§7方式1: 从背包点击物品",
            "§7方式2: 点击下方方格输入物品名",
            "",
            "§a左键选择 / 点击方格输入名称"));
        
        // 物品槽位
        if (selectedItem != null) {
            inventory.setItem(22, selectedItem.clone());
        } else {
            inventory.setItem(22, createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7点击输入物品名称",
                "",
                "§7或从背包点击物品选择"));
        }
        
        // 数量设置
        inventory.setItem(29, createItem(Material.RED_DYE, "§c-10", "§7点击减少10个"));
        inventory.setItem(30, createItem(Material.ORANGE_DYE, "§c-1", "§7点击减少1个"));
        
        ItemStack amountItem = createItem(Material.PAPER, "§e§l求购数量: §f" + amount,
            "",
            "§7点击在聊天中输入数量");
        inventory.setItem(31, amountItem);
        
        inventory.setItem(32, createItem(Material.LIME_DYE, "§a+1", "§7点击增加1个"));
        inventory.setItem(33, createItem(Material.GREEN_DYE, "§a+10", "§7点击增加10个"));
        
        // 价格设置
        inventory.setItem(38, createPriceItem());
        
        // 货币类型
        String nyeName = plugin.getConfig().getString("currency.nye-currency-name", "鸽币");
        Material currencyMaterial = currencyType.equals("vault") ? Material.GOLD_INGOT : Material.EMERALD;
        String currencyName = currencyType.equals("vault") ? "金币" : nyeName;
        inventory.setItem(40, createItem(currencyMaterial, "§b§l货币类型: §f" + currencyName,
            "",
            "§7点击切换货币类型"));
        
        // 确认发布
        double totalPrice = price * amount;
        boolean canPublish = selectedItem != null && amount > 0 && price > 0;
        if (canPublish) {
            String zhName = plugin.getGlobalMarketManager().getChineseNamePublic(selectedItem.getType());
            String itemDisplayName = zhName != null ? zhName : selectedItem.getType().name();
            int expireDays = plugin.getConfigManager().getConfig().getInt("buy-request.default-expire-days", 7);
            String expireInfo = expireDays > 0 ? "§7有效期: §f" + expireDays + " 天后自动到期退款" : "§7有效期: §f永久有效";
            double balance = plugin.getEconomyManager().getBalance(player, currencyType);
            boolean canAfford = balance >= totalPrice;
            String balanceLine = canAfford
                ? "§7你的余额: §a" + plugin.getEconomyManager().formatCurrency(balance, currencyType) + "§7，点击发布求购"
                : "§7你的余额: §c" + plugin.getEconomyManager().formatCurrency(balance, currencyType) + "§c，不足够发布";
            Material btnMaterial = canAfford ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK;
            inventory.setItem(49, createItem(btnMaterial, canAfford ? "§a§l确认发布" : "§c§l余额不足",
                "",
                "§7求购物品: §f" + itemDisplayName,
                "§7求购数量: §f" + amount + " 个",
                "§7你的收购出价: §a" + plugin.getEconomyManager().formatCurrency(price, currencyType) + "§7/个",
                "§7你需预付总额: §c" + plugin.getEconomyManager().formatCurrency(totalPrice, currencyType),
                expireInfo,
                "",
                "§8（卖家接单后，你将获得物品；卖家获得税后收入）",
                "",
                balanceLine,
                "",
                "§c预付金额立即扣除，取消求购可全额退还"));
        } else {
            inventory.setItem(49, createItem(Material.BARRIER, "§c§l无法发布",
                "",
                selectedItem == null ? "§c✗ 未选择物品" : "§a✓ 已选择物品",
                amount <= 0 ? "§c✗ 数量无效" : "§a✓ 数量: " + amount,
                price <= 0 ? "§c✗ 价格无效" : "§a✓ 单价: " + price));
        }
        
        // 返回
        inventory.setItem(45, createItem(Material.ARROW, "§7返回求购市场"));
        
        // 关闭
        inventory.setItem(53, createItem(Material.BARRIER, "§c关闭"));
    }

    private void loadPriceHistoryAsync() {
        if (selectedItem == null) { priceHistoryCache = Collections.emptyList(); return; }
        String typeName = selectedItem.getType().name();
        plugin.getTransactionLogManager().getItemPriceHistoryAsync(typeName, currencyType, 5, history -> {
            priceHistoryCache = history;
            if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory().equals(inventory)) {
                fillInventory();
            }
        });
    }

    private ItemStack createPriceItem() {
        List<String> lore = new ArrayList<>();
        lore.add("");

        if (price > 0) {
            lore.add("§7当前单价: §a" + plugin.getEconomyManager().formatCurrency(price, currencyType));
            double total = price * amount;
            lore.add("§7预计总花费: §c" + plugin.getEconomyManager().formatCurrency(total, currencyType));
        } else {
            lore.add("§7当前单价: §c未设置");
        }

        if (selectedItem != null) {
            List<Double> history = priceHistoryCache;
            lore.add("");
            String zhName = plugin.getGlobalMarketManager().getChineseNamePublic(selectedItem.getType());
            String itemLabel = zhName != null ? zhName : selectedItem.getType().name();
            lore.add("§8▌ §7近期成交参考 (" + itemLabel + ")");
            if (history.isEmpty()) {
                lore.add("§8  暂无成交记录");
            } else {
                double sum = 0, minP = Double.MAX_VALUE, maxP = -Double.MAX_VALUE;
                for (double p : history) { sum += p; if (p < minP) minP = p; if (p > maxP) maxP = p; }
                double avg = sum / history.size();
                lore.add("§8  均价: §f" + plugin.getEconomyManager().formatCurrency(avg, currencyType));
                lore.add("§8  最低: §a" + plugin.getEconomyManager().formatCurrency(minP, currencyType));
                lore.add("§8  最高: §e" + plugin.getEconomyManager().formatCurrency(maxP, currencyType));
            }
        }

        lore.add("");
        lore.add("§e左键 » 在聊天中输入单价");

        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String priceText = price > 0
                ? plugin.getEconomyManager().formatCurrency(price, currencyType)
                : "§c未设置";
            meta.setDisplayName("§e§l单价: §f" + priceText);
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
        
        // 允许玩家从自己背包拿物品
        if (slot >= 54) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR) {
                selectedItem = clicked.clone();
                selectedItem.setAmount(1);
                loadPriceHistoryAsync();
                fillInventory();
            }
            return;
        }
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        switch (slot) {
            case 22 -> {
                if (selectedItem != null) {
                    selectedItem = null;
                    fillInventory();
                } else {
                    navigatingAway = true;
                    player.closeInventory();
                    new ItemSelectGUI(plugin, player, item -> {
                        if (item != null) {
                            selectedItem = item;
                            loadPriceHistoryAsync();
                        }
                        fillInventory();
                        open();
                    }).open();
                }
            }
            case 29 -> {
                // -10
                amount = Math.max(1, amount - 10);
                fillInventory();
            }
            case 30 -> {
                // -1
                amount = Math.max(1, amount - 1);
                fillInventory();
            }
            case 31 -> {
                // 输入数量
                waitingForAmount = true;
                waitingForPrice = false;
                player.closeInventory();
                plugin.getMessageManager().sendRaw(player, "&e请在聊天中输入求购数量（输入 cancel 取消）:");
            }
            case 32 -> {
                // +1
                amount = Math.min(2304, amount + 1);
                fillInventory();
            }
            case 33 -> {
                // +10
                amount = Math.min(2304, amount + 10);
                fillInventory();
            }
            case 38 -> {
                // 输入价格
                waitingForPrice = true;
                waitingForAmount = false;
                player.closeInventory();
                plugin.getMessageManager().sendRaw(player, "&e请在聊天中输入单价（输入 cancel 取消）:");
            }
            case 40 -> {
                // 切换货币
                currencyType = currencyType.equals("vault") ? "nye" : "vault";
                fillInventory();
            }
            case 45 -> {
                // 返回
                player.closeInventory();
                new BuyRequestGUI(plugin, player).open();
            }
            case 49 -> {
                // 确认发布
                if (selectedItem != null && amount > 0 && price > 0) {
                    publishRequest();
                }
            }
            case 53 -> {
                // 关闭
                player.closeInventory();
            }
        }
    }

    private void publishRequest() {
        // 检查限制
        int currentCount = plugin.getBuyRequestManager().getPlayerRequestCount(player.getUniqueId());
        int maxCount = plugin.getBuyRequestManager().getMaxRequestsPerPlayer();
        
        if (currentCount >= maxCount) {
            plugin.getMessageManager().sendRaw(player, "&c你的求购数量已达上限! (" + currentCount + "/" + maxCount + ")");
            return;
        }
        
        // 检查营业执照要求
        if (plugin.getBuyRequestManager().requiresLicense()) {
            if (!plugin.getLicenseManager().hasLicense(player.getUniqueId())) {
                plugin.getMessageManager().sendRaw(player, "&c发布求购需要营业执照!");
                return;
            }
        }
        
        // 检查玩家余额（需要有足够的钱来支付）
        double totalPrice = price * amount;
        if (!plugin.getEconomyManager().has(player, totalPrice, currencyType)) {
            plugin.getMessageManager().sendRaw(player, "&c余额不足! 需要 " + 
                plugin.getEconomyManager().formatCurrency(totalPrice, currencyType));
            return;
        }
        
        // 发布求购
        plugin.getBuyRequestManager().createRequest(player, selectedItem, amount, price, currencyType);
        player.closeInventory();
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (!waitingForPrice && !waitingForAmount) return;
        
        event.setCancelled(true);
        String message = event.getMessage().trim();
        
        if (message.equalsIgnoreCase("cancel")) {
            waitingForPrice = false;
            waitingForAmount = false;
            plugin.getServer().getScheduler().runTask(plugin, this::open);
            return;
        }
        
        if (waitingForPrice) {
            try {
                double inputPrice = Double.parseDouble(message);
                if (inputPrice <= 0) {
                    plugin.getMessageManager().sendRaw(player, "&c价格必须大于0!");
                    return;
                }
                price = inputPrice;
                waitingForPrice = false;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    fillInventory();
                    open();
                });
            } catch (NumberFormatException e) {
                plugin.getMessageManager().sendRaw(player, "&c请输入有效的数字!");
            }
        } else if (waitingForAmount) {
            try {
                int inputAmount = Integer.parseInt(message);
                if (inputAmount <= 0) {
                    plugin.getMessageManager().sendRaw(player, "&c数量必须大于0!");
                    return;
                }
                if (inputAmount > 2304) {
                    plugin.getMessageManager().sendRaw(player, "&c数量不能超过2304!");
                    return;
                }
                amount = inputAmount;
                waitingForAmount = false;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    fillInventory();
                    open();
                });
            } catch (NumberFormatException e) {
                plugin.getMessageManager().sendRaw(player, "&c请输入有效的整数!");
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory) && event.getPlayer().equals(player)) {
            if (navigatingAway) {
                navigatingAway = false;
                return;
            }
            if (!waitingForPrice && !waitingForAmount) {
                HandlerList.unregisterAll(this);
            }
        }
    }
}

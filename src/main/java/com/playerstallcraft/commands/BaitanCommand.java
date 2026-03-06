package com.playerstallcraft.commands;

import com.playerstallcraft.PlayerStallCraft;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import com.playerstallcraft.models.PlayerStall;
import com.playerstallcraft.models.Advertisement;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BaitanCommand implements CommandExecutor, TabCompleter {

    private final PlayerStallCraft plugin;

    public BaitanCommand(PlayerStallCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "on" -> handleOpen(sender);
            case "off" -> handleClose(sender);
            case "slogan" -> handleSlogan(sender, args);
            case "myitems" -> handleMyItems(sender);
            case "buy" -> handleBuyRequest(sender);
            case "market" -> handleMarket(sender);
            case "shop" -> handleShop(sender);
            case "regioncreate", "rc" -> handleRegionCreate(sender);
            case "regionmanage", "rm" -> handleRegionManage(sender);
            case "license" -> handleLicense(sender, args);
            case "list" -> handleList(sender);
            case "price" -> handlePrice(sender);
            case "reload" -> handleReload(sender);
            case "confirmmove" -> handleConfirmMove(sender);
            case "cancelmove" -> handleCancelMove(sender);
            case "ad" -> handleAd(sender, args);
            case "giveshelf" -> handleGiveShelf(sender, args);
            case "top" -> handleTop(sender, args);
            case "help" -> sendHelp(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleOpen(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        if (!player.hasPermission("stall.use")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return;
        }

        if (plugin.getStallManager().hasStall(player)) {
            plugin.getMessageManager().send(player, "stall.already-open");
            return;
        }

        String slogan = "欢迎光临!";

        if (plugin.getStallManager().openStall(player, slogan)) {
            plugin.getMessageManager().send(player, "stall.opened");
        }
    }

    private void handleSlogan(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        if (!plugin.getStallManager().hasStall(player)) {
            plugin.getMessageManager().send(player, "stall.not-open");
            return;
        }

        if (args.length < 2) {
            plugin.getMessageManager().sendRaw(sender, "&c用法: /baitan slogan <标语内容>");
            return;
        }

        String slogan = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        
        // 标语长度限制
        int maxLength = plugin.getConfigManager().getConfig().getInt("stall.max-slogan-length", 50);
        if (slogan.length() > maxLength) {
            plugin.getMessageManager().sendRaw(player, "&c标语过长! 最大长度: " + maxLength + " 字符");
            return;
        }
        
        // 行数限制
        int maxLines = plugin.getConfigManager().getConfig().getInt("stall.max-slogan-lines", 3);
        String[] lines = slogan.split("#");
        if (lines.length > maxLines) {
            plugin.getMessageManager().sendRaw(player, "&c标语行数过多! 最多: " + maxLines + " 行");
            return;
        }
        
        PlayerStall stall = plugin.getStallManager().getStall(player);
        stall.setSlogan(slogan);
        // 立即刷新全息图显示
        plugin.getStallNPCManager().refreshDisplay(player.getUniqueId());
        plugin.getMessageManager().sendRaw(player, "&a标语已设置为: &f" + slogan);
        plugin.getMessageManager().sendRaw(player, "&7提示: 使用 # 可以换行 (最多" + maxLines + "行, " + maxLength + "字符)");
    }

    private void handleClose(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        if (!plugin.getStallManager().hasStall(player)) {
            plugin.getMessageManager().send(player, "stall.not-open");
            return;
        }

        if (plugin.getStallManager().closeStall(player)) {
            plugin.getMessageManager().send(player, "stall.closed");
        }
    }

    private void handleMyItems(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        if (!plugin.getStallManager().hasStall(player)) {
            plugin.getMessageManager().send(player, "stall.not-open");
            return;
        }

        PlayerStall stall = plugin.getStallManager().getStall(player);
        // 打开我的商品GUI
        new com.playerstallcraft.gui.MyItemsGUI(plugin, player, stall).open();
    }

    private void handleBuyRequest(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        // 打开求购市场GUI
        new com.playerstallcraft.gui.BuyRequestGUI(plugin, player).open();
    }

    private void handleMarket(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        // 打开全服交易市场GUI
        new com.playerstallcraft.gui.GlobalMarketGUI(plugin, player).open();
    }

    private void handleShop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        // 打开商铺列表GUI
        new com.playerstallcraft.gui.ShopListGUI(plugin, player).open();
    }

    private void handleRegionCreate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        if (!player.hasPermission("stall.region")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return;
        }

        if (!plugin.getRegionManager().hasSelection(player)) {
            plugin.getMessageManager().sendRaw(player, "&c请先用下界合金锄选择区域!");
            plugin.getMessageManager().sendRaw(player, "&7左键选择点1，右键选择点2");
            return;
        }

        org.bukkit.Location[] selection = plugin.getRegionManager().getSelection(player);
        new com.playerstallcraft.gui.RegionTypeSelectGUI(plugin, player, selection[0], selection[1]).open();
    }

    private void handleRegionManage(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        if (!player.hasPermission("stall.region")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return;
        }

        new com.playerstallcraft.gui.RegionManageGUI(plugin, player).open();
    }

    private void handleList(CommandSender sender) {
        plugin.getMessageManager().sendRaw(sender, "&6=== 当前摆摊玩家 ===");
        plugin.getStallManager().getActiveStalls().forEach((uuid, stall) -> {
            plugin.getMessageManager().sendRaw(sender, String.format(
                    "&e%s &7- %d件商品 - %s",
                    stall.getOwnerName(),
                    stall.getItemCount(),
                    stall.getSlogan()
            ));
        });
        if (plugin.getStallManager().getActiveStalls().isEmpty()) {
            plugin.getMessageManager().sendRaw(sender, "&7暂无玩家摆摊");
        }
    }

    private void handleLicense(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        if (args.length < 2) {
            // 显示执照信息
            if (plugin.getLicenseManager().hasLicense(player)) {
                var license = plugin.getLicenseManager().getLicense(player.getUniqueId());
                plugin.getMessageManager().sendRaw(sender, "&6=== 我的营业执照 ===");
                plugin.getMessageManager().sendRaw(sender, "&e状态: &a有效");
                plugin.getMessageManager().sendRaw(sender, "&e剩余天数: &f" + license.getRemainingDays() + " 天");
                plugin.getMessageManager().sendRaw(sender, "&e税率优惠: &f8% (原15%)");
            } else {
                plugin.getMessageManager().sendRaw(sender, "&c您还没有营业执照");
                plugin.getMessageManager().sendRaw(sender, "&7使用 /baitan license buy 购买执照 &8(金币)");
                plugin.getMessageManager().sendRaw(sender, "&7使用 /baitan license buy 鸽币 购买执照 &8(鸽币)");
                double vaultPrice = plugin.getConfigManager().getLicensePrice();
                double nyePrice = plugin.getConfig().getDouble("license.nye-price", 50000);
                String nyeName = plugin.getConfig().getString("currency.nye-currency-name", "鸽币");
                plugin.getMessageManager().sendRaw(sender, "&7价格: &e" + plugin.getEconomyManager().formatCurrency(vaultPrice, "vault") + 
                    " &7或 &e" + String.format("%.0f", nyePrice) + " " + nyeName);
            }
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "buy", "购买" -> {
                if (plugin.getLicenseManager().hasLicense(player)) {
                    plugin.getMessageManager().sendRaw(player, "&c您已经拥有营业执照，请使用 /baitan license renew 续期");
                    return;
                }
                // 检查是否指定货币类型
                String currency = "vault";
                if (args.length >= 3) {
                    String currArg = args[2].toLowerCase();
                    if (currArg.equals("鸽币") || currArg.equals("nye")) {
                        currency = "nye";
                    } else if (currArg.equals("金币") || currArg.equals("vault")) {
                        currency = "vault";
                    }
                }
                plugin.getLicenseManager().purchaseLicense(player, currency);
            }
            case "renew", "续期" -> {
                plugin.getLicenseManager().renewLicense(player);
            }
            default -> {
                plugin.getMessageManager().sendRaw(sender, "&c用法: /baitan license [buy|renew]");
            }
        }
    }

    private void handlePrice(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType().isAir()) {
            plugin.getMessageManager().sendRaw(player, "&c请手持要查询的物品!");
            return;
        }

        String itemName = getItemName(itemInHand);
        String itemType = itemInHand.getType().name();
        
        plugin.getMessageManager().sendRaw(player, "&6=== " + itemName + " 价格查询 ===");
        plugin.getMessageManager().sendRaw(player, "&7正在查询，请稍候...");
        final String finalItemName = itemName;
        plugin.getTransactionLogManager().getItemPriceHistoryAsync(itemType, "vault", 10, vaultPrices -> {
            plugin.getTransactionLogManager().getItemPriceHistoryAsync(itemType, "nye", 10, nyePrices -> {
                plugin.getMessageManager().sendRaw(player, "&6=== " + finalItemName + " 价格查询 ===");
                if (!vaultPrices.isEmpty()) {
                    double avgPrice = vaultPrices.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    double minPrice = vaultPrices.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                    double maxPrice = vaultPrices.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                    plugin.getMessageManager().sendRaw(player, "&e【金币】 &7成交 " + vaultPrices.size() + " 笔");
                    plugin.getMessageManager().sendRaw(player, "&7  均价: &f" + String.format("%.2f", avgPrice) + " 金币/个");
                    plugin.getMessageManager().sendRaw(player, "&7  最低: &a" + String.format("%.2f", minPrice) + " &7| 最高: &c" + String.format("%.2f", maxPrice));
                }
                if (!nyePrices.isEmpty()) {
                    double avgPrice = nyePrices.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    double minPrice = nyePrices.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                    double maxPrice = nyePrices.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                    plugin.getMessageManager().sendRaw(player, "&a【鸽币】 &7成交 " + nyePrices.size() + " 笔");
                    plugin.getMessageManager().sendRaw(player, "&7  均价: &f" + String.format("%.2f", avgPrice) + " 鸽币/个");
                    plugin.getMessageManager().sendRaw(player, "&7  最低: &a" + String.format("%.2f", minPrice) + " &7| 最高: &c" + String.format("%.2f", maxPrice));
                }
                if (vaultPrices.isEmpty() && nyePrices.isEmpty()) {
                    plugin.getMessageManager().sendRaw(player, "&7暂无成交记录");
                }
            });
        });
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().replace("_", " ").toLowerCase();
    }

    private void handleTop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("\u00a7c只有玩家才能使用此命令!");
            return;
        }
        // 支持: /baitan top [vault|nye] [7d|30d]
        String currency = "vault";
        int days = 30;
        if (args.length >= 2) currency = args[1].equalsIgnoreCase("nye") ? "nye" : "vault";
        if (args.length >= 3) {
            try { days = Integer.parseInt(args[2].replace("d", "")); } catch (NumberFormatException ignored) {}
        }
        final String finalCurrency = currency;
        final int finalDays = days;
        long sinceMs = System.currentTimeMillis() - days * 86400000L;
        String currencyName = plugin.getEconomyManager().getCurrencyName(currency);

        player.sendMessage("\u00a76=== 销售额排行 (近" + days + "天\u00a76|§e" + currencyName + "\u00a76) ===");
        player.sendMessage("\u00a77查询中，请稍候...");

        plugin.getTransactionLogManager().getTopSellersAsync(finalCurrency, 10, sinceMs, rows -> {
            if (rows.isEmpty()) {
                player.sendMessage("\u00a77暂无数据。");
                return;
            }
            player.sendMessage("\u00a76=== 销售额排行 (近" + finalDays + "天\u00a76|§e" + currencyName + "\u00a76) ===");
            String[] medals = {"\u00a76\u2460", "\u00a77\u2461", "\u00a77\u2462"};
            for (int i = 0; i < rows.size(); i++) {
                String[] row = rows.get(i);
                String medal = i < medals.length ? medals[i] : "\u00a77 " + (i + 1) + ".";
                double revenue = Double.parseDouble(row[1]);
                int orders = Integer.parseInt(row[2]);
                player.sendMessage(medal + " \u00a7f" + row[0]
                    + " \u00a77| 入\u00a7a" + plugin.getEconomyManager().formatCurrency(revenue, finalCurrency)
                    + " \u00a77| " + orders + "笔");
            }
        });
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("stall.admin")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }
        plugin.getConfigManager().reload();
        plugin.getMessageManager().reload();
        // 重建所有摆摊全息图，使配置立即生效
        plugin.getStallNPCManager().rebuildAllDisplays();
        plugin.getMessageManager().send(sender, "general.reload-success");
    }

    private void handleConfirmMove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        com.playerstallcraft.models.ShopShelf shelf = plugin.getShopManager().getPendingShelfMove(player.getUniqueId());
        if (shelf == null) {
            plugin.getMessageManager().sendRaw(player, "&c没有待移动的货架!");
            return;
        }

        com.playerstallcraft.models.Shop shop = plugin.getShopManager().getShop(shelf.getShopId());
        if (shop == null) {
            plugin.getMessageManager().sendRaw(player, "&c找不到商铺!");
            plugin.getShopManager().clearPendingShelfMove(player.getUniqueId());
            return;
        }

        if (!shop.isInRegion(player.getLocation())) {
            plugin.getMessageManager().sendRaw(player, "&c请站在商铺区域内!");
            return;
        }

        org.bukkit.Location newLoc = player.getLocation().getBlock().getLocation();
        org.bukkit.block.Block newBlock = newLoc.getBlock();

        if (!newBlock.getType().isAir()) {
            plugin.getMessageManager().sendRaw(player, "&c该位置已有方块!");
            return;
        }

        // 移除旧位置的木桶
        org.bukkit.Location oldLoc = shelf.getLocation();
        if (oldLoc != null) {
            org.bukkit.block.Block oldBlock = oldLoc.getBlock();
            if (oldBlock.getType() == org.bukkit.Material.BARREL) {
                oldBlock.setType(org.bukkit.Material.AIR);
            }
        }

        // 放置新木桶
        newBlock.setType(org.bukkit.Material.BARREL);

        // 更新货架位置
        shelf.setLocation(newLoc);
        plugin.getShopManager().updateShelfLocation(shelf);

        // 更新全息图
        plugin.getShelfHologramManager().removeHologram(shelf.getId());
        plugin.getShelfHologramManager().createHologram(shelf);

        plugin.getShopManager().clearPendingShelfMove(player.getUniqueId());
        plugin.getMessageManager().sendRaw(player, "&a货架已移动到新位置!");
    }

    private void handleCancelMove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        if (plugin.getShopManager().getPendingShelfMove(player.getUniqueId()) != null) {
            plugin.getShopManager().clearPendingShelfMove(player.getUniqueId());
            plugin.getMessageManager().sendRaw(player, "&a已取消移动货架");
        } else {
            plugin.getMessageManager().sendRaw(player, "&c没有待移动的货架!");
        }
    }

    private void handleAd(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        new com.playerstallcraft.gui.AdGUI(plugin, player).open();
    }

    private void handleGiveShelf(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playerstallcraft.admin")) {
            plugin.getMessageManager().sendRaw(sender, "&c你没有权限执行此命令");
            return;
        }

        // /baitan giveshelf <玩家> [耐久度] (-1为无限)
        if (args.length < 2) {
            plugin.getMessageManager().sendRaw(sender, "&c用法: /baitan giveshelf <玩家> [耐久度]");
            plugin.getMessageManager().sendRaw(sender, "&7耐久度: -1为无限, 默认从配置读取");
            return;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            plugin.getMessageManager().sendRaw(sender, "&c玩家不在线!");
            return;
        }

        int durability;
        if (args.length >= 3) {
            try {
                durability = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                plugin.getMessageManager().sendRaw(sender, "&c耐久度必须是数字!");
                return;
            }
        } else {
            durability = plugin.getConfigManager().getConfig().getInt("shop.shelf.durability.default-durability", 10);
        }

        // 创建货架物品
        org.bukkit.inventory.ItemStack shelfItem = createShelfItem(durability);
        target.getInventory().addItem(shelfItem);

        int salesPerDurability = plugin.getConfigManager().getConfig().getInt("shop.shelf.durability.sales-per-durability", 64);
        String durabilityText = durability == -1 ? "无限" : durability + " (可售卖" + (durability * salesPerDurability) + "件物品)";
        
        plugin.getMessageManager().sendRaw(sender, "&a已给予 &e" + target.getName() + " &a货架物品! 耐久度: &f" + durabilityText);
        plugin.getMessageManager().sendRaw(target, "&a你获得了一个货架物品! 耐久度: &f" + durabilityText);
    }

    private org.bukkit.inventory.ItemStack createShelfItem(int durability) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BARREL);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&6&l商铺货架"));
            
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7═══════════════════"));
            lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&f将此物品放置在商铺内"));
            lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&f即可创建一个货架"));
            lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7═══════════════════"));
            
            int salesPerDurability = plugin.getConfigManager().getConfig().getInt("shop.shelf.durability.sales-per-durability", 64);
            if (durability == -1) {
                lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&a耐久度: &f无限"));
            } else {
                lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&a耐久度: &f" + durability));
                lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7可售卖: &f" + (durability * salesPerDurability) + " 件物品"));
            }
            
            lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7"));
            lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&e&lPLAYERSTALLCRAFT_SHELF"));
            lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&8DURABILITY:" + durability));
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessageManager().sendRaw(sender, "&6=== 摆摊系统帮助 ===");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan on &7- 开始摆摊");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan off &7- 收摊");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan slogan <标语> &7- 设置摊位标语");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan myitems &7- 打开商品管理界面");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan buy &7- 打开求购市场");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan market &7- 打开全服交易市场");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan shop &7- 查看/租赁/购买商铺");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan license &7- 查看/购买营业执照");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan list &7- 查看摆摊玩家");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan price &7- 查询手持物品价格");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan top [vault|nye] [天数] &7- 销售额排行榜");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan ad &7- 广告系统");
        if (sender.hasPermission("stall.region")) {
            plugin.getMessageManager().sendRaw(sender, "&6--- 管理员 ---");
            plugin.getMessageManager().sendRaw(sender, "&e/baitan rm &7- 区域管理");
            plugin.getMessageManager().sendRaw(sender, "&e/baitan rc &7- 创建区域");
            if (sender.hasPermission("stall.admin")) {
                plugin.getMessageManager().sendRaw(sender, "&e/baitan reload &7- 重载");
                plugin.getMessageManager().sendRaw(sender, "&e/baitan giveshelf <玩家> [耐久度] &7- 给予货架");
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("on", "off", "slogan", "myitems", "buy", "market", "shop", "license", "list", "price", "ad", "top", "help", "confirmmove", "cancelmove"));
            if (sender.hasPermission("stall.region")) {
                completions.add("rm");
                completions.add("rc");
            }
            if (sender.hasPermission("stall.admin")) {
                completions.add("reload");
                completions.add("giveshelf");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("giveshelf")) {
            // 补全在线玩家名
            if (sender.hasPermission("playerstallcraft.admin")) {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("giveshelf")) {
            // 补全耐久度
            if (sender.hasPermission("playerstallcraft.admin")) {
                completions.addAll(Arrays.asList("-1", "1", "5", "10", "50", "100"));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("license")) {
            completions.addAll(Arrays.asList("buy", "renew"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("license") && args[1].equalsIgnoreCase("buy")) {
            completions.addAll(Arrays.asList("金币", "鸽币", "vault", "nye"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("ad")) {
            completions.addAll(Arrays.asList("list", "place", "cancel", "my"));
            if (sender.hasPermission("playerstallcraft.admin")) {
                completions.add("create");
                completions.add("delete");
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("ad")) {
            if (args[1].equalsIgnoreCase("place") || args[1].equalsIgnoreCase("delete")) {
                for (Integer slotId : plugin.getAdManager().getAdSlots().keySet()) {
                    completions.add(String.valueOf(slotId));
                }
            } else if (args[1].equalsIgnoreCase("cancel") && sender instanceof Player player) {
                for (Advertisement ad : plugin.getAdManager().getPlayerAds(player.getUniqueId())) {
                    completions.add(String.valueOf(ad.getId()));
                }
            }
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .toList();
    }
}

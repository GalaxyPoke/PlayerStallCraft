package com.playerstallcraft.commands;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.managers.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import com.playerstallcraft.models.PlayerStall;

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
            case "on", "open", "开摊" -> handleOpen(sender);
            case "off", "close", "收摊" -> handleClose(sender);
            case "slogan", "标语" -> handleSlogan(sender, args);
            case "additem", "上架" -> handleMyItems(sender);
            case "removeitem", "下架" -> handleMyItems(sender);
            case "myitems", "我的商品" -> handleMyItems(sender);
            case "region", "区域" -> handleRegion(sender, args);
            case "license", "执照" -> handleLicense(sender, args);
            case "list", "列表" -> handleList(sender);
            case "reload", "重载" -> handleReload(sender);
            case "help", "帮助" -> sendHelp(sender);
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
        PlayerStall stall = plugin.getStallManager().getStall(player);
        stall.setSlogan(slogan);
        // 立即刷新全息图显示
        plugin.getStallNPCManager().refreshDisplay(player.getUniqueId());
        plugin.getMessageManager().sendRaw(player, "&a标语已设置为: &f" + slogan);
        plugin.getMessageManager().sendRaw(player, "&7提示: 使用 || 可以分隔多行标语");
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

    private void handleRegion(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return;
        }

        if (!player.hasPermission("stall.region")) {
            plugin.getMessageManager().send(player, "general.no-permission");
            return;
        }

        if (args.length < 2) {
            plugin.getMessageManager().sendRaw(sender, "&c用法: /baitan region <save|delete|list> [名称]");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "save", "保存" -> {
                if (args.length < 3) {
                    plugin.getMessageManager().sendRaw(sender, "&c请指定区域名称!");
                    return;
                }
                if (!plugin.getRegionManager().hasSelection(player)) {
                    plugin.getMessageManager().send(player, "region.need-select-points");
                    return;
                }
                String name = args[2];
                plugin.getRegionManager().saveRegion(name, player);
                plugin.getMessageManager().send(player, "region.region-saved", 
                        MessageManager.placeholders("name", name));
            }
            case "delete", "删除" -> {
                if (args.length < 3) {
                    plugin.getMessageManager().sendRaw(sender, "&c请指定区域名称!");
                    return;
                }
                String name = args[2];
                plugin.getRegionManager().deleteRegion(name);
                plugin.getMessageManager().send(player, "region.region-deleted",
                        MessageManager.placeholders("name", name));
            }
            case "list", "列表" -> {
                plugin.getMessageManager().sendRaw(sender, "&6=== 摆摊区域列表 ===");
                plugin.getRegionManager().getRegions().forEach((name, region) -> {
                    plugin.getMessageManager().sendRaw(sender, String.format(
                            "&e%s &7- %s (%d,%d,%d) -> (%d,%d,%d)",
                            name, region.getWorldName(),
                            region.getX1(), region.getY1(), region.getZ1(),
                            region.getX2(), region.getY2(), region.getZ2()
                    ));
                });
            }
        }
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
                plugin.getMessageManager().sendRaw(sender, "&7使用 /baitan license buy 购买执照");
                plugin.getMessageManager().sendRaw(sender, "&7价格: &e" + plugin.getEconomyManager().formatCurrency(
                        plugin.getConfigManager().getLicensePrice(), "vault"));
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
                plugin.getLicenseManager().purchaseLicense(player);
            }
            case "renew", "续期" -> {
                plugin.getLicenseManager().renewLicense(player);
            }
            default -> {
                plugin.getMessageManager().sendRaw(sender, "&c用法: /baitan license [buy|renew]");
            }
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("stall.admin")) {
            plugin.getMessageManager().send(sender, "general.no-permission");
            return;
        }
        plugin.getConfigManager().reload();
        plugin.getMessageManager().reload();
        plugin.getMessageManager().send(sender, "general.reload-success");
    }

    private void sendHelp(CommandSender sender) {
        plugin.getMessageManager().sendRaw(sender, "&6=== 摆摊系统帮助 ===");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan on &7- 开始摆摊");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan off &7- 收摊");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan slogan <标语> &7- 设置摊位标语");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan myitems &7- 打开商品管理界面");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan license &7- 查看/购买营业执照");
        plugin.getMessageManager().sendRaw(sender, "&e/baitan list &7- 查看摆摊玩家");
        if (sender.hasPermission("stall.region")) {
            plugin.getMessageManager().sendRaw(sender, "&e/baitan region save <名称> &7- 保存区域");
            plugin.getMessageManager().sendRaw(sender, "&e/baitan region delete <名称> &7- 删除区域");
            plugin.getMessageManager().sendRaw(sender, "&e/baitan region list &7- 列出区域");
        }
        if (sender.hasPermission("stall.admin")) {
            plugin.getMessageManager().sendRaw(sender, "&e/baitan reload &7- 重载配置");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("on", "off", "slogan", "myitems", "license", "list", "help"));
            if (sender.hasPermission("stall.region")) {
                completions.add("region");
            }
            if (sender.hasPermission("stall.admin")) {
                completions.add("reload");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("region")) {
            completions.addAll(Arrays.asList("save", "delete", "list"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("region") && args[1].equalsIgnoreCase("delete")) {
            completions.addAll(plugin.getRegionManager().getRegions().keySet());
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .toList();
    }
}

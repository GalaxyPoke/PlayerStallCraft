package com.playerstallcraft.commands;

import com.playerstallcraft.PlayerStallCraft;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PriceCommand implements CommandExecutor {

    private final PlayerStallCraft plugin;

    public PriceCommand(PlayerStallCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only");
            return true;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType().isAir()) {
            plugin.getMessageManager().sendRaw(player, "&c请手持要查询的物品!");
            return true;
        }

        String itemType = itemInHand.getType().name();
        
        // 查询近10次成交记录
        plugin.getDatabaseManager().queryAsync(
                "SELECT price, currency_type, amount, created_at FROM transaction_logs WHERE item_data LIKE ? ORDER BY created_at DESC LIMIT 10",
                "%" + itemType + "%"
        ).thenAccept(rs -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    plugin.getMessageManager().sendRaw(player, "&6=== " + getItemName(itemInHand) + " 价格查询 ===");
                    boolean hasRecords = false;
                    while (rs != null && rs.next()) {
                        hasRecords = true;
                        double price = rs.getDouble("price");
                        String currency = rs.getString("currency_type");
                        int amount = rs.getInt("amount");
                        String time = rs.getString("created_at");
                        
                        plugin.getMessageManager().sendRaw(player, String.format(
                                "&7%s &e%.2f %s &7x%d",
                                time, price, currency, amount
                        ));
                    }
                    if (!hasRecords) {
                        plugin.getMessageManager().sendRaw(player, "&7暂无成交记录");
                    }
                } catch (Exception e) {
                    plugin.getMessageManager().sendRaw(player, "&c查询失败: " + e.getMessage());
                }
            });
        });

        return true;
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().replace("_", " ").toLowerCase();
    }
}

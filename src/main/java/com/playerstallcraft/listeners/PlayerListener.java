package com.playerstallcraft.listeners;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.gui.StallViewGUI;
import com.playerstallcraft.models.PlayerStall;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerListener implements Listener {

    private final PlayerStallCraft plugin;
    // 移动提示冷却（防止刷屏）
    private final Map<UUID, Long> moveCooldowns = new ConcurrentHashMap<>();
    private static final long MOVE_COOLDOWN_MS = 3000; // 3秒冷却
    // 租期提醒冷却：key=uuid+shopId，value=上次提醒时间（防止每次登录都刷邮件）
    private final Map<String, Long> rentWarnCooldowns = new ConcurrentHashMap<>();
    private static final long RENT_WARN_COOLDOWN_MS = 24 * 60 * 60 * 1000L; // 24小时

    public PlayerListener(PlayerStallCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 延迟1秒检查，确保插件完全加载
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            checkRentExpiry(player);
        }, 20L);
    }

    private void checkRentExpiry(Player player) {
        int warnDays = plugin.getConfig().getInt("shop.rent-expiry-warning-days", 3);
        if (warnDays <= 0) return;
        long now = System.currentTimeMillis();
        for (com.playerstallcraft.models.Shop shop : plugin.getShopManager().getPlayerShops(player.getUniqueId())) {
            if (!shop.isRented()) continue;
            int remaining = shop.getRemainingRentDays();
            if (remaining > warnDays) continue;
            // 24小时内已提醒过则跳过
            String cooldownKey = player.getUniqueId() + "_" + shop.getId();
            Long lastWarn = rentWarnCooldowns.get(cooldownKey);
            if (lastWarn != null && now - lastWarn < RENT_WARN_COOLDOWN_MS) continue;
            rentWarnCooldowns.put(cooldownKey, now);
            String timeStr = shop.getRemainingRentTime();
            boolean mailed = plugin.getSweetMailManager().sendNoticeMail(
                player.getUniqueId(),
                "商铺租期即将到期",
                "商铺 [" + shop.getName() + "] 租期仅剩 " + timeStr,
                "请尽快续租，否则商铺将被收回！",
                "进入游戏后使用 /baitan shop 进行续租。");
            if (!mailed) {
                plugin.getMessageManager().sendRaw(player,
                    "&e[商铺提醒] &7商铺 &e" + shop.getName() +
                    " &7租期仅剩 &c" + timeStr + "&7，请尽快续租！");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        java.util.UUID uuid = player.getUniqueId();
        
        // 玩家退出时自动收摊
        if (plugin.getStallManager().hasStall(player)) {
            plugin.getStallManager().closeStall(player);
        }
        
        // 清理玩家相关缓存 (防止内存泄漏)
        plugin.getShopManager().clearPlayerPendingData(uuid);
        plugin.getRegionManager().clearPlayerSelection(uuid);
        moveCooldowns.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        // 摆摊中的玩家禁止移动（允许转头）
        if (plugin.getStallNPCManager().isPlayerFrozen(player.getUniqueId())) {
            if (event.getFrom().getX() != event.getTo().getX() 
                    || event.getFrom().getY() != event.getTo().getY() 
                    || event.getFrom().getZ() != event.getTo().getZ()) {
                // 兜底检查：若玩家意外离开摆摊区域（如被其他插件强制移动），自动收摊
                if (plugin.getStallManager().hasStall(player)
                        && !plugin.getRegionManager().isInStallRegion(event.getTo())) {
                    plugin.getStallManager().closeStall(player);
                    plugin.getMessageManager().sendRaw(player, "&c你已离开摆摊区域，摊位已自动收摊，物品已返还!");
                    return;
                }
                event.setCancelled(true);
                // 移动提示（带冷却防止刷屏）
                UUID uuid = player.getUniqueId();
                long now = System.currentTimeMillis();
                Long lastWarn = moveCooldowns.get(uuid);
                if (lastWarn == null || now - lastWarn >= MOVE_COOLDOWN_MS) {
                    moveCooldowns.put(uuid, now);
                    plugin.getMessageManager().sendRaw(player, "&c你正在摆摊中，无法移动! &7使用 /baitan off 收摊后才能移动");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        // 摆摊中的玩家禁止蹲下(sit)
        if (plugin.getStallManager().hasStall(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        // 摆摊中的玩家禁止被传送（HIGHEST优先级确保最后执行，覆盖其他插件）
        if (plugin.getStallManager().hasStall(player)) {
            event.setCancelled(true);
            plugin.getMessageManager().sendRaw(player, "&c你正在摆摊中，无法传送! &7使用 /baitan off 收摊后才能传送");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerTeleportMonitor(PlayerTeleportEvent event) {
        // 兜底：若传送最终未被取消（如被其他插件强制放行），且玩家摊位已移出摆摊区域，则自动收摊
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (plugin.getStallManager().hasStall(player)
                && event.getTo() != null
                && !plugin.getRegionManager().isInStallRegion(event.getTo())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getStallManager().closeStall(player);
                plugin.getMessageManager().sendRaw(player, "&c你已离开摆摊区域，摊位已自动收摊，物品已返还!");
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityMount(EntityMountEvent event) {
        // 摆摊中的玩家禁止骑乘实体（阻止/sit等插件）
        if (event.getEntity() instanceof Player player) {
            if (plugin.getStallManager().hasStall(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }

        Player player = event.getPlayer();

        // 需要蹲下+右键才能查看摎位
        if (!player.isSneaking()) {
            return;
        }

        PlayerStall stall = plugin.getStallManager().getStall(target);
        if (stall == null) {
            return;
        }

        event.setCancelled(true);
        
        // 打开摊位查看GUI
        new StallViewGUI(plugin, player, stall).open();
    }
}

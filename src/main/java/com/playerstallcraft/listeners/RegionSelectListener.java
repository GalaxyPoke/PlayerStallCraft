package com.playerstallcraft.listeners;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.managers.ConfigManager;
import com.playerstallcraft.managers.MessageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class RegionSelectListener implements Listener {

    private final PlayerStallCraft plugin;

    public RegionSelectListener(PlayerStallCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        if (!player.hasPermission("stall.region")) {
            return;
        }

        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.NETHERITE_HOE) {
            return;
        }

        if (event.getClickedBlock() == null) {
            return;
        }

        Location clickedLocation = event.getClickedBlock().getLocation();
        Action action = event.getAction();

        if (action == Action.LEFT_CLICK_BLOCK) {
            // 左键设置点1
            event.setCancelled(true);
            plugin.getRegionManager().setPos1(player, clickedLocation);
            plugin.getMessageManager().send(player, "region.pos1-set", MessageManager.placeholders(
                    "x", String.valueOf(clickedLocation.getBlockX()),
                    "y", String.valueOf(clickedLocation.getBlockY()),
                    "z", String.valueOf(clickedLocation.getBlockZ())
            ));
            // 显示点1粒子
            spawnPos1Particles(clickedLocation);
            // 如果两点都设置了，显示选区边框
            showSelectionIfComplete(player);
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            // 右键设置点2
            event.setCancelled(true);
            plugin.getRegionManager().setPos2(player, clickedLocation);
            plugin.getMessageManager().send(player, "region.pos2-set", MessageManager.placeholders(
                    "x", String.valueOf(clickedLocation.getBlockX()),
                    "y", String.valueOf(clickedLocation.getBlockY()),
                    "z", String.valueOf(clickedLocation.getBlockZ())
            ));
            // 显示点2粒子
            spawnPos2Particles(clickedLocation);
            // 如果两点都设置了，显示选区边框
            showSelectionIfComplete(player);
        }
    }

    private void spawnPos1Particles(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        
        ConfigManager config = plugin.getConfigManager();
        Particle particle = getParticle(config.getPos1Particle(), Particle.FLAME);
        int count = config.getPos1ParticleCount();
        double spread = config.getPos1ParticleSpread();
        
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        world.spawnParticle(particle, center, count, spread, spread, spread, 0.01);
    }

    private void spawnPos2Particles(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        
        ConfigManager config = plugin.getConfigManager();
        Particle particle = getParticle(config.getPos2Particle(), Particle.SOUL_FIRE_FLAME);
        int count = config.getPos2ParticleCount();
        double spread = config.getPos2ParticleSpread();
        
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        world.spawnParticle(particle, center, count, spread, spread, spread, 0.01);
    }

    private Particle getParticle(String name, Particle defaultParticle) {
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无效的粒子类型: " + name + ", 使用默认值");
            return defaultParticle;
        }
    }

    private void showSelectionIfComplete(Player player) {
        if (!plugin.getRegionManager().hasSelection(player)) return;
        
        Location[] selection = plugin.getRegionManager().getSelection(player);
        if (selection == null) return;
        
        Location pos1 = selection[0];
        Location pos2 = selection[1];
        
        // 启动一个任务来显示选区边框粒子
        ConfigManager config = plugin.getConfigManager();
        final int maxTicks = config.getBorderDuration() * 20; // 秒转tick
        final int refreshInterval = config.getBorderRefreshInterval();
        
        new BukkitRunnable() {
            int ticks = 0;
            
            @Override
            public void run() {
                if (ticks >= maxTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                
                if (ticks % refreshInterval == 0) {
                    drawSelectionBorder(pos1, pos2, player);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drawSelectionBorder(Location pos1, Location pos2, Player player) {
        World world = pos1.getWorld();
        if (world == null) return;
        
        ConfigManager config = plugin.getConfigManager();
        Particle borderParticle = getParticle(config.getBorderParticle(), Particle.END_ROD);
        double step = config.getBorderStep();
        
        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX()) + 1;
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY()) + 1;
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ()) + 1;
        
        // 底部4条线
        for (double x = minX; x <= maxX; x += step) {
            player.spawnParticle(borderParticle, x, minY, minZ, 1, 0, 0, 0, 0);
            player.spawnParticle(borderParticle, x, minY, maxZ, 1, 0, 0, 0, 0);
        }
        for (double z = minZ; z <= maxZ; z += step) {
            player.spawnParticle(borderParticle, minX, minY, z, 1, 0, 0, 0, 0);
            player.spawnParticle(borderParticle, maxX, minY, z, 1, 0, 0, 0, 0);
        }
        
        // 顶部4条线
        for (double x = minX; x <= maxX; x += step) {
            player.spawnParticle(borderParticle, x, maxY, minZ, 1, 0, 0, 0, 0);
            player.spawnParticle(borderParticle, x, maxY, maxZ, 1, 0, 0, 0, 0);
        }
        for (double z = minZ; z <= maxZ; z += step) {
            player.spawnParticle(borderParticle, minX, maxY, z, 1, 0, 0, 0, 0);
            player.spawnParticle(borderParticle, maxX, maxY, z, 1, 0, 0, 0, 0);
        }
        
        // 垂直4条线（只在4个角显示）
        for (double y = minY; y <= maxY; y += step) {
            player.spawnParticle(borderParticle, minX, y, minZ, 1, 0, 0, 0, 0);
            player.spawnParticle(borderParticle, maxX, y, minZ, 1, 0, 0, 0, 0);
            player.spawnParticle(borderParticle, minX, y, maxZ, 1, 0, 0, 0, 0);
            player.spawnParticle(borderParticle, maxX, y, maxZ, 1, 0, 0, 0, 0);
        }
    }
}

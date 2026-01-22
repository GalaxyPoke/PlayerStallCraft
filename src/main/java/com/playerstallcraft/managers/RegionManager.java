package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.StallRegion;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RegionManager {

    private final PlayerStallCraft plugin;
    private final Map<String, StallRegion> regions;
    private final Map<UUID, Location> pos1Selections;
    private final Map<UUID, Location> pos2Selections;

    public RegionManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        this.regions = new ConcurrentHashMap<>();
        this.pos1Selections = new HashMap<>();
        this.pos2Selections = new HashMap<>();
        loadRegions();
    }

    private void loadRegions() {
        plugin.getDatabaseManager().queryAsync("SELECT * FROM stall_regions").thenAccept(rs -> {
            try {
                while (rs != null && rs.next()) {
                    StallRegion region = new StallRegion(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("world"),
                            rs.getInt("x1"), rs.getInt("y1"), rs.getInt("z1"),
                            rs.getInt("x2"), rs.getInt("y2"), rs.getInt("z2")
                    );
                    regions.put(region.getName(), region);
                }
                plugin.getLogger().info("已加载 " + regions.size() + " 个摆摊区域");
            } catch (SQLException e) {
                plugin.getLogger().severe("加载区域失败: " + e.getMessage());
            }
        });
    }

    public void setPos1(Player player, Location location) {
        pos1Selections.put(player.getUniqueId(), location);
    }

    public void setPos2(Player player, Location location) {
        pos2Selections.put(player.getUniqueId(), location);
    }

    public Location getPos1(Player player) {
        return pos1Selections.get(player.getUniqueId());
    }

    public Location getPos2(Player player) {
        return pos2Selections.get(player.getUniqueId());
    }

    public boolean hasSelection(Player player) {
        return pos1Selections.containsKey(player.getUniqueId()) 
                && pos2Selections.containsKey(player.getUniqueId());
    }

    public void saveRegion(String name, Player player) {
        Location pos1 = pos1Selections.get(player.getUniqueId());
        Location pos2 = pos2Selections.get(player.getUniqueId());

        if (pos1 == null || pos2 == null || !pos1.getWorld().equals(pos2.getWorld())) {
            return;
        }

        int x1 = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int y1 = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int z1 = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int x2 = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int y2 = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int z2 = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        String world = pos1.getWorld().getName();

        plugin.getDatabaseManager().executeAsync(
                "INSERT OR REPLACE INTO stall_regions (name, world, x1, y1, z1, x2, y2, z2) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                name, world, x1, y1, z1, x2, y2, z2
        ).thenRun(() -> {
            StallRegion region = new StallRegion(0, name, world, x1, y1, z1, x2, y2, z2);
            regions.put(name, region);
            pos1Selections.remove(player.getUniqueId());
            pos2Selections.remove(player.getUniqueId());
        });
    }

    public void deleteRegion(String name) {
        plugin.getDatabaseManager().executeAsync("DELETE FROM stall_regions WHERE name = ?", name)
                .thenRun(() -> regions.remove(name));
    }

    public boolean isInStallRegion(Location location) {
        for (StallRegion region : regions.values()) {
            if (region.contains(location)) {
                return true;
            }
        }
        return false;
    }

    public StallRegion getRegionAt(Location location) {
        for (StallRegion region : regions.values()) {
            if (region.contains(location)) {
                return region;
            }
        }
        return null;
    }

    public Map<String, StallRegion> getRegions() {
        return regions;
    }
}

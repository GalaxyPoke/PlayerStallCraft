package com.playerstallcraft.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class StallRegion {

    private final int id;
    private final String name;
    private final String worldName;
    private final int x1, y1, z1;
    private final int x2, y2, z2;

    public StallRegion(int id, String name, String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.id = id;
        this.name = name;
        this.worldName = worldName;
        this.x1 = Math.min(x1, x2);
        this.y1 = Math.min(y1, y2);
        this.z1 = Math.min(z1, z2);
        this.x2 = Math.max(x1, x2);
        this.y2 = Math.max(y1, y2);
        this.z2 = Math.max(z1, z2);
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equals(worldName)) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX1() {
        return x1;
    }

    public int getY1() {
        return y1;
    }

    public int getZ1() {
        return z1;
    }

    public int getX2() {
        return x2;
    }

    public int getY2() {
        return y2;
    }

    public int getZ2() {
        return z2;
    }
}

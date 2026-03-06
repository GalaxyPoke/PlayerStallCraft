package com.playerstallcraft.models;

import com.playerstallcraft.PlayerStallCraft;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerStall {

    private final PlayerStallCraft plugin;
    private final UUID ownerUuid;
    private final String ownerName;
    private String slogan;
    private final Location location;
    private final Map<Integer, StallItem> items;
    private BukkitTask hologramTask;
    private int currentDisplayIndex;
    private boolean active;
    private int totalSoldCount;

    public PlayerStall(PlayerStallCraft plugin, Player owner, String slogan) {
        this.plugin = plugin;
        this.ownerUuid = owner.getUniqueId();
        this.ownerName = owner.getName();
        this.slogan = slogan;
        this.location = owner.getLocation().clone();
        this.items = new HashMap<>();
        this.currentDisplayIndex = 0;
        this.active = false;
        this.totalSoldCount = 0;
        // 从 DB 加载历史累计销售件数
        plugin.getDatabaseManager().queryAsync(
                "SELECT total_sold_count FROM player_data WHERE uuid = ?",
                ownerUuid.toString()
        ).thenAccept(rs -> {
            try {
                if (rs != null && rs.next()) {
                    this.totalSoldCount = rs.getInt("total_sold_count");
                }
                if (rs != null) rs.close();
            } catch (Exception ignored) {}
        });
    }

    public void start() {
        this.active = true;
        startHologramDisplay();
    }

    public void stop() {
        this.active = false;
        if (hologramTask != null) {
            hologramTask.cancel();
            hologramTask = null;
        }
        clearHologram();
    }

    private void startHologramDisplay() {
        int interval = plugin.getConfigManager().getHologramRefreshInterval();
        hologramTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateHologram, 0L, interval);
    }

    private void updateHologram() {
        if (!active || items.isEmpty()) {
            return;
        }

        // 轮播显示商品
        StallItem[] itemArray = items.values().toArray(new StallItem[0]);
        if (itemArray.length == 0) return;

        currentDisplayIndex = (currentDisplayIndex + 1) % itemArray.length;

        // TODO: 使用全息显示API显示商品信息
        // 这里需要集成如HolographicDisplays或DecentHolograms等插件
        // 暂时使用ActionBar作为替代方案
    }

    private void clearHologram() {
        // TODO: 清除全息显示
    }

    public boolean addItem(int slot, ItemStack itemStack, double price, String currencyType) {
        if (slot < 0 || slot >= plugin.getConfigManager().getMaxSlots()) {
            return false;
        }
        if (items.containsKey(slot)) {
            return false;
        }

        StallItem stallItem = new StallItem(slot, itemStack, price, currencyType);
        items.put(slot, stallItem);

        // 保存到数据库
        plugin.getDatabaseManager().executeAsync(
                "INSERT INTO stall_items (owner_uuid, item_data, price, currency_type, amount, slot) VALUES (?, ?, ?, ?, ?, ?)",
                ownerUuid.toString(), 
                itemStackToBase64(itemStack),
                price, 
                currencyType,
                itemStack.getAmount(),
                slot
        );

        return true;
    }

    public StallItem removeItem(int slot) {
        StallItem item = items.remove(slot);
        if (item != null) {
            plugin.getDatabaseManager().executeAsync(
                    "DELETE FROM stall_items WHERE owner_uuid = ? AND slot = ?",
                    ownerUuid.toString(), slot
            );
        }
        return item;
    }

    public StallItem getItem(int slot) {
        return items.get(slot);
    }

    private String itemStackToBase64(ItemStack item) {
        try {
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            org.bukkit.util.io.BukkitObjectOutputStream dataOutput = new org.bukkit.util.io.BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return java.util.Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    public static ItemStack itemStackFromBase64(String data) {
        try {
            java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(data));
            org.bukkit.util.io.BukkitObjectInputStream dataInput = new org.bukkit.util.io.BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) {
            return null;
        }
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getSlogan() {
        return slogan;
    }

    public void setSlogan(String slogan) {
        this.slogan = slogan;
    }

    public Location getLocation() {
        return location;
    }

    public Map<Integer, StallItem> getItems() {
        return items;
    }

    public boolean isActive() {
        return active;
    }

    public int getItemCount() {
        return items.size();
    }

    public int getTotalSoldCount() {
        return totalSoldCount;
    }

    public void incrementSoldCount(int amount) {
        this.totalSoldCount += amount;
        plugin.getDatabaseManager().executeAsync(
                "UPDATE player_data SET total_sold_count = total_sold_count + ? WHERE uuid = ?",
                amount, ownerUuid.toString()
        );
    }

    public int getNextAvailableSlot() {
        int maxSlots = plugin.getConfigManager().getMaxSlots();
        for (int i = 0; i < maxSlots; i++) {
            if (!items.containsKey(i)) {
                return i;
            }
        }
        return -1;
    }

    public void clearAllItems() {
        items.clear();
        plugin.getDatabaseManager().executeAsync(
                "DELETE FROM stall_items WHERE owner_uuid = ?",
                ownerUuid.toString()
        );
    }

    public void addItem(StallItem stallItem) {
        items.put(stallItem.getSlot(), stallItem);
        
        plugin.getDatabaseManager().executeAsync(
                "INSERT INTO stall_items (owner_uuid, item_data, price, currency_type, amount, slot) VALUES (?, ?, ?, ?, ?, ?)",
                ownerUuid.toString(), 
                itemStackToBase64(stallItem.getItemStack()),
                stallItem.getPrice(), 
                stallItem.getCurrencyType(),
                stallItem.getAmount(),
                stallItem.getSlot()
        );
    }
}

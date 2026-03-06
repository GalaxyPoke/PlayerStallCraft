package com.playerstallcraft.npc;

import com.playerstallcraft.PlayerStallCraft;
import com.playerstallcraft.models.PlayerStall;
import com.playerstallcraft.models.StallItem;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StallNPCManager {

    private final PlayerStallCraft plugin;
    private final Map<UUID, StallDisplay> stallDisplays;
    private final Set<UUID> frozenPlayers;
    private boolean hasDecentHolograms;

    public StallNPCManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        this.stallDisplays = new HashMap<>();
        this.frozenPlayers = new HashSet<>();
        this.hasDecentHolograms = Bukkit.getPluginManager().getPlugin("DecentHolograms") != null;
        if (hasDecentHolograms) {
            plugin.getLogger().info("已检测到DecentHolograms，将使用物品图标显示");
        }
    }

    public void createStallDisplay(PlayerStall stall, Player owner) {
        removeStallDisplay(owner.getUniqueId());

        Location loc = owner.getLocation().clone();
        StallDisplay display = new StallDisplay(plugin, stall, loc, owner, hasDecentHolograms);
        display.start();
        stallDisplays.put(owner.getUniqueId(), display);
        frozenPlayers.add(owner.getUniqueId());
    }

    public void removeStallDisplay(UUID ownerUuid) {
        StallDisplay display = stallDisplays.remove(ownerUuid);
        if (display != null) {
            display.remove();
        }
        frozenPlayers.remove(ownerUuid);
    }

    public void removeAllDisplays() {
        for (StallDisplay display : stallDisplays.values()) {
            display.remove();
        }
        stallDisplays.clear();
        frozenPlayers.clear();
    }

    public void updateDisplay(UUID ownerUuid) {
        StallDisplay display = stallDisplays.get(ownerUuid);
        if (display != null) {
            display.updateHolograms();
        }
    }

    public void refreshDisplay(UUID ownerUuid) {
        StallDisplay display = stallDisplays.get(ownerUuid);
        if (display != null) {
            display.rebuild();
        }
    }

    public void rebuildAllDisplays() {
        for (StallDisplay display : stallDisplays.values()) {
            display.rebuild();
        }
    }

    public boolean isPlayerFrozen(UUID uuid) {
        return frozenPlayers.contains(uuid);
    }

    public static class StallDisplay {
        private final PlayerStallCraft plugin;
        private final PlayerStall stall;
        private final Location location;
        private final Player owner;
        private final boolean useDecentHolograms;
        // 已移除座位机制，玩家站立摆摎
        private Hologram decentHologram;
        private final List<ArmorStand> hologramStands;
        private BukkitTask rotationTask;
        private int currentItemIndex = 0;
        private int sloganLineCount = 1; // 标语行数

        public StallDisplay(PlayerStallCraft plugin, PlayerStall stall, Location location, Player owner, boolean useDecentHolograms) {
            this.plugin = plugin;
            this.stall = stall;
            this.location = location.clone();
            this.owner = owner;
            this.useDecentHolograms = useDecentHolograms;
            this.hologramStands = new ArrayList<>();
        }

        public void start() {
            // 玩家站立摆摎，通过PlayerMoveEvent禁止移动

            // 创建全息显示（在玩家头顶）
            if (useDecentHolograms) {
                createDecentHologram();
            } else {
                createFallbackHolograms();
            }
            
            // 启动轮播任务
            startRotation();
        }

        private void createDecentHologram() {
            double hologramHeight = plugin.getConfigManager().getConfig().getDouble("stall.hologram-height-offset", 4.5);
            Location holoLoc = location.clone().add(0, hologramHeight, 0);
            String holoName = "stall_" + owner.getUniqueId().toString().replace("-", "");
            
            // 删除可能存在的旧全息
            if (DHAPI.getHologram(holoName) != null) {
                DHAPI.removeHologram(holoName);
            }
            
            List<String> lines = new ArrayList<>();
            lines.add("&a&l" + owner.getName() + "正在摆摊中...");
            // 标语行（支持多行，用#分隔）
            String[] sloganParts = stall.getSlogan().split("#");
            sloganLineCount = sloganParts.length;
            for (String sloganLine : sloganParts) {
                lines.add("&6" + sloganLine.trim());
            }
            lines.add("&eShift+右键点击查看商品");
            lines.add("#ICON:BARRIER");
            lines.add("&e价格: &f暂无商品");
            lines.add("&7累计销售: &f0 &7件商品");
            
            decentHologram = DHAPI.createHologram(holoName, holoLoc, lines);
        }

        private void createFallbackHolograms() {
            double hologramHeight = plugin.getConfigManager().getConfig().getDouble("stall.hologram-height-offset", 4.5);
            Location holoLoc = location.clone().add(0, hologramHeight, 0);
            
            hologramStands.add(createHologramLine(holoLoc, "§a§l" + owner.getName() + "正在摆摊中..."));
            // 标语行（支持多行，用#分隔）
            double offset = 0.3;
            String[] sloganLines = stall.getSlogan().split("#");
            for (String sloganLine : sloganLines) {
                hologramStands.add(createHologramLine(holoLoc.clone().subtract(0, offset, 0), "§6" + sloganLine.trim()));
                offset += 0.3;
            }
            hologramStands.add(createHologramLine(holoLoc.clone().subtract(0, offset, 0), "§eShift+右键点击查看商品"));
            offset += 0.4;
            hologramStands.add(createHologramLine(holoLoc.clone().subtract(0, offset, 0), "§f")); // 商品名
            offset += 0.3;
            hologramStands.add(createHologramLine(holoLoc.clone().subtract(0, offset, 0), "§f")); // 价格
            offset += 0.4;
            hologramStands.add(createHologramLine(holoLoc.clone().subtract(0, offset, 0), "§7累计销售: §f0 §7件商品"));
        }

        private ArmorStand createHologramLine(Location loc, String text) {
            ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setCustomName(text);
            stand.setCustomNameVisible(true);
            stand.setMarker(true);
            stand.setInvulnerable(true);
            stand.setSmall(true);
            return stand;
        }

        public void updateHolograms() {
            var items = stall.getItems();
            
            if (useDecentHolograms && decentHologram != null) {
                updateDecentHologram(items);
            } else {
                updateFallbackHolograms(items);
            }
        }

        private void updateDecentHologram(Map<Integer, StallItem> items) {
            try {
                // 行索引: 0=玩家名, 1~sloganLineCount=标语, sloganLineCount+1=提示, sloganLineCount+2=图标, sloganLineCount+3=价格, sloganLineCount+4=销售
                int iconLineIndex = sloganLineCount + 2;
                int priceLineIndex = sloganLineCount + 3;
                int salesLineIndex = sloganLineCount + 4;
                
                // 检查全息图行数是否足够
                if (decentHologram == null || decentHologram.getPage(0).getLines().size() <= salesLineIndex) {
                    // 行数不够，重建全息图
                    rebuild();
                    return;
                }
                
                if (items.isEmpty()) {
                    DHAPI.setHologramLine(decentHologram, iconLineIndex, "#ICON:BARRIER");
                    DHAPI.setHologramLine(decentHologram, priceLineIndex, "&7暂无商品");
                } else {
                    var itemArray = items.values().toArray(new StallItem[0]);
                    if (currentItemIndex >= itemArray.length) {
                        currentItemIndex = 0;
                    }
                    
                    StallItem item = itemArray[currentItemIndex];
                    String iconLine = "#ICON:" + item.getItemStack().getType().name();
                    DHAPI.setHologramLine(decentHologram, iconLineIndex, iconLine);
                    DHAPI.setHologramLine(decentHologram, priceLineIndex, "&e价格: &f" + 
                            plugin.getEconomyManager().formatCurrency(item.getPrice(), item.getCurrencyType()));
                    
                    currentItemIndex = (currentItemIndex + 1) % itemArray.length;
                }
                
                DHAPI.setHologramLine(decentHologram, salesLineIndex, "&7累计销售: &f" + stall.getTotalSoldCount() + " &7件商品");
            } catch (Exception e) {
                // 出错时重建全息图
                rebuild();
            }
        }

        private void updateFallbackHolograms(Map<Integer, StallItem> items) {
            if (hologramStands.size() < 6) return;

            if (items.isEmpty()) {
                hologramStands.get(2).setCustomName("§7暂无商品");
                hologramStands.get(3).setCustomName("§7快来看看吧~");
            } else {
                var itemArray = items.values().toArray(new StallItem[0]);
                if (currentItemIndex >= itemArray.length) {
                    currentItemIndex = 0;
                }
                
                StallItem item = itemArray[currentItemIndex];
                String itemName = item.getItemName();
                if (itemName.length() > 20) {
                    itemName = itemName.substring(0, 17) + "...";
                }
                
                hologramStands.get(2).setCustomName("§b" + itemName);
                hologramStands.get(3).setCustomName("§e价格: §f" + 
                        plugin.getEconomyManager().formatCurrency(item.getPrice(), item.getCurrencyType()));
                
                currentItemIndex = (currentItemIndex + 1) % itemArray.length;
            }

            hologramStands.get(4).setCustomName("§6" + stall.getSlogan());
            hologramStands.get(5).setCustomName("§7累计销售: §f" + stall.getTotalSoldCount() + " §7件商品");
        }

        private void startRotation() {
            int interval = plugin.getConfigManager().getHologramRefreshInterval();
            rotationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateHolograms, 0L, interval);
        }

        public void rebuild() {
            // 重建全息图（用于标语更新等需要改变行数的情况）
            if (useDecentHolograms && decentHologram != null) {
                String holoName = "stall_" + owner.getUniqueId().toString().replace("-", "");
                DHAPI.removeHologram(holoName);
                decentHologram = null;
                createDecentHologram();
            } else {
                // 移除旧的盔甲架全息
                for (ArmorStand stand : hologramStands) {
                    if (stand != null && !stand.isDead()) {
                        stand.remove();
                    }
                }
                hologramStands.clear();
                // 重建
                createFallbackHolograms();
            }
        }

        public void remove() {
            if (rotationTask != null) {
                rotationTask.cancel();
                rotationTask = null;
            }

            // 移除DecentHolograms全息
            if (useDecentHolograms && decentHologram != null) {
                String holoName = "stall_" + owner.getUniqueId().toString().replace("-", "");
                DHAPI.removeHologram(holoName);
                decentHologram = null;
            }

            // 移除后备盔甲架全息
            for (ArmorStand stand : hologramStands) {
                if (stand != null && !stand.isDead()) {
                    stand.remove();
                }
            }
            hologramStands.clear();
        }
    }
}

package com.playerstallcraft.models;

import org.bukkit.inventory.ItemStack;

public class StallItem {

    private final int slot;
    private final ItemStack itemStack;
    private final double price;
    private final String currencyType;
    private int soldAmount;

    public StallItem(int slot, ItemStack itemStack, double price, String currencyType) {
        this.slot = slot;
        this.itemStack = itemStack.clone();
        this.price = price;
        this.currencyType = currencyType;
        this.soldAmount = 0;
    }

    public int getSlot() {
        return slot;
    }

    public ItemStack getItemStack() {
        return itemStack.clone();
    }

    public double getPrice() {
        return price;
    }

    public double getTotalPrice() {
        return price * itemStack.getAmount();
    }

    public String getCurrencyType() {
        return currencyType;
    }

    public int getAmount() {
        return itemStack.getAmount();
    }

    public int getSoldAmount() {
        return soldAmount;
    }

    public void addSoldAmount(int amount) {
        this.soldAmount += amount;
    }

    public void reduceAmount(int amount) {
        itemStack.setAmount(Math.max(0, itemStack.getAmount() - amount));
    }

    public String getItemName() {
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
            return itemStack.getItemMeta().getDisplayName();
        }
        return itemStack.getType().name().replace("_", " ").toLowerCase();
    }

    public String serialize() {
        try {
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            org.bukkit.util.io.BukkitObjectOutputStream dataOutput = new org.bukkit.util.io.BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(itemStack);
            dataOutput.close();
            return java.util.Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    public static StallItem deserialize(String data, int slot, double price, String currencyType) {
        try {
            java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(data));
            org.bukkit.util.io.BukkitObjectInputStream dataInput = new org.bukkit.util.io.BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return new StallItem(slot, item, price, currencyType);
        } catch (Exception e) {
            return null;
        }
    }
}

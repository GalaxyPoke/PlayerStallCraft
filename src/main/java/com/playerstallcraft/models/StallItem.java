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

    public String getItemName() {
        if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
            return itemStack.getItemMeta().getDisplayName();
        }
        return itemStack.getType().name().replace("_", " ").toLowerCase();
    }
}

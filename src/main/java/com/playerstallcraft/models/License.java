package com.playerstallcraft.models;

import java.util.UUID;

public class License {

    private final int id;
    private final UUID ownerUuid;
    private final String ownerName;
    private final long purchaseTime;
    private final long expireTime;
    private boolean active;

    public License(int id, UUID ownerUuid, String ownerName, long purchaseTime, long expireTime) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.purchaseTime = purchaseTime;
        this.expireTime = expireTime;
        this.active = true;
    }

    public int getId() {
        return id;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public long getPurchaseTime() {
        return purchaseTime;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public boolean isActive() {
        return active && !isExpired();
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expireTime;
    }

    public long getRemainingTime() {
        return Math.max(0, expireTime - System.currentTimeMillis());
    }

    public int getRemainingDays() {
        return (int) (getRemainingTime() / (1000 * 60 * 60 * 24));
    }
}

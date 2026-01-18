package com.github.cinnaio.relations.model;

import org.bukkit.Location;
import org.bukkit.Bukkit;

import java.sql.Timestamp;
import java.util.UUID;

public class Relation {
    private final UUID player1;
    private final UUID player2;
    private final String type;
    private int affinity;
    private int dailyAffinityGain;
    private Timestamp lastAffinityReset;
    private final Timestamp createdAt;
    
    // Marriage specific
    private String homeWorld;
    private double homeX, homeY, homeZ;
    private float homeYaw, homePitch;
    private boolean hasHome;

    public Relation(UUID player1, UUID player2, String type, int affinity, Timestamp createdAt) {
        this(player1, player2, type, affinity, 0, new Timestamp(System.currentTimeMillis()), createdAt);
    }
    
    public Relation(UUID player1, UUID player2, String type, int affinity, int dailyAffinityGain, Timestamp lastAffinityReset, Timestamp createdAt) {
        this.player1 = player1;
        this.player2 = player2;
        this.type = type;
        this.affinity = affinity;
        this.dailyAffinityGain = dailyAffinityGain;
        this.lastAffinityReset = lastAffinityReset;
        this.createdAt = createdAt;
        this.hasHome = false;
    }

    public UUID getPlayer1() { return player1; }
    public UUID getPlayer2() { return player2; }
    public String getType() { return type; }
    public int getAffinity() { return affinity; }
    public void setAffinity(int affinity) { this.affinity = affinity; }
    
    public int getDailyAffinityGain() { return dailyAffinityGain; }
    public void setDailyAffinityGain(int dailyAffinityGain) { this.dailyAffinityGain = dailyAffinityGain; }
    public void addDailyAffinityGain(int amount) { this.dailyAffinityGain += amount; }
    
    public Timestamp getLastAffinityReset() { return lastAffinityReset; }
    public void setLastAffinityReset(Timestamp lastAffinityReset) { this.lastAffinityReset = lastAffinityReset; }
    
    public Timestamp getCreatedAt() { return createdAt; }
    
    public void setHome(Location loc) {
        this.homeWorld = loc.getWorld().getName();
        this.homeX = loc.getX();
        this.homeY = loc.getY();
        this.homeZ = loc.getZ();
        this.homeYaw = loc.getYaw();
        this.homePitch = loc.getPitch();
        this.hasHome = true;
    }
    
    public Location getHome() {
        if (!hasHome || homeWorld == null) return null;
        return new Location(Bukkit.getWorld(homeWorld), homeX, homeY, homeZ, homeYaw, homePitch);
    }
    
    public boolean hasHome() { return hasHome; }
    
    // Get the other player relative to the given player
    public UUID getPartner(UUID player) {
        return player.equals(player1) ? player2 : player1;
    }
    
    // For database loading
    public void setHomeData(String world, double x, double y, double z, float yaw, float pitch) {
        this.homeWorld = world;
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
        this.homeYaw = yaw;
        this.homePitch = pitch;
        this.hasHome = true;
    }
}

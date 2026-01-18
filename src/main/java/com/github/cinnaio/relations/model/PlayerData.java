package com.github.cinnaio.relations.model;

import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private String gender;

    public PlayerData(UUID uuid, String gender) {
        this.uuid = uuid;
        this.gender = gender;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }
}

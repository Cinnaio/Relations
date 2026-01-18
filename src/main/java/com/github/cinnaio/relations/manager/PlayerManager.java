package com.github.cinnaio.relations.manager;

import com.github.cinnaio.relations.Relations;
import com.github.cinnaio.relations.database.PlayerDAO;
import com.github.cinnaio.relations.model.PlayerData;
import com.github.cinnaio.relations.util.SchedulerUtils;
import org.bukkit.Bukkit;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {

    private final Relations plugin;
    private final PlayerDAO playerDAO;
    private final Map<UUID, PlayerData> dataCache = new ConcurrentHashMap<>();

    public PlayerManager(Relations plugin, PlayerDAO playerDAO) {
        this.plugin = plugin;
        this.playerDAO = playerDAO;
    }

    public void loadData(UUID uuid) {
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                PlayerData data = playerDAO.getPlayerData(uuid);
                if (data == null) {
                    data = new PlayerData(uuid, "UNKNOWN");
                }
                dataCache.put(uuid, data);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void unloadData(UUID uuid) {
        dataCache.remove(uuid);
    }

    public PlayerData getData(UUID uuid) {
        return dataCache.get(uuid);
    }

    public void setGender(UUID uuid, String gender) {
        PlayerData data = dataCache.get(uuid);
        if (data == null) {
             data = new PlayerData(uuid, gender);
             dataCache.put(uuid, data);
        } else {
            data.setGender(gender);
        }
        
        PlayerData finalData = data;
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                playerDAO.savePlayerData(finalData);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
    
    public String getGender(UUID uuid) {
        PlayerData data = getData(uuid);
        return data != null ? data.getGender() : "UNKNOWN";
    }
}

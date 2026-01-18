package com.github.cinnaio.relations.database;

import com.github.cinnaio.relations.model.PlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PlayerDAO {

    private final DatabaseManager dbManager;

    public PlayerDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void savePlayerData(PlayerData data) throws SQLException {
        String sql = "REPLACE INTO player_data (uuid, gender) VALUES (?, ?)";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, data.getUuid().toString());
            ps.setString(2, data.getGender());
            ps.executeUpdate();
        }
    }

    public PlayerData getPlayerData(UUID uuid) throws SQLException {
        String sql = "SELECT * FROM player_data WHERE uuid = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerData(uuid, rs.getString("gender"));
                }
            }
        }
        return null;
    }
}

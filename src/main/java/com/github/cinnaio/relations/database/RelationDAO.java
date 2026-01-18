package com.github.cinnaio.relations.database;

import com.github.cinnaio.relations.model.Relation;
import org.bukkit.Location;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RelationDAO {

    private final DatabaseManager dbManager;

    public RelationDAO(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void createRelation(Relation relation) throws SQLException {
        String sql = "INSERT INTO relations_data (player1, player2, type, affinity, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, relation.getPlayer1().toString());
            ps.setString(2, relation.getPlayer2().toString());
            ps.setString(3, relation.getType());
            ps.setInt(4, relation.getAffinity());
            ps.setTimestamp(5, relation.getCreatedAt());
            ps.executeUpdate();
        }
    }

    public void deleteRelation(UUID player1, UUID player2, String type) throws SQLException {
        // Ensure order
        String p1 = player1.toString();
        String p2 = player2.toString();
        if (player1.compareTo(player2) > 0) {
            p1 = player2.toString();
            p2 = player1.toString();
        }

        String sql = "DELETE FROM relations_data WHERE player1 = ? AND player2 = ? AND type = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p1);
            ps.setString(2, p2);
            ps.setString(3, type);
            ps.executeUpdate();
        }
    }

    public void updateAffinity(UUID player1, UUID player2, String type, int newAffinity) throws SQLException {
         String p1 = player1.toString();
        String p2 = player2.toString();
        if (player1.compareTo(player2) > 0) {
            p1 = player2.toString();
            p2 = player1.toString();
        }
        
        String sql = "UPDATE relations_data SET affinity = ? WHERE player1 = ? AND player2 = ? AND type = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newAffinity);
            ps.setString(2, p1);
            ps.setString(3, p2);
            ps.setString(4, type);
            ps.executeUpdate();
        }
    }
    
    public void updateDailyAffinity(UUID player1, UUID player2, String type, int gain, Timestamp reset) throws SQLException {
         String p1 = player1.toString();
        String p2 = player2.toString();
        if (player1.compareTo(player2) > 0) {
            p1 = player2.toString();
            p2 = player1.toString();
        }
        
        String sql = "UPDATE relations_data SET daily_affinity_gain = ?, last_affinity_reset = ? WHERE player1 = ? AND player2 = ? AND type = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, gain);
            ps.setTimestamp(2, reset);
            ps.setString(3, p1);
            ps.setString(4, p2);
            ps.setString(5, type);
            ps.executeUpdate();
        }
    }

    public void updateHome(Relation relation) throws SQLException {
        String sql = "UPDATE relations_data SET home_world = ?, home_x = ?, home_y = ?, home_z = ?, home_yaw = ?, home_pitch = ? WHERE player1 = ? AND player2 = ? AND type = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            Location loc = relation.getHome();
            if (loc != null) {
                ps.setString(1, loc.getWorld().getName());
                ps.setDouble(2, loc.getX());
                ps.setDouble(3, loc.getY());
                ps.setDouble(4, loc.getZ());
                ps.setFloat(5, loc.getYaw());
                ps.setFloat(6, loc.getPitch());
            } else {
                ps.setNull(1, Types.VARCHAR);
                ps.setNull(2, Types.DOUBLE);
                ps.setNull(3, Types.DOUBLE);
                ps.setNull(4, Types.DOUBLE);
                ps.setNull(5, Types.FLOAT);
                ps.setNull(6, Types.FLOAT);
            }
            ps.setString(7, relation.getPlayer1().toString());
            ps.setString(8, relation.getPlayer2().toString());
            ps.setString(9, relation.getType());
            ps.executeUpdate();
        }
    }

    public List<Relation> getRelations(UUID player) throws SQLException {
        List<Relation> relations = new ArrayList<>();
        String pid = player.toString();
        // Check both player1 and player2 columns
        String sql = "SELECT * FROM relations_data WHERE player1 = ? OR player2 = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, pid);
            ps.setString(2, pid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    relations.add(mapRow(rs));
                }
            }
        }
        return relations;
    }
    
    public Relation getRelation(UUID p1, UUID p2, String type) throws SQLException {
        if (p1.compareTo(p2) > 0) {
            UUID temp = p1; p1 = p2; p2 = temp;
        }
        String sql = "SELECT * FROM relations_data WHERE player1 = ? AND player2 = ? AND type = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p1.toString());
            ps.setString(2, p2.toString());
            ps.setString(3, type);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public List<Relation> getRelationsByType(String type) throws SQLException {
        List<Relation> relations = new ArrayList<>();
        String sql = "SELECT * FROM relations_data WHERE type = ? ORDER BY created_at DESC LIMIT 50"; // Limit for safety
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    relations.add(mapRow(rs));
                }
            }
        }
        return relations;
    }

    public List<Relation> getTopRelations(String type, int limit) throws SQLException {
        List<Relation> relations = new ArrayList<>();
        String sql = "SELECT * FROM relations_data WHERE type = ? ORDER BY affinity DESC LIMIT ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    relations.add(mapRow(rs));
                }
            }
        }
        return relations;
    }

    private Relation mapRow(ResultSet rs) throws SQLException {
        UUID p1 = UUID.fromString(rs.getString("player1"));
        UUID p2 = UUID.fromString(rs.getString("player2"));
        String type = rs.getString("type");
        int affinity = rs.getInt("affinity");
        Timestamp createdAt = rs.getTimestamp("created_at");
        int dailyGain = rs.getInt("daily_affinity_gain");
        Timestamp lastReset = rs.getTimestamp("last_affinity_reset");
        if (lastReset == null) lastReset = createdAt; // fallback
        
        Relation rel = new Relation(p1, p2, type, affinity, dailyGain, lastReset, createdAt);
        
        String hw = rs.getString("home_world");
        if (hw != null) {
            rel.setHomeData(hw, rs.getDouble("home_x"), rs.getDouble("home_y"), rs.getDouble("home_z"), rs.getFloat("home_yaw"), rs.getFloat("home_pitch"));
        }
        return rel;
    }
}

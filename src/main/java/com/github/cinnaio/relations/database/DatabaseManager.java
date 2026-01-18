package com.github.cinnaio.relations.database;

import com.github.cinnaio.relations.Relations;
import com.github.cinnaio.relations.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final Relations plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(Relations plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        ConfigManager config = plugin.getConfigManager();
        String type = config.getDatabaseType();

        HikariConfig hikariConfig = new HikariConfig();

        if (type.equalsIgnoreCase("MYSQL")) {
            hikariConfig.setJdbcUrl("jdbc:mysql://" + config.getDatabaseHost() + ":" + config.getDatabasePort() + "/" + config.getDatabaseName());
            hikariConfig.setUsername(config.getDatabaseUser());
            hikariConfig.setPassword(config.getDatabasePassword());
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            if (config.useSSL()) {
                 hikariConfig.addDataSourceProperty("useSSL", "true");
            } else {
                 hikariConfig.addDataSourceProperty("useSSL", "false");
            }
        } else {
            // SQLite
            File file = new File(plugin.getDataFolder(), "relations.db");
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        }

        hikariConfig.setMaximumPoolSize(config.getPoolSize());
        hikariConfig.setPoolName("RelationsPool");

        dataSource = new HikariDataSource(hikariConfig);

        createTables();
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS relations_data (" +
                    "player1 VARCHAR(36) NOT NULL," +
                    "player2 VARCHAR(36) NOT NULL," +
                    "type VARCHAR(32) NOT NULL," +
                    "affinity INT DEFAULT 0," +
                    "daily_affinity_gain INT DEFAULT 0," +
                    "last_affinity_reset TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "home_world VARCHAR(64)," +
                    "home_x DOUBLE," +
                    "home_y DOUBLE," +
                    "home_z DOUBLE," +
                    "home_yaw FLOAT," +
                    "home_pitch FLOAT," +
                    "PRIMARY KEY (player1, player2, type)" +
                    ");";
            stmt.execute(sql);

            String playerSql = "CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "gender VARCHAR(16)" +
                    ");";
            stmt.execute(playerSql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            dataSource = null; // Ensure reference is cleared
        }
    }
    
    public boolean isClosed() {
        return dataSource == null || dataSource.isClosed();
    }
}

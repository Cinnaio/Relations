package com.github.cinnaio.relations;

import com.github.cinnaio.relations.command.RelationsCommand;
import com.github.cinnaio.relations.config.ConfigManager;
import com.github.cinnaio.relations.database.DatabaseManager;
import com.github.cinnaio.relations.database.RelationDAO;
import com.github.cinnaio.relations.gui.GuiManager;
import com.github.cinnaio.relations.listener.RelationListener;
import com.github.cinnaio.relations.database.PlayerDAO;
import com.github.cinnaio.relations.manager.ActionManager;
import com.github.cinnaio.relations.manager.AffinityItemManager;
import com.github.cinnaio.relations.manager.LevelManager;
import com.github.cinnaio.relations.manager.PlayerManager;
import com.github.cinnaio.relations.manager.RelationManager;
import com.github.cinnaio.relations.papi.RelationsExpansion;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Relations extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private RelationManager relationManager;
    private PlayerManager playerManager;
    private GuiManager guiManager;
    private AffinityItemManager affinityItemManager;
    private LevelManager levelManager;
    private ActionManager actionManager;

    @Override
    public void onEnable() {
        // Config
        this.configManager = new ConfigManager(this);
        this.actionManager = new ActionManager(this);
        this.levelManager = new LevelManager(this);

        // Database
        // Check if DB needs initialization or if it was just a soft reload
        if (this.databaseManager != null && !this.databaseManager.isClosed()) {
             this.databaseManager.close();
        }
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.initialize();
        
        // DAO & Manager
        RelationDAO relationDAO = new RelationDAO(databaseManager);
        this.relationManager = new RelationManager(this, relationDAO);
        
        PlayerDAO playerDAO = new PlayerDAO(databaseManager);
        this.playerManager = new PlayerManager(this, playerDAO);
        
        this.affinityItemManager = new AffinityItemManager(this);
        
        // GUI
        this.guiManager = new GuiManager(this);
        
        // Commands
        getCommand("relations").setExecutor(new RelationsCommand(this));
        
        // Listeners
        // Unregister listeners if re-enabling? Plugman calls onDisable -> onEnable.
        // Bukkit automatically unregisters listeners on disable.
        getServer().getPluginManager().registerEvents(new RelationListener(this), this);
        
        // PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new RelationsExpansion(this).register();
        }
        
        // Reload online players' data (if plugman reload happened while players are online)
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            relationManager.loadRelations(p.getUniqueId());
            playerManager.loadData(p.getUniqueId());
        }
        
        getLogger().info("Relations plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("Relations plugin disabled!");
    }
    
    public ConfigManager getConfigManager() { return configManager; }
    public RelationManager getRelationManager() { return relationManager; }
    public PlayerManager getPlayerManager() { return playerManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public AffinityItemManager getAffinityItemManager() { return affinityItemManager; }
    public LevelManager getLevelManager() { return levelManager; }
    public ActionManager getActionManager() { return actionManager; }
}

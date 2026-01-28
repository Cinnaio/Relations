package com.github.cinnaio.relations.manager;

import com.github.cinnaio.relations.Relations;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class LevelManager {

    private final Relations plugin;
    // Affinity -> Level Number
    private final TreeMap<Integer, Integer> levels = new TreeMap<>();
    // Level Number -> Actions
    private final Map<Integer, List<String>> levelActions = new HashMap<>();

    public LevelManager(Relations plugin) {
        this.plugin = plugin;
        loadLevels();
    }

    public void loadLevels() {
        levels.clear();
        levelActions.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("levels");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                int level = Integer.parseInt(key); // Level number from key
                int requiredAffinity;
                List<String> actions = new ArrayList<>();

                if (section.isConfigurationSection(key)) {
                    ConfigurationSection lvlSec = section.getConfigurationSection(key);
                    requiredAffinity = lvlSec.getInt("affinity");
                    if (lvlSec.contains("actions")) {
                        actions.addAll(lvlSec.getStringList("actions"));
                    }
                } else {
                    requiredAffinity = section.getInt(key);
                }
                
                // We use requiredAffinity as key to find the level for a given affinity
                levels.put(requiredAffinity, level);
                if (!actions.isEmpty()) {
                    levelActions.put(level, actions);
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid level key in config: " + key);
            }
        }
    }

    public int getLevel(int affinity) {
        Map.Entry<Integer, Integer> entry = levels.floorEntry(affinity);
        return entry != null ? entry.getValue() : 0; // 0 if below lowest configured level
    }
    
    public String getLevelDisplay(int affinity) {
        int lvl = getLevel(affinity);
        return "Lv." + lvl;
    }

    public List<String> getLevelActions(int level) {
        return levelActions.getOrDefault(level, Collections.emptyList());
    }
}

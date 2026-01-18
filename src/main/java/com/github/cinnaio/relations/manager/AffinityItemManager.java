package com.github.cinnaio.relations.manager;

import com.github.cinnaio.relations.Relations;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AffinityItemManager {

    private final Relations plugin;
    private final Map<String, ItemStack> affinityItems = new HashMap<>();
    private final Map<String, Integer> affinityAmounts = new HashMap<>();
    private File itemsFile;
    private FileConfiguration itemsConfig;

    public AffinityItemManager(Relations plugin) {
        this.plugin = plugin;
        loadItems();
    }

    private final Map<String, String> affinityTypes = new HashMap<>(); // key -> type

    public void loadItems() {
        affinityItems.clear();
        affinityAmounts.clear();
        affinityTypes.clear();
        
        itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            plugin.saveResource("items.yml", false);
        }
        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
        
        ConfigurationSection section = itemsConfig.getConfigurationSection("affinity-items");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            if (section.contains(key + ".type")) {
                // New format (key=id, has explicit type field)
                String type = section.getString(key + ".type");
                int amount = section.getInt(key + ".amount", 10);
                ItemStack item = section.getItemStack(key + ".item");
                
                if (type != null && item != null) {
                    affinityItems.put(key, item);
                    affinityAmounts.put(key, amount);
                    affinityTypes.put(key, type);
                }
            } else if (section.isItemStack(key + ".item")) {
                // Old format (key=type)
                // Treat key as ID, but also type.
                ItemStack item = section.getItemStack(key + ".item");
                int amount = section.getInt(key + ".amount", 10);
                if (item != null) {
                    affinityItems.put(key, item);
                    affinityAmounts.put(key, amount);
                    affinityTypes.put(key, key); // key is type
                }
            }
        }
    }

    public void saveItem(String type, int amount, ItemStack item) {
        if (itemsConfig == null) loadItems();
        
        // Generate unique ID
        String id = type + "_" + amount + "_" + System.currentTimeMillis();
        
        itemsConfig.set("affinity-items." + id + ".type", type);
        itemsConfig.set("affinity-items." + id + ".amount", amount);
        itemsConfig.set("affinity-items." + id + ".item", item);
        
        try {
            itemsConfig.save(itemsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save items.yml!");
            e.printStackTrace();
        }
        
        loadItems();
    }
    
    // Deprecated simple save
    public void saveItem(String type, ItemStack item) {
        saveItem(type, 10, item);
    }

    public String getAffinityType(ItemStack item) {
        String key = getItemKey(item);
        return key != null ? affinityTypes.get(key) : null;
    }

    public int getAffinityAmount(ItemStack item) {
        String key = getItemKey(item);
        return key != null ? affinityAmounts.getOrDefault(key, 10) : 0;
    }
    
    // Helper to find key by item
    private String getItemKey(ItemStack item) {
        if (item == null) return null;
        for (Map.Entry<String, ItemStack> entry : affinityItems.entrySet()) {
            if (entry.getValue().isSimilar(item)) {
                return entry.getKey();
            }
        }
        return null;
    }
}

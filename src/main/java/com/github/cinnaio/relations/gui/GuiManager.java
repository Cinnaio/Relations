package com.github.cinnaio.relations.gui;

import com.github.cinnaio.relations.Relations;
import com.github.cinnaio.relations.model.Relation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class GuiManager {

    private final Relations plugin;
    public static final Map<UUID, String> FAKE_NAMES = new java.util.concurrent.ConcurrentHashMap<>();

    public GuiManager(Relations plugin) {
        this.plugin = plugin;
    }

    public void openRelationsGui(Player player) {
        openRelationsGui(player, 1);
    }
    
    public void closeAllMenus() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof RelationsHolder) {
                p.closeInventory();
            }
        }
    }

    public void openRelationsGui(Player player, int page) {
        FileConfiguration menuConfig = plugin.getConfigManager().getMenuConfig();
        if (menuConfig == null) {
            player.sendMessage(Component.text("Menu configuration not found!", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        String title = plugin.getConfigManager().processColorCodes(menuConfig.getString("title", "Relations"));
        title = title.replace("<page>", String.valueOf(page));
        int size = menuConfig.getInt("size", 54);
        
        Inventory inv = Bukkit.createInventory(new RelationsHolder(page), size, MiniMessage.miniMessage().deserialize(title));

        // Layout Support
        List<Integer> relationSlots = new ArrayList<>();
        Map<Integer, ItemStack> staticItems = new HashMap<>();
        
        if (menuConfig.contains("Layout")) {
            parseLayout(menuConfig, size, relationSlots, staticItems, player, page);
        } else {
            // Legacy/Default Mode
            // Load valid slots for relations
            ConfigurationSection relConfig = menuConfig.getConfigurationSection("relations");
            if (relConfig != null) {
                List<Integer> configSlots = relConfig.getIntegerList("slots");
                if (!configSlots.isEmpty()) {
                    relationSlots.addAll(configSlots);
                } else {
                    int start = relConfig.getInt("start-slot", 0);
                    int end = relConfig.getInt("end-slot", size - 1);
                    for (int i = start; i <= end; i++) {
                        relationSlots.add(i);
                    }
                }
            }
            
            // Static Items
            ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String key : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSec = itemsSection.getConfigurationSection(key);
                    if (itemSec == null) continue;
                    
                    ItemStack item = buildItem(itemSec, null, null);
                    // Add PDC action if present
                    if (itemSec.contains("action")) {
                        String action = itemSec.getString("action");
                        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "action"), PersistentDataType.STRING, action);
                        item.setItemMeta(meta);
                    }
                    // Support new actions format in legacy items too if needed?
                    // For now keeping legacy behavior strict for legacy config.
                    
                    List<Integer> slots = itemSec.getIntegerList("slots");
                    for (int slot : slots) {
                        if (slot >= 0 && slot < size) {
                            staticItems.put(slot, item);
                        }
                    }
                }
            }
        }

        // Apply Static Items
        for (Map.Entry<Integer, ItemStack> entry : staticItems.entrySet()) {
            inv.setItem(entry.getKey(), entry.getValue());
        }
        
        // Render Relations
        ConfigurationSection relConfig = menuConfig.getConfigurationSection("relations");
        boolean lineBreak = relConfig != null && relConfig.getBoolean("line-break", true);
        int itemsPerPage = relationSlots.size();

        // 1. Prepare items to render (Virtual List)
        // ... (Existing logic for relations rendering)
        List<ItemStack> virtualItems = new ArrayList<>();
        List<String> types = plugin.getConfigManager().getRelationTypes().stream().toList();
        List<Relation> relations = plugin.getRelationManager().getRelations(player.getUniqueId());
        
        Map<Integer, ItemStack> pageItems = new HashMap<>();
        int globalIndex = 0; 
        
        for (String type : types) {
             // ... (Existing logic stats)
            int current = 0;
            List<Relation> typeRelations = new ArrayList<>();
            for (Relation r : relations) {
                if (r.getType().equalsIgnoreCase(type)) {
                    current++;
                    typeRelations.add(r);
                }
            }
            int max = plugin.getRelationManager().getMaxRelations(player, type);

            // Header
            if (relConfig != null && relConfig.contains("header-item")) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("<display>", plugin.getConfigManager().getRelationDisplay(type));
                placeholders.put("<current>", String.valueOf(current));
                placeholders.put("<max>", String.valueOf(max));
                
                ConfigurationSection headerSection = relConfig.getConfigurationSection("header-item");
                if (relConfig.contains("types." + type + ".header-item")) {
                    headerSection = relConfig.getConfigurationSection("types." + type + ".header-item");
                }
                
                ItemStack header = buildItem(headerSection, placeholders, null);
                pageItems.put(globalIndex++, header);
            }

            // Members
            if (relConfig != null && relConfig.contains("member-item")) {
                ConfigurationSection memberSection = relConfig.getConfigurationSection("member-item");
                if (relConfig.contains("types." + type + ".member-item")) {
                     memberSection = relConfig.getConfigurationSection("types." + type + ".member-item");
                }

                for (Relation r : typeRelations) {
                    UUID partnerId = r.getPartner(player.getUniqueId());
                    OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);
                    
                    Map<String, String> placeholders = new HashMap<>();
                    String rDisplay = plugin.getConfigManager().getRelationDisplay(r.getType());
                    placeholders.put("<display>", rDisplay);
                    
                    String partnerName = partner.getName();
                    if (partnerName == null) partnerName = FAKE_NAMES.get(partnerId);
                    placeholders.put("<player>", partnerName != null ? partnerName : "Unknown");
                    
                    placeholders.put("<affinity>", String.valueOf(r.getAffinity()));
                    placeholders.put("<level>", String.valueOf(plugin.getLevelManager().getLevel(r.getAffinity())));
                    placeholders.put("<level_display>", plugin.getLevelManager().getLevelDisplay(r.getAffinity()));
                    placeholders.put("<date>", r.getCreatedAt().toString());

                    ItemStack head = buildItem(memberSection, placeholders, partner);
                    pageItems.put(globalIndex++, head);
                }
            }
            
            // Line Break Logic (Same as before)
            if (lineBreak && !relationSlots.isEmpty()) {
                if (globalIndex > 0) {
                     int prevVirtualIndex = (globalIndex - 1) % itemsPerPage;
                     int prevPhysicalSlot = relationSlots.get(prevVirtualIndex);
                     int prevRow = prevPhysicalSlot / 9;
                     
                     int checkIndex = globalIndex;
                     boolean found = false;
                     for (int i = 0; i < itemsPerPage; i++) {
                         int virtualIndex = checkIndex % itemsPerPage;
                         int physicalSlot = relationSlots.get(virtualIndex);
                         int row = physicalSlot / 9;
                         
                         int pageOfCheck = checkIndex / itemsPerPage;
                         int pageOfPrev = (globalIndex - 1) / itemsPerPage;
                         
                         if (pageOfCheck > pageOfPrev) {
                             globalIndex = checkIndex;
                             found = true;
                             break;
                         }
                         
                         if (row > prevRow) {
                             globalIndex = checkIndex;
                             found = true;
                             break;
                         }
                         checkIndex++;
                     }
                }
            }
        }
        
        // Determine Max Pages
        int totalItems = globalIndex;
        int totalPages = (totalItems > 0) ? (int) Math.ceil((double) totalItems / itemsPerPage) : 1;
        if (page > totalPages) page = totalPages;
        if (page < 1) page = 1;
        
        // Handle Prev/Next buttons visibility if they are static items in Layout
        // In Layout mode, static items are already in 'staticItems' map.
        // We need to hide them if not needed? 
        // TrMenu usually handles this by having different icons or conditional icons.
        // For simplicity, we assume Layout defines them and they are always there, 
        // OR we check for "action" in static items and hide if invalid page.
        // But let's stick to standard behavior: if the item is there, it's there.
        // However, if we use the old logic's "items" section, we have logic for hiding.
        // In Layout mode, we should check actions.
        
        if (menuConfig.contains("Layout")) {
            // Post-process static items for page navigation visibility
            Iterator<Map.Entry<Integer, ItemStack>> it = staticItems.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, ItemStack> entry = it.next();
                ItemStack item = entry.getValue();
                if (item == null || item.getItemMeta() == null) continue;
                String action = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "action"), PersistentDataType.STRING);
                if (action != null) {
                    if (action.contains("previous_page") && page <= 1) {
                         inv.setItem(entry.getKey(), new ItemStack(Material.AIR)); // Remove from inventory
                         // We don't remove from map as map is local var
                    } else if (action.contains("next_page") && page >= totalPages) {
                         inv.setItem(entry.getKey(), new ItemStack(Material.AIR));
                    }
                }
            }
        }

        // Fill Relations
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);
        
        for (int i = startIndex; i < endIndex; i++) {
            ItemStack item = pageItems.get(i);
            if (item != null) {
                int virtualIndex = i % itemsPerPage;
                int physicalSlot = relationSlots.get(virtualIndex);
                inv.setItem(physicalSlot, item);
            }
        }

        player.openInventory(inv);
    }

    private void parseLayout(FileConfiguration config, int size, List<Integer> relationSlots, Map<Integer, ItemStack> staticItems, Player player, int page) {
        List<?> layoutList = config.getList("Layout");
        if (layoutList == null) return;
        
        List<String> rows = new ArrayList<>();
        
        // Detect layout type: Single Page (List<String>) or Multi Page (List<List<String>>)
        boolean isMultiPage = false;
        if (!layoutList.isEmpty() && layoutList.get(0) instanceof List) {
            isMultiPage = true;
        }

        if (isMultiPage) {
            // Get layout for specific page
            int index = page - 1;
            if (index < 0) index = 0;
            
            // If page exceeds defined layouts, use the last one
            if (index >= layoutList.size()) {
                index = layoutList.size() - 1;
            }
            
            Object pageLayoutObj = layoutList.get(index);
            if (pageLayoutObj instanceof List) {
                for (Object sub : (List<?>) pageLayoutObj) {
                    rows.add(sub.toString());
                }
            }
        } else {
            // Legacy/Single Page Mode: Flatten everything
            for (Object obj : layoutList) {
                if (obj instanceof List) {
                    for (Object sub : (List<?>) obj) {
                        rows.add(sub.toString());
                    }
                } else {
                    rows.add(obj.toString());
                }
            }
        }
        
        ConfigurationSection icons = config.getConfigurationSection("Icons");
        // Identify relation char: defaults to '+' if not defined in relations.layout-char
        String relChar = config.getString("relations.layout-char", "+");
        
        int slot = 0;
        for (String row : rows) {
            // Assume simple char grid for now, or space separated?
            // TrMenu usually 1 char = 1 slot.
            // If the string length > 9, maybe it's space separated?
            // User example: '#   A   #' (9 chars)
            // User example: '# `B1``A1``D2` #' -> this is tricky.
            // Let's assume standard char grid (9 chars per row) first. 
            // If row length > 9, we might try to split by whitespace if it looks like keys.
            
            // Standardizing: if row length <= 9, treat as chars.
            // If row length > 9, split by space? 
            // The user's example '# `B1``A1``D2` #' has backticks. Maybe they mean specific keys.
            // Let's try to be smart:
            String[] keys;
            if (row.length() <= 9) {
                keys = row.split("");
            } else {
                // Try splitting by space, but handle " " as a key?
                // Actually, let's just use chars if it matches the grid size exactly (ignoring spaces?).
                // Let's support space-separated keys if it contains spaces and length > 9.
                keys = row.trim().split("\\s+");
                if (keys.length > 9) {
                     // Fallback to chars if split fails?
                     keys = row.split("");
                }
            }
            
            for (String key : keys) {
                if (slot >= size) break;
                
                if (key.equals(relChar)) {
                    relationSlots.add(slot);
                } else if (icons != null && icons.contains(key)) {
                    ConfigurationSection iconSec = icons.getConfigurationSection(key);
                    if (iconSec != null) {
                         // Check for display section (TrMenu style)
                         if (iconSec.contains("display")) {
                             iconSec = iconSec.getConfigurationSection("display");
                         }
                         
                         ItemStack item = buildItem(iconSec, null, player);
                         
                         // Actions
                         // Check parent section for actions
                         ConfigurationSection parentSec = icons.getConfigurationSection(key);
                         if (parentSec.contains("actions")) {
                             ConfigurationSection actions = parentSec.getConfigurationSection("actions");
                             if (actions != null && actions.contains("all")) {
                                 Object allAction = actions.get("all");
                                 String actionStr = "";
                                 if (allAction instanceof List) {
                                     actionStr = String.join(";", (List<String>) allAction);
                                 } else {
                                     actionStr = allAction.toString();
                                 }
                                 
                                 org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                                 meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "action"), PersistentDataType.STRING, actionStr);
                                 item.setItemMeta(meta);
                             }
                         }
                         
                         staticItems.put(slot, item);
                    }
                } else {
                    // Empty or Unknown
                }
                slot++;
            }
        }
    }

    private ItemStack buildItem(ConfigurationSection section, Map<String, String> placeholders, OfflinePlayer skullOwner) {
        if (section == null) return new ItemStack(Material.AIR);
        String matName = section.getString("material", "STONE");
        
        ItemStack item;
        Integer customModelData = null;
        
        // Parse material{cmd:1000} syntax
        if (matName.contains("{cmd:") && matName.endsWith("}")) {
            try {
                int start = matName.indexOf("{cmd:");
                int end = matName.indexOf("}", start);
                String cmdStr = matName.substring(start + 5, end);
                customModelData = Integer.parseInt(cmdStr);
                matName = matName.substring(0, start).trim();
            } catch (NumberFormatException e) {
                // Ignore invalid format, proceed with original string
            }
        }
        
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.STONE;
        item = new ItemStack(mat);
        
        final Integer finalCmd = customModelData;

        // Name
        String name = section.getString("name", "");
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                name = name.replace(entry.getKey(), entry.getValue());
            }
        }
        name = plugin.getConfigManager().processColorCodes(name);
        String finalName = name;

        // Lore
        List<String> lore = section.getStringList("lore");
        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    line = line.replace(entry.getKey(), entry.getValue());
                }
            }
            line = plugin.getConfigManager().processColorCodes(line);
            loreComponents.add(MiniMessage.miniMessage().deserialize(line));
        }

        item.editMeta(meta -> {
            if (finalName != null && !finalName.isEmpty()) {
                meta.displayName(MiniMessage.miniMessage().deserialize(finalName));
            }
            meta.lore(loreComponents);
            
            // Apply CustomModelData from syntax or config
            if (finalCmd != null) {
                meta.setCustomModelData(finalCmd);
            }
            if (section.contains("custom-model-data")) {
                meta.setCustomModelData(section.getInt("custom-model-data"));
            }
            
            if (meta instanceof SkullMeta && skullOwner != null) {
                ((SkullMeta) meta).setOwningPlayer(skullOwner);
            }
        });
        
        return item;
    }
}

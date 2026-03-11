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

    public void openQuickRelationGui(Player player, Player target) {
        FileConfiguration quickConfig = plugin.getConfigManager().getQuickMenuConfig();
        if (quickConfig == null) {
             player.sendMessage(Component.text("Quick Menu configuration not found!", net.kyori.adventure.text.format.NamedTextColor.RED));
             return;
        }

        String titleRaw = quickConfig.getString("title", "Interact with <target>");
        titleRaw = titleRaw.replace("<target>", target.getName());
        String title = plugin.getConfigManager().processColorCodes(titleRaw);
        int size = quickConfig.getInt("size", 27);
        
        Inventory inv = Bukkit.createInventory(new RelationsHolder(0), size, MiniMessage.miniMessage().deserialize(title));
        
        // 1. Static Mappings (Border)
        ConfigurationSection mappings = quickConfig.getConfigurationSection("mappings");
        if (mappings != null) {
             for (String key : mappings.getKeys(false)) {
                 ConfigurationSection itemSec = mappings.getConfigurationSection(key);
                 if (itemSec == null) continue;
                 ItemStack item = buildItem(itemSec, null, null);
                 
                 List<String> sorts = itemSec.getStringList("sorts");
                 // Support single string/int if not list
                 if (sorts.isEmpty() && itemSec.contains("sorts")) {
                     Object obj = itemSec.get("sorts");
                     if (obj instanceof List) {
                        for (Object o : (List<?>) obj) sorts.add(o.toString());
                     } else if (obj != null) {
                        sorts.add(obj.toString());
                     }
                 }
                 
                 for (String sort : sorts) {
                     if (sort.contains("-")) {
                        String[] range = sort.split("-");
                        if (range.length == 2) {
                            try {
                                int start = Integer.parseInt(range[0].trim());
                                int end = Integer.parseInt(range[1].trim());
                                for (int i = start; i <= end; i++) {
                                    if (i >= 0 && i < size) inv.setItem(i, item);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                     } else {
                         try {
                            int slot = Integer.parseInt(sort.trim());
                            if (slot >= 0 && slot < size) inv.setItem(slot, item);
                         } catch (NumberFormatException ignored) {}
                     }
                 }
             }
        }
        
        // 2. Interaction Items
        ConfigurationSection items = quickConfig.getConfigurationSection("items");
        if (items != null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("<target>", target.getName());
            
            for (String key : items.getKeys(false)) {
                ConfigurationSection itemSec = items.getConfigurationSection(key);
                if (itemSec == null) continue;
                
                ItemStack item = buildItem(itemSec, placeholders, target); 
                
                // Add action
                // Support new 'actions' list
                List<String> actionList = itemSec.getStringList("actions");
                
                if (!actionList.isEmpty()) {
                    String combinedActions = String.join(";", actionList);
                    combinedActions = combinedActions.replace("<target>", target.getName());
                    addPDCAction(item, combinedActions);
                }
                
                int slot = itemSec.getInt("slot", -1);
                if (slot >= 0 && slot < size) {
                    inv.setItem(slot, item);
                }
            }
        }
        
        player.openInventory(inv);
    }

    public void openRelationsGui(Player player, int page) {
        FileConfiguration menuConfig = plugin.getConfigManager().getMenuConfig();
        if (menuConfig == null) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("errors.config-not-found", player)));
            return;
        }

        String title = plugin.getConfigManager().processColorCodes(menuConfig.getString("title", "Relations"));
        title = title.replace("<page>", String.valueOf(page));
        int size = 54;
        
        Inventory inv = Bukkit.createInventory(new RelationsHolder(page), size, MiniMessage.miniMessage().deserialize(title));

        // 1. Build Static Mappings (Border, Buttons, etc.)
        ConfigurationSection mappings = menuConfig.getConfigurationSection("mappings");
        Set<Integer> occupiedSlots = new HashSet<>();
        ItemStack borderFallback = new ItemStack(Material.GRAY_STAINED_GLASS_PANE); // Default
        
        if (mappings != null) {
            // First pass: find border item
            if (mappings.contains("border")) {
                 borderFallback = buildItem(mappings.getConfigurationSection("border"), null, null);
            }

            for (String key : mappings.getKeys(false)) {
                ConfigurationSection itemSec = mappings.getConfigurationSection(key);
                if (itemSec == null) continue;
                
                ItemStack item = buildItem(itemSec, null, null);
                
                // Add actions for known keys
                if (key.equalsIgnoreCase("button_previous")) {
                    addPDCAction(item, "previous_page");
                } else if (key.equalsIgnoreCase("button_next")) {
                    addPDCAction(item, "next_page");
                } else if (key.equalsIgnoreCase("button_close")) {
                    addPDCAction(item, "close");
                }
                
                // Parse Sorts
                List<String> sorts = itemSec.getStringList("sorts");
                // Support single string if not list
                if (sorts.isEmpty() && itemSec.contains("sorts")) {
                    // Try parsing as single string or list of integers directly?
                    // Bukkit handles list of integers as List<?> or List<Integer>
                    List<?> rawList = itemSec.getList("sorts");
                    if (rawList != null) {
                        for (Object obj : rawList) {
                            sorts.add(obj.toString());
                        }
                    } else {
                         sorts.add(itemSec.getString("sorts"));
                    }
                }

                for (String sort : sorts) {
                    if (sort.contains("-")) {
                        String[] range = sort.split("-");
                        if (range.length == 2) {
                            try {
                                int start = Integer.parseInt(range[0].trim());
                                int end = Integer.parseInt(range[1].trim());
                                for (int i = start; i <= end; i++) {
                                    if (i >= 0 && i < size) {
                                        inv.setItem(i, item);
                                        occupiedSlots.add(i);
                                    }
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    } else {
                        try {
                            int slot = Integer.parseInt(sort.trim());
                            if (slot >= 0 && slot < size) {
                                inv.setItem(slot, item);
                                occupiedSlots.add(slot);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        // 2. Prepare Relations Content
        // Identify available rows for layout flow
        // We scan row by row. If a row has ANY available slot, we consider it a candidate row?
        // Or we just find the first available slot in a row and start there?
        // The user wants "Line by Line".
        
        List<List<Integer>> availableRows = new ArrayList<>();
        for (int r = 0; r < 6; r++) {
            List<Integer> rowSlots = new ArrayList<>();
            for (int c = 0; c < 9; c++) {
                int slot = r * 9 + c;
                if (!occupiedSlots.contains(slot)) {
                    rowSlots.add(slot);
                }
            }
            if (!rowSlots.isEmpty()) {
                availableRows.add(rowSlots);
            }
        }
        
        if (availableRows.isEmpty()) {
            player.openInventory(inv);
            return;
        }

        List<String> types = new ArrayList<>(plugin.getConfigManager().getRelationTypes());
        List<Relation> relations = plugin.getRelationManager().getRelations(player.getUniqueId());
        ConfigurationSection relConfig = menuConfig.getConfigurationSection("relations");
        
        // Content Items to be placed
        List<List<ItemStack>> flowLines = new ArrayList<>();
        
        for (String type : types) {
             List<ItemStack> typeItems = new ArrayList<>();
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
                typeItems.add(buildItem(headerSection, placeholders, null));
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

                    typeItems.add(buildItem(memberSection, placeholders, partner));
                }
             }
             
             if (!typeItems.isEmpty()) {
                 flowLines.add(typeItems);
             }
        }
        
        // Paging Logic
        // We map flowLines to availableRows.
        // A flowLine can span multiple rows if it's too long.
        // But a new flowLine MUST start on a new row.
        
        List<Map<Integer, ItemStack>> pages = new ArrayList<>();
        Map<Integer, ItemStack> currentPage = new HashMap<>();
        
        int currentRowIndex = 0; // Index in availableRows
        
        for (List<ItemStack> lineItems : flowLines) {
            // Find next available row (if we are in the middle of a row, move to next)
            // But wait, "currentRowIndex" tracks the current row being filled.
            // If we just finished a line, we increment row index?
            // "One type per line" -> Start new type on new row.
            // So for each type (lineItems), we start at a fresh row.
            
            // Check if we need to advance row (if current row is partially filled? No, we always start fresh for new type)
            // Actually, we need to track if the previous type used the current row.
            // Let's say we are at start of logic: currentRowIndex = 0.
            // Place items.
            // If items overflow row, move to next row.
            // After items done, increment currentRowIndex to start next type on fresh row.
            
            // BUT: if we just finished a page, we reset currentRowIndex to 0 for new page.
            
            if (currentRowIndex >= availableRows.size()) {
                // Page full, push and reset
                pages.add(new HashMap<>(currentPage));
                currentPage.clear();
                currentRowIndex = 0;
            }
            
            List<Integer> currentRowSlots = availableRows.get(currentRowIndex);
            int currentSlotInRow = 0;
            
            for (ItemStack item : lineItems) {
                if (currentSlotInRow >= currentRowSlots.size()) {
                    // Row full, move to next row
                    currentRowIndex++;
                    
                    if (currentRowIndex >= availableRows.size()) {
                         // Page full
                         pages.add(new HashMap<>(currentPage));
                         currentPage.clear();
                         currentRowIndex = 0;
                    }
                    currentRowSlots = availableRows.get(currentRowIndex);
                    // Indentation: Skip first slot when wrapping to next row within same type
                    currentSlotInRow = 1;
                }
                
                if (currentSlotInRow < currentRowSlots.size()) {
                    int slot = currentRowSlots.get(currentSlotInRow++);
                    currentPage.put(slot, item);
                }
            }
            
            // Finished type, force new row for next type
            currentRowIndex++;
        }
        
        if (!currentPage.isEmpty()) {
            pages.add(currentPage);
        }
        
        // Render Page
        int totalPages = pages.size();
        if (page > totalPages) page = totalPages;
        if (page < 1) page = 1;
        
        if (totalPages > 0) {
            Map<Integer, ItemStack> pageContent = pages.get(page - 1);
            for (Map.Entry<Integer, ItemStack> entry : pageContent.entrySet()) {
                inv.setItem(entry.getKey(), entry.getValue());
            }
        }
        
        // Update Buttons Visibility (if they are in static items, we might need to hide/show them)
        // Since we placed them via mappings, they are already there.
        // But we should hide P if page 1, N if page last.
        // We need to find their slots again?
        // Or check occupiedSlots?
        // Better: check config mappings again for buttons and update slots.
        if (mappings != null) {
             if (mappings.contains("button_previous") && page <= 1) {
                 replaceButton(inv, mappings.getConfigurationSection("button_previous"), borderFallback);
             }
             if (mappings.contains("button_next") && page >= totalPages) {
                 replaceButton(inv, mappings.getConfigurationSection("button_next"), borderFallback);
             }
        }

        player.openInventory(inv);
    }
    
    private void replaceButton(Inventory inv, ConfigurationSection sec, ItemStack replacement) {
        if (sec == null) return;
        List<String> sorts = sec.getStringList("sorts");
        if (sorts.isEmpty()) {
             Object obj = sec.get("sorts");
             if (obj != null) sorts.add(obj.toString());
        }
        for (String sort : sorts) {
            try {
                int slot = Integer.parseInt(sort.trim());
                inv.setItem(slot, replacement);
            } catch (NumberFormatException ignored) {}
        }
    }

    
    private void addPDCAction(ItemStack item, String action) {
        if (item == null || item.getType() == Material.AIR) return;
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "action"), PersistentDataType.STRING, action);
        });
    }

    // Removed parseLayout method as it is no longer used


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
        
        // Handle <partner> specifically if not already handled
        if (name.contains("<partner>")) {
             String partnerName = "Unknown";
             if (placeholders != null && placeholders.containsKey("<player>")) {
                 partnerName = placeholders.get("<player>"); // In member context, <player> IS the partner
             }
             name = name.replace("<partner>", partnerName);
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
            
            // Handle <partner> specifically if not already handled
            if (line.contains("<partner>")) {
                 String partnerName = "Unknown";
                 if (placeholders != null && placeholders.containsKey("<player>")) {
                     partnerName = placeholders.get("<player>");
                 }
                 line = line.replace("<partner>", partnerName);
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

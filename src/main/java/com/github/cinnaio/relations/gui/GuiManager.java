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

import java.util.*;

public class GuiManager {

    private final Relations plugin;

    public GuiManager(Relations plugin) {
        this.plugin = plugin;
    }

    public void openRelationsGui(Player player) {
        openRelationsGui(player, 1);
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

        // Load valid slots for relations
        List<Integer> relationSlots = new ArrayList<>();
        ConfigurationSection relConfig = menuConfig.getConfigurationSection("relations");
        if (relConfig != null) {
            List<Integer> configSlots = relConfig.getIntegerList("slots");
            if (!configSlots.isEmpty()) {
                relationSlots.addAll(configSlots);
            } else {
                // Fallback to start/end logic if slots not provided
                int start = relConfig.getInt("start-slot", 0);
                int end = relConfig.getInt("end-slot", size - 1);
                for (int i = start; i <= end; i++) {
                    relationSlots.add(i);
                }
            }
        }
        
        boolean lineBreak = relConfig != null && relConfig.getBoolean("line-break", true);
        int itemsPerPage = relationSlots.size();

        // 1. Prepare items to render (Virtual List)
        List<ItemStack> virtualItems = new ArrayList<>();
        List<String> types = plugin.getConfigManager().getRelationTypes().stream().toList();
        List<Relation> relations = plugin.getRelationManager().getRelations(player.getUniqueId());

        // We need to simulate placement to handle line breaks properly
        // Actually, if we use a virtual list, line-break effectively inserts empty items until next row?
        // Let's build a Map<Integer, ItemStack> where key is the index in relationSlots.
        
        Map<Integer, ItemStack> pageItems = new HashMap<>();
        int globalIndex = 0; // Index in relationSlots (across pages)

        for (String type : types) {
            // Stats
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
                
                // Check for type-specific override
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
                // Check override (optional)
                if (relConfig.contains("types." + type + ".member-item")) {
                     memberSection = relConfig.getConfigurationSection("types." + type + ".member-item");
                }

                for (Relation r : typeRelations) {
                    UUID partnerId = r.getPartner(player.getUniqueId());
                    OfflinePlayer partner = Bukkit.getOfflinePlayer(partnerId);
                    
                    Map<String, String> placeholders = new HashMap<>();
                    String rDisplay = plugin.getConfigManager().getRelationDisplay(r.getType());
                    placeholders.put("<display>", rDisplay);
                    placeholders.put("<player>", partner.getName() != null ? partner.getName() : "Unknown");
                    placeholders.put("<affinity>", String.valueOf(r.getAffinity()));
                    placeholders.put("<date>", r.getCreatedAt().toString());

                    ItemStack head = buildItem(memberSection, placeholders, partner);
                    pageItems.put(globalIndex++, head);
                }
            }

            // Line Break logic
            if (lineBreak && !relationSlots.isEmpty()) {
                // Current physical slot index in relationSlots
                // The item just placed was at globalIndex - 1.
                // We want next item to be at a slot that is on a new row compared to (globalIndex - 1)
                
                if (globalIndex > 0) {
                     int prevVirtualIndex = (globalIndex - 1) % itemsPerPage;
                     int prevPhysicalSlot = relationSlots.get(prevVirtualIndex);
                     int prevRow = prevPhysicalSlot / 9;
                     
                     // Look ahead to find next slot that is on a different row
                     // We need to advance globalIndex until relationSlots.get(globalIndex % itemsPerPage) / 9 > prevRow
                     // OR if we wrap to next page
                     
                     int checkIndex = globalIndex;
                     boolean found = false;
                     // Limit lookahead to avoid infinite loop (e.g. 100 slots)
                     for (int i = 0; i < itemsPerPage; i++) {
                         int virtualIndex = checkIndex % itemsPerPage;
                         int physicalSlot = relationSlots.get(virtualIndex);
                         int row = physicalSlot / 9;
                         
                         // Check if we wrapped page
                         int pageOfCheck = checkIndex / itemsPerPage;
                         int pageOfPrev = (globalIndex - 1) / itemsPerPage;
                         
                         if (pageOfCheck > pageOfPrev) {
                             // Wrapped to next page, effectively a line break (new page starts at top)
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
                     if (!found) {
                         // If we couldn't find a new row on this page, maybe just push to next page?
                         // globalIndex is already incremented, so it will naturally fill.
                     }
                }
            }
        }

        // 2. Determine Max Pages
        int totalItems = globalIndex;
        int totalPages = (totalItems > 0) ? (int) Math.ceil((double) totalItems / itemsPerPage) : 1;
        if (page > totalPages) page = totalPages;
        if (page < 1) page = 1;

        // 3. Render Static Items (Border & Buttons)
        ConfigurationSection itemsSection = menuConfig.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemSec = itemsSection.getConfigurationSection(key);
                if (itemSec == null) continue;
                
                // Logic for Prev/Next buttons
                if (key.equalsIgnoreCase("previous-page") && page <= 1) continue;
                if (key.equalsIgnoreCase("next-page") && page >= totalPages) continue;

                ItemStack item = buildItem(itemSec, null, null);
                
                // Add PDC action if present
                if (itemSec.contains("action")) {
                    String action = itemSec.getString("action");
                    org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                    meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "action"), PersistentDataType.STRING, action);
                    item.setItemMeta(meta);
                }

                List<Integer> slots = itemSec.getIntegerList("slots");
                for (int slot : slots) {
                    if (slot >= 0 && slot < size) {
                        inv.setItem(slot, item);
                    }
                }
            }
        }

        // 4. Fill Relations for Current Page
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems); // exclusive
        
        for (int i = startIndex; i < endIndex; i++) {
            ItemStack item = pageItems.get(i); // might be null if we skipped indices for line break?
            // Actually pageItems map might be sparse if we skipped globalIndex.
            // But my loop above incremented globalIndex linearly or skipped.
            // If I skipped, there is no entry in map.
            // But `i` iterates all integers.
            // Wait, if I skipped `globalIndex` from 5 to 8. `pageItems.get(6)` is null.
            // That's correct, we don't render anything there.
            // But we need to map `i` to physical slot.
            
            if (item != null) {
                int virtualIndex = i % itemsPerPage;
                int physicalSlot = relationSlots.get(virtualIndex);
                inv.setItem(physicalSlot, item);
            }
        }

        player.openInventory(inv);
    }

    private ItemStack buildItem(ConfigurationSection section, Map<String, String> placeholders, OfflinePlayer skullOwner) {
        if (section == null) return new ItemStack(Material.AIR);
        String matName = section.getString("material", "STONE");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.STONE;

        ItemStack item = new ItemStack(mat);

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

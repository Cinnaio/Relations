package com.github.cinnaio.relations.papi;

import com.github.cinnaio.relations.Relations;
import com.github.cinnaio.relations.model.Relation;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.UUID;

public class RelationsExpansion extends PlaceholderExpansion {

    private final Relations plugin;

    public RelationsExpansion(Relations plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "relations";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Cinnaio";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        // %relations_partner_<type>%
        if (params.startsWith("partner_")) {
            String type = params.substring("partner_".length());
            Relation r = getFirstRelation(player.getUniqueId(), type);
            if (r != null) {
                return Bukkit.getOfflinePlayer(r.getPartner(player.getUniqueId())).getName();
            }
            return "None";
        }
        
        // %relations_has_partner_<type>%
        if (params.startsWith("has_partner_")) {
            String type = params.substring("has_partner_".length());
            Relation r = getFirstRelation(player.getUniqueId(), type);
            return r != null ? "true" : "false";
        }
        
        // %relations_affinity_<type>%
        if (params.startsWith("affinity_")) {
            String type = params.substring("affinity_".length());
             Relation r = getFirstRelation(player.getUniqueId(), type);
            if (r != null) {
                return String.valueOf(r.getAffinity());
            }
            return "0";
        }
        
        // %relations_level_<type>%
        if (params.startsWith("level_")) {
            // Check if it is "level_display_..."
            boolean isDisplay = params.startsWith("level_display_");
            String type;
            if (isDisplay) {
                type = params.substring("level_display_".length());
            } else {
                type = params.substring("level_".length());
            }
            
            Relation r = getFirstRelation(player.getUniqueId(), type);
            if (r != null) {
                if (isDisplay) {
                    return plugin.getLevelManager().getLevelDisplay(r.getAffinity());
                } else {
                    return String.valueOf(plugin.getLevelManager().getLevel(r.getAffinity()));
                }
            }
            return isDisplay ? "Lv.0" : "0";
        }

        // %relations_marriage_date%
        if (params.equals("marriage_date")) {
             Relation r = plugin.getRelationManager().getMarriage(player.getUniqueId());
             if (r != null) {
                 SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                 return sdf.format(r.getCreatedAt());
             }
             return "N/A";
        }
        
        // %relations_gender%
        if (params.equals("gender")) {
            return plugin.getPlayerManager().getGender(player.getUniqueId());
        }
        
        // %relations_top_name_<type>_<rank>%
        if (params.startsWith("top_name_")) {
            // format: top_name_marriage_1
            try {
                String[] parts = params.split("_");
                // parts[0] = top, [1] = name, [2] = type, [3] = rank
                if (parts.length >= 4) {
                    String type = parts[2];
                    int rank = Integer.parseInt(parts[3]);
                    return getTopInfo(type, rank, "name");
                }
            } catch (Exception ignored) {}
        }
        
        // %relations_top_affinity_<type>_<rank>%
        if (params.startsWith("top_affinity_")) {
            try {
                String[] parts = params.split("_");
                if (parts.length >= 4) {
                    String type = parts[2];
                    int rank = Integer.parseInt(parts[3]);
                    return getTopInfo(type, rank, "affinity");
                }
            } catch (Exception ignored) {}
        }

        return null;
    }
    
    private String getTopInfo(String type, int rank, String infoType) {
        try {
            // Ideally we cache this, but for now fetch async logic is tricky in PAPI (should be sync or cached)
            // PAPI onRequest is usually sync. DB calls here will lag server!
            // We MUST cache the top list in RelationManager or similar.
            // For this task, I'll fetch but warn, or implement a simple cache in RelationManager?
            // Let's implement a cached method in RelationManager for top lists.
            List<Relation> top = plugin.getRelationManager().getCachedTopRelations(type);
            if (top == null || top.size() < rank) return "---";
            
            Relation r = top.get(rank - 1);
            if (infoType.equals("name")) {
                String p1 = Bukkit.getOfflinePlayer(r.getPlayer1()).getName();
                String p2 = Bukkit.getOfflinePlayer(r.getPlayer2()).getName();
                return (p1 != null ? p1 : "?") + " & " + (p2 != null ? p2 : "?");
            } else if (infoType.equals("affinity")) {
                return String.valueOf(r.getAffinity());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Error";
    }
    
    private Relation getFirstRelation(UUID player, String type) {
        List<Relation> rels = plugin.getRelationManager().getRelations(player);
        for (Relation r : rels) {
            if (r.getType().equalsIgnoreCase(type)) return r;
        }
        return null;
    }
}

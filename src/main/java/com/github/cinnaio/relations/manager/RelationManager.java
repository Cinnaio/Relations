package com.github.cinnaio.relations.manager;

import com.github.cinnaio.relations.Relations;
import com.github.cinnaio.relations.database.RelationDAO;
import com.github.cinnaio.relations.model.Relation;
import com.github.cinnaio.relations.util.SchedulerUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RelationManager {

    private final Relations plugin;
    private final RelationDAO relationDAO;
    private final Map<UUID, List<Relation>> relationCache = new ConcurrentHashMap<>();
    // Receiver -> Sender -> Type
    private final Map<UUID, Map<UUID, String>> pendingRequests = new ConcurrentHashMap<>();

    private final Map<String, List<Relation>> topCache = new ConcurrentHashMap<>();
    private final Map<String, Long> topCacheTime = new ConcurrentHashMap<>();

    public RelationManager(Relations plugin, RelationDAO relationDAO) {
        this.plugin = plugin;
        this.relationDAO = relationDAO;
    }
    
    public List<Relation> getCachedTopRelations(String type) {
        long now = System.currentTimeMillis();
        if (!topCache.containsKey(type) || now - topCacheTime.getOrDefault(type, 0L) > 60000) { // 1 min cache
            // Refresh async but return old/empty for now to avoid lag
            SchedulerUtils.runAsync(plugin, () -> {
                try {
                    List<Relation> top = relationDAO.getTopRelations(type, 10);
                    topCache.put(type, top);
                    topCacheTime.put(type, System.currentTimeMillis());
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        }
        return topCache.getOrDefault(type, new ArrayList<>());
    }

    public void loadRelations(UUID player) {
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                List<Relation> relations = relationDAO.getRelations(player);
                relationCache.put(player, relations);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void unloadRelations(UUID player) {
        relationCache.remove(player);
    }

    public List<Relation> getRelations(UUID player) {
        return relationCache.getOrDefault(player, new ArrayList<>());
    }
    
    public List<Relation> getGlobalMarriages() throws SQLException {
        return relationDAO.getRelationsByType("marriage");
    }

    public List<Relation> getTopRelations(String type, int limit) throws SQLException {
        return relationDAO.getTopRelations(type, limit);
    }

    public void createRelationForce(UUID p1, UUID p2, String type, int affinity) {
        Relation relation = new Relation(p1, p2, type, affinity, 0, new Timestamp(System.currentTimeMillis()), new Timestamp(System.currentTimeMillis()));
        try {
            relationDAO.createRelation(relation);
            updateCache(p1);
            updateCache(p2);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void sendRequest(Player sender, Player target, String type) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
             sender.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("relation.cannot-relate-self")));
             return;
        }
        if (areRelated(sender.getUniqueId(), target.getUniqueId())) {
             sender.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("relation.already-related")));
             return;
        }
        
        // Check limits
        int senderMax = getMaxRelations(sender, type);
        int senderCurrent = countRelations(sender.getUniqueId(), type);
        if (senderCurrent >= senderMax) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("relation.limit-reached")));
            return;
        }
        
        // Check target limits (optional, but good UX to fail early)
        int targetMax = getMaxRelations(target, type);
        int targetCurrent = countRelations(target.getUniqueId(), type);
        if (targetCurrent >= targetMax) {
             sender.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("target-limit-reached")));
             return;
        }
        
        pendingRequests.computeIfAbsent(target.getUniqueId(), k -> new HashMap<>()).put(sender.getUniqueId(), type);
        
        String reqSent = plugin.getConfigManager().getMessage("relation.request-sent")
                .replace("<target>", target.getName());
        sender.sendMessage(MiniMessage.miniMessage().deserialize(reqSent));

        String reqRec = plugin.getConfigManager().getMessage("relation.request-received")
                .replace("<player>", sender.getName())
                .replace("<relation>", plugin.getConfigManager().getRelationDisplay(type));
        target.sendMessage(MiniMessage.miniMessage().deserialize(reqRec));
    }

    public void acceptRequest(Player receiver, Player sender) {
        Map<UUID, String> requests = pendingRequests.get(receiver.getUniqueId());
        if (requests == null || !requests.containsKey(sender.getUniqueId())) {
            return;
        }
        String type = requests.remove(sender.getUniqueId());
        
        // Re-check limits before accepting
        int receiverMax = getMaxRelations(receiver, type);
        int receiverCurrent = countRelations(receiver.getUniqueId(), type);
        if (receiverCurrent >= receiverMax) {
            receiver.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("relation.limit-reached")));
            return;
        }
        
        int senderMax = getMaxRelations(sender, type);
        int senderCurrent = countRelations(sender.getUniqueId(), type);
        if (senderCurrent >= senderMax) {
             receiver.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("sender-limit-reached")));
             return;
        }

        UUID p1 = receiver.getUniqueId();
        UUID p2 = sender.getUniqueId();
        if (p1.compareTo(p2) > 0) {
            UUID temp = p1; p1 = p2; p2 = temp;
        }

        Relation relation = new Relation(p1, p2, type, 0, Timestamp.from(Instant.now()));
        
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                relationDAO.createRelation(relation);
                updateCache(receiver.getUniqueId());
                updateCache(sender.getUniqueId());
                
                String display = plugin.getConfigManager().getRelationDisplay(type);
                String msg = plugin.getConfigManager().getMessage("relation.request-accepted")
                        .replace("<relation>", display)
                        .replace("<target>", sender.getName());
                receiver.sendMessage(MiniMessage.miniMessage().deserialize(msg));
                
                String msg2 = plugin.getConfigManager().getMessage("relation.request-accepted")
                        .replace("<relation>", display)
                        .replace("<target>", receiver.getName());
                sender.sendMessage(MiniMessage.miniMessage().deserialize(msg2));
                
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
    
    public int getMaxRelations(Player player, String type) {
        if (plugin.getConfigManager().isMarriage(type)) {
            return 1;
        }
        
        // Permission check: relations.limit.<type>.<number>
        String prefix = "relations.limit." + type.toLowerCase() + ".";
        int max = 1; // Default to 1 as per user request
        
        // Also check config default if no permission? 
        // User said "others default are one", but I can keep config max as base if I want.
        // Let's use config max as base, but default that to 1 in config.yml.
        max = plugin.getConfigManager().getRelationMax(type);
        
        for (org.bukkit.permissions.PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            String perm = pai.getPermission();
            if (perm.startsWith(prefix)) {
                try {
                    int val = Integer.parseInt(perm.substring(prefix.length()));
                    if (val > max) max = val;
                } catch (NumberFormatException ignored) {}
            }
        }
        return max;
    }
    
    public int countRelations(UUID player, String type) {
        List<Relation> rels = getRelations(player);
        int count = 0;
        for (Relation r : rels) {
            if (r.getType().equalsIgnoreCase(type)) {
                count++;
            }
        }
        return count;
    }
    
    public void denyRequest(Player receiver, Player sender) {
        Map<UUID, String> requests = pendingRequests.get(receiver.getUniqueId());
        if (requests != null) {
            requests.remove(sender.getUniqueId());
        }
        
        receiver.sendMessage(MiniMessage.miniMessage().deserialize(
                plugin.getConfigManager().getMessage("relation.request-denied").replace("<target>", sender.getName())
        ));
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
                plugin.getConfigManager().getMessage("relation.request-denied-target").replace("<target>", receiver.getName())
        ));
    }

    public void removeRelation(UUID p1, UUID p2) {
        List<Relation> rels = getRelations(p1);
        Relation targetRel = null;
        for (Relation r : rels) {
            if (r.getPartner(p1).equals(p2)) {
                targetRel = r;
                break;
            }
        }
        
        if (targetRel != null) {
            Relation finalTargetRel = targetRel;
            SchedulerUtils.runAsync(plugin, () -> {
                try {
                    relationDAO.deleteRelation(finalTargetRel.getPlayer1(), finalTargetRel.getPlayer2(), finalTargetRel.getType());
                    updateCache(p1);
                    updateCache(p2);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        }
    }
    
    public int setAffinity(UUID p1, UUID p2, String type, int amount) {
        return setAffinity(p1, p2, type, amount, false);
    }

    public int setAffinity(UUID p1, UUID p2, String type, int amount, boolean bypassLimit) {
         List<Relation> rels = getRelations(p1);
        Relation targetRel = null;
        for (Relation r : rels) {
            if (r.getPartner(p1).equals(p2) && r.getType().equalsIgnoreCase(type)) {
                targetRel = r;
                break;
            }
        }
        
        if (targetRel != null) {
            Relation finalTargetRel = targetRel;
            int diff = amount - finalTargetRel.getAffinity();
            
            // Check daily limit if increasing
            if (diff > 0 && !bypassLimit) {
                 // Check if reset needed
                 checkDailyReset(finalTargetRel);
                 
                 // Get limit of the initiator (p1). 
                 // Note: 'p1' in setAffinity call from Listener is the item user.
                 // In RelationListener: relationManager.setAffinity(player.getUniqueId(), target.getUniqueId(), ...)
                 // So p1 is the player using the item.
                 int limit = getDailyAffinityLimit(p1, type);
                 
                 // Debug info
                 if (plugin.getConfigManager().isDebug()) {
                     Player player1 = Bukkit.getPlayer(p1);
                     int used = finalTargetRel.getDailyAffinityGain();
                     String debugMsg = "<color:#999999>[Debug] Daily Limit Info: " +
                             "<color:#FFCC00>Initiator Limit: " + limit + "</color> " +
                             "<color:#FFCC00>Used (Before): " + used + "</color> " +
                             "<color:#FFCC00>Remaining: " + (limit - used) + "</color> " +
                             "<color:#FFCC00>Attempting to add: " + diff + "</color></color>";
                     if (player1 != null) player1.sendMessage(MiniMessage.miniMessage().deserialize(debugMsg));
                 }
                 
                 if (finalTargetRel.getDailyAffinityGain() + diff > limit) {
                     int allowed = limit - finalTargetRel.getDailyAffinityGain();
                     if (allowed <= 0) {
                         // Limit reached
                         amount = finalTargetRel.getAffinity(); // Reset amount to current (no increase)
                         diff = 0; // No gain
                     } else {
                         // allowed > 0 but diff > allowed
                         amount = finalTargetRel.getAffinity() + allowed;
                         diff = allowed;
                     }
                     
                     finalTargetRel.addDailyAffinityGain(diff);
                 } else {
                     finalTargetRel.addDailyAffinityGain(diff);
                 }
                 // Save daily gain
                 final Relation relToSave = finalTargetRel;
                 SchedulerUtils.runAsync(plugin, () -> {
                    try {
                        relationDAO.updateDailyAffinity(relToSave.getPlayer1(), relToSave.getPlayer2(), relToSave.getType(), relToSave.getDailyAffinityGain(), relToSave.getLastAffinityReset());
                        // Important: After DB update, refresh cache if needed, OR we trust the in-memory object is updated?
                        // In-memory `finalTargetRel` IS updated by `addDailyAffinityGain`.
                        // However, `updateCache` fetches from DB.
                        // If we call updateCache immediately inside async task, it might read stale data if transaction not committed?
                        // But here we just updated it.
                        // Actually, since `finalTargetRel` is a reference to the object inside `relationCache`, 
                        // the cache IS already updated in memory!
                        // We only need to ensure thread safety if multiple threads access it.
                        // But wait, `updateCache` REPLACES the list in the map with a NEW list from DB.
                        // If we don't call updateCache, the cache has the modified object.
                        // If we DO call updateCache, we get fresh data.
                        
                        // The issue user reported: "Used 0 -> Used 0" suggests the cache was NOT updated or reverted?
                        // Ah! `updateCache` calls `relationDAO.getRelations(player)`.
                        // `getRelations` reads from DB.
                        // If `updateDailyAffinity` hasn't committed or is slow, `getRelations` might read old data (0).
                        // And then `updateCache` overwrites our in-memory `finalTargetRel` (which had 5) with the old one (0)!
                        
                        // FIX: Do NOT call updateCache here blindly. 
                        // The in-memory object `finalTargetRel` is already up-to-date.
                        // We should only sync to DB.
                        // OR, we must ensure updateCache happens AFTER DB write is confirmed.
                        // It is in the same thread, so it should be fine IF JDBC is auto-commit.
                        
                        // But wait, look at `setAffinity` bottom part:
                        /*
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            relationDAO.updateAffinity(...)
                            updateCache(p1);
                            updateCache(p2);
                        });
                        */
                        // This runs in PARALLEL or SEQUENTIAL to the daily-gain update task above?
                        // They are both `runTaskAsynchronously`. They might run in parallel threads!
                        // If the second task runs and calls `updateCache`, it might fetch the record BEFORE the first task wrote the daily gain.
                        // Result: Daily gain is overwritten with 0 (from DB).
                        
                        // SOLUTION: Combine them into ONE async task.
                        
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                 });
            }
            
            finalTargetRel.setAffinity(amount);
            int finalAmount = amount;
            
            // COMBINED UPDATE TASK
            final Relation relToSave = finalTargetRel;
            SchedulerUtils.runAsync(plugin, () -> {
                try {
                    // 1. Update Daily Gain (if changed)
                    relationDAO.updateDailyAffinity(relToSave.getPlayer1(), relToSave.getPlayer2(), relToSave.getType(), relToSave.getDailyAffinityGain(), relToSave.getLastAffinityReset());
                    
                    // 2. Update Total Affinity
                    relationDAO.updateAffinity(relToSave.getPlayer1(), relToSave.getPlayer2(), relToSave.getType(), finalAmount);
                    
                    // 3. Refresh Cache (now DB has both updates)
                    updateCache(p1);
                    updateCache(p2);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
            return finalAmount;
        }
        return -1; // Not found
    }
    
    private void checkDailyReset(Relation r) {
        long now = System.currentTimeMillis();
        long last = r.getLastAffinityReset().getTime();
        // Simple day check (24h or calendar day?)
        // Usually calendar day.
        Calendar current = Calendar.getInstance();
        Calendar saved = Calendar.getInstance();
        saved.setTimeInMillis(last);
        
        if (current.get(Calendar.DAY_OF_YEAR) != saved.get(Calendar.DAY_OF_YEAR) || current.get(Calendar.YEAR) != saved.get(Calendar.YEAR)) {
            r.setDailyAffinityGain(0);
            r.setLastAffinityReset(new Timestamp(now));
            // Async update DB
             SchedulerUtils.runAsync(plugin, () -> {
                try {
                    relationDAO.updateDailyAffinity(r.getPlayer1(), r.getPlayer2(), r.getType(), 0, r.getLastAffinityReset());
                } catch (SQLException e) {
                    e.printStackTrace();
                }
             });
        }
    }
    
    public int getDailyAffinityLimit(UUID uuid, String type) {
        // 1. First check permission (Highest priority)
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            String prefix = "relations.affinity_limit." + type.toLowerCase() + ".";
            int permMax = -1; // -1 indicates no permission found
            
            for (org.bukkit.permissions.PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
                String perm = pai.getPermission();
                if (perm.startsWith(prefix)) {
                    try {
                        int val = Integer.parseInt(perm.substring(prefix.length()));
                        // We want the HIGHEST permission value the player has.
                        if (val > permMax) {
                            permMax = val;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            
            // If permission found, return it immediately
            if (permMax != -1) {
                return permMax;
            }
        }
        
        // 2. If no permission, use config default
        return plugin.getConfigManager().getRelationDailyLimit(type);
    }
    
    public void setHome(Player player) {
        Relation marriage = getMarriage(player.getUniqueId());
        if (marriage == null) return;
        
        org.bukkit.Location loc = player.getLocation();
        marriage.setHome(loc);
        
        SchedulerUtils.runAsync(plugin, () -> {
            try {
                relationDAO.updateHome(marriage);
                SchedulerUtils.runTask(plugin, player, () -> {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("marriage.home-set")));
                });
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
    
    public void teleportHome(Player player) {
        Relation marriage = getMarriage(player.getUniqueId());
        if (marriage == null || !marriage.hasHome()) {
             String msg = plugin.getConfigManager().getMessage("marriage.no-home-set");
             if (msg == null || msg.isEmpty()) msg = "<color:#FF6B6B>你还没有设置婚姻家园！请使用 /rel marry sethome 设置。</color>";
             player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
             return;
        }
        player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("marriage.home-teleport")));
        SchedulerUtils.teleport(player, marriage.getHome());
    }
    
    public Relation getMarriage(UUID player) {
        for (Relation r : getRelations(player)) {
            if (plugin.getConfigManager().isMarriage(r.getType())) {
                return r;
            }
        }
        return null;
    }
    
    public boolean areRelated(UUID p1, UUID p2) {
        for (Relation r : getRelations(p1)) {
            if (r.getPartner(p1).equals(p2)) return true;
        }
        return false;
    }

    private void updateCache(UUID player) {
        if (Bukkit.getPlayer(player) != null) {
            try {
                List<Relation> relations = relationDAO.getRelations(player);
                relationCache.put(player, relations);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}

package com.github.cinnaio.relations.listener;

import java.util.List;
import java.util.ArrayList;
import com.github.cinnaio.relations.Relations;
import com.github.cinnaio.relations.manager.AffinityItemManager;
import com.github.cinnaio.relations.manager.RelationManager;
import com.github.cinnaio.relations.model.Relation;
import com.github.cinnaio.relations.gui.RelationsHolder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class RelationListener implements Listener {

    private final Relations plugin;
    private final RelationManager relationManager;

    public RelationListener(Relations plugin) {
        this.plugin = plugin;
        this.relationManager = plugin.getRelationManager();
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player)) return;

        Player player = event.getPlayer();
        Player target = (Player) event.getRightClicked();
        
        // 1. Quick Relation GUI Trigger
        org.bukkit.configuration.file.FileConfiguration quickConfig = plugin.getConfigManager().getQuickMenuConfig();
        if (quickConfig != null && quickConfig.getBoolean("trigger.enable", true)) {
             boolean needsSneak = quickConfig.getBoolean("trigger.sneak", true);
             if (!needsSneak || player.isSneaking()) {
                 plugin.getGuiManager().openQuickRelationGui(player, target);
                 return;
             }
        }

        // 2. Affinity Items Logic
        ItemStack item = player.getInventory().getItemInMainHand();
        AffinityItemManager itemManager = plugin.getAffinityItemManager();
        String type = itemManager.getAffinityType(item);
        
        if (type != null) {
            java.util.List<Relation> rels = relationManager.getRelations(player.getUniqueId());
            Relation r = rels.stream().filter(rel -> rel.getPartner(player.getUniqueId()).equals(target.getUniqueId()) && rel.getType().equalsIgnoreCase(type)).findFirst().orElse(null);

            if (r == null) {
                boolean anyRelation = rels.stream().anyMatch(rel -> rel.getPartner(player.getUniqueId()).equals(target.getUniqueId()));
                if (anyRelation) {
                    String theirType = rels.stream()
                            .filter(rel -> rel.getPartner(player.getUniqueId()).equals(target.getUniqueId()))
                            .map(Relation::getType)
                            .findFirst().orElse("?");
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("relation.item-type-mismatch", player)
                            .replace("<type>", type)
                            .replace("<actual>", theirType)));
                } else {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-relation-found", player)));
                }
                return;
            }

            int amount = itemManager.getAffinityAmount(item);
            int oldAff = r.getAffinity();
            
            int newAffinity = relationManager.setAffinity(player.getUniqueId(), target.getUniqueId(), type, oldAff + amount);
            int actualGain = newAffinity - oldAff;

            if (actualGain <= 0) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("relation.daily-limit-reached", player)));
                return;
            }

            item.setAmount(item.getAmount() - 1);
            player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("relation.affinity-item-used", player)
                    .replace("<target>", target.getName())
                    .replace("<amount>", String.valueOf(actualGain))));
            target.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("relation.affinity-item-received", target)
                    .replace("<player>", player.getName())
                    .replace("<amount>", String.valueOf(actualGain))));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        relationManager.loadRelations(event.getPlayer().getUniqueId());
        plugin.getPlayerManager().loadData(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        relationManager.unloadRelations(event.getPlayer().getUniqueId());
        plugin.getPlayerManager().unloadData(event.getPlayer().getUniqueId());
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof RelationsHolder) {
            event.setCancelled(true);
            
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();
            
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getItemMeta() == null) return;
            
            String action = clicked.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "action"), PersistentDataType.STRING);
            if (action == null) return;
            
            RelationsHolder holder = (RelationsHolder) event.getInventory().getHolder();
            int currentPage = holder.getPage();
            
            // Handle multiple actions (separated by ;)
            String[] actions = action.split(";");
            List<String> formattedActions = new ArrayList<>();
            
            // Convert old style actions to new format for ActionManager
            for (String act : actions) {
                act = act.trim();
                if (act.isEmpty()) continue;
                
                if (act.equalsIgnoreCase("next_page")) {
                    plugin.getGuiManager().openRelationsGui(player, currentPage + 1);
                } else if (act.equalsIgnoreCase("previous_page")) {
                    plugin.getGuiManager().openRelationsGui(player, currentPage - 1);
                } else if (act.startsWith("[")) {
                     // Direct ActionManager format
                     formattedActions.add(act);
                }
            }
            
            if (!formattedActions.isEmpty()) {
                plugin.getActionManager().executeActions(formattedActions, player, null);
            }
        }
    }

    @EventHandler
    public void onKiss(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player)) return;
        if (!plugin.getConfigManager().isMarriageFeatureEnabled("kiss")) return;
        
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        
        Player target = (Player) event.getRightClicked();
        
        Relation marriage = relationManager.getMarriage(player.getUniqueId());
        if (marriage != null && marriage.getPartner(player.getUniqueId()).equals(target.getUniqueId())) {
            player.getWorld().spawnParticle(Particle.HEART, player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.5)), 5);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
            
            player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("marriage.kiss", player).replace("<target>", target.getName())));
            target.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("marriage.kissed", target).replace("<target>", player.getName())));
        }
    }
}

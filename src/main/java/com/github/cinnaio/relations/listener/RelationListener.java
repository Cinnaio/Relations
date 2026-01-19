package com.github.cinnaio.relations.listener;

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
    public void onAffinityItemUse(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player)) return;

        Player player = event.getPlayer();
        Player target = (Player) event.getRightClicked();
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
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("relation.item-type-mismatch")
                            .replace("<type>", type)
                            .replace("<actual>", theirType)));
                } else {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-relation-found")));
                }
                return;
            }

            int amount = itemManager.getAffinityAmount(item);
            int oldAff = r.getAffinity();
            
            int newAffinity = relationManager.setAffinity(player.getUniqueId(), target.getUniqueId(), type, oldAff + amount);
            int actualGain = newAffinity - oldAff;

            if (actualGain <= 0) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("relation.daily-limit-reached")));
                return;
            }

            item.setAmount(item.getAmount() - 1);
            player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("relation.affinity-item-used")
                    .replace("<target>", target.getName())
                    .replace("<amount>", String.valueOf(actualGain))));
            target.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("relation.affinity-item-received")
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
            for (String act : actions) {
                act = act.trim();
                if (act.isEmpty()) continue;
                
                if (act.equalsIgnoreCase("next_page")) {
                    plugin.getGuiManager().openRelationsGui(player, currentPage + 1);
                } else if (act.equalsIgnoreCase("previous_page")) {
                    plugin.getGuiManager().openRelationsGui(player, currentPage - 1);
                } else if (act.equalsIgnoreCase("close")) {
                    player.closeInventory();
                } else if (act.toLowerCase().startsWith("sound:")) {
                    String[] parts = act.split(":");
                    if (parts.length > 1) {
                        // Format: sound: NAME-volume-pitch
                        String soundData = parts[1].trim();
                        String[] soundParts = soundData.split("-");
                        String soundName = soundParts[0];
                        float vol = 1f;
                        float pitch = 1f;
                        if (soundParts.length > 1) vol = Float.parseFloat(soundParts[1]);
                        if (soundParts.length > 2) pitch = Float.parseFloat(soundParts[2]);
                        
                        try {
                            Sound s = Sound.valueOf(soundName.toUpperCase());
                            player.playSound(player.getLocation(), s, vol, pitch);
                        } catch (IllegalArgumentException e) {
                            // Invalid sound
                        }
                    }
                } else if (act.toLowerCase().startsWith("console:")) {
                    String cmd = act.substring("console:".length()).trim();
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("<player>", player.getName()));
                } else if (act.toLowerCase().startsWith("player:")) {
                    String cmd = act.substring("player:".length()).trim();
                    player.performCommand(cmd);
                }
            }
        }
    }

    @EventHandler
    public void onKiss(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player)) return;
        
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        
        Player target = (Player) event.getRightClicked();
        
        Relation marriage = relationManager.getMarriage(player.getUniqueId());
        if (marriage != null && marriage.getPartner(player.getUniqueId()).equals(target.getUniqueId())) {
            player.getWorld().spawnParticle(Particle.HEART, player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.5)), 5);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
            
            player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("marriage.kiss").replace("<target>", target.getName())));
            target.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("marriage.kissed").replace("<target>", player.getName())));
        }
    }
}

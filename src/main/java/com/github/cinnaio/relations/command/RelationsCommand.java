package com.github.cinnaio.relations.command;

import com.github.cinnaio.relations.Relations;
import com.github.cinnaio.relations.manager.RelationManager;
import com.github.cinnaio.relations.model.Relation;
import com.github.cinnaio.relations.util.SchedulerUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class RelationsCommand implements CommandExecutor, TabCompleter {

    private final Relations plugin;
    private final RelationManager relationManager;

    public RelationsCommand(Relations plugin) {
        this.plugin = plugin;
        this.relationManager = plugin.getRelationManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            if (!sender.hasPermission("relations.admin")) {
                sender.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-permission")));
                return true;
            }
            handleAdmin(sender, args);
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("player-only")));
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            // Show help
            showHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "gui":
                plugin.getGuiManager().openRelationsGui(player);
                break;
            case "list":
                if (!player.hasPermission("relations.command.list")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-permission")));
                    return true;
                }
                showList(player);
                break;
            case "request":
                if (!player.hasPermission("relations.command.request")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-permission")));
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("usage.request")));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("player-not-found")));
                    return true;
                }
                String type = args[2].toLowerCase();
                if (!plugin.getConfigManager().getRelationTypes().contains(type)) {
                     player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("invalid-relation-type")));
                     return true;
                }
                relationManager.sendRequest(player, target, type);
                break;
            case "accept":
                if (!player.hasPermission("relations.command.accept")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-permission")));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("usage.accept")));
                    return true;
                }
                Player accTarget = Bukkit.getPlayer(args[1]);
                if (accTarget == null) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("player-not-found")));
                    return true;
                }
                relationManager.acceptRequest(player, accTarget);
                break;
            case "deny":
                if (!player.hasPermission("relations.command.deny")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-permission")));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("usage.deny")));
                    return true;
                }
                Player denyTarget = Bukkit.getPlayer(args[1]);
                if (denyTarget == null) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("player-not-found")));
                    return true;
                }
                relationManager.denyRequest(player, denyTarget);
                break;
            case "remove":
                if (!player.hasPermission("relations.command.remove")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-permission")));
                    return true;
                }
                 if (args.length < 2) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("usage.remove")));
                    return true;
                }
                // Need to find offline player if needed, but for now online
                Player remTarget = Bukkit.getPlayer(args[1]);
                if (remTarget == null) {
                     // Try offline? UUID fetch? Simplified for now
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("player-not-found")));
                    return true;
                }
                relationManager.removeRelation(player.getUniqueId(), remTarget.getUniqueId());
                player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("relation.removed").replace("<target>", remTarget.getName())));
                break;
            case "gender":
                if (!player.hasPermission("relations.command.gender")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-permission")));
                    return true;
                }
                handleGender(player, args);
                break;
            case "top":
                if (!player.hasPermission("relations.command.top")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-permission")));
                    return true;
                }
                handleTop(player, args);
                break;
            case "marry":
                if (!player.hasPermission("relations.command.marry")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-permission")));
                    return true;
                }
                handleMarry(player, args);
                break;
            case "save":
                if (!player.hasPermission("relations.admin.save")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-permission")));
                    return true;
                }
                handleSave(player, args);
                break;
            case "reload":
                if (!player.hasPermission("relations.admin")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-permission")));
                    return true;
                }
                plugin.getGuiManager().closeAllMenus();
                plugin.getConfigManager().reload();
                plugin.getAffinityItemManager().loadItems();
                player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("reload-success")));
                break;
            case "admin":
                 // Permission check handled in top-level check for admin arg
                 // But for safety if accessed differently (not possible with current structure but good practice)
                 if (!player.hasPermission("relations.admin")) {
                     player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-permission")));
                     return true;
                 }
                 handleAdmin(player, args);
                 break;
            case "debug":
                if (!player.hasPermission("relations.admin")) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-permission")));
                    return true;
                }
                handleDebug(player, args);
                break;
            default:
                player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("unknown-command")));
        }

        return true;
    }

    private void handleSave(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("usage.save")));
            return;
        }
        String type = args[1].toLowerCase();
        if (!plugin.getConfigManager().getRelationTypes().contains(type)) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("invalid-relation-type")));
            return;
        }
        
        int amount = 10;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("invalid-amount")));
                return;
            }
        }
        
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("must-hold-item")));
            return;
        }
        
        plugin.getAffinityItemManager().saveItem(type, amount, item);
        player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("item-saved")
                .replace("<type>", type)
                .replace("<amount>", String.valueOf(amount))));
    }

    private void handleGender(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("usage.gender")));
            return;
        }
        String genderInput = args[1].toLowerCase();
        String gender = "OTHER";
        if (genderInput.equals("male")) gender = "MALE";
        else if (genderInput.equals("female")) gender = "FEMALE";
        else if (genderInput.equals("other")) gender = "OTHER";
        else {
             player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("invalid-gender")));
             return;
        }
        
        plugin.getPlayerManager().setGender(player.getUniqueId(), gender);
        String msg = plugin.getConfigManager().getMessage("gender-set").replace("<gender>", gender);
        player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
    }

    private void showHelp(Player player) {
        player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("help.header")));
        player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("help.gui")));
        player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("help.gender")));
        player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("help.top")));
        player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("help.request")));
        player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("help.accept")));
        player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("help.deny")));
        player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("help.remove")));
        player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("help.marry")));
        if (player.hasPermission("relations.admin.save")) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("help.save")));
        }
    }

    private void showList(Player player) {
        List<Relation> relations = relationManager.getRelations(player.getUniqueId());
        player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("list-header")));
        for (Relation r : relations) {
            UUID partnerId = r.getPartner(player.getUniqueId());
            String partnerName = Bukkit.getOfflinePlayer(partnerId).getName();
            String display = plugin.getConfigManager().getRelationDisplay(r.getType());
            player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("relation.list-entry")
                    .replace("<display>", display)
                    .replace("<partner>", partnerName != null ? partnerName : "Unknown")
                    .replace("<affinity>", String.valueOf(r.getAffinity()))));
        }
    }

    private void handleTop(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("usage.top")));
            return;
        }
        String type = args[1].toLowerCase();
        if (!plugin.getConfigManager().getRelationTypes().contains(type)) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("invalid-relation-type")));
            return;
        }

        SchedulerUtils.runAsync(plugin, () -> {
            try {
                List<Relation> topRelations = plugin.getRelationManager().getTopRelations(type, 10);
                if (topRelations.isEmpty()) {
                    SchedulerUtils.runTask(plugin, player, () -> {
                        player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-relations-found")));
                    });
                    return;
                }
                
                SchedulerUtils.runTask(plugin, player, () -> {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("top-header").replace("<type>", plugin.getConfigManager().getRelationDisplay(type))));
                    int rank = 1;
                    for (Relation r : topRelations) {
                        String p1Name = Bukkit.getOfflinePlayer(r.getPlayer1()).getName();
                        String p2Name = Bukkit.getOfflinePlayer(r.getPlayer2()).getName();
                        if (p1Name == null) p1Name = "Unknown";
                        if (p2Name == null) p2Name = "Unknown";
    
                        player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("top-entry")
                                .replace("<rank>", String.valueOf(rank))
                                .replace("<p1>", p1Name)
                                .replace("<p2>", p2Name)
                                .replace("<affinity>", String.valueOf(r.getAffinity()))));
                        rank++;
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                SchedulerUtils.runTask(plugin, player, () -> {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("error-fetching-list")));
                });
            }
        });
    }

    private void handleMarry(Player player, String[] args) {
        if (args.length < 2) {
             player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("usage.marry")));
             return;
        }
        Relation marriage = relationManager.getMarriage(player.getUniqueId());
        if (marriage == null && !args[1].equalsIgnoreCase("list")) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("not-married")));
            return;
        }
        
        String sub = args[1].toLowerCase();
        switch (sub) {
            case "sethome":
                relationManager.setHome(player);
                break;
            case "home":
                relationManager.teleportHome(player);
                break;
            case "gift":
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item == null || item.getType() == Material.AIR) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("must-hold-item")));
                    return;
                }
                UUID partnerId = marriage.getPartner(player.getUniqueId());
                Player partner = Bukkit.getPlayer(partnerId);
                if (partner == null) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("partner-not-online")));
                    return;
                }
                if (partner.getInventory().firstEmpty() == -1) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("partner-inventory-full")));
                    return;
                }
                partner.getInventory().addItem(item.clone());
                player.getInventory().setItemInMainHand(null);
                player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("marriage.gift-sent").replace("<target>", partner.getName())));
                partner.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("marriage.gift-received").replace("<target>", player.getName())));
                break;
            case "tp":
                UUID pid = marriage.getPartner(player.getUniqueId());
                Player p = Bukkit.getPlayer(pid);
                if (p == null) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("partner-not-online")));
                    return;
                }
                player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("marriage.teleporting")));
                SchedulerUtils.teleport(player, p.getLocation()).thenAccept(success -> {
                    if (!success) {
                         player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("teleport-failed") != null ? plugin.getConfigManager().getMessage("teleport-failed") : "<red>Teleportation failed!"));
                    }
                });
                break;
            case "list":
                SchedulerUtils.runAsync(plugin, () -> {
                   try {
                       List<Relation> marriages = plugin.getRelationManager().getGlobalMarriages();
                       if (marriages.isEmpty()) {
                           player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-marriages-found")));
                           return;
                       }
                       player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("marriage-list-header")));
                       for (Relation r : marriages) {
                           String p1Name = Bukkit.getOfflinePlayer(r.getPlayer1()).getName();
                           String p2Name = Bukkit.getOfflinePlayer(r.getPlayer2()).getName();
                           if (p1Name == null) p1Name = "Unknown";
                           if (p2Name == null) p2Name = "Unknown";
                           
                           player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("marriage-entry")
                                   .replace("<p1>", p1Name)
                                   .replace("<p2>", p2Name)
                                   .replace("<date>", r.getCreatedAt().toString())));
                       }
                   } catch (Exception e) {
                       e.printStackTrace();
                       player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("error-fetching-list")));
                   }
                });
                break;
        }
    }
    
    private void handleAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) return;
        String sub = args[1].toLowerCase();
        
        if (sub.equals("reload")) {
            // Already handled by top-level reload, but kept for legacy admin sub-command compatibility
            plugin.getGuiManager().closeAllMenus();
            plugin.getConfigManager().reload();
            plugin.getAffinityItemManager().loadItems();
            sender.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("reload-success")));
        } else if (sub.equals("affinity")) {
             // affinity <add/set/remove> <p1> [p2] <type> <amt>
             if (args.length < 6) {
                 sender.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("usage.admin-affinity")));
                 return;
             }
             String action = args[2];
             Player p1 = Bukkit.getPlayer(args[3]);
             
             if (p1 == null) {
                 sender.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("player-not-found")));
                 return;
             }
             
             Player p2 = null;
             String type = null;
             int amount = 0;
             
             // Check if arg 4 is a relation type (implicit p2)
             // Or if arg 4 is p2
             
             // Strategy: If args.length == 6, assume p2 is implicit.
             // args: admin, affinity, action, p1, type, amt
             // 0, 1, 2, 3, 4, 5
             
             if (args.length == 6) {
                 type = args[4];
                 try {
                     amount = Integer.parseInt(args[5]);
                 } catch (NumberFormatException e) {
                     sender.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("invalid-amount")));
                     return;
                 }
                 
                 // Find implicit p2
                 List<Relation> rels = relationManager.getRelations(p1.getUniqueId());
                 String finalType = type;
                 List<Relation> matches = rels.stream().filter(r -> r.getType().equalsIgnoreCase(finalType)).toList();
                 
                 if (matches.isEmpty()) {
                     sender.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-relation-found")));
                     return;
                 } else if (matches.size() > 1) {
                     sender.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("multiple-relations").replace("<type>", type)));
                     return;
                 } else {
                     UUID partnerId = matches.get(0).getPartner(p1.getUniqueId());
                     p2 = Bukkit.getPlayer(partnerId);
                 }
                 
                 // We have p2 UUID effectively from relation.
                 UUID p2Id = matches.get(0).getPartner(p1.getUniqueId());
                 String p2Name = Bukkit.getOfflinePlayer(p2Id).getName();
                 
                 // Apply update
                 updateAffinity(sender, action, p1.getUniqueId(), p2Id, type, amount, p2Name);
                 return;
                 
             } else if (args.length >= 7) {
                 // Explicit p2
                 p2 = Bukkit.getPlayer(args[4]);
                 if (p2 == null) {
                      sender.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("player-not-found")));
                      return;
                 }
                 type = args[5];
                 try {
                     amount = Integer.parseInt(args[6]);
                 } catch (NumberFormatException e) {
                     sender.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("invalid-amount")));
                     return;
                 }
                 
                 updateAffinity(sender, action, p1.getUniqueId(), p2.getUniqueId(), type, amount, p2.getName());
                 return;
             }
        }
    }
    
    private void updateAffinity(CommandSender admin, String action, UUID p1, UUID p2, String type, int amount, String p2Name) {
         List<Relation> rels = relationManager.getRelations(p1);
         Relation r = rels.stream().filter(rel -> rel.getPartner(p1).equals(p2) && rel.getType().equalsIgnoreCase(type)).findFirst().orElse(null);
         
         if (r == null) {
             admin.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("no-relation-found")));
             return;
         }
         
         int newAff = r.getAffinity();
         if (action.equalsIgnoreCase("set")) newAff = amount;
         else if (action.equalsIgnoreCase("add")) newAff += amount;
         else if (action.equalsIgnoreCase("remove")) newAff -= amount;
         
         // Admin bypasses limit, so we pass true for bypassLimit
         int actualAffinity = relationManager.setAffinity(p1, p2, type, newAff, true);
         admin.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().getMessage("relation.affinity-update")
                 .replace("<target>", p2Name != null ? p2Name : "Unknown")
                 .replace("<amount>", String.valueOf(actualAffinity))));
    }

    private void handleDebug(Player player, String[] args) {
        if (args.length < 2) {
             player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Usage: /relations debug addfake <name> <type> [affinity]"));
             return;
        }
        String sub = args[1].toLowerCase();
        if (sub.equals("addfake")) {
            if (args.length < 4) {
                player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Usage: /relations debug addfake <name> <type> [affinity]"));
                return;
            }
            String name = args[2];
            String type = args[3];
            int affinity = 0;
            if (args.length >= 5) {
                try {
                    affinity = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Invalid affinity number."));
                    return;
                }
            }
            
            UUID fakeUUID = UUID.randomUUID();
            com.github.cinnaio.relations.gui.GuiManager.FAKE_NAMES.put(fakeUUID, name);
            relationManager.createRelationForce(player.getUniqueId(), fakeUUID, type, affinity);
            player.sendMessage(MiniMessage.miniMessage().deserialize("<green>Added fake relation with " + name + " (" + type + ")"));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("gui");
            completions.add("list");
            completions.add("request");
            completions.add("accept");
            completions.add("deny");
            completions.add("remove");
            completions.add("gender");
            completions.add("top");
            completions.add("marry");
            if (sender.hasPermission("relations.admin.save")) {
                completions.add("save");
            }
            if (sender.hasPermission("relations.admin")) {
                completions.add("admin");
                completions.add("reload");
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("gender")) {
                completions.add("male");
                completions.add("female");
                completions.add("other");
            }
            if (args[0].equalsIgnoreCase("top")) {
                completions.addAll(plugin.getConfigManager().getRelationTypes());
            }
            if (args[0].equalsIgnoreCase("save")) {
                if (sender.hasPermission("relations.admin.save")) {
                    completions.addAll(plugin.getConfigManager().getRelationTypes());
                }
            }
            if (args[0].equalsIgnoreCase("request") || args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("deny") || args[0].equalsIgnoreCase("remove")) {
                return null; // Player list
            }
            if (args[0].equalsIgnoreCase("marry")) {
                completions.add("sethome");
                completions.add("home");
                completions.add("gift");
                completions.add("tp");
                completions.add("list");
            }
            if (args[0].equalsIgnoreCase("admin")) {
                completions.add("reload");
                completions.add("affinity");
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("request")) {
                completions.addAll(plugin.getConfigManager().getRelationTypes());
            }
            if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("affinity")) {
                completions.add("set");
                completions.add("add");
                completions.add("remove");
            }
        } else if (args.length == 4) {
            // admin affinity set <p1>
             if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("affinity")) {
                 return null; // Player list for p1
             }
        } else if (args.length == 5) {
             // admin affinity set p1 [p2/type]
             if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("affinity")) {
                 // Suggest players (p2) AND types (implicit p2)
                 completions.addAll(plugin.getConfigManager().getRelationTypes());
                 return null; // This might override relation types if we return null? 
                 // Actually if we return null, it shows players.
                 // We want BOTH players and relation types.
                 // So we need to fetch players manually and add types.
                 // Bukkit.getOnlinePlayers()...
                 // But return null is client side player list usually.
                 // To mix, we must provide list.
                 // For performance, maybe just types? User asked "can default not specify p2".
                 // So if they type a relation type here, it matches logic.
                 // But they might also want to type p2.
             }
        } else if (args.length == 6) {
             // admin affinity set p1 p2 type (if explicit)
             // OR admin affinity set p1 type amount (if implicit) -> arg 6 is amount, no completion?
             // If arg 4 was type, then arg 5 (length 6) is amount.
             // If arg 4 was player, then arg 5 (length 6) is type.
             
             if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("affinity")) {
                 // Check if arg 4 is a valid relation type
                 String potentialType = args[4];
                 if (plugin.getConfigManager().getRelationTypes().contains(potentialType)) {
                     // Arg 4 is type, so arg 5 is amount. No completion.
                 } else {
                     // Arg 4 was likely a player, so arg 5 is type.
                     completions.addAll(plugin.getConfigManager().getRelationTypes());
                 }
             }
        }
        
        // Manual player fetching for mixed completion at arg 5 (length 5)
        if (args.length == 5 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("affinity")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }
            completions.addAll(plugin.getConfigManager().getRelationTypes());
        }
        
        return completions;
    }
}

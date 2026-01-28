package com.github.cinnaio.relations.manager;

import com.github.cinnaio.relations.Relations;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActionManager {

    private final Relations plugin;
    private static final Pattern ACTION_PATTERN = Pattern.compile("^\\[(op|player|console|message|broadcast|title|potion)]\\s*(.*)$", Pattern.CASE_INSENSITIVE);

    public ActionManager(Relations plugin) {
        this.plugin = plugin;
    }

    public void executeActions(Player player, List<String> actions, Map<String, String> placeholders) {
        if (player == null || actions == null || actions.isEmpty()) return;

        for (String actionLine : actions) {
            // Replace placeholders first
            String finalLine = actionLine;
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    finalLine = finalLine.replace(entry.getKey(), entry.getValue());
                }
            }

            Matcher matcher = ACTION_PATTERN.matcher(finalLine);
            if (matcher.find()) {
                String type = matcher.group(1).toLowerCase();
                String content = matcher.group(2).trim();
                executeSingleAction(player, type, content);
            }
        }
    }

    private void executeSingleAction(Player player, String type, String content) {
        switch (type) {
            case "op":
                boolean isOp = player.isOp();
                try {
                    player.setOp(true);
                    player.performCommand(content);
                } finally {
                    player.setOp(isOp);
                }
                break;
            case "player":
                player.performCommand(content);
                break;
            case "console":
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), content);
                break;
            case "message":
                player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().processColorCodes(content)));
                break;
            case "broadcast":
                Bukkit.broadcast(MiniMessage.miniMessage().deserialize(plugin.getConfigManager().processColorCodes(content)));
                break;
            case "title":
                handleTitle(player, content);
                break;
            case "potion":
                handlePotion(player, content);
                break;
        }
    }

    private void handleTitle(Player player, String content) {
        // Format: title;subtitle;fadeIn;stay;fadeOut
        String[] parts = content.split(";");
        String titleText = parts.length > 0 ? parts[0] : "";
        String subtitleText = parts.length > 1 ? parts[1] : "";
        
        long fadeIn = 10;
        long stay = 70;
        long fadeOut = 20;
        
        try {
            if (parts.length > 2) fadeIn = Long.parseLong(parts[2]);
            if (parts.length > 3) stay = Long.parseLong(parts[3]);
            if (parts.length > 4) fadeOut = Long.parseLong(parts[4]);
        } catch (NumberFormatException ignored) {}

        Title title = Title.title(
                MiniMessage.miniMessage().deserialize(plugin.getConfigManager().processColorCodes(titleText)),
                MiniMessage.miniMessage().deserialize(plugin.getConfigManager().processColorCodes(subtitleText)),
                Title.Times.times(Duration.ofMillis(fadeIn * 50), Duration.ofMillis(stay * 50), Duration.ofMillis(fadeOut * 50))
        );
        player.showTitle(title);
    }

    private void handlePotion(Player player, String content) {
        // Format: type;duration(seconds);amplifier
        String[] parts = content.split(";");
        if (parts.length < 1) return;

        PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase()); // Deprecated but widely used, or use Registry
        // Try NamespacedKey if null
        if (type == null) {
            type = PotionEffectType.getByKey(NamespacedKey.minecraft(parts[0].toLowerCase()));
        }
        
        if (type == null) return;

        int duration = 200; // 10s
        int amplifier = 0;

        try {
            if (parts.length > 1) duration = Integer.parseInt(parts[1]) * 20;
            if (parts.length > 2) amplifier = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignored) {}

        player.addPotionEffect(new PotionEffect(type, duration, amplifier));
    }
}

package com.github.cinnaio.relations.config;

import com.github.cinnaio.relations.Relations;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigManager {

    private final Relations plugin;
    private FileConfiguration config;
    private FileConfiguration menuConfig;
    private FileConfiguration quickMenuConfig;
    private java.io.File menuFile;
    private java.io.File quickMenuFile;
    
    // Multi-language support
    private final java.util.Map<String, FileConfiguration> langConfigs = new java.util.HashMap<>();
    private String defaultLang;

    private static final Pattern HEX_PATTERN_AMP = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern HEX_PATTERN_BRACE = Pattern.compile("\\{#([0-9a-fA-F]{6})\\}");
    private static final Pattern LEGACY_PATTERN = Pattern.compile("&([0-9a-fA-Fk-oK-OrR])");

    public ConfigManager(Relations plugin) {
        this.plugin = plugin;
        this.plugin.saveDefaultConfig();
        this.config = this.plugin.getConfig();
        
        loadMenuConfig();
        loadQuickMenuConfig();
        loadLangConfigs();
    }
    
    private void loadLangConfigs() {
        this.langConfigs.clear();
        this.defaultLang = config.getString("settings.default-lang", "zh_cn");
        
        java.io.File langFolder = new java.io.File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
            // Save default lang resource if folder created
            plugin.saveResource("lang/zh_cn.yml", false);
        }
        
        java.io.File[] files = langFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (java.io.File file : files) {
                String langName = file.getName().replace(".yml", "").toLowerCase();
                this.langConfigs.put(langName, org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file));
                plugin.getLogger().info("Loaded language: " + langName);
            }
        }
        
        if (langConfigs.isEmpty()) {
            plugin.getLogger().warning("No language files found! Creating default zh_cn.yml");
            plugin.saveResource("lang/zh_cn.yml", false);
            loadLangConfigs(); // Reload
        }
    }

    private void loadMenuConfig() {
        this.menuFile = new java.io.File(plugin.getDataFolder(), "menu.yml");
        if (!menuFile.exists()) {
            plugin.saveResource("menu.yml", false);
        }
        this.menuConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(menuFile);
    }

    private void loadQuickMenuConfig() {
        this.quickMenuFile = new java.io.File(plugin.getDataFolder(), "quick_menu.yml");
        if (!quickMenuFile.exists()) {
            plugin.saveResource("quick_menu.yml", false);
        }
        this.quickMenuConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(quickMenuFile);
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        loadMenuConfig();
        loadQuickMenuConfig();
        loadLangConfigs();
        if (plugin.getLevelManager() != null) {
            plugin.getLevelManager().loadLevels();
        }
    }
    
    public FileConfiguration getMenuConfig() {
        return menuConfig;
    }

    public FileConfiguration getQuickMenuConfig() {
        return quickMenuConfig;
    }

    public String getDatabaseType() {
        return config.getString("database.type", "SQLITE");
    }

    public String getDatabaseHost() {
        return config.getString("database.host", "localhost");
    }

    public int getDatabasePort() {
        return config.getInt("database.port", 3306);
    }

    public String getDatabaseName() {
        return config.getString("database.database", "relations");
    }

    public String getDatabaseUser() {
        return config.getString("database.username", "root");
    }

    public String getDatabasePassword() {
        return config.getString("database.password", "");
    }

    public boolean useSSL() {
        return config.getBoolean("database.ssl", false);
    }
    
    public int getPoolSize() {
        return config.getInt("database.pool-size", 10);
    }

    public Set<String> getRelationTypes() {
        ConfigurationSection section = config.getConfigurationSection("relations");
        if (section == null) return Collections.emptySet();
        return section.getKeys(false);
    }

    public String getRelationDisplay(String type) {
        return processColorCodes(config.getString("relations." + type + ".display", type));
    }

    public int getRelationMax(String type) {
        return config.getInt("relations." + type + ".max", 1);
    }
    
    public int getRelationDailyLimit(String type) {
        return config.getInt("relations." + type + ".daily-affinity-limit", 1000);
    }

    public boolean isMarriage(String type) {
        return type.equalsIgnoreCase("marriage"); 
        // Or check features/config, but simplified for now based on prompt.
        // Actually, let's check config features if I want to be generic later.
        // But prompt says "Marriage module must support...", implying it's a specific type.
    }
    
    public boolean isFeatureEnabled(String relationType, String feature) {
        ConfigurationSection sec = config.getConfigurationSection("relations." + relationType + ".features");
        if (sec == null) return true;
        return sec.getBoolean(feature, true);
    }
    
    public boolean isMarriageFeatureEnabled(String feature) {
        return isFeatureEnabled("marriage", feature);
    }

    public boolean isDebug() {
        return config.getBoolean("debug", false);
    }

    public String getMessage(String path) {
        return getMessage(path, null);
    }
    
    public String getMessage(String path, org.bukkit.entity.Player player) {
        String lang = defaultLang;
        if (player != null) {
            try {
                lang = player.getLocale().toLowerCase();
            } catch (Exception e) {}
        }
        
        FileConfiguration langConfig = langConfigs.get(lang);
        if (langConfig == null && lang.contains("_")) {
             langConfig = langConfigs.get(lang.split("_")[0]);
        }
        
        if (langConfig == null) {
            langConfig = langConfigs.get(defaultLang);
        }
        
        // If still null (default lang file missing), try any loaded lang or return error
        if (langConfig == null && !langConfigs.isEmpty()) {
            langConfig = langConfigs.values().iterator().next();
        }
        
        if (langConfig == null) {
            return "Missing language file for " + path;
        }

        String msg = langConfig.getString(path);
        if (msg == null) {
             // Fallback to default lang
             FileConfiguration defConfig = langConfigs.get(defaultLang);
             if (defConfig != null) {
                 msg = defConfig.getString(path);
             }
        }
        
        if (msg == null) return "Missing message: " + path;
        
        String prefix = langConfig.getString("prefix", "");
        return processColorCodes(prefix + msg);
    }
    
    public String getPrefix() {
        // Deprecated usage, prefix is now auto-prepended in getMessage
        return ""; 
    }
    
    public String processColorCodes(String message) {
        if (message == null) return null;

        // Handle Hex &#RRGGBB
        Matcher matcher = HEX_PATTERN_AMP.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<color:#" + matcher.group(1) + ">");
        }
        matcher.appendTail(sb);
        message = sb.toString();

        // Handle Hex {#RRGGBB}
        matcher = HEX_PATTERN_BRACE.matcher(message);
        sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<color:#" + matcher.group(1) + ">");
        }
        matcher.appendTail(sb);
        message = sb.toString();

        // Handle Legacy &c
        matcher = LEGACY_PATTERN.matcher(message);
        sb = new StringBuffer();
        while (matcher.find()) {
            String code = matcher.group(1).toLowerCase();
            String replacement;
            switch (code) {
                case "0": replacement = "<black>"; break;
                case "1": replacement = "<dark_blue>"; break;
                case "2": replacement = "<dark_green>"; break;
                case "3": replacement = "<dark_aqua>"; break;
                case "4": replacement = "<dark_red>"; break;
                case "5": replacement = "<dark_purple>"; break;
                case "6": replacement = "<gold>"; break;
                case "7": replacement = "<gray>"; break;
                case "8": replacement = "<dark_gray>"; break;
                case "9": replacement = "<blue>"; break;
                case "a": replacement = "<green>"; break;
                case "b": replacement = "<aqua>"; break;
                case "c": replacement = "<red>"; break;
                case "d": replacement = "<light_purple>"; break;
                case "e": replacement = "<yellow>"; break;
                case "f": replacement = "<white>"; break;
                case "k": replacement = "<obfuscated>"; break;
                case "l": replacement = "<bold>"; break;
                case "m": replacement = "<strikethrough>"; break;
                case "n": replacement = "<underlined>"; break;
                case "o": replacement = "<italic>"; break;
                case "r": replacement = "<reset>"; break;
                default: replacement = "&" + code; break;
            }
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        
        return sb.toString();
    }
}

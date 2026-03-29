package net.tylers1066.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class PluginConfig {
    private final String denyPlaceMessage;
    private final String denyBreakMessage;
    private final String denyInteractMessage;
    private final boolean debug;
    private final long messageCooldownMs;

    // Farm settings
    private final int globalGrowInterval;
    private final int minGrowInterval;
    private final boolean replantOnlyMature;
    private final boolean suppressDropsOnReplant;

    // Block regen settings
    private final int blockRegenDefaultDelay;

    // Mob spawn settings
    private final int mobSpawnInterval;
    private final int mobSpawnMax;
    private final int mobSpawnCount;
    private final int mobDefaultLevelMin;
    private final int mobDefaultLevelMax;
    private final int mobSpawnAttempts;

    public PluginConfig(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        this.denyPlaceMessage = config.getString("messages.deny-place",
                "&cYou are not allowed to place &e{block} &chere.");
        this.denyBreakMessage = config.getString("messages.deny-break",
                "&cYou are not allowed to break &e{block} &chere.");
        this.denyInteractMessage = config.getString("messages.deny-interact",
                "&cYou are not allowed to interact with &e{block} &chere.");
        this.debug = config.getBoolean("debug", false);
        this.messageCooldownMs = config.getLong("message-cooldown", 2) * 1000L;

        this.blockRegenDefaultDelay = config.getInt("block-regen.default-delay", 1200);

        this.globalGrowInterval = config.getInt("farm.grow-interval", 400);
        this.minGrowInterval = config.getInt("farm.min-grow-interval", 20);
        this.replantOnlyMature = config.getBoolean("farm.replant-only-mature", true);
        this.suppressDropsOnReplant = config.getBoolean("farm.suppress-drops-on-replant", false);

        this.mobSpawnInterval = config.getInt("mob-spawn.spawn-interval", 400);
        this.mobSpawnMax = config.getInt("mob-spawn.max-mobs", 5);
        this.mobSpawnCount = config.getInt("mob-spawn.spawn-count", 1);
        this.mobDefaultLevelMin = config.getInt("mob-spawn.default-level-min", 1);
        this.mobDefaultLevelMax = config.getInt("mob-spawn.default-level-max", 1);
        this.mobSpawnAttempts = config.getInt("mob-spawn.spawn-attempts", 20);
    }

    public Component getDenyPlaceMessage(Material block) {
        return formatMessage(denyPlaceMessage, block);
    }

    public Component getDenyBreakMessage(Material block) {
        return formatMessage(denyBreakMessage, block);
    }

    public Component getDenyInteractMessage(Material block) {
        return formatMessage(denyInteractMessage, block);
    }

    public boolean isDebug() {
        return debug;
    }

    public long getMessageCooldownMs() {
        return messageCooldownMs;
    }

    public int getGlobalGrowInterval() {
        return globalGrowInterval;
    }

    public int getMinGrowInterval() {
        return minGrowInterval;
    }

    public boolean isReplantOnlyMature() {
        return replantOnlyMature;
    }

    public boolean isSuppressDropsOnReplant() {
        return suppressDropsOnReplant;
    }

    public int getBlockRegenDefaultDelay() {
        return blockRegenDefaultDelay;
    }

    public int getMobSpawnInterval() {
        return mobSpawnInterval;
    }

    public int getMobSpawnMax() {
        return mobSpawnMax;
    }

    public int getMobSpawnCount() {
        return mobSpawnCount;
    }

    public int getMobDefaultLevelMin() {
        return mobDefaultLevelMin;
    }

    public int getMobDefaultLevelMax() {
        return mobDefaultLevelMax;
    }

    public int getMobSpawnAttempts() {
        return mobSpawnAttempts;
    }

    private Component formatMessage(String message, Material block) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        String formatted = message
                .replace("{block}", formatMaterialName(block));
        return LegacyComponentSerializer.legacyAmpersand().deserialize(formatted);
    }

    private String formatMaterialName(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }
}

package net.tylers1066.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class PluginConfig {
    private final boolean debugFarm;
    private final boolean debugMob;
    private final boolean debugBlocks;
    private final long messageCooldownMs;

    // Logging settings
    private final boolean logModuleLifecycle;
    private final boolean logSchedulerEvents;
    private final boolean logSpawnWarnings;

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
        this.debugFarm   = config.getBoolean("debug.farm",   false);
        this.debugMob    = config.getBoolean("debug.mob",    false);
        this.debugBlocks = config.getBoolean("debug.blocks", false);
        this.messageCooldownMs = config.getLong("message-cooldown", 2) * 1000L;

        this.logModuleLifecycle = config.getBoolean("logging.module-lifecycle", false);
        this.logSchedulerEvents = config.getBoolean("logging.scheduler-events", false);
        this.logSpawnWarnings = config.getBoolean("logging.spawn-warnings", false);

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

    /** Returns true if debug logging is enabled for farm zones (crop growth, chunk scanning). */
    public boolean isDebugFarm() {
        return debugFarm;
    }

    /** Returns true if debug logging is enabled for mob spawn zones. */
    public boolean isDebugMob() {
        return debugMob;
    }

    /** Returns true if debug logging is enabled for block flag checks (place/break/interact). */
    public boolean isDebugBlocks() {
        return debugBlocks;
    }

    /** Returns true if ANY debug category is enabled (convenience for /wgbf info). */
    public boolean isAnyDebugEnabled() {
        return debugFarm || debugMob || debugBlocks;
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

    public boolean isLogModuleLifecycle() {
        return logModuleLifecycle;
    }

    public boolean isLogSchedulerEvents() {
        return logSchedulerEvents;
    }

    public boolean isLogSpawnWarnings() {
        return logSpawnWarnings;
    }

    public static String formatMaterialName(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }
}

package net.tylers1066.mob;

import net.tylers1066.WGBlockFlags;
import net.tylers1066.mob.mythic.MythicAdapter;
import net.tylers1066.mob.mythic.MythicNoopAdapter;
import net.tylers1066.mob.mythic.MythicV5Adapter;
import org.bukkit.Bukkit;

/**
 * Coordinates the mob-spawn sub-system.
 *
 * <p>Owned by {@link WGBlockFlags} and exposed via {@code getMobModule()}.
 * The module detects MythicMobs at enable time and disables itself gracefully
 * if the plugin is absent or if an unsupported version is detected.
 */
public class MobModule {

    private final WGBlockFlags plugin;
    private MythicAdapter adapter;
    private MobRegionCache cache;
    private MobSpawnManager manager;
    /** Guard against registering MobDropListener more than once. */
    private boolean dropListenerRegistered = false;
    /** Guard against registering MobSpawnFilterListener more than once. */
    private boolean filterListenerRegistered = false;
    private boolean deathListenerRegistered = false;
    private boolean damageListenerRegistered = false;

    public MobModule(WGBlockFlags plugin) {
        this.plugin = plugin;
    }

    /**
     * Detects MythicMobs, builds the region cache, and starts the scheduler.
     * Must be called from {@link WGBlockFlags#onEnable()}.
     */
    public void enable() {
        // Drop rate listener works for all mobs regardless of MythicMobs presence.
        // Guard prevents double-registration when reload() falls back to enable().
        if (!dropListenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(new MobDropListener(), plugin);
            dropListenerRegistered = true;
        }

        // Build (or rebuild) the shared cache. We keep the same instance across
        // enable()/reload() calls so the already-registered filter listener continues
        // to reference valid data after a reload.
        if (cache == null) {
            cache = new MobRegionCache();
        }
        cache.rebuild(plugin.getPluginConfig());

        // Spawn filter listener works independently of MythicMobs presence.
        // Guard prevents double-registration when reload() falls back to enable().
        if (!filterListenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(new MobSpawnFilterListener(cache), plugin);
            filterListenerRegistered = true;
        }

        adapter = resolveMythicAdapter();
        if (!adapter.isAvailable()) {
            // Warning already logged inside resolveMythicAdapter()
            return;
        }
        manager = new MobSpawnManager(plugin, cache, adapter);
        manager.start();
        if (!deathListenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(
                    new MobZoneDeathListener(this), plugin);
            deathListenerRegistered = true;
        }
        if (!damageListenerRegistered) {
            plugin.getServer().getPluginManager().registerEvents(
                    new MobDamageListener(this), plugin);
            damageListenerRegistered = true;
        }
        plugin.getLogger().info("[MobSpawn] Mob spawn module enabled.");
    }

    /**
     * Stops the scheduler.
     * Must be called from {@link WGBlockFlags#onDisable()}.
     */
    public void disable() {
        if (manager != null) {
            manager.stop();
            manager = null;
        }
    }

    /**
     * Rebuilds the cache and restarts the scheduler with updated configuration.
     * Called by {@link WGBlockFlags#reloadPluginConfig()}.
     */
    public void reload() {
        if (adapter == null || !adapter.isAvailable()) {
            // Retry detection in case MythicMobs was loaded after the first enable
            enable();
            return;
        }
        if (manager != null) {
            manager.stop();
        }
        cache.rebuild(plugin.getPluginConfig());
        manager = new MobSpawnManager(plugin, cache, adapter);
        manager.start();
    }

    public WGBlockFlags getPlugin() { return plugin; }

    public MobRegionCache getCache() {
        return cache;
    }

    public MobSpawnManager getManager() {
        return manager;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Detects which MythicMobs version (if any) is present and returns the
     * appropriate adapter.
     */
    private MythicAdapter resolveMythicAdapter() {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            plugin.getLogger().info("[MobSpawn] MythicMobs not found — mob spawn module disabled.");
            return new MythicNoopAdapter();
        }
        try {
            Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            plugin.getLogger().info("[MobSpawn] MythicMobs 5.x detected.");
            return new MythicV5Adapter();
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning(
                    "[MobSpawn] MythicMobs 4.x detected — only 5.x is supported. "
                    + "Mob spawn module disabled. Please upgrade MythicMobs.");
            return new MythicNoopAdapter();
        }
    }
}

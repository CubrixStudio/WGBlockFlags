package net.tylers1066.farm;

import net.tylers1066.WGBlockFlags;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.HandlerList;

/**
 * Coordinates the farm zone management sub-system.
 *
 * <p>Owned by {@link WGBlockFlags} and exposed via {@code getFarmModule()}.
 * The module can be started, stopped, and reloaded independently of the
 * rest of the plugin.
 */
public class FarmModule {

    private final WGBlockFlags plugin;
    private final FarmRegionCache cache;
    private final FarmScheduler scheduler;
    private FarmBreakListener listener;

    public FarmModule(WGBlockFlags plugin) {
        this.plugin = plugin;
        this.cache = new FarmRegionCache();
        this.scheduler = new FarmScheduler(plugin, cache);
    }

    /**
     * Builds the cache, registers the event listener, and starts the scheduler.
     * Must be called from {@link WGBlockFlags#onEnable()}.
     */
    public void enable() {
        cache.rebuild(plugin.getPluginConfig());
        listener = new FarmBreakListener(plugin, cache, scheduler);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        scheduler.start();
        // Scan all already-loaded chunks so crops in loaded regions are tracked immediately.
        scanLoadedChunks();
    }

    /**
     * Stops the scheduler and unregisters the event listener.
     * Must be called from {@link WGBlockFlags#onDisable()}.
     */
    public void disable() {
        scheduler.stop();
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }

    /**
     * Reloads the cache with the updated plugin configuration and re-scans
     * loaded chunks.  Called by {@link WGBlockFlags#reloadPluginConfig()}.
     */
    public void reload() {
        scheduler.stop();
        cache.rebuild(plugin.getPluginConfig());
        scheduler.start();
        scanLoadedChunks();
    }

    public FarmRegionCache getCache() {
        return cache;
    }

    public FarmScheduler getScheduler() {
        return scheduler;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Scans every loaded chunk in every world so crops are tracked from the start. */
    private void scanLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scheduler.onChunkLoad(chunk);
            }
        }
    }
}

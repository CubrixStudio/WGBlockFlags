package net.tylers1066.farm;

import net.tylers1066.WGBlockFlags;
import net.tylers1066.util.LogUtil;
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
        plugin.getServer().getPluginManager().registerEvents(new FarmDropListener(), plugin);
        scheduler.start();
        scheduler.queueLoadedChunksInRegions();
        LogUtil.moduleLifecycle("[FarmModule] Enabled — "
                + cache.getAutoGrowRegionCount() + " auto-grow region(s). "
                + "Chunk scans queued (" + scheduler.getPendingScanCount() + " chunk(s)).");
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
        scheduler.queueLoadedChunksInRegions();
    }

    public FarmRegionCache getCache() {
        return cache;
    }

    public FarmScheduler getScheduler() {
        return scheduler;
    }

}

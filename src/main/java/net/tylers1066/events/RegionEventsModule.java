package net.tylers1066.events;

import net.tylers1066.WGBlockFlags;
import net.tylers1066.util.LogUtil;
import org.bukkit.plugin.PluginManager;

/**
 * Coordinates the region-events sub-system.
 *
 * <p>Fires console commands, chat messages, titles, and action-bar text
 * when players enter or exit WorldGuard regions that have event flags configured.
 */
public class RegionEventsModule {

    private final WGBlockFlags plugin;
    private RegionEventsCache cache;
    private RegionEventsListener listener;

    public RegionEventsModule(WGBlockFlags plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        cache = new RegionEventsCache();
        cache.rebuild();
        listener = new RegionEventsListener(cache);
        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(listener, plugin);
        LogUtil.moduleLifecycle("[RegionEvents] Region events module enabled.");
    }

    public void disable() {
        if (listener != null) {
            listener.clear();
            listener = null;
        }
    }

    public void reload() {
        if (cache == null) {
            enable();
            return;
        }
        if (listener != null) {
            listener.clear();
        }
        cache.rebuild();
        LogUtil.moduleLifecycle("[RegionEvents] Region events module reloaded.");
    }

    public RegionEventsCache getCache() {
        return cache;
    }
}

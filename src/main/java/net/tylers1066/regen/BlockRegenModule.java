package net.tylers1066.regen;

import net.tylers1066.WGBlockFlags;
import org.bukkit.plugin.PluginManager;

/**
 * Coordinates the block-regen sub-system.
 *
 * <p>When enabled, broken blocks inside regen-enabled regions regenerate automatically
 * after the configured delay. Blocks placed by players are excluded from regeneration.
 */
public class BlockRegenModule {

    private final WGBlockFlags plugin;
    private BlockRegenCache cache;
    private BlockRegenListener listener;

    public BlockRegenModule(WGBlockFlags plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        cache = new BlockRegenCache();
        cache.rebuild(plugin.getPluginConfig());
        listener = new BlockRegenListener(plugin, cache);
        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(listener, plugin);
        plugin.getLogger().info("[BlockRegen] Block regen module enabled.");
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
        cache.rebuild(plugin.getPluginConfig());
        plugin.getLogger().info("[BlockRegen] Block regen module reloaded.");
    }

    public BlockRegenCache getCache() {
        return cache;
    }
}

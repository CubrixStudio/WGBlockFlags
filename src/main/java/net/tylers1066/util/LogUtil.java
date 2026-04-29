package net.tylers1066.util;

import net.tylers1066.WGBlockFlags;
import net.tylers1066.config.PluginConfig;

/**
 * Centralized logging utility that respects the logging configuration.
 * Provides methods for conditional logging based on config settings.
 */
public class LogUtil {

    private static WGBlockFlags plugin;

    public LogUtil(WGBlockFlags plugin) {
        LogUtil.plugin = plugin;
    }

    public static void moduleLifecycle(String message) {
        if (plugin == null) return;
        PluginConfig config = plugin.getPluginConfig();
        if (config.isLogModuleLifecycle()) {
            plugin.getLogger().info(message);
        }
    }

    public static void schedulerEvent(String message) {
        if (plugin == null) return;
        PluginConfig config = plugin.getPluginConfig();
        if (config.isLogSchedulerEvents()) {
            plugin.getLogger().info(message);
        }
    }

    public static void spawnWarning(String message) {
        if (plugin == null) return;
        PluginConfig config = plugin.getPluginConfig();
        if (config.isLogSpawnWarnings()) {
            plugin.getLogger().warning(message);
        }
    }

    public static void severe(String message) {
        if (plugin != null) {
            plugin.getLogger().severe(message);
        }
    }

    public static void info(String message) {
        if (plugin != null) {
            plugin.getLogger().info(message);
        }
    }
}

package net.tylers1066.events;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RegionEventsCache {

    /**
     * Immutable snapshot of a region's entry/exit event configuration.
     *
     * @param enterCommand    console command on enter (null = none)
     * @param exitCommand     console command on exit (null = none)
     * @param enterMessage    chat message on enter (null = none)
     * @param exitMessage     chat message on exit (null = none)
     * @param enterTitle      title on enter in "Title;Subtitle" format (null = none)
     * @param enterActionbar  action-bar text on enter (null = none)
     */
    public record RegionEventData(
            @Nullable String enterCommand,
            @Nullable String exitCommand,
            @Nullable String enterMessage,
            @Nullable String exitMessage,
            @Nullable String enterTitle,
            @Nullable String enterActionbar
    ) {
        /** Returns true if this record has at least one non-null event configured. */
        public boolean hasAnyEvent() {
            return enterCommand != null || exitCommand != null
                    || enterMessage != null || exitMessage != null
                    || enterTitle != null || enterActionbar != null;
        }
    }

    /** A region paired with its event data. */
    public record RegionEntry(ProtectedRegion region, RegionEventData data) {}

    // world name → (region id → entry)
    private final Map<String, Map<String, RegionEntry>> cache = new HashMap<>();

    /**
     * Rebuilds the cache from current WorldGuard region flags.
     * Must be called on the main thread.
     */
    public void rebuild() {
        cache.clear();
        for (World world : Bukkit.getWorlds()) {
            RegionManager rm = WorldGuard.getInstance().getPlatform()
                    .getRegionContainer().get(BukkitAdapter.adapt(world));
            if (rm == null) {
                continue;
            }
            Map<String, RegionEntry> worldCache = new HashMap<>();
            cache.put(world.getName(), worldCache);

            for (Map.Entry<String, ProtectedRegion> entry : rm.getRegions().entrySet()) {
                ProtectedRegion region = entry.getValue();
                RegionEventData data = buildData(region);
                if (data != null) {
                    worldCache.put(entry.getKey(), new RegionEntry(region, data));
                }
            }
        }
    }

    /**
     * Returns all region entries that have at least one event configured for the given world.
     */
    public List<RegionEntry> getEventEntries(String worldName) {
        Map<String, RegionEntry> worldCache = cache.get(worldName);
        if (worldCache == null) return Collections.emptyList();
        return new ArrayList<>(worldCache.values());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private @Nullable RegionEventData buildData(ProtectedRegion region) {
        String enterCommand = nullIfBlank(region.getFlag(RegionEventsFlags.REGION_ENTER_COMMAND));
        String exitCommand = nullIfBlank(region.getFlag(RegionEventsFlags.REGION_EXIT_COMMAND));
        String enterMessage = nullIfBlank(region.getFlag(RegionEventsFlags.REGION_ENTER_MESSAGE));
        String exitMessage = nullIfBlank(region.getFlag(RegionEventsFlags.REGION_EXIT_MESSAGE));
        String enterTitle = nullIfBlank(region.getFlag(RegionEventsFlags.REGION_ENTER_TITLE));
        String enterActionbar = nullIfBlank(region.getFlag(RegionEventsFlags.REGION_ENTER_ACTIONBAR));

        RegionEventData data = new RegionEventData(
                enterCommand, exitCommand, enterMessage, exitMessage, enterTitle, enterActionbar);
        return data.hasAnyEvent() ? data : null;
    }

    private @Nullable String nullIfBlank(@Nullable String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}

package net.tylers1066.farm;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.managers.RegionManager;
import net.tylers1066.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FarmRegionCache {

    /**
     * Immutable snapshot of a region's farm configuration, computed once at build time.
     *
     * @param crops         the set of crop materials managed — empty means all crops
     * @param autoGrow      whether auto-grow is active for this region
     * @param growInterval  ticks between forced growth cycles
     * @param autoReplant   whether auto-replant is active for this region
     * @param protectCrops  whether non-mature crops are protected from breaking
     * @param activeTime    time-of-day restriction: "any", "day", or "night"
     * @param activeWeather weather restriction: "any", "clear", or "rain"
     * @param maxHeight     max height for upward vertical crops; 0 = use crop defaults
     */
    public record FarmRegionData(
            Set<Material> crops,
            boolean autoGrow,
            int growInterval,
            boolean autoReplant,
            boolean protectCrops,
            String activeTime,
            String activeWeather,
            int maxHeight
    ) {
        /** Returns true if the given material is managed by this farm region. */
        public boolean manages(Material material) {
            return crops.isEmpty() || crops.contains(material);
        }
    }

    /**
     * A region and its farm data paired together.
     * The {@link ProtectedRegion} reference is needed for bounding-box queries during chunk scanning.
     */
    public record RegionEntry(ProtectedRegion region, FarmRegionData data) {}

    // world name → (region id → entry)
    private final Map<String, Map<String, RegionEntry>> cache = new HashMap<>();

    /**
     * Rebuilds the entire cache by reading WorldGuard region flags for every loaded world.
     * Must be called on the main thread.
     */
    public void rebuild(PluginConfig config) {
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
                FarmRegionData data = buildData(region, config);
                if (data != null) {
                    worldCache.put(entry.getKey(), new RegionEntry(region, data));
                }
            }
        }
    }

    /**
     * Returns the farm data for the given region, or {@code null} if the region
     * is not configured as a farm region (no auto-grow and no auto-replant).
     */
    public @Nullable FarmRegionData getData(String worldName, String regionId) {
        Map<String, RegionEntry> worldCache = cache.get(worldName);
        if (worldCache == null) {
            return null;
        }
        RegionEntry entry = worldCache.get(regionId);
        return entry != null ? entry.data() : null;
    }

    /** Returns the total number of auto-grow regions across all worlds. */
    public int getAutoGrowRegionCount() {
        int count = 0;
        for (Map<String, RegionEntry> worldCache : cache.values()) {
            for (RegionEntry entry : worldCache.values()) {
                if (entry.data().autoGrow()) count++;
            }
        }
        return count;
    }

    /**
     * Returns all farm region entries in the given world that have auto-grow active.
     * Used by the scheduler to determine which regions to scan when a chunk loads.
     */
    public List<RegionEntry> getAutoGrowEntries(String worldName) {
        Map<String, RegionEntry> worldCache = cache.get(worldName);
        if (worldCache == null) {
            return Collections.emptyList();
        }
        List<RegionEntry> result = new ArrayList<>();
        for (RegionEntry entry : worldCache.values()) {
            if (entry.data().autoGrow()) {
                result.add(entry);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link FarmRegionData} from the flags set on a region.
     * Returns {@code null} if neither auto-grow nor auto-replant is configured.
     */
    private @Nullable FarmRegionData buildData(ProtectedRegion region, PluginConfig config) {
        StateFlag.State autoGrowState = region.getFlag(FarmFlags.FARM_AUTOGROW);
        StateFlag.State autoReplantState = region.getFlag(FarmFlags.FARM_AUTOREPLANT);
        StateFlag.State protectState = region.getFlag(FarmFlags.FARM_PROTECT_CROPS);

        boolean autoGrow = autoGrowState == StateFlag.State.ALLOW;
        boolean autoReplant = autoReplantState == StateFlag.State.ALLOW;
        boolean protectCrops = protectState == StateFlag.State.ALLOW;

        if (!autoGrow && !autoReplant && !protectCrops) {
            return null;
        }

        Integer intervalFlag = region.getFlag(FarmFlags.FARM_GROW_INTERVAL);
        int interval;
        if (intervalFlag != null) {
            interval = Math.max(config.getMinGrowInterval(), intervalFlag);
        } else {
            interval = config.getGlobalGrowInterval();
        }

        Set<Material> rawCrops = region.getFlag(FarmFlags.FARM_CROPS);
        Set<Material> crops = rawCrops != null ? Collections.unmodifiableSet(new HashSet<>(rawCrops))
                : Collections.emptySet();

        String activeTime = region.getFlag(FarmFlags.FARM_ACTIVE_TIME);
        if (activeTime == null || activeTime.isBlank()) {
            activeTime = "any";
        } else {
            activeTime = activeTime.toLowerCase(java.util.Locale.ROOT);
        }

        String activeWeather = region.getFlag(FarmFlags.FARM_ACTIVE_WEATHER);
        if (activeWeather == null || activeWeather.isBlank()) {
            activeWeather = "any";
        } else {
            activeWeather = activeWeather.toLowerCase(java.util.Locale.ROOT);
        }

        Integer maxHeightFlag = region.getFlag(FarmFlags.FARM_MAX_HEIGHT);
        int maxHeight = (maxHeightFlag != null && maxHeightFlag > 0) ? maxHeightFlag : 0;

        return new FarmRegionData(crops, autoGrow, interval, autoReplant, protectCrops,
                activeTime, activeWeather, maxHeight);
    }
}

package net.tylers1066.mob;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.tylers1066.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MobRegionCache {

    /**
     * Immutable snapshot of a region's mob-spawn configuration, computed at rebuild time.
     *
     * @param mobTypes     the set of MythicMobs names to spawn — never empty (null-guarded at build time)
     * @param autoSpawn    whether mob-autospawn is active
     * @param spawnInterval ticks between spawn cycles
     * @param maxMobs      maximum concurrent mobs of this zone's types inside the region
     * @param spawnCount   mobs attempted per cycle
     * @param levelMin     minimum spawn level
     * @param levelMax     maximum spawn level
     * @param spawnTime    time restriction: "any", "day", or "night"
     * @param spawnWeather weather restriction: "any", "clear", or "rain"
     */
    public record MobSpawnData(
            Set<String> mobTypes,
            boolean autoSpawn,
            int spawnInterval,
            int maxMobs,
            int spawnCount,
            int levelMin,
            int levelMax,
            String spawnTime,
            String spawnWeather
    ) {}

    /**
     * Immutable snapshot of a region's mob spawn filter configuration.
     *
     * @param filterEnabled whether spawn filtering is active (mob-spawn-filter=allow)
     * @param allowTypes    whitelist of allowed mob types (if empty, no whitelist applied)
     * @param denyTypes     blacklist of denied mob types (only checked if allowTypes is empty)
     * @param allowVanilla  whether vanilla mobs are allowed (true=allow, deny=block vanilla)
     */
    public record MobFilterData(
            boolean filterEnabled,
            Set<String> allowTypes,
            Set<String> denyTypes,
            boolean allowVanilla
    ) {}

    /**
     * A region paired with its computed spawn data.
     * The {@link ProtectedRegion} reference is needed in {@link MobSpawnManager}
     * for bounding-box access and {@code region.contains()} checks.
     */
    public record RegionEntry(ProtectedRegion region, MobSpawnData spawnData, MobFilterData filterData) {}

    // world name → (region id → entry)
    private final Map<String, Map<String, RegionEntry>> cache = new HashMap<>();

    /**
     * Rebuilds the entire cache from the current WorldGuard region flags.
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
                MobSpawnData spawnData = buildData(region, config);
                MobFilterData filterData = buildFilterData(region);
                worldCache.put(entry.getKey(), new RegionEntry(region, spawnData, filterData));
            }
        }
    }

    /**
     * Returns the spawn data for the given region, or {@code null} if the region
     * has no active mob-spawn configuration.
     */
    public @Nullable MobSpawnData getData(String worldName, String regionId) {
        Map<String, RegionEntry> worldCache = cache.get(worldName);
        if (worldCache == null) {
            return null;
        }
        RegionEntry entry = worldCache.get(regionId);
        return entry != null ? entry.spawnData() : null;
    }

    /**
     * Returns all region entries with {@code autoSpawn == true} for the given world.
     */
    public List<RegionEntry> getAutoSpawnEntries(String worldName) {
        Map<String, RegionEntry> worldCache = cache.get(worldName);
        if (worldCache == null) {
            return Collections.emptyList();
        }
        List<RegionEntry> result = new ArrayList<>();
        for (RegionEntry entry : worldCache.values()) {
            MobSpawnData data = entry.spawnData();
            if (data != null && data.autoSpawn()) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Helper method to get a RegionEntry by world name and region ID.
     * Used by {@link MobSpawnFilterListener}.
     */
    public RegionEntry getRegionEntry(String worldName, String regionId) {
        Map<String, RegionEntry> worldCache = cache.get(worldName);
        return worldCache != null ? worldCache.get(regionId) : null;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds spawn data from the flags set on a region.
     * Returns {@code null} if auto-spawn is not enabled or no mob types are configured.
     */
    private @Nullable MobSpawnData buildData(ProtectedRegion region, PluginConfig config) {
        StateFlag.State autoSpawnState = region.getFlag(MobSpawnFlags.MOB_AUTOSPAWN);
        if (autoSpawnState != StateFlag.State.ALLOW) {
            return null;
        }

        Set<String> rawMobs = region.getFlag(MobSpawnFlags.MOB_SPAWN_MOBS);
        if (rawMobs == null || rawMobs.isEmpty()) {
            // Auto-spawn is enabled but no mobs are configured — skip.
            return null;
        }
        Set<String> mobTypes = Collections.unmodifiableSet(new HashSet<>(rawMobs));

        int interval = resolveInt(region, MobSpawnFlags.MOB_SPAWN_INTERVAL,
                config.getMobSpawnInterval());
        int maxMobs = resolveInt(region, MobSpawnFlags.MOB_SPAWN_MAX,
                config.getMobSpawnMax());
        int spawnCount = resolveInt(region, MobSpawnFlags.MOB_SPAWN_COUNT,
                config.getMobSpawnCount());
        int levelMin = resolveInt(region, MobSpawnFlags.MOB_SPAWN_LEVEL_MIN,
                config.getMobDefaultLevelMin());
        int levelMax = resolveInt(region, MobSpawnFlags.MOB_SPAWN_LEVEL_MAX,
                config.getMobDefaultLevelMax());

        // Clamp: ensure levelMax >= levelMin
        if (levelMax < levelMin) {
            levelMax = levelMin;
        }

        String spawnTime = region.getFlag(MobSpawnFlags.MOB_SPAWN_TIME);
        if (spawnTime == null || spawnTime.isBlank()) {
            spawnTime = "any";
        } else {
            spawnTime = spawnTime.toLowerCase(java.util.Locale.ROOT);
        }

        String spawnWeather = region.getFlag(MobSpawnFlags.MOB_SPAWN_WEATHER);
        if (spawnWeather == null || spawnWeather.isBlank()) {
            spawnWeather = "any";
        } else {
            spawnWeather = spawnWeather.toLowerCase(java.util.Locale.ROOT);
        }

        return new MobSpawnData(mobTypes, true, interval, maxMobs, spawnCount,
                levelMin, levelMax, spawnTime, spawnWeather);
    }

    private int resolveInt(ProtectedRegion region,
           com.sk89q.worldguard.protection.flags.IntegerFlag flag,
           int defaultValue) {
        Integer value = region.getFlag(flag);
        return (value != null && value > 0) ? value : defaultValue;
    }

    /**
     * Builds filter data from the flags set on a region.
     * Always returns a MobFilterData instance (may have filterEnabled=false).
     */
    private MobFilterData buildFilterData(ProtectedRegion region) {
        StateFlag.State filterState = region.getFlag(MobSpawnFlags.MOB_SPAWN_FILTER);
        boolean filterEnabled = (filterState == StateFlag.State.ALLOW);

        Set<String> rawAllow = region.getFlag(MobSpawnFlags.MOB_ALLOW_TYPES);
        Set<String> allowTypes = (rawAllow != null && !rawAllow.isEmpty())
                ? Collections.unmodifiableSet(new HashSet<>(rawAllow))
                : Collections.emptySet();

        Set<String> rawDeny = region.getFlag(MobSpawnFlags.MOB_DENY_TYPES);
        Set<String> denyTypes = (rawDeny != null && !rawDeny.isEmpty())
                ? Collections.unmodifiableSet(new HashSet<>(rawDeny))
                : Collections.emptySet();

        StateFlag.State vanillaState = region.getFlag(MobSpawnFlags.MOB_ALLOW_VANILLA);
        boolean allowVanilla = (vanillaState != StateFlag.State.DENY);

        return new MobFilterData(filterEnabled, allowTypes, denyTypes, allowVanilla);
    }
}

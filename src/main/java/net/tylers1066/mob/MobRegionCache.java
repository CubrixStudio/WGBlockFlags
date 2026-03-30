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
     * A region paired with its computed spawn data.
     * The {@link ProtectedRegion} reference is needed in {@link MobSpawnManager}
     * for bounding-box access and {@code region.contains()} checks.
     */
    public record RegionEntry(ProtectedRegion region, MobSpawnData data) {}

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
                MobSpawnData data = buildData(region, config);
                if (data != null) {
                    worldCache.put(entry.getKey(), new RegionEntry(region, data));
                }
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
        return entry != null ? entry.data() : null;
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
            if (entry.data().autoSpawn()) {
                result.add(entry);
            }
        }
        return result;
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
}

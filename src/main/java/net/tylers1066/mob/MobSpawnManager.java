package net.tylers1066.mob;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.tylers1066.WGBlockFlags;
import net.tylers1066.mob.MobRegionCache.MobSpawnData;
import net.tylers1066.mob.MobRegionCache.RegionEntry;
import net.tylers1066.mob.mythic.MythicAdapter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives the automatic mob-spawn mechanic for WorldGuard farm regions.
 *
 * <p>A single BukkitScheduler task fires every {@value #TASK_PERIOD_TICKS} ticks.
 * On each fire it iterates all worlds and all autospawn regions, checks per-region
 * countdown timers, and spawns mobs when the interval has elapsed.
 *
 * <p>Population is tracked via UUID: each spawned mob is recorded in
 * {@link #trackedMobs} and its zone counter is decremented when the mob dies
 * (see {@link #recordDeath(UUID)}). No chunk tickets are held and no spatial
 * entity queries are needed for the population cap.
 */
public class MobSpawnManager {

    /** Ticks between scheduler fires (1 second). */
    private static final int TASK_PERIOD_TICKS = 20;

    private final WGBlockFlags plugin;
    private final MobRegionCache cache;
    private final MythicAdapter adapter;

    private static ThreadLocalRandom rng() { return ThreadLocalRandom.current(); }

    // ---- Per-zone countdown timer ----
    /**
     * Remaining ticks before the next spawn cycle per region key
     * ({@code "worldName:regionId"}). Decremented by {@link #TASK_PERIOD_TICKS}
     * each scheduler fire; a value ≤ 0 means "ready to spawn".
     */
    private final Map<String, Integer> countdown = new HashMap<>();

    // ---- UUID-based population tracking ----
    /**
     * Maps each tracked mob UUID to its zone key.
     * Used to decrement the correct zone counter on death.
     */
    private final Map<UUID, String> trackedMobs = new HashMap<>();

    /**
     * Live population count per zone key. Incremented on spawn, decremented on
     * death. Never goes below zero. Accurate across chunk loads/unloads because
     * it does not rely on entities being in loaded chunks.
     */
    private final Map<String, Integer> zoneCount = new HashMap<>();

    /** Incremented on every scheduler fire for heartbeat logging. */
    private int tickCounter = 0;

    /** Zones that have already logged their "at capacity" message — avoids log spam. */
    private final Set<String> atCapacityLogged = new HashSet<>();

    private BukkitTask task = null;

    public MobSpawnManager(WGBlockFlags plugin, MobRegionCache cache, MythicAdapter adapter) {
        this.plugin = plugin;
        this.cache = cache;
        this.adapter = adapter;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void start() {
        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, 1L, TASK_PERIOD_TICKS);
        plugin.getLogger().info("[MobSpawn] Scheduler started (taskId=" + task.getTaskId() + ").");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        countdown.clear();
        trackedMobs.clear();
        zoneCount.clear();
        atCapacityLogged.clear();
        plugin.getLogger().info("[MobSpawn] Scheduler stopped.");
    }

    // -------------------------------------------------------------------------
    // Population tracking (called by MobZoneDeathListener)
    // -------------------------------------------------------------------------

    /**
     * Records that a mob was spawned in the given zone.
     * Called from {@link #spawnBatch} after a successful MythicMobs spawn.
     */
    void recordSpawn(String zoneKey, UUID uuid) {
        trackedMobs.put(uuid, zoneKey);
        zoneCount.merge(zoneKey, 1, Integer::sum);
    }

    /**
     * Records that a tracked mob has been removed from the world (killed,
     * despawned, removed by a plugin, etc.).
     * Called from {@link MobZoneDeathListener} on {@code EntityDeathEvent}.
     */
    public void recordDeath(UUID uuid) {
        String zoneKey = trackedMobs.remove(uuid);
        if (zoneKey != null) {
            zoneCount.merge(zoneKey, -1, (cur, d) -> Math.max(0, cur + d));
        }
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    private void tick() {
        tickCounter++;
        if (plugin.getPluginConfig().isDebugMob() && tickCounter % 20 == 0) {
            plugin.getLogger().info("[MobSpawn] Scheduler alive — tick #" + tickCounter);
        }
        try {
            tickInternal();
        } catch (Throwable e) {
            plugin.getLogger().severe("[MobSpawn] Uncaught exception in spawn tick — scheduler kept alive:");
            e.printStackTrace();
        }
    }

    private void tickInternal() {
        for (World world : plugin.getServer().getWorlds()) {
            List<RegionEntry> entries = cache.getAutoSpawnEntries(world.getName());

            for (RegionEntry entry : entries) {
                MobSpawnData data = entry.spawnData();
                String regionId = entry.region().getId();
                String key = world.getName() + ":" + regionId;

                int remaining = countdown.getOrDefault(key, 0) - TASK_PERIOD_TICKS;
                if (remaining > 0) {
                    countdown.put(key, remaining);
                    continue;
                }

                debug("[MobSpawn] Zone '" + regionId + "': ready to spawn, evaluating...");

                if (!isValidTime(world, data.spawnTime())) {
                    debug("[MobSpawn] Zone '" + regionId + "': skipped — time restriction '"
                            + data.spawnTime() + "'.");
                    countdown.put(key, 0);
                    continue;
                }
                if (!isValidWeather(world, data.spawnWeather())) {
                    debug("[MobSpawn] Zone '" + regionId + "': skipped — weather restriction '"
                            + data.spawnWeather() + "'.");
                    countdown.put(key, 0);
                    continue;
                }

                countdown.put(key, data.spawnInterval());

                // Population check using UUID-tracked count — no chunk loading needed.
                int current = zoneCount.getOrDefault(key, 0);
                int toSpawn = Math.min(data.spawnCount(), data.maxMobs() - current);
                if (toSpawn <= 0) {
                    // Log at-capacity once (when first hitting the cap), then only in debug.
                    if (current == data.maxMobs() && !atCapacityLogged.contains(key)) {
                        plugin.getLogger().info("[MobSpawn] " + regionId
                                + ": at capacity (" + current + "/" + data.maxMobs() + ").");
                        atCapacityLogged.add(key);
                    } else {
                        debug("[MobSpawn] Zone '" + regionId + "': at capacity ("
                                + current + "/" + data.maxMobs() + ") — skipping cycle.");
                    }
                    continue;
                }
                atCapacityLogged.remove(key); // below cap again after a death

                debug("[MobSpawn] Zone '" + regionId + "': spawning " + toSpawn
                        + " mob(s) (" + current + "/" + data.maxMobs() + " present).");
                spawnBatch(world, entry.region(), data, toSpawn, key);            }
        }
    }

    // -------------------------------------------------------------------------
    // Spawn helpers
    // -------------------------------------------------------------------------

    private void spawnBatch(World world, ProtectedRegion region, MobSpawnData data,
                            int count, String zoneKey) {
        int attempts = plugin.getPluginConfig().getMobSpawnAttempts();
        List<String> types = new ArrayList<>(data.mobTypes());
        int spawned = 0;

        for (int i = 0; i < count; i++) {
            Location loc = findSafeLocation(world, region, attempts);
            if (loc == null) break;

            String mobType = types.get(rng().nextInt(types.size()));
            double level = resolveLevel(data.levelMin(), data.levelMax());
            Optional<UUID> result = adapter.spawnMob(mobType, loc, level);

            if (result.isPresent()) {
                recordSpawn(zoneKey, result.get());
                spawned++;
                debug("[MobSpawn] Spawned '" + mobType + "' at "
                        + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                        + " in region '" + region.getId() + "'.");
            } else {
                plugin.getLogger().warning("[MobSpawn] FAILED to spawn '" + mobType
                        + "' in region '" + region.getId() + "' — mob name not found in MythicMobs.");
            }
        }

        // Always log a brief cycle summary so spawn activity is visible even without debug.mob.
        if (spawned > 0) {
            int newTotal = zoneCount.getOrDefault(zoneKey, 0);
            plugin.getLogger().info("[MobSpawn] " + region.getId()
                    + ": +" + spawned + " mob(s) → " + newTotal + "/" + data.maxMobs() + ".");
        }
    }

    /**
     * Searches for a safe spawn location by trying random positions inside the
     * region bounding box. Chunks are loaded on demand (synchronous, main thread)
     * only when needed for block queries; they are released after this call.
     *
     * @param attempts number of random positions to try before giving up
     * @return a safe {@link Location} inside the region, or {@code null}
     */
    private @Nullable Location findSafeLocation(World world, ProtectedRegion region, int attempts) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        List<int[]> chunks = new ArrayList<>();
        for (int cx = min.x() >> 4; cx <= max.x() >> 4; cx++) {
            for (int cz = min.z() >> 4; cz <= max.z() >> 4; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }

        ThreadLocalRandom rng = rng();
        for (int attempt = 0; attempt < attempts; attempt++) {
            int[] col = chunks.get(rng.nextInt(chunks.size()));
            int x = Math.max(min.x(), Math.min(max.x(), col[0] * 16 + rng.nextInt(16)));
            int z = Math.max(min.z(), Math.min(max.z(), col[1] * 16 + rng.nextInt(16)));

            // Load the chunk temporarily so block queries return real data.
            if (!world.isChunkLoaded(col[0], col[1])) {
                world.getChunkAt(col[0], col[1]);
            }

            for (int y = max.y() - 1; y >= min.y(); y--) {
                if (!region.contains(x, y + 1, z)) continue;
                Block ground = world.getBlockAt(x, y, z);
                Block feet   = world.getBlockAt(x, y + 1, z);
                Block head   = world.getBlockAt(x, y + 2, z);
                if (isSolidGround(ground) && isPassable(feet) && isPassable(head)) {
                    return new Location(world, x + 0.5, y + 1, z + 0.5);
                }
            }
        }
        plugin.getLogger().warning("[MobSpawn] No valid spawn position in region '"
                + region.getId() + "' after " + attempts + " attempts. "
                + "Check that the region has accessible solid ground.");
        return null;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean isValidTime(World world, String spawnTime) {
        return switch (spawnTime) {
            case "day"   -> world.isDayTime();
            case "night" -> !world.isDayTime();
            default      -> true;
        };
    }

    private boolean isValidWeather(World world, String spawnWeather) {
        return switch (spawnWeather) {
            case "clear" -> !world.hasStorm() && !world.isThundering();
            case "rain"  -> world.hasStorm() || world.isThundering();
            default      -> true;
        };
    }

    private double resolveLevel(int min, int max) {
        return (min >= max) ? min : min + rng().nextInt(max - min + 1);
    }

    private boolean isSolidGround(Block block) {
        Material type = block.getType();
        if (!type.isSolid() || type.isAir()) return false;
        if (type == Material.WATER || type == Material.LAVA) return false;
        if (Tag.LEAVES.isTagged(type)) return false;
        if (Tag.LOGS.isTagged(type)) return false;
        return true;
    }

    private boolean isPassable(Block block) {
        return block.isPassable();
    }

    private void debug(String msg) {
        if (plugin.getPluginConfig().isDebugMob()) {
            plugin.getLogger().info(msg);
        }
    }
}

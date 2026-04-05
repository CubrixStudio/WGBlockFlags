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
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives the automatic mob-spawn mechanic for WorldGuard farm regions.
 *
 * <p>A single BukkitScheduler task fires every 20 ticks (1 second). On each fire it
 * iterates all loaded worlds and all auto-spawn regions, checks per-region timers,
 * and spawns mobs when the interval has elapsed.
 *
 * <p>Population is controlled by counting live MythicMobs entities inside the
 * region at spawn time — no UUID tracking needed, so mob deaths are handled
 * implicitly at the next cycle.
 */
public class MobSpawnManager {

    /** Ticks between scheduler fires. Lower values waste CPU; 20 is fine for spawn intervals measured in hundreds of ticks. */
    private static final int TASK_PERIOD_TICKS = 20;

    private final WGBlockFlags plugin;
    private final MobRegionCache cache;
    private final MythicAdapter adapter;
    // Use ThreadLocalRandom — no contention, no object allocation each use.
    private static ThreadLocalRandom rng() { return ThreadLocalRandom.current(); }

    /** Last spawn tick per region key ({@code "worldName:regionId"}). Uses {@link World#getFullTime()}. */
    private final Map<String, Long> lastSpawnTick = new HashMap<>();

    private int taskId = -1;

    public MobSpawnManager(WGBlockFlags plugin, MobRegionCache cache, MythicAdapter adapter) {
        this.plugin = plugin;
        this.cache = cache;
        this.adapter = adapter;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void start() {
        taskId = plugin.getServer().getScheduler()
                .scheduleSyncRepeatingTask(plugin, this::tick, 0L, TASK_PERIOD_TICKS);
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        lastSpawnTick.clear();
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    private void tick() {
        for (World world : plugin.getServer().getWorlds()) {
            long currentTick = world.getFullTime();
            List<RegionEntry> entries = cache.getAutoSpawnEntries(world.getName());

            for (RegionEntry entry : entries) {
                MobSpawnData data = entry.spawnData();
                String key = world.getName() + ":" + entry.region().getId();

                long last = lastSpawnTick.getOrDefault(key, 0L);
                if (currentTick - last < data.spawnInterval()) {
                    continue;
                }

                // Check time-of-day and weather conditions BEFORE advancing the timer.
                // If conditions are not met the timer is not reset, so we will retry
                // on the next scheduler fire (every TASK_PERIOD_TICKS ticks) rather
                // than waiting a full interval before trying again.
                if (!isValidTime(world, data.spawnTime())) {
                    continue;
                }
                if (!isValidWeather(world, data.spawnWeather())) {
                    continue;
                }

                // Conditions met — advance the timer now to prevent rapid re-spawning.
                lastSpawnTick.put(key, currentTick);

                // Population check
                int current = adapter.countMobsInRegion(world, entry.region(), data.mobTypes());
                int toSpawn = Math.min(data.spawnCount(), data.maxMobs() - current);
                if (toSpawn <= 0) {
                    debug("[MobSpawn] Region '" + entry.region().getId() + "' at capacity ("
                            + current + "/" + data.maxMobs() + ") — skipping cycle.");
                    continue;
                }

                spawnBatch(world, entry.region(), data, toSpawn);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Spawn helpers
    // -------------------------------------------------------------------------

    private void spawnBatch(World world, ProtectedRegion region, MobSpawnData data, int count) {
        int attempts = plugin.getPluginConfig().getMobSpawnAttempts();
        List<String> types = new ArrayList<>(data.mobTypes());

        for (int i = 0; i < count; i++) {
            Location loc = findSafeLocation(world, region, attempts);
            if (loc == null) {
                // findSafeLocation already logged the specific reason (no chunks / no terrain).
                break;
            }
            String mobType = types.get(rng().nextInt(types.size()));
            double level = resolveLevel(data.levelMin(), data.levelMax());
            boolean spawned = adapter.spawnMob(mobType, loc, level).isPresent();
            if (plugin.getPluginConfig().isDebugMob()) {
                if (spawned) {
                    debug("[MobSpawn] Spawned '" + mobType + "' at "
                            + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                            + " in region '" + region.getId() + "'.");
                } else {
                    plugin.getLogger().warning("[MobSpawn] FAILED to spawn '" + mobType
                            + "' in region '" + region.getId() + "' — check that the mob name"
                            + " matches exactly (case-sensitive) the MythicMobs mob internal name.");
                }
            }
        }
    }

    /**
     * Searches for a safe spawn location inside the region by trying random
     * positions within <em>loaded</em> chunks only.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Enumerate every chunk column that overlaps the region bounding box
     *       and is currently loaded. This is O(region_chunks) but avoids wasting
     *       all {@code attempts} on unloaded-chunk positions in large zones.</li>
     *   <li>Pick a random loaded chunk, then a random (x,z) inside it, clamped
     *       to the region bounding box.</li>
     *   <li>Scan downward for a solid ground block with two passable blocks
     *       above it, with the spawn point (y+1) inside the region.</li>
     * </ol>
     *
     * @param attempts number of random positions to try before giving up
     * @return a safe {@link Location} inside the region, or {@code null} if none found
     */
    private @Nullable Location findSafeLocation(World world, ProtectedRegion region, int attempts) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        // Collect loaded chunk columns that overlap the region.
        List<int[]> loadedChunks = new ArrayList<>();
        for (int cx = min.x() >> 4; cx <= max.x() >> 4; cx++) {
            for (int cz = min.z() >> 4; cz <= max.z() >> 4; cz++) {
                if (world.isChunkLoaded(cx, cz)) {
                    loadedChunks.add(new int[]{cx, cz});
                }
            }
        }
        if (loadedChunks.isEmpty()) {
            debug("[MobSpawn] Region '" + region.getId() + "' has no loaded chunks — skipping.");
            return null;
        }

        ThreadLocalRandom rng = rng();
        for (int attempt = 0; attempt < attempts; attempt++) {
            // Pick a random loaded chunk, then a random position inside it, clamped to region bounds.
            int[] col = loadedChunks.get(rng.nextInt(loadedChunks.size()));
            int x = Math.max(min.x(), Math.min(max.x(), col[0] * 16 + rng.nextInt(16)));
            int z = Math.max(min.z(), Math.min(max.z(), col[1] * 16 + rng.nextInt(16)));

            // Scan downward for solid ground + two passable blocks above.
            // The spawn point (y+1, mob's feet) must be inside the region.
            for (int y = max.y() - 1; y >= min.y(); y--) {
                if (!region.contains(x, y + 1, z)) {
                    continue;
                }
                Block ground = world.getBlockAt(x, y, z);
                Block feet   = world.getBlockAt(x, y + 1, z);
                Block head   = world.getBlockAt(x, y + 2, z);

                if (isSolidGround(ground) && isPassable(feet) && isPassable(head)) {
                    return new Location(world, x + 0.5, y + 1, z + 0.5);
                }
            }
        }
        // All attempts exhausted — terrain issue (chunks were loaded but no valid ground found).
        plugin.getLogger().warning("[MobSpawn] No valid spawn position in region '"
                + region.getId() + "' after " + attempts + " attempts across "
                + loadedChunks.size() + " loaded chunk(s). "
                + "Check that the region has accessible solid ground.");
        return null;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean isValidTime(World world, String spawnTime) {
        return switch (spawnTime) {
            case "day" -> world.isDayTime();
            case "night" -> !world.isDayTime();
            default -> true; // "any" or unrecognised value
        };
    }

    private boolean isValidWeather(World world, String spawnWeather) {
        return switch (spawnWeather) {
            case "clear" -> !world.hasStorm() && !world.isThundering();
            case "rain" -> world.hasStorm() || world.isThundering();
            default -> true; // "any" or unrecognised value
        };
    }

    private double resolveLevel(int min, int max) {
        if (min >= max) {
            return min;
        }
        return min + rng().nextInt(max - min + 1);
    }

    private boolean isSolidGround(Block block) {
        Material type = block.getType();
        if (!type.isSolid() || type.isAir()) return false;
        if (type == Material.WATER || type == Material.LAVA) return false;
        // Exclude tree components — leaves and logs are technically "solid" in the
        // Bukkit API but mobs should never spawn on top of them.
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

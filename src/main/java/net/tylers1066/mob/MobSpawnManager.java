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

    /** Incremented on every scheduler fire — lets the heartbeat log confirm the task is alive. */
    private int tickCounter = 0;

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
        // Use runTaskTimer (non-deprecated) instead of scheduleSyncRepeatingTask.
        // delay=1 avoids firing during the same tick as start() — useful during
        // server startup when WorldGuard regions may not yet be fully loaded.
        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, 1L, TASK_PERIOD_TICKS);
        plugin.getLogger().info("[MobSpawn] Scheduler started (taskId=" + task.getTaskId() + ").");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        lastSpawnTick.clear();
        plugin.getLogger().info("[MobSpawn] Scheduler stopped.");
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    private void tick() {
        // Increment and heartbeat OUTSIDE try-catch so we always see it,
        // even if tickInternal() is somehow aborting before its own logs.
        tickCounter++;
        // Heartbeat every 20 fires (= 400 ticks = 20 s) when debug.mob is on.
        if (plugin.getPluginConfig().isDebugMob() && tickCounter % 20 == 0) {
            plugin.getLogger().info("[MobSpawn] Scheduler alive — tick #" + tickCounter);
        }
        try {
            tickInternal();
        } catch (Throwable e) {
            // Catch Throwable (not just Exception) so that errors from third-party
            // libraries (e.g. MythicMobs) don't silently kill the repeating task.
            // Always print full stack trace — getMessage() returns null for NPE.
            plugin.getLogger().severe("[MobSpawn] Uncaught exception in spawn tick — scheduler kept alive:");
            e.printStackTrace();
        }
    }

    private void tickInternal() {
        for (World world : plugin.getServer().getWorlds()) {
            long currentTick = world.getFullTime();
            List<RegionEntry> entries = cache.getAutoSpawnEntries(world.getName());

            for (RegionEntry entry : entries) {
                MobSpawnData data = entry.spawnData();
                String regionId = entry.region().getId();
                String key = world.getName() + ":" + regionId;

                long last = lastSpawnTick.getOrDefault(key, 0L);
                long elapsed = currentTick - last;
                if (elapsed < data.spawnInterval()) {
                    continue; // Interval not yet reached — most common path, no logging.
                }

                // Interval has elapsed — log that we are evaluating this zone.
                debug("[MobSpawn] Zone '" + regionId + "': interval elapsed ("
                        + elapsed + "/" + data.spawnInterval() + " ticks), evaluating...");

                // Check time-of-day and weather conditions BEFORE advancing the timer.
                // If conditions are not met the timer is not reset, so the zone will
                // be re-evaluated on the next scheduler fire rather than waiting a
                // full interval before trying again.
                if (!isValidTime(world, data.spawnTime())) {
                    debug("[MobSpawn] Zone '" + regionId + "': skipped — time restriction '"
                            + data.spawnTime() + "'.");
                    continue;
                }
                if (!isValidWeather(world, data.spawnWeather())) {
                    debug("[MobSpawn] Zone '" + regionId + "': skipped — weather restriction '"
                            + data.spawnWeather() + "'.");
                    continue;
                }

                // Conditions met — advance the timer now to prevent rapid re-spawning.
                lastSpawnTick.put(key, currentTick);

                // Population check
                int current = adapter.countMobsInRegion(world, entry.region(), data.mobTypes());
                int toSpawn = Math.min(data.spawnCount(), data.maxMobs() - current);
                if (toSpawn <= 0) {
                    debug("[MobSpawn] Zone '" + regionId + "': at capacity ("
                            + current + "/" + data.maxMobs() + ") — skipping cycle.");
                    continue;
                }

                debug("[MobSpawn] Zone '" + regionId + "': spawning " + toSpawn
                        + " mob(s) (" + current + "/" + data.maxMobs() + " present).");
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
     * Searches for a safe spawn location inside the region by trying random positions.
     * Chunks are loaded synchronously on demand so that zones spawn even with no
     * nearby players.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Enumerate all chunk columns overlapping the region bounding box.</li>
     *   <li>Pick a random column, then a random (x,z) clamped to the region bbox.</li>
     *   <li>Load the chunk if not already loaded ({@code getChunkAt} is safe on the main thread).</li>
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

        // Collect all chunk columns that overlap the region bounding box.
        // Chunks are loaded on demand (synchronous on the main thread, safe) so that
        // mob zones can spawn even when no player is nearby.
        List<int[]> chunks = new ArrayList<>();
        for (int cx = min.x() >> 4; cx <= max.x() >> 4; cx++) {
            for (int cz = min.z() >> 4; cz <= max.z() >> 4; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }

        ThreadLocalRandom rng = rng();
        for (int attempt = 0; attempt < attempts; attempt++) {
            // Pick a random chunk column, then a random position inside it, clamped to region bounds.
            int[] col = chunks.get(rng.nextInt(chunks.size()));
            int x = Math.max(min.x(), Math.min(max.x(), col[0] * 16 + rng.nextInt(16)));
            int z = Math.max(min.z(), Math.min(max.z(), col[1] * 16 + rng.nextInt(16)));

            // Ensure the chunk is loaded before accessing blocks.
            // getChunkAt() loads it synchronously if needed (disk cache, ~1-5 ms).
            if (!world.isChunkLoaded(col[0], col[1])) {
                world.getChunkAt(col[0], col[1]);
            }

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
        // All attempts exhausted — no valid ground found.
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

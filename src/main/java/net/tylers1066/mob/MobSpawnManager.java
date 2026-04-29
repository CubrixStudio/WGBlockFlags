package net.tylers1066.mob;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.tylers1066.WGBlockFlags;
import net.tylers1066.mob.MobRegionCache.MobSpawnData;
import net.tylers1066.mob.MobRegionCache.RegionEntry;
import net.tylers1066.mob.mythic.MythicAdapter;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
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
 * {@link #trackedMobs} and its zone counter is decremented when the mob is
 * removed (see {@link #recordDeath(UUID)}). No chunk tickets are held and no
 * spatial entity queries are needed for the population cap.
 *
 * <p>Spawned mobs are marked {@code persistent} so Minecraft never despawns them
 * regardless of player distance. Region chunks are forceloaded to ensure mobs
 * remain active and AI-enabled even when no players are nearby.
 *
 * <p>A periodic containment check teleports any loaded mob that has wandered
 * outside its region back to a safe position inside it.
 */
public class MobSpawnManager {

    /** Ticks between scheduler fires (1 second). */
    private static final int TASK_PERIOD_TICKS = 20;

    /** Run containment check every N scheduler fires (N × TASK_PERIOD_TICKS ms). */
    private static final int CONTAINMENT_PERIOD = 4;

    private final WGBlockFlags plugin;
    private final MobRegionCache cache;
    private final MythicAdapter adapter;

    private static ThreadLocalRandom rng() { return ThreadLocalRandom.current(); }

    // ---- Per-zone countdown timer ----
    private final Map<String, Integer> countdown = new HashMap<>();

    // ---- UUID-based population tracking ----
    private final Map<UUID, String>    trackedMobs         = new HashMap<>();
    private final Map<String, Integer> zoneCount           = new HashMap<>();

    // ---- Region + location caches (rebuilt lazily, cleared on stop) ----
    /** Maps zone key → ProtectedRegion; populated lazily in tickInternal(). */
    private final Map<String, ProtectedRegion> zoneRegions          = new HashMap<>();
    /** Last valid spawn location per zone; reused for containment teleports. */
    private final Map<String, Location>        zoneLastSafeLocation = new HashMap<>();
    /** Forceloaded chunks per zone; ensures mobs remain active even without players. */
    private final Map<String, Set<Chunk>>      forceloadedChunks    = new HashMap<>();

    /** Incremented on every scheduler fire for heartbeat logging. */
    private int tickCounter      = 0;
    /** Counts scheduler fires since the last containment check. */
    private int containmentTick  = 0;

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
        // Unforceload all region chunks to free server resources
        for (Set<Chunk> chunks : forceloadedChunks.values()) {
            for (Chunk chunk : chunks) {
                chunk.setForceLoaded(false);
            }
        }
        countdown.clear();
        trackedMobs.clear();
        zoneCount.clear();
        zoneRegions.clear();
        zoneLastSafeLocation.clear();
        forceloadedChunks.clear();
        plugin.getLogger().info("[MobSpawn] Scheduler stopped.");
    }

    // -------------------------------------------------------------------------
    // Public query API
    // -------------------------------------------------------------------------

    /**
     * Returns a snapshot of all active autospawn zones with their current
     * population and configuration. Used by {@code /wgbf mobs}.
     */
    public List<ZoneSummary> getZoneSummaries() {
        List<ZoneSummary> list = new ArrayList<>();
        for (World world : plugin.getServer().getWorlds()) {
            for (RegionEntry entry : cache.getAutoSpawnEntries(world.getName())) {
                String key = world.getName() + ":" + entry.region().getId();
                int current = zoneCount.getOrDefault(key, 0);
                list.add(new ZoneSummary(
                        entry.region().getId(),
                        world.getName(),
                        new ArrayList<>(entry.spawnData().mobTypes()),
                        current,
                        entry.spawnData().maxMobs()
                ));
            }
        }
        return list;
    }

    /** Immutable snapshot of a zone's current population state. */
    public record ZoneSummary(
            String regionId,
            String worldName,
            List<String> mobTypes,
            int current,
            int maxMobs
    ) {}

    // -------------------------------------------------------------------------
    // Population tracking (called by MobZoneDeathListener)
    // -------------------------------------------------------------------------

    /** Returns true if the given UUID belongs to a mob tracked by this manager. */
    public boolean isTracked(java.util.UUID uuid) {
        return trackedMobs.containsKey(uuid);
    }

    void recordSpawn(String zoneKey, UUID uuid) {
        trackedMobs.put(uuid, zoneKey);
        zoneCount.merge(zoneKey, 1, Integer::sum);
    }

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
            int playerCount = plugin.getServer().getOnlinePlayers().size();
            plugin.getLogger().info("[MobSpawn] Scheduler alive — tick #" + tickCounter
                    + " (players: " + playerCount + ")");
        }
        try {
            tickInternal();
            // Containment check runs every CONTAINMENT_PERIOD fires (4 s by default)
            // rather than every tick to reduce getEntity() overhead.
            if (++containmentTick >= CONTAINMENT_PERIOD) {
                containmentTick = 0;
                checkContainment();
                enforcePersistence();  // Also ensure mobs haven't lost persistent flag
            }
        } catch (Throwable e) {
            plugin.getLogger().severe("[MobSpawn] Uncaught exception in spawn tick — scheduler kept alive:");
            e.printStackTrace();
        }
    }

    private void tickInternal() {
        for (World world : plugin.getServer().getWorlds()) {
            for (RegionEntry entry : cache.getAutoSpawnEntries(world.getName())) {
                MobSpawnData data = entry.spawnData();
                String regionId = entry.region().getId();
                String key = world.getName() + ":" + regionId;

                // Register region lazily — only on first encounter after start/reload.
                zoneRegions.computeIfAbsent(key, k -> entry.region());

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

                int current = zoneCount.getOrDefault(key, 0);
                int toSpawn = Math.min(data.spawnCount(), data.maxMobs() - current);
                if (toSpawn <= 0) {
                    debug("[MobSpawn] Zone '" + regionId + "': at capacity ("
                            + current + "/" + data.maxMobs() + ") — skipping cycle.");
                    continue;
                }

                debug("[MobSpawn] Zone '" + regionId + "': spawning " + toSpawn
                        + " mob(s) (" + current + "/" + data.maxMobs() + " present).");
                // Ensure region chunks stay forceloaded so mobs remain active without players.
                ensureChunksForceloaded(world, entry.region(), key);
                spawnBatch(world, entry.region(), data, toSpawn, key);
            }
        }
    }

    /**
     * Checks every currently loaded tracked mob and teleports it back inside its
     * region if it has wandered outside. Runs every {@value #CONTAINMENT_PERIOD}
     * scheduler fires (~4 s) to amortise UUID lookup costs. Mobs in unloaded
     * chunks cannot have moved and are skipped automatically (getEntity returns null).
     */
    private void checkContainment() {
        // Defensive copy: recordDeath() may modify trackedMobs if a teleport
        // triggers EntityRemoveEvent on the main thread.
        for (Map.Entry<UUID, String> mobEntry : new ArrayList<>(trackedMobs.entrySet())) {
            UUID uuid = mobEntry.getKey();
            String zoneKey = mobEntry.getValue();

            Entity entity = plugin.getServer().getEntity(uuid);
            if (entity == null || !entity.isValid()) continue;

            ProtectedRegion region = zoneRegions.get(zoneKey);
            if (region == null) continue;

            Location loc = entity.getLocation();
            boolean outsideRegion = !region.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            boolean inWater = entity.isInWater()
                    || loc.getBlock().getType() == Material.WATER
                    || loc.getBlock().getType() == Material.BUBBLE_COLUMN;

            if (!outsideRegion && !inWater) continue;

            // Mob has left the region or entered water — teleport to cached safe location.
            Location safe = zoneLastSafeLocation.get(zoneKey);
            if (safe == null) {
                int sep = zoneKey.indexOf(':');
                World world = plugin.getServer().getWorld(zoneKey.substring(0, sep));
                if (world == null) continue;
                safe = findSafeLocation(world, region, plugin.getPluginConfig().getMobSpawnAttempts());
                if (safe == null) continue;
                zoneLastSafeLocation.put(zoneKey, safe);
            }

            entity.teleport(safe);
            if (outsideRegion) {
                debug("[MobSpawn] Mob " + uuid + " left region '" + region.getId() + "' — teleported back.");
            } else {
                debug("[MobSpawn] Mob " + uuid + " entered water in region '" + region.getId() + "' — teleported back.");
            }
        }
    }

    /**
     * Periodically ensures that all tracked mobs have the persistent flag set.
     * Some Minecraft mechanics or plugins may unset this flag; resetting it here
     * prevents despawning. Also disables removal-when-far-away for LivingEntities.
     */
    private void enforcePersistence() {
        for (UUID uuid : new ArrayList<>(trackedMobs.keySet())) {
            Entity entity = plugin.getServer().getEntity(uuid);
            if (entity == null || !entity.isValid()) continue;

            // Ensure persistent flag is still set
            if (!entity.isPersistent()) {
                entity.setPersistent(true);
                debug("[MobSpawn] Mob " + uuid + " lost persistent flag — reapplied.");
            }

            // For LivingEntity, also disable removal when far from players
            if (entity instanceof org.bukkit.entity.LivingEntity living) {
                // This prevents removal mechanics that operate independently of persistent flag
                living.setRemoveWhenFarAway(false);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Spawn helpers
    // -------------------------------------------------------------------------

    private void spawnBatch(World world, ProtectedRegion region, MobSpawnData data,
                            int count, String zoneKey) {
        int attempts = plugin.getPluginConfig().getMobSpawnAttempts();
        List<String> types = new ArrayList<>(data.mobTypes());

        for (int i = 0; i < count; i++) {
            Location loc = findSafeLocation(world, region, attempts);
            if (loc == null) break;

            // Cache for containment teleports; overwrite to keep the location fresh.
            zoneLastSafeLocation.put(zoneKey, loc);

            String mobType = types.get(rng().nextInt(types.size()));
            double level = resolveLevel(data.levelMin(), data.levelMax());
            Optional<UUID> result = adapter.spawnMob(mobType, loc, level);

            if (result.isPresent()) {
                recordSpawn(zoneKey, result.get());
                debug("[MobSpawn] Spawned '" + mobType + "' at "
                        + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                        + " in region '" + region.getId() + "'.");
            } else {
                debug("[MobSpawn] FAILED to spawn '" + mobType
                        + "' in region '" + region.getId() + "' — mob name not found in MythicMobs.");
            }
        }
    }

    /**
     * Ensures all chunks within the region are forceloaded so mobs stay active
     * even when no players are nearby. Called before spawning to guarantee chunks
     * remain loaded for AI processing.
     */
    private void ensureChunksForceloaded(World world, ProtectedRegion region, String zoneKey) {
        if (forceloadedChunks.containsKey(zoneKey)) {
            return; // Already forceloaded
        }

        Set<Chunk> chunks = new HashSet<>();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        for (int cx = min.x() >> 4; cx <= max.x() >> 4; cx++) {
            for (int cz = min.z() >> 4; cz <= max.z() >> 4; cz++) {
                Chunk chunk = world.getChunkAt(cx, cz);
                chunk.setForceLoaded(true);
                chunks.add(chunk);
            }
        }

        forceloadedChunks.put(zoneKey, chunks);
        debug("[MobSpawn] Forceloaded " + chunks.size() + " chunks in region '" + region.getId() + "'.");
    }

    /**
     * Searches for a safe spawn location inside the region bounding box.
     *
     * <p>Uses {@link World#getHighestBlockYAt(int, int, HeightMap)} with
     * {@link HeightMap#MOTION_BLOCKING_NO_LEAVES} to skip air columns above the
     * surface instead of scanning from the region's maximum Y down — significantly
     * fewer block queries for tall regions or regions with leaf canopies.
     *
     * @param attempts number of random (x, z) positions to try before giving up
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

            if (!world.isChunkLoaded(col[0], col[1])) {
                world.getChunkAt(col[0], col[1]);
            }

            // Start from the terrain surface rather than the region's top Y,
            // avoiding a scan through potentially hundreds of empty air blocks.
            int highestY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            int startY = Math.min(highestY, max.y() - 1);

            for (int y = startY; y >= min.y(); y--) {
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
        if (!block.isPassable()) return false;
        Material type = block.getType();
        if (Tag.LEAVES.isTagged(type)) return false;
        if (type == Material.COBWEB) return false;
        return true;
    }

    private void debug(String msg) {
        if (plugin.getPluginConfig().isDebugMob()) {
            plugin.getLogger().info(msg);
        }
    }
}

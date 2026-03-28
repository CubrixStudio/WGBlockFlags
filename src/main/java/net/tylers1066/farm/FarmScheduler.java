package net.tylers1066.farm;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.tylers1066.WGBlockFlags;
import net.tylers1066.farm.FarmRegionCache.FarmRegionData;
import net.tylers1066.farm.FarmRegionCache.RegionEntry;
import net.tylers1066.utils.WGUtils;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.*;

/**
 * Drives the auto-grow mechanic for farm regions.
 *
 * <p>Strategy — track-on-demand:
 * <ul>
 *   <li>When a chunk loads, any crop block inside a farm auto-grow region is added
 *       to the tracked set for its chunk.</li>
 *   <li>When a chunk unloads, all tracked blocks for that chunk are removed.</li>
 *   <li>A single BukkitScheduler task fires every tick.  For each tracked block
 *       it checks whether the per-block timer has elapsed; if so it advances growth
 *       by one stage and resets the timer.</li>
 * </ul>
 * Performance: the per-tick work is proportional to the number of tracked crop
 * blocks.  Only loaded chunks are ever processed — unloaded chunks are skipped.
 */
public class FarmScheduler {

    /** Three-dimensional block position inside a specific world. */
    private record BlockPos(String world, int x, int y, int z) {}

    /** Encodes chunk coordinates as a single long for use as a map key. */
    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    private final WGBlockFlags plugin;
    private final FarmRegionCache cache;

    /**
     * Tracked crops, indexed by world then by chunk key.
     * Only blocks whose chunk is loaded at a given tick are processed.
     */
    private final Map<World, Map<Long, Set<BlockPos>>> trackedByChunk = new HashMap<>();

    /** Cached grow interval (ticks) per block, computed at track time. */
    private final Map<BlockPos, Integer> growIntervals = new HashMap<>();

    /** WorldGuard full-time tick at which each block last grew. */
    private final Map<BlockPos, Long> lastGrowTick = new HashMap<>();

    private int taskId = -1;

    public FarmScheduler(WGBlockFlags plugin, FarmRegionCache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Starts the repeating scheduler task. */
    public void start() {
        taskId = plugin.getServer().getScheduler()
                .scheduleSyncRepeatingTask(plugin, this::tick, 0L, 1L);
    }

    /** Stops the task and clears all tracking state. */
    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        trackedByChunk.clear();
        growIntervals.clear();
        lastGrowTick.clear();
    }

    // -------------------------------------------------------------------------
    // Chunk events (called from FarmBreakListener)
    // -------------------------------------------------------------------------

    /**
     * Scans a newly loaded chunk and registers any farm crop blocks found inside
     * auto-grow regions that overlap the chunk.
     */
    public void onChunkLoad(Chunk chunk) {
        World world = chunk.getWorld();
        List<RegionEntry> autoGrowEntries = cache.getAutoGrowEntries(world.getName());
        if (autoGrowEntries.isEmpty()) {
            return;
        }

        int chunkMinX = chunk.getX() * 16;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunk.getZ() * 16;
        int chunkMaxZ = chunkMinZ + 15;

        for (RegionEntry entry : autoGrowEntries) {
            ProtectedRegion region = entry.region();
            FarmRegionData data = entry.data();

            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();

            // Quick bounding-box overlap check
            if (max.x() < chunkMinX || min.x() > chunkMaxX
                    || max.z() < chunkMinZ || min.z() > chunkMaxZ) {
                continue;
            }

            int scanMinX = Math.max(min.x(), chunkMinX);
            int scanMaxX = Math.min(max.x(), chunkMaxX);
            int scanMinZ = Math.max(min.z(), chunkMinZ);
            int scanMaxZ = Math.min(max.z(), chunkMaxZ);
            int scanMinY = min.y();
            int scanMaxY = max.y();

            for (int x = scanMinX; x <= scanMaxX; x++) {
                for (int z = scanMinZ; z <= scanMaxZ; z++) {
                    for (int y = scanMinY; y <= scanMaxY; y++) {
                        if (!region.contains(x, y, z)) {
                            continue;
                        }
                        Block block = world.getBlockAt(x, y, z);
                        if (!CropUtils.isCrop(block)) {
                            continue;
                        }
                        // For vertical crops only track the bottom of the column
                        if (CropUtils.isVerticalCrop(block.getType())
                                && !CropUtils.isVerticalCropBottom(block)) {
                            continue;
                        }
                        // Apply farm-crops filter
                        if (!data.manages(block.getType())) {
                            continue;
                        }
                        trackBlock(block, data.growInterval());
                    }
                }
            }
        }
    }

    /** Removes all tracked blocks that belong to the unloaded chunk. */
    public void onChunkUnload(Chunk chunk) {
        World world = chunk.getWorld();
        Map<Long, Set<BlockPos>> worldMap = trackedByChunk.get(world);
        if (worldMap == null) {
            return;
        }
        long key = chunkKey(chunk.getX(), chunk.getZ());
        Set<BlockPos> removed = worldMap.remove(key);
        if (removed != null) {
            for (BlockPos pos : removed) {
                growIntervals.remove(pos);
                lastGrowTick.remove(pos);
            }
        }
        if (worldMap.isEmpty()) {
            trackedByChunk.remove(world);
        }
    }

    // -------------------------------------------------------------------------
    // Block tracking (called from FarmBreakListener for replanted crops)
    // -------------------------------------------------------------------------

    /**
     * Registers a crop block for auto-grow only if it is inside an active
     * {@code farm-autogrow} region that manages its material type.
     * Does nothing if no such region covers the block.
     */
    public void trackBlock(Block block) {
        ApplicableRegionSet regions = WGUtils.getApplicableRegions(block.getLocation());
        for (ProtectedRegion region : regions.getRegions()) {
            FarmRegionData data = cache.getData(block.getWorld().getName(), region.getId());
            if (data != null && data.autoGrow() && data.manages(block.getType())) {
                trackBlock(block, data.growInterval());
                return;
            }
        }
        // Block is not inside any auto-grow region — do not track.
    }

    /** Removes a crop block from the tracked set (e.g. after it is broken). */
    public void untrackBlock(Block block) {
        BlockPos pos = posOf(block);
        long key = chunkKey(block.getX() >> 4, block.getZ() >> 4);
        Map<Long, Set<BlockPos>> worldMap = trackedByChunk.get(block.getWorld());
        if (worldMap != null) {
            Set<BlockPos> chunk = worldMap.get(key);
            if (chunk != null) {
                chunk.remove(pos);
            }
        }
        growIntervals.remove(pos);
        lastGrowTick.remove(pos);
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    private void tick() {
        for (Map.Entry<World, Map<Long, Set<BlockPos>>> worldEntry : trackedByChunk.entrySet()) {
            World world = worldEntry.getKey();
            long currentTick = world.getFullTime();

            for (Map.Entry<Long, Set<BlockPos>> chunkEntry : worldEntry.getValue().entrySet()) {
                long chunkKey = chunkEntry.getKey();
                int chunkX = (int) (chunkKey & 0xFFFFFFFFL);
                int chunkZ = (int) (chunkKey >> 32);

                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }

                Set<BlockPos> positions = chunkEntry.getValue();
                Iterator<BlockPos> iter = positions.iterator();
                while (iter.hasNext()) {
                    BlockPos pos = iter.next();
                    Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());

                    // Clean up stale entries (block was broken without a break event, physics, etc.)
                    if (!CropUtils.isCrop(block)) {
                        iter.remove();
                        growIntervals.remove(pos);
                        lastGrowTick.remove(pos);
                        continue;
                    }

                    // Skip fully-grown Ageable crops — they are ready for harvest
                    if (CropUtils.isFullyGrown(block)) {
                        continue;
                    }

                    int interval = growIntervals.getOrDefault(pos,
                            plugin.getPluginConfig().getGlobalGrowInterval());
                    long lastTick = lastGrowTick.getOrDefault(pos, 0L);

                    if (currentTick - lastTick >= interval) {
                        CropUtils.advanceGrowth(block);
                        lastGrowTick.put(pos, currentTick);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void trackBlock(Block block, int interval) {
        BlockPos pos = posOf(block);
        long key = chunkKey(block.getX() >> 4, block.getZ() >> 4);
        trackedByChunk
                .computeIfAbsent(block.getWorld(), w -> new HashMap<>())
                .computeIfAbsent(key, k -> new HashSet<>())
                .add(pos);
        growIntervals.put(pos, interval);
        // Initialise the timer so the first grow happens after one full interval.
        lastGrowTick.putIfAbsent(pos, block.getWorld().getFullTime());
    }

    private static BlockPos posOf(Block block) {
        return new BlockPos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }
}

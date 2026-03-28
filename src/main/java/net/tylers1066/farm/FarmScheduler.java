package net.tylers1066.farm;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.tylers1066.WGBlockFlags;
import net.tylers1066.farm.FarmRegionCache.FarmRegionData;
import net.tylers1066.farm.FarmRegionCache.RegionEntry;
import net.tylers1066.utils.WGUtils;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;

/**
 * Drives the auto-grow mechanic for farm regions.
 *
 * <p>Strategy:
 * <ul>
 *   <li>When a chunk loads it is <em>queued</em> for scanning — the actual block scan
 *       is spread over multiple ticks (see {@link #SCANS_PER_TICK}) so reloading a
 *       server with many loaded chunks never freezes the main thread.</li>
 *   <li>Tracked crop blocks are stored in a per-world {@link TreeMap} keyed by their
 *       next scheduled grow tick.  The repeating task only processes entries whose time
 *       has come, giving O(k log n) per tick where k is the number of blocks growing
 *       this tick (usually very small).</li>
 *   <li>Chunk-unload tracking is maintained separately for O(1) cleanup.</li>
 * </ul>
 */
public class FarmScheduler {

    /** Three-dimensional block position inside a specific world. */
    private record BlockPos(String world, int x, int y, int z) {}

    /** Encodes chunk coordinates as a single long for use as a map key. */
    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    /** Maximum number of chunks to scan per tick from the pending queue. */
    private static final int SCANS_PER_TICK = 3;

    private final WGBlockFlags plugin;
    private final FarmRegionCache cache;

    /** Chunk-unload cleanup: World → chunkKey → tracked positions in that chunk. */
    private final Map<World, Map<Long, Set<BlockPos>>> chunkTracking = new HashMap<>();

    /** Per-block grow interval (ticks). */
    private final Map<BlockPos, Integer> growIntervals = new HashMap<>();

    /** The tick at which each block is next scheduled to grow (mirrors its key in growQueues). */
    private final Map<BlockPos, Long> nextGrowTick = new HashMap<>();

    /**
     * Priority grow queue per world: nextGrowTick → set of block positions due at that tick.
     * Only entries whose key ≤ currentTick are processed each tick.
     */
    private final Map<World, TreeMap<Long, Set<BlockPos>>> growQueues = new HashMap<>();

    /** Chunks queued for scanning; processed SCANS_PER_TICK entries per tick. */
    private final Queue<Chunk> pendingScans = new ArrayDeque<>();

    private int taskId = -1;
    private long debugTickCounter = 0;

    public FarmScheduler(WGBlockFlags plugin, FarmRegionCache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void start() {
        taskId = plugin.getServer().getScheduler()
                .scheduleSyncRepeatingTask(plugin, this::tick, 0L, 1L);
        debug("Scheduler started (taskId=" + taskId + ")");
    }

    /** Returns the total number of crop blocks currently tracked. */
    public int getTrackedCount() {
        return growIntervals.size();
    }

    /** Returns the number of chunks still waiting to be scanned. */
    public int getPendingScanCount() {
        return pendingScans.size();
    }

    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        chunkTracking.clear();
        growIntervals.clear();
        nextGrowTick.clear();
        growQueues.clear();
        pendingScans.clear();
    }

    // -------------------------------------------------------------------------
    // Chunk events (called from FarmBreakListener)
    // -------------------------------------------------------------------------

    /**
     * Queues a chunk for scanning.  Returns immediately — the actual block scan
     * happens asynchronously over the next few ticks.
     */
    public void onChunkLoad(Chunk chunk) {
        if (!cache.getAutoGrowEntries(chunk.getWorld().getName()).isEmpty()) {
            pendingScans.add(chunk);
        }
    }

    /** Removes all tracked blocks that belong to the unloaded chunk. */
    public void onChunkUnload(Chunk chunk) {
        World world = chunk.getWorld();
        Map<Long, Set<BlockPos>> worldMap = chunkTracking.get(world);
        if (worldMap == null) return;

        long key = chunkKey(chunk.getX(), chunk.getZ());
        Set<BlockPos> removed = worldMap.remove(key);
        if (removed != null) {
            TreeMap<Long, Set<BlockPos>> queue = growQueues.get(world);
            for (BlockPos pos : removed) {
                Long scheduled = nextGrowTick.remove(pos);
                if (scheduled != null && queue != null) {
                    Set<BlockPos> set = queue.get(scheduled);
                    if (set != null) set.remove(pos);
                }
                growIntervals.remove(pos);
            }
        }
        if (worldMap.isEmpty()) chunkTracking.remove(world);
    }

    // -------------------------------------------------------------------------
    // Block tracking (called from FarmBreakListener for replanted / placed crops)
    // -------------------------------------------------------------------------

    /**
     * Registers a crop block for auto-grow only if it is inside an active
     * {@code farm-autogrow} region that manages its material type.
     */
    public void trackBlock(Block block) {
        ApplicableRegionSet regions = WGUtils.getApplicableRegions(block.getLocation());
        for (ProtectedRegion region : regions.getRegions()) {
            FarmRegionData data = cache.getData(block.getWorld().getName(), region.getId());
            if (data != null && data.autoGrow() && data.manages(block.getType())) {
                trackBlock(block, data.growInterval());
                debug("Tracked " + block.getType() + " at " + block.getX() + "," + block.getY() + "," + block.getZ()
                        + " (region=" + region.getId() + ", interval=" + data.growInterval() + ")");
                return;
            }
            if (data != null) {
                debug("Skip track " + block.getType() + " at " + block.getX() + "," + block.getY() + "," + block.getZ()
                        + " (region=" + region.getId() + ", autoGrow=" + data.autoGrow()
                        + ", manages=" + data.manages(block.getType()) + ")");
            }
        }
        debug("No auto-grow region for " + block.getType() + " at "
                + block.getX() + "," + block.getY() + "," + block.getZ());
    }

    /** Removes a crop block from the tracked set (e.g. after it is broken). */
    public void untrackBlock(Block block) {
        BlockPos pos = posOf(block);
        World world = block.getWorld();

        Long scheduled = nextGrowTick.remove(pos);
        if (scheduled != null) {
            TreeMap<Long, Set<BlockPos>> queue = growQueues.get(world);
            if (queue != null) {
                Set<BlockPos> set = queue.get(scheduled);
                if (set != null) set.remove(pos);
            }
        }
        growIntervals.remove(pos);

        long chunkKeyVal = chunkKey(block.getX() >> 4, block.getZ() >> 4);
        Map<Long, Set<BlockPos>> worldMap = chunkTracking.get(world);
        if (worldMap != null) {
            Set<BlockPos> chunkSet = worldMap.get(chunkKeyVal);
            if (chunkSet != null) chunkSet.remove(pos);
        }
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    private void tick() {
        debugTickCounter++;

        // 1. Process pending chunk scans in small batches to avoid freezing.
        int scanned = 0;
        while (!pendingScans.isEmpty() && scanned < SCANS_PER_TICK) {
            Chunk chunk = pendingScans.poll();
            if (chunk != null && chunk.isLoaded()) {
                doScanChunk(chunk);
                scanned++;
            }
        }

        // 2. Process only blocks whose scheduled grow tick has arrived.
        for (Map.Entry<World, TreeMap<Long, Set<BlockPos>>> worldEntry : growQueues.entrySet()) {
            World world = worldEntry.getKey();
            long currentTick = world.getFullTime();
            TreeMap<Long, Set<BlockPos>> queue = worldEntry.getValue();

            // headMap(currentTick, inclusive=true) → all entries with key ≤ currentTick
            NavigableMap<Long, Set<BlockPos>> due = queue.headMap(currentTick, true);
            if (due.isEmpty()) continue;

            // Copy to list before clearing to avoid ConcurrentModificationException.
            List<Map.Entry<Long, Set<BlockPos>>> batch = new ArrayList<>(due.entrySet());
            due.clear(); // removes processed entries from the underlying TreeMap

            for (Map.Entry<Long, Set<BlockPos>> entry : batch) {
                for (BlockPos pos : entry.getValue()) {
                    nextGrowTick.remove(pos);

                    int interval = growIntervals.getOrDefault(pos,
                            plugin.getPluginConfig().getGlobalGrowInterval());
                    Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());

                    // Clean up stale entries (block broken without an event, physics, etc.)
                    if (!CropUtils.isCrop(block)) {
                        growIntervals.remove(pos);
                        removeFromChunkTracking(world, pos);
                        continue;
                    }

                    if (!CropUtils.isFullyGrown(block)) {
                        debug("Growing " + block.getType() + " at "
                                + pos.x() + "," + pos.y() + "," + pos.z());
                        CropUtils.advanceGrowth(block);
                    }

                    if (CropUtils.isFullyGrown(block)) {
                        // Fully grown — no need to keep tracking.
                        // The replant handler will re-track the block after harvest.
                        growIntervals.remove(pos);
                        removeFromChunkTracking(world, pos);
                    } else {
                        // Schedule next grow cycle.
                        long nextTick = currentTick + interval;
                        nextGrowTick.put(pos, nextTick);
                        queue.computeIfAbsent(nextTick, k -> new HashSet<>()).add(pos);
                    }
                }
            }
        }

        if (plugin.getPluginConfig().isDebug() && debugTickCounter % 200 == 0) {
            debug("Tick #" + debugTickCounter + " — tracking " + getTrackedCount()
                    + " block(s), " + pendingScans.size() + " chunk(s) pending scan");
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Performs the actual block scan for a single chunk. */
    private void doScanChunk(Chunk chunk) {
        World world = chunk.getWorld();
        List<RegionEntry> autoGrowEntries = cache.getAutoGrowEntries(world.getName());

        int chunkMinX = chunk.getX() * 16;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunk.getZ() * 16;
        int chunkMaxZ = chunkMinZ + 15;

        int foundCount = 0;
        for (RegionEntry entry : autoGrowEntries) {
            ProtectedRegion region = entry.region();
            FarmRegionData data = entry.data();

            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();

            if (max.x() < chunkMinX || min.x() > chunkMaxX
                    || max.z() < chunkMinZ || min.z() > chunkMaxZ) {
                continue;
            }

            int scanMinX = Math.max(min.x(), chunkMinX);
            int scanMaxX = Math.min(max.x(), chunkMaxX);
            int scanMinZ = Math.max(min.z(), chunkMinZ);
            int scanMaxZ = Math.min(max.z(), chunkMaxZ);

            for (int x = scanMinX; x <= scanMaxX; x++) {
                for (int z = scanMinZ; z <= scanMaxZ; z++) {
                    for (int y = min.y(); y <= max.y(); y++) {
                        if (!region.contains(x, y, z)) continue;
                        Block block = world.getBlockAt(x, y, z);
                        if (!CropUtils.isCrop(block)) continue;
                        if (CropUtils.isVerticalCrop(block.getType())
                                && !CropUtils.isVerticalCropBottom(block)) continue;
                        if (!data.manages(block.getType())) continue;
                        trackBlock(block, data.growInterval());
                        foundCount++;
                    }
                }
            }
        }
        if (foundCount > 0) {
            debug("Scanned chunk " + world.getName() + " [" + chunk.getX() + "," + chunk.getZ()
                    + "] → tracked " + foundCount + " crop(s)");
        }
    }

    /**
     * Registers a crop block with an explicit interval.
     * No-ops if the block is already tracked (prevents double-scheduling).
     */
    private void trackBlock(Block block, int interval) {
        BlockPos pos = posOf(block);
        if (nextGrowTick.containsKey(pos)) {
            return; // Already scheduled — skip to avoid duplicate queue entries.
        }

        World world = block.getWorld();

        // Chunk-unload tracking
        long chunkKeyVal = chunkKey(block.getX() >> 4, block.getZ() >> 4);
        chunkTracking.computeIfAbsent(world, w -> new HashMap<>())
                     .computeIfAbsent(chunkKeyVal, k -> new HashSet<>())
                     .add(pos);

        // Priority grow queue
        growIntervals.put(pos, interval);
        long nextTick = world.getFullTime() + interval;
        nextGrowTick.put(pos, nextTick);
        growQueues.computeIfAbsent(world, w -> new TreeMap<>())
                  .computeIfAbsent(nextTick, t -> new HashSet<>())
                  .add(pos);
    }

    private void removeFromChunkTracking(World world, BlockPos pos) {
        long chunkKeyVal = chunkKey(pos.x() >> 4, pos.z() >> 4);
        Map<Long, Set<BlockPos>> worldMap = chunkTracking.get(world);
        if (worldMap != null) {
            Set<BlockPos> set = worldMap.get(chunkKeyVal);
            if (set != null) set.remove(pos);
        }
    }

    private static BlockPos posOf(Block block) {
        return new BlockPos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    private void debug(String msg) {
        if (plugin.getPluginConfig().isDebug()) {
            plugin.getLogger().info("[FarmDebug] " + msg);
        }
    }
}

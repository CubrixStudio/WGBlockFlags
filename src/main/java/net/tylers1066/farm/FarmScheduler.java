package net.tylers1066.farm;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.tylers1066.WGBlockFlags;
import net.tylers1066.farm.FarmRegionCache.FarmRegionData;
import net.tylers1066.farm.FarmRegionCache.RegionEntry;
import net.tylers1066.utils.WGUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;

/**
 * Drives the auto-grow mechanic for farm regions.
 *
 * <p>Performance design:
 * <ul>
 *   <li><b>Chunk scan on (re)load</b>: only iterates chunk coordinates within each
 *       farm region's bounding box — never calls {@code world.getLoadedChunks()}.
 *       Scans are queued and processed <em>one per tick</em> to avoid main-thread
 *       freezes on large farms.</li>
 *   <li><b>Chunk deduplication</b>: a chunk covered by multiple overlapping regions
 *       is queued and scanned only once.</li>
 *   <li><b>Grow scheduling</b>: tracked blocks are stored in a per-world
 *       {@link TreeMap} keyed by their next grow tick.  Each tick only dequeues
 *       blocks that are actually due — O(k log n) instead of O(n).</li>
 * </ul>
 */
public class FarmScheduler {

    /** Three-dimensional block position inside a specific world. */
    private record BlockPos(String world, int x, int y, int z) {}

    /** Encodes chunk coordinates as a single long map key. */
    private static long chunkKey(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | ((long) cz << 32);
    }

    private final WGBlockFlags plugin;
    private final FarmRegionCache cache;

    /** Chunk-unload cleanup: World → chunkKey → tracked positions in that chunk. */
    private final Map<World, Map<Long, Set<BlockPos>>> chunkTracking = new HashMap<>();

    /** Per-block grow interval (ticks). */
    private final Map<BlockPos, Integer> growIntervals = new HashMap<>();

    /** BlockPos → the TreeMap key it is stored under, for O(1) removal. */
    private final Map<BlockPos, Long> nextGrowTick = new HashMap<>();

    /** Priority grow queue per world: nextGrowTick → blocks due at that tick. */
    private final Map<World, TreeMap<Long, Set<BlockPos>>> growQueues = new HashMap<>();

    /** Ordered queue of chunks waiting to be scanned. */
    private final Queue<Chunk> pendingScans = new ArrayDeque<>();

    /**
     * Deduplication guard: tracks which chunk keys are already in {@link #pendingScans}
     * per world so we never queue the same chunk twice.
     */
    private final Map<World, Set<Long>> queuedChunkKeys = new HashMap<>();

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
        queuedChunkKeys.clear();
    }

    // -------------------------------------------------------------------------
    // Startup / reload scan
    // -------------------------------------------------------------------------

    /**
     * Queues all currently loaded chunks that fall within any auto-grow region.
     * Only iterates chunk coordinates inside region bounding boxes —
     * avoids the expensive {@code world.getLoadedChunks()} call entirely.
     * Call this after {@link #start()} during enable/reload.
     */
    public void queueLoadedChunksInRegions() {
        for (World world : Bukkit.getWorlds()) {
            List<RegionEntry> entries = cache.getAutoGrowEntries(world.getName());
            for (RegionEntry entry : entries) {
                BlockVector3 min = entry.region().getMinimumPoint();
                BlockVector3 max = entry.region().getMaximumPoint();
                int minCX = min.x() >> 4;
                int maxCX = max.x() >> 4;
                int minCZ = min.z() >> 4;
                int maxCZ = max.z() >> 4;
                for (int cx = minCX; cx <= maxCX; cx++) {
                    for (int cz = minCZ; cz <= maxCZ; cz++) {
                        if (world.isChunkLoaded(cx, cz)) {
                            enqueueChunk(world.getChunkAt(cx, cz));
                        }
                    }
                }
            }
        }
        debug("queueLoadedChunksInRegions: " + pendingScans.size() + " chunk(s) queued");
    }

    // -------------------------------------------------------------------------
    // Chunk events (called from FarmBreakListener)
    // -------------------------------------------------------------------------

    /**
     * Queues a chunk for scanning.  Returns immediately — the actual block scan
     * happens one tick at a time.  Duplicate queuing is suppressed.
     */
    public void onChunkLoad(Chunk chunk) {
        enqueueChunk(chunk);
    }

    /** Removes all tracked blocks that belong to the unloaded chunk. */
    public void onChunkUnload(Chunk chunk) {
        World world = chunk.getWorld();
        long key = chunkKey(chunk.getX(), chunk.getZ());

        // Remove from deduplication guard (so the chunk can be re-queued if it reloads)
        Set<Long> queued = queuedChunkKeys.get(world);
        if (queued != null) queued.remove(key);

        Map<Long, Set<BlockPos>> worldMap = chunkTracking.get(world);
        if (worldMap == null) return;

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
                debug("Tracked " + block.getType() + " at "
                        + block.getX() + "," + block.getY() + "," + block.getZ()
                        + " (region=" + region.getId() + ", interval=" + data.growInterval() + ")");
                return;
            }
            if (data != null) {
                debug("Skip track " + block.getType() + " at "
                        + block.getX() + "," + block.getY() + "," + block.getZ()
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

        long ck = chunkKey(block.getX() >> 4, block.getZ() >> 4);
        Map<Long, Set<BlockPos>> worldMap = chunkTracking.get(world);
        if (worldMap != null) {
            Set<BlockPos> chunkSet = worldMap.get(ck);
            if (chunkSet != null) chunkSet.remove(pos);
        }
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    private void tick() {
        debugTickCounter++;

        // 1. Process ONE pending chunk scan per tick to bound per-tick cost.
        if (!pendingScans.isEmpty()) {
            Chunk chunk = pendingScans.poll();
            if (chunk != null) {
                // Remove from dedup guard so it can be re-queued after a future chunk reload.
                Set<Long> queued = queuedChunkKeys.get(chunk.getWorld());
                if (queued != null) queued.remove(chunkKey(chunk.getX(), chunk.getZ()));

                if (chunk.isLoaded()) {
                    doScanChunk(chunk);
                }
            }
        }

        // 2. Process only blocks whose scheduled grow tick has arrived (O(k log n)).
        for (Map.Entry<World, TreeMap<Long, Set<BlockPos>>> worldEntry : growQueues.entrySet()) {
            World world = worldEntry.getKey();
            long currentTick = world.getFullTime();
            TreeMap<Long, Set<BlockPos>> queue = worldEntry.getValue();

            NavigableMap<Long, Set<BlockPos>> due = queue.headMap(currentTick, true);
            if (due.isEmpty()) continue;

            List<Map.Entry<Long, Set<BlockPos>>> batch = new ArrayList<>(due.entrySet());
            due.clear();

            for (Map.Entry<Long, Set<BlockPos>> entry : batch) {
                for (BlockPos pos : entry.getValue()) {
                    nextGrowTick.remove(pos);

                    int interval = growIntervals.getOrDefault(pos,
                            plugin.getPluginConfig().getGlobalGrowInterval());
                    Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());

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
                        // Fully grown — untrack; replant handler will re-track after harvest.
                        growIntervals.remove(pos);
                        removeFromChunkTracking(world, pos);
                    } else {
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

    /** Adds a chunk to the scan queue, suppressing duplicate entries. */
    private void enqueueChunk(Chunk chunk) {
        if (cache.getAutoGrowEntries(chunk.getWorld().getName()).isEmpty()) return;
        World world = chunk.getWorld();
        long key = chunkKey(chunk.getX(), chunk.getZ());
        if (queuedChunkKeys.computeIfAbsent(world, w -> new HashSet<>()).add(key)) {
            pendingScans.add(chunk);
        }
    }

    /** Scans a single chunk and registers crop blocks found inside auto-grow regions. */
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
            debug("Scanned chunk " + world.getName() + " ["
                    + chunk.getX() + "," + chunk.getZ() + "] → tracked " + foundCount + " crop(s)");
        }
    }

    /** Registers a block for growth scheduling; no-ops if already tracked. */
    private void trackBlock(Block block, int interval) {
        BlockPos pos = posOf(block);
        if (nextGrowTick.containsKey(pos)) return; // Already scheduled

        World world = block.getWorld();
        long ck = chunkKey(block.getX() >> 4, block.getZ() >> 4);
        chunkTracking.computeIfAbsent(world, w -> new HashMap<>())
                     .computeIfAbsent(ck, k -> new HashSet<>())
                     .add(pos);

        growIntervals.put(pos, interval);
        long nextTick = world.getFullTime() + interval;
        nextGrowTick.put(pos, nextTick);
        growQueues.computeIfAbsent(world, w -> new TreeMap<>())
                  .computeIfAbsent(nextTick, t -> new HashSet<>())
                  .add(pos);
    }

    private void removeFromChunkTracking(World world, BlockPos pos) {
        long ck = chunkKey(pos.x() >> 4, pos.z() >> 4);
        Map<Long, Set<BlockPos>> worldMap = chunkTracking.get(world);
        if (worldMap != null) {
            Set<BlockPos> set = worldMap.get(ck);
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

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
 * <p>Key design decisions:
 * <ul>
 *   <li><b>Clock</b>: uses an internal {@link #tickCounter} that increments once
 *       per task fire, completely independent of {@code world.getFullTime()} and all
 *       Minecraft gamerules (including {@code randomTickSpeed 0}).</li>
 *   <li><b>Grow queue</b>: single global {@link TreeMap}&lt;Long, Set&lt;BlockPos&gt;&gt;
 *       keyed by scheduled grow tick — O(k log n) per tick, k ≈ 0 most ticks.</li>
 *   <li><b>Chunk scan</b>: only iterates chunk coords within farm region bounding
 *       boxes; never calls {@code world.getLoadedChunks()}.  One chunk scanned per
 *       tick to avoid main-thread spikes on reload.</li>
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

    /**
     * Internal tick counter — increments once per task execution regardless of
     * any Minecraft gamerule or world-time setting.
     */
    private long tickCounter = 0;

    /** Global priority grow queue: scheduledTick → blocks due at that tick. */
    private final TreeMap<Long, Set<BlockPos>> growQueue = new TreeMap<>();

    /** BlockPos → the growQueue key it is stored under (for O(1) removal). */
    private final Map<BlockPos, Long> nextGrowTick = new HashMap<>();

    /** Per-block grow interval (ticks). */
    private final Map<BlockPos, Integer> growIntervals = new HashMap<>();

    /** Per-block time-of-day restriction ("any", "day", "night"). */
    private final Map<BlockPos, String> activeTimeMap = new HashMap<>();

    /** Per-block weather restriction ("any", "clear", "rain"). */
    private final Map<BlockPos, String> activeWeatherMap = new HashMap<>();

    /** Per-block max height override for vertical crops (0 = use crop defaults). */
    private final Map<BlockPos, Integer> maxHeightMap = new HashMap<>();

    /** Chunk-unload cleanup: worldName → chunkKey → tracked positions. */
    private final Map<String, Map<Long, Set<BlockPos>>> chunkTracking = new HashMap<>();

    /** Ordered queue of chunks waiting to be scanned (one per tick). */
    private final Queue<Chunk> pendingScans = new ArrayDeque<>();

    /**
     * Deduplication: chunk keys already in {@link #pendingScans}, per world name.
     * Prevents the same chunk from being queued or scanned twice.
     */
    private final Map<String, Set<Long>> queuedChunkKeys = new HashMap<>();

    private int taskId = -1;

    public FarmScheduler(WGBlockFlags plugin, FarmRegionCache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void start() {
        tickCounter = 0;
        taskId = plugin.getServer().getScheduler()
                .scheduleSyncRepeatingTask(plugin, this::tick, 0L, 1L);
        debug("Scheduler started (taskId=" + taskId + ")");
    }

    /** Returns the total number of crop blocks currently scheduled for growth. */
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
        growQueue.clear();
        nextGrowTick.clear();
        growIntervals.clear();
        activeTimeMap.clear();
        activeWeatherMap.clear();
        maxHeightMap.clear();
        chunkTracking.clear();
        pendingScans.clear();
        queuedChunkKeys.clear();
        tickCounter = 0;
    }

    // -------------------------------------------------------------------------
    // Startup / reload scan
    // -------------------------------------------------------------------------

    /**
     * Queues all currently loaded chunks that fall within any auto-grow region.
     * Uses region bounding boxes to avoid the expensive {@code world.getLoadedChunks()}.
     */
    public void queueLoadedChunksInRegions() {
        for (World world : Bukkit.getWorlds()) {
            List<RegionEntry> entries = cache.getAutoGrowEntries(world.getName());
            for (RegionEntry entry : entries) {
                BlockVector3 min = entry.region().getMinimumPoint();
                BlockVector3 max = entry.region().getMaximumPoint();
                for (int cx = min.x() >> 4; cx <= max.x() >> 4; cx++) {
                    for (int cz = min.z() >> 4; cz <= max.z() >> 4; cz++) {
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

    /** Queues a chunk for scanning (non-blocking). */
    public void onChunkLoad(Chunk chunk) {
        enqueueChunk(chunk);
    }

    /** Removes all tracked blocks in the unloaded chunk. */
    public void onChunkUnload(Chunk chunk) {
        String worldName = chunk.getWorld().getName();
        long key = chunkKey(chunk.getX(), chunk.getZ());

        // Allow re-queue after the chunk reloads.
        Set<Long> queued = queuedChunkKeys.get(worldName);
        if (queued != null) queued.remove(key);

        Map<Long, Set<BlockPos>> worldMap = chunkTracking.get(worldName);
        if (worldMap == null) return;

        Set<BlockPos> removed = worldMap.remove(key);
        if (removed != null) {
            for (BlockPos pos : removed) {
                Long scheduled = nextGrowTick.remove(pos);
                if (scheduled != null) {
                    Set<BlockPos> set = growQueue.get(scheduled);
                    if (set != null) set.remove(pos);
                }
                growIntervals.remove(pos);
                activeTimeMap.remove(pos);
                activeWeatherMap.remove(pos);
                maxHeightMap.remove(pos);
            }
        }
        if (worldMap.isEmpty()) chunkTracking.remove(worldName);
    }

    // -------------------------------------------------------------------------
    // Block tracking
    // -------------------------------------------------------------------------

    /**
     * Registers a crop block for auto-grow if covered by an active
     * {@code farm-autogrow} region that manages its material type.
     */
    public void trackBlock(Block block) {
        ApplicableRegionSet regions = WGUtils.getApplicableRegions(block.getLocation());
        for (ProtectedRegion region : regions.getRegions()) {
            FarmRegionData data = cache.getData(block.getWorld().getName(), region.getId());
            if (data != null && data.autoGrow() && data.manages(block.getType())) {
                trackBlock(block, data.growInterval(), data.activeTime(), data.activeWeather(), data.maxHeight());
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

    /** Removes a crop block from tracking (e.g. after it is broken). */
    public void untrackBlock(Block block) {
        BlockPos pos = posOf(block);

        Long scheduled = nextGrowTick.remove(pos);
        if (scheduled != null) {
            Set<BlockPos> set = growQueue.get(scheduled);
            if (set != null) set.remove(pos);
        }
        growIntervals.remove(pos);
        activeTimeMap.remove(pos);
        activeWeatherMap.remove(pos);
        maxHeightMap.remove(pos);

        String worldName = block.getWorld().getName();
        long ck = chunkKey(block.getX() >> 4, block.getZ() >> 4);
        Map<Long, Set<BlockPos>> worldMap = chunkTracking.get(worldName);
        if (worldMap != null) {
            Set<BlockPos> chunkSet = worldMap.get(ck);
            if (chunkSet != null) chunkSet.remove(pos);
        }
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    private void tick() {
        tickCounter++;

        // 1. Scan one pending chunk per tick (spreads reload cost evenly).
        if (!pendingScans.isEmpty()) {
            Chunk chunk = pendingScans.poll();
            if (chunk != null) {
                Set<Long> queued = queuedChunkKeys.get(chunk.getWorld().getName());
                if (queued != null) queued.remove(chunkKey(chunk.getX(), chunk.getZ()));
                if (chunk.isLoaded()) {
                    doScanChunk(chunk);
                }
            }
        }

        // 2. Grow all blocks whose scheduled tick has arrived.
        //    headMap(tickCounter, true) → entries with key ≤ tickCounter.
        NavigableMap<Long, Set<BlockPos>> due = growQueue.headMap(tickCounter, true);
        if (!due.isEmpty()) {
            // Snapshot before clearing so we can safely modify growQueue inside the loop.
            List<Map.Entry<Long, Set<BlockPos>>> batch = new ArrayList<>(due.entrySet());
            due.clear();

            for (Map.Entry<Long, Set<BlockPos>> entry : batch) {
                for (BlockPos pos : entry.getValue()) {
                    nextGrowTick.remove(pos);

                    int interval = growIntervals.getOrDefault(pos,
                            plugin.getPluginConfig().getGlobalGrowInterval());

                    World world = Bukkit.getWorld(pos.world());
                    if (world == null) {
                        growIntervals.remove(pos);
                        removeFromChunkTracking(pos.world(), pos);
                        continue;
                    }

                    Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());

                    // Clean up stale entries.
                    if (!CropUtils.isCrop(block)) {
                        growIntervals.remove(pos);
                        activeTimeMap.remove(pos);
                        activeWeatherMap.remove(pos);
                        maxHeightMap.remove(pos);
                        removeFromChunkTracking(pos.world(), pos);
                        continue;
                    }

                    // Time-of-day and weather condition checks.
                    String activeTime = activeTimeMap.getOrDefault(pos, "any");
                    String activeWeather = activeWeatherMap.getOrDefault(pos, "any");
                    if (!isValidTime(world, activeTime) || !isValidWeather(world, activeWeather)) {
                        // Conditions not met — reschedule without growing.
                        long nextTick = tickCounter + interval;
                        nextGrowTick.put(pos, nextTick);
                        growQueue.computeIfAbsent(nextTick, k -> new HashSet<>()).add(pos);
                        continue;
                    }

                    // Advance growth if not already fully grown.
                    if (!CropUtils.isFullyGrown(block)) {
                        debug("Growing " + block.getType() + " at "
                                + pos.x() + "," + pos.y() + "," + pos.z());
                        int maxHeight = maxHeightMap.getOrDefault(pos, 0);
                        CropUtils.advanceGrowth(block, maxHeight);
                    }

                    // Re-check after growth.
                    if (CropUtils.isFullyGrown(block)) {
                        // Fully grown — untrack; replant handler re-tracks after harvest.
                        growIntervals.remove(pos);
                        activeTimeMap.remove(pos);
                        activeWeatherMap.remove(pos);
                        maxHeightMap.remove(pos);
                        removeFromChunkTracking(pos.world(), pos);
                    } else {
                        // Schedule next grow cycle.
                        long nextTick = tickCounter + interval;
                        nextGrowTick.put(pos, nextTick);
                        growQueue.computeIfAbsent(nextTick, k -> new HashSet<>()).add(pos);
                    }
                }
            }
        }

        if (plugin.getPluginConfig().isDebug() && tickCounter % 200 == 0) {
            debug("Tick #" + tickCounter + " — tracking " + getTrackedCount()
                    + " block(s), " + pendingScans.size() + " chunk(s) pending scan");
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void enqueueChunk(Chunk chunk) {
        if (cache.getAutoGrowEntries(chunk.getWorld().getName()).isEmpty()) return;
        String worldName = chunk.getWorld().getName();
        long key = chunkKey(chunk.getX(), chunk.getZ());
        if (queuedChunkKeys.computeIfAbsent(worldName, w -> new HashSet<>()).add(key)) {
            pendingScans.add(chunk);
        }
    }

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
                        trackBlock(block, data.growInterval(), data.activeTime(), data.activeWeather(), data.maxHeight());
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

    /** Registers a block; no-ops if already scheduled. */
    private void trackBlock(Block block, int interval, String activeTime, String activeWeather, int maxHeight) {
        BlockPos pos = posOf(block);
        if (nextGrowTick.containsKey(pos)) return;

        String worldName = block.getWorld().getName();
        long ck = chunkKey(block.getX() >> 4, block.getZ() >> 4);
        chunkTracking.computeIfAbsent(worldName, w -> new HashMap<>())
                     .computeIfAbsent(ck, k -> new HashSet<>())
                     .add(pos);

        growIntervals.put(pos, interval);
        activeTimeMap.put(pos, activeTime);
        activeWeatherMap.put(pos, activeWeather);
        maxHeightMap.put(pos, maxHeight);
        long nextTick = tickCounter + interval;
        nextGrowTick.put(pos, nextTick);
        growQueue.computeIfAbsent(nextTick, k -> new HashSet<>()).add(pos);
    }

    private boolean isValidTime(World world, String activeTime) {
        return switch (activeTime) {
            case "day" -> world.isDayTime();
            case "night" -> !world.isDayTime();
            default -> true;
        };
    }

    private boolean isValidWeather(World world, String activeWeather) {
        return switch (activeWeather) {
            case "clear" -> !world.hasStorm() && !world.isThundering();
            case "rain" -> world.hasStorm() || world.isThundering();
            default -> true;
        };
    }

    private void removeFromChunkTracking(String worldName, BlockPos pos) {
        long ck = chunkKey(pos.x() >> 4, pos.z() >> 4);
        Map<Long, Set<BlockPos>> worldMap = chunkTracking.get(worldName);
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

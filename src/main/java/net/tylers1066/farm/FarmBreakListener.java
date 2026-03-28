package net.tylers1066.farm;

import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.tylers1066.WGBlockFlags;
import net.tylers1066.farm.FarmRegionCache.FarmRegionData;
import net.tylers1066.utils.WGUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Handles auto-replant and delegates chunk load/unload events to {@link FarmScheduler}.
 *
 * <p>Auto-replant fires on {@link BlockBreakEvent} at {@code MONITOR} priority so it
 * sees only events that were not cancelled.  A 1-tick delay ensures the block has
 * been fully removed from the world before we attempt to place the new crop.
 */
public class FarmBreakListener implements Listener {

    private final WGBlockFlags plugin;
    private final FarmRegionCache cache;
    private final FarmScheduler scheduler;

    public FarmBreakListener(WGBlockFlags plugin, FarmRegionCache cache, FarmScheduler scheduler) {
        this.plugin = plugin;
        this.cache = cache;
        this.scheduler = scheduler;
    }

    // -------------------------------------------------------------------------
    // Crop break protection
    // -------------------------------------------------------------------------

    /**
     * Prevents players from breaking non-mature crops inside regions that have
     * {@code farm-protect-crops=allow}.  Fires at NORMAL priority so it runs
     * before protection plugins that run at HIGH/HIGHEST, and before the
     * auto-replant handler at MONITOR.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreakProtect(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();

        if (!CropUtils.isCrop(block)) {
            return;
        }
        // Fully-grown crops may always be harvested.
        if (CropUtils.isFullyGrown(block)) {
            return;
        }

        ApplicableRegionSet regions = WGUtils.getApplicableRegions(block.getLocation());
        for (ProtectedRegion region : regions.getRegions()) {
            FarmRegionData data = cache.getData(block.getWorld().getName(), region.getId());
            if (data != null && data.protectCrops() && data.manages(type)) {
                // Players with the bypass permission can always harvest immature crops.
                if (event.getPlayer() != null
                        && event.getPlayer().hasPermission("wgblockflags.farm.harvest")) {
                    return;
                }
                event.setCancelled(true);
                if (event.getPlayer() != null) {
                    event.getPlayer().sendMessage(
                            net.kyori.adventure.text.Component.text(
                                    "§cVous ne pouvez pas casser une plantation qui n'est pas encore mûre."));
                }
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Auto-replant
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();

        // Only handle Ageable crops — vertical crops have no well-defined "replant" seed.
        if (!CropUtils.AGEABLE_CROPS.contains(type)) {
            return;
        }
        // Verify the block has valid soil support for replanting.
        if (!CropUtils.canReplant(block)) {
            return;
        }
        // Respect the "replant-only-mature" setting.
        if (plugin.getPluginConfig().isReplantOnlyMature() && !CropUtils.isFullyGrown(block)) {
            return;
        }

        // Check whether any applicable WG region has farm-autoreplant active for this crop.
        ApplicableRegionSet regions = WGUtils.getApplicableRegions(block.getLocation());
        FarmRegionData farmData = resolveFarmReplantData(regions, block.getWorld().getName(), type);
        if (farmData == null) {
            return;
        }

        // Optionally suppress item drops so seeds don't accumulate.
        if (plugin.getPluginConfig().isSuppressDropsOnReplant()) {
            event.setDropItems(false);
        }

        // Untrack the block now (it will no longer be a crop after the event resolves).
        scheduler.untrackBlock(block);

        // Snapshot everything we need; the block object will be stale in the delayed task.
        Location loc = block.getLocation().clone();
        Material cropType = type;

        // 1-tick delay: the block state is AIR only after the break event has fully resolved.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Block target = loc.getBlock();
            if (target.getType() != Material.AIR) {
                // Something was placed there in the meantime — skip.
                return;
            }
            CropUtils.replant(target, cropType);
            scheduler.trackBlock(target);
        }, 1L);
    }

    // -------------------------------------------------------------------------
    // Block-place → start tracking immediately (chunk may already be loaded)
    // -------------------------------------------------------------------------

    /**
     * When a player plants a crop in an already-loaded chunk the {@code ChunkLoadEvent}
     * never fires for that chunk again, so the new block would not be picked up by the
     * scheduler until the chunk reloads.  This handler closes that gap by tracking the
     * newly placed crop right away if it falls inside an auto-grow region.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!CropUtils.isCrop(block)) {
            return;
        }
        // For vertical crops only track the base of the column.
        if (CropUtils.isVerticalCrop(block.getType()) && !CropUtils.isVerticalCropBottom(block)) {
            return;
        }
        scheduler.trackBlock(block);
    }

    // -------------------------------------------------------------------------
    // Chunk events → delegate to FarmScheduler
    // -------------------------------------------------------------------------

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        scheduler.onChunkLoad(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        scheduler.onChunkUnload(event.getChunk());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Searches the applicable region set for a farm region that has
     * {@code auto-replant} enabled and manages the given crop type.
     *
     * @return the first matching {@link FarmRegionData}, or {@code null} if none found
     */
    private FarmRegionData resolveFarmReplantData(ApplicableRegionSet regions,
                                                   String worldName, Material cropType) {
        for (ProtectedRegion region : regions.getRegions()) {
            FarmRegionData data = cache.getData(worldName, region.getId());
            if (data != null && data.autoReplant() && data.manages(cropType)) {
                return data;
            }
        }
        return null;
    }
}

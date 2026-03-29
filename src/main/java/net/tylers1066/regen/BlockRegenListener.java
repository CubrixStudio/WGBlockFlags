package net.tylers1066.regen;

import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.tylers1066.WGBlockFlags;
import net.tylers1066.regen.BlockRegenCache.BlockRegenData;
import net.tylers1066.utils.WGUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashSet;
import java.util.Set;

public class BlockRegenListener implements Listener {

    private final WGBlockFlags plugin;
    private final BlockRegenCache cache;

    /**
     * Locations of blocks placed by players — these are excluded from regen tracking
     * so that player-placed blocks don't regenerate if broken.
     */
    private final Set<Location> playerPlacedBlocks = new HashSet<>();

    public BlockRegenListener(WGBlockFlags plugin, BlockRegenCache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();

        // Skip blocks originally placed by players.
        if (playerPlacedBlocks.remove(loc)) {
            return;
        }

        Material brokenType = block.getType();
        BlockData brokenData = block.getBlockData();

        ApplicableRegionSet regions = WGUtils.getApplicableRegions(loc);
        for (ProtectedRegion region : regions.getRegions()) {
            BlockRegenData data = cache.getData(loc.getWorld().getName(), region.getId());
            if (data == null || !data.manages(brokenType)) {
                continue;
            }

            // Schedule regeneration after the configured delay.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Block target = loc.getWorld().getBlockAt(loc);
                if (target.getType() == Material.AIR) {
                    target.setBlockData(brokenData, false);
                }
            }, data.regenDelay());
            return; // highest-priority region already handled
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // Mark this block as player-placed so it won't regenerate if broken.
        playerPlacedBlocks.add(event.getBlock().getLocation());
    }

    /** Clears the player-placed set (called on module disable/reload). */
    public void clear() {
        playerPlacedBlocks.clear();
    }
}

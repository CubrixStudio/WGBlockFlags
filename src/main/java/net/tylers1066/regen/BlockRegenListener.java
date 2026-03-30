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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BlockRegenListener implements Listener {

    private final WGBlockFlags plugin;
    private final BlockRegenCache cache;

    /**
     * Blocks placed by players — keyed by {@code worldName → Set<encodedBlockPos>} so that
     * player-placed blocks don't regenerate when broken.
     *
     * <p>Using a packed long (x, y, z encoded into 64 bits) avoids the overhead of mutable
     * {@link Location} objects as {@code HashSet} keys.
     */
    private final Map<String, Set<Long>> playerPlaced = new HashMap<>();

    /** Packs block coordinates into a single long key. */
    private static long blockKey(int x, int y, int z) {
        // x: 26 bits (±33M), y: 12 bits (±2048), z: 26 bits — fits in 64 bits.
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    public BlockRegenListener(WGBlockFlags plugin, BlockRegenCache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String worldName = block.getWorld().getName();
        long key = blockKey(block.getX(), block.getY(), block.getZ());

        // Skip blocks originally placed by players.
        Set<Long> worldPlaced = playerPlaced.get(worldName);
        if (worldPlaced != null && worldPlaced.remove(key)) {
            return;
        }

        Material brokenType = block.getType();
        BlockData brokenData = block.getBlockData();
        Location loc = block.getLocation();

        ApplicableRegionSet regions = WGUtils.getApplicableRegions(loc);
        for (ProtectedRegion region : regions.getRegions()) {
            BlockRegenData data = cache.getData(worldName, region.getId());
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
        Block block = event.getBlock();
        // Mark this block as player-placed so it won't regenerate if broken.
        playerPlaced.computeIfAbsent(block.getWorld().getName(), w -> new HashSet<>())
                    .add(blockKey(block.getX(), block.getY(), block.getZ()));
    }

    /** Clears the player-placed set (called on module disable/reload). */
    public void clear() {
        playerPlaced.clear();
    }
}

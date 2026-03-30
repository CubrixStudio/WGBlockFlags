package net.tylers1066.regen;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.tylers1066.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BlockRegenCache {

    /**
     * Immutable snapshot of a region's block-regen configuration.
     *
     * @param regenDelay  ticks before a broken block regenerates
     * @param materials   materials that regen; empty = all materials
     */
    public record BlockRegenData(int regenDelay, Set<Material> materials) {
        /** Returns true if the given material should regenerate in this region. */
        public boolean manages(Material material) {
            return materials.isEmpty() || materials.contains(material);
        }
    }

    /** A region paired with its regen data. */
    public record RegionEntry(ProtectedRegion region, BlockRegenData data) {}

    // world name → (region id → entry)
    private final Map<String, Map<String, RegionEntry>> cache = new HashMap<>();

    /**
     * Rebuilds the cache from current WorldGuard region flags.
     * Must be called on the main thread.
     */
    public void rebuild(PluginConfig config) {
        cache.clear();
        for (World world : Bukkit.getWorlds()) {
            RegionManager rm = WorldGuard.getInstance().getPlatform()
                    .getRegionContainer().get(BukkitAdapter.adapt(world));
            if (rm == null) {
                continue;
            }
            Map<String, RegionEntry> worldCache = new HashMap<>();
            cache.put(world.getName(), worldCache);

            for (Map.Entry<String, ProtectedRegion> entry : rm.getRegions().entrySet()) {
                ProtectedRegion region = entry.getValue();
                BlockRegenData data = buildData(region, config);
                if (data != null) {
                    worldCache.put(entry.getKey(), new RegionEntry(region, data));
                }
            }
        }
    }

    /**
     * Returns the regen data for the given region, or {@code null} if regen is not active.
     */
    public @Nullable BlockRegenData getData(String worldName, String regionId) {
        Map<String, RegionEntry> worldCache = cache.get(worldName);
        if (worldCache == null) return null;
        RegionEntry entry = worldCache.get(regionId);
        return entry != null ? entry.data() : null;
    }

    /**
     * Returns all regen-enabled region entries for the given world.
     */
    public List<RegionEntry> getRegenEntries(String worldName) {
        Map<String, RegionEntry> worldCache = cache.get(worldName);
        if (worldCache == null) return Collections.emptyList();
        return new ArrayList<>(worldCache.values());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private @Nullable BlockRegenData buildData(ProtectedRegion region, PluginConfig config) {
        StateFlag.State state = region.getFlag(BlockRegenFlags.BLOCK_REGEN);
        if (state != StateFlag.State.ALLOW) {
            return null;
        }

        Integer delayFlag = region.getFlag(BlockRegenFlags.BLOCK_REGEN_DELAY);
        int delay = (delayFlag != null && delayFlag > 0) ? delayFlag : config.getBlockRegenDefaultDelay();

        Set<Material> rawMaterials = region.getFlag(BlockRegenFlags.BLOCK_REGEN_MATERIALS);
        Set<Material> materials = rawMaterials != null
                ? Collections.unmodifiableSet(new HashSet<>(rawMaterials))
                : Collections.emptySet();

        return new BlockRegenData(delay, materials);
    }
}

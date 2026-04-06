package net.tylers1066.mob.mythic;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * MythicMobs 5.x (Lumine) adapter.
 *
 * <p>All MythicMobs API calls are confined to this class, so that the rest of the
 * plugin compiles cleanly when MythicMobs is absent at runtime.
 *
 * <p>Mob name matching is normalised: comparison is case-insensitive and treats
 * hyphens and underscores as equivalent, so {@code bear-polar}, {@code bear_polar}
 * and {@code Bear_Polar} all refer to the same MythicMobs mob.
 */
public class MythicV5Adapter implements MythicAdapter {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<UUID> spawnMob(@NotNull String mobName, @NotNull Location location, double level) {
        Optional<MythicMob> mythicMob = resolveMythicMob(mobName);
        if (mythicMob.isEmpty()) {
            return Optional.empty();
        }
        try {
            ActiveMob active = mythicMob.get().spawn(BukkitAdapter.adapt(location), level);
            return Optional.of(active.getEntity().getUniqueId());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public int countMobsInRegion(@NotNull World world,
                                 @NotNull ProtectedRegion region,
                                 @NotNull Set<String> mobTypes) {
        // Use Bukkit's spatial entity API to find candidates within the region
        // bounding box, then look each one up in MythicMobs by UUID.
        // This is more reliable than iterating getActiveMobs(), which can lag
        // behind freshly-spawned entities.
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        double cx = (min.x() + max.x()) / 2.0;
        double cy = (min.y() + max.y()) / 2.0;
        double cz = (min.z() + max.z()) / 2.0;
        double rx = (max.x() - min.x()) / 2.0 + 1;
        double ry = (max.y() - min.y()) / 2.0 + 1;
        double rz = (max.z() - min.z()) / 2.0 + 1;

        Collection<Entity> candidates = world.getNearbyEntities(
                new Location(world, cx, cy, cz), rx, ry, rz);

        final boolean filterByType = !mobTypes.isEmpty();
        int count = 0;

        for (Entity entity : candidates) {
            // Precise region containment check (bounding-box is wider than the region).
            Location loc = entity.getLocation();
            if (!region.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                continue;
            }
            // Check whether this Bukkit entity is a tracked MythicMobs mob.
            Optional<ActiveMob> activeMob = MythicBukkit.inst().getMobManager()
                    .getActiveMob(entity.getUniqueId());
            if (activeMob.isEmpty()) continue;

            ActiveMob mob = activeMob.get();
            if (mob.isDead()) continue;

            // Type filter using normalised name comparison.
            if (filterByType && !matchesAny(mob.getMobType(), mobTypes)) continue;

            count++;
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a MythicMobs mob by name using normalised matching:
     * <ol>
     *   <li>Exact lookup (fast path — no allocation).</li>
     *   <li>Case-insensitive + hyphen/underscore-agnostic scan over all registered
     *       mob types (only reached when the exact name fails).</li>
     * </ol>
     */
    private Optional<MythicMob> resolveMythicMob(String name) {
        // Fast path: exact match.
        Optional<MythicMob> exact = MythicBukkit.inst().getMobManager().getMythicMob(name);
        if (exact.isPresent()) {
            return exact;
        }
        // Slow path: normalised scan over all registered mob types.
        String needle = normalize(name);
        for (MythicMob mob : MythicBukkit.inst().getMobManager().getMobTypes()) {
            if (normalize(mob.getInternalName()).equals(needle)) {
                return Optional.of(mob);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns {@code true} if {@code mobType} (from MythicMobs) matches any name
     * in {@code requested} (from WorldGuard flag) using normalised comparison.
     */
    private boolean matchesAny(String mobType, Set<String> requested) {
        String normType = normalize(mobType);
        for (String r : requested) {
            if (normalize(r).equals(normType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normalises a mob name for comparison: lowercase + hyphens treated as underscores.
     * {@code "Bear-Polar"} → {@code "bear_polar"}, {@code "bear_polar"} → {@code "bear_polar"}.
     */
    private static String normalize(String name) {
        return name.strip().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}

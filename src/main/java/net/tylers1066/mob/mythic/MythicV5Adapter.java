package net.tylers1066.mob.mythic;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.World;
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
        // Pre-normalise the requested mob types once so the inner loop stays cheap.
        final boolean filterByType = !mobTypes.isEmpty();
        Collection<ActiveMob> allActive = MythicBukkit.inst().getMobManager().getActiveMobs();
        int count = 0;
        for (ActiveMob mob : allActive) {
            if (mob.isDead()) continue;
            // Cheap type filter before any location work (normalised comparison).
            if (filterByType && !matchesAny(mob.getMobType(), mobTypes)) continue;

            AbstractEntity abstractEntity = mob.getEntity();
            if (abstractEntity == null || abstractEntity.isDead()) continue;

            Location loc = abstractEntity.getBukkitEntity().getLocation();
            // Filter by world reference before the bounding-box / contains() check.
            if (!world.equals(loc.getWorld())) continue;

            if (region.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                count++;
            }
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
        return name.toLowerCase(Locale.ROOT).replace('-', '_');
    }
}

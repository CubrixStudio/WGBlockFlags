package net.tylers1066.mob.mythic;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.Raider;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;
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
            org.bukkit.entity.Entity entity = BukkitAdapter.adapt(active.getEntity());
            // Mark persistent immediately — getServer().getEntity(uuid) in the caller
            // may return null if the entity is not yet registered in Bukkit's registry.
            entity.setPersistent(true);
            // Ravagers and other Raiders are linked to Minecraft's raid mechanic:
            // without a nearby player the raid "fails" and the mob gets despawned
            // despite being persistent. Detach from raid logic so it stays alive.
            if (entity instanceof Raider raider) {
                raider.setCanJoinRaid(false);
            }
            return Optional.of(entity.getUniqueId());
        } catch (Exception e) {
            return Optional.empty();
        }
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
        Optional<MythicMob> exact = MythicBukkit.inst().getMobManager().getMythicMob(name);
        if (exact.isPresent()) {
            return exact;
        }
        String needle = normalize(name);
        for (MythicMob mob : MythicBukkit.inst().getMobManager().getMobTypes()) {
            if (normalize(mob.getInternalName()).equals(needle)) {
                return Optional.of(mob);
            }
        }
        return Optional.empty();
    }

    private static String normalize(String name) {
        return name.strip().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}

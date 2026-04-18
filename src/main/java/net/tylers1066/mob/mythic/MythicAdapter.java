package net.tylers1066.mob.mythic;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Abstraction layer for MythicMobs integration.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link MythicV5Adapter} — MythicMobs 5.x (Lumine)</li>
 *   <li>{@link MythicNoopAdapter} — fallback when MythicMobs is absent or unsupported</li>
 * </ul>
 */
public interface MythicAdapter {

    /** Returns true if the underlying MythicMobs plugin is available and supported. */
    boolean isAvailable();

    /**
     * Attempts to spawn a MythicMobs mob at the given location.
     *
     * @param mobName  the MythicMobs internal mob name (case-sensitive)
     * @param location the spawn location
     * @param level    the mob level
     * @return the UUID of the spawned entity, or empty if the spawn failed
     */
    Optional<UUID> spawnMob(@NotNull String mobName, @NotNull Location location, double level);
}

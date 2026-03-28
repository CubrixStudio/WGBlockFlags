package net.tylers1066.mob.mythic;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * No-op adapter used when MythicMobs is not installed or when the detected
 * version is not supported.  All methods return safe neutral values so the
 * rest of the mob-spawn module can reference the adapter without null checks.
 */
public class MythicNoopAdapter implements MythicAdapter {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Optional<UUID> spawnMob(@NotNull String mobName,
                                   @NotNull Location location,
                                   double level) {
        return Optional.empty();
    }

    @Override
    public int countMobsInRegion(@NotNull World world,
                                 @NotNull ProtectedRegion region,
                                 @NotNull Set<String> mobTypes) {
        return 0;
    }
}

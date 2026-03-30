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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;


/**
 * MythicMobs 5.x (Lumine) adapter.
 *
 * <p>All MythicMobs API calls are confined to this class, so that the rest of the
 * plugin compiles cleanly when MythicMobs is absent at runtime.
 */
public class MythicV5Adapter implements MythicAdapter {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Optional<UUID> spawnMob(@NotNull String mobName, @NotNull Location location, double level) {
        Optional<MythicMob> mythicMob = MythicBukkit.inst().getMobManager().getMythicMob(mobName);
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
        Collection<ActiveMob> allActive = MythicBukkit.inst().getMobManager().getActiveMobs();
        int count = 0;
        for (ActiveMob mob : allActive) {
            if (mob.isDead()) continue;
            // Cheap type filter before any location work.
            if (!mobTypes.isEmpty() && !mobTypes.contains(mob.getMobType())) continue;

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
}

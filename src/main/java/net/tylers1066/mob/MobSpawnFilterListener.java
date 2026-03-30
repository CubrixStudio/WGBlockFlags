package net.tylers1066.mob;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Set;

/**
 * Listener that filters natural mob spawning based on WorldGuard region flags.
 *
 * <p>Supported flags:
 * <ul>
 *   <li>{@code mob-spawn-filter} - Enables/disables filtering in the region</li>
 *   <li>{@code mob-allow-types} - Whitelist of allowed mob types</li>
 *   <li>{@code mob-deny-types} - Blacklist of denied mob types</li>
 *   <li>{@code mob-allow-vanilla} - Whether vanilla mobs are allowed</li>
 * </ul>
 *
 * <p>Filter logic (checked in order):
 * <ol>
 *   <li>If mob-spawn-filter != allow → no filtering</li>
 *   <li>If mob-allow-types defined AND mob type IN allow-types → ALLOW</li>
 *   <li>If mob-allow-types defined AND mob type NOT IN allow-types → DENY</li>
 *   <li>If mob-deny-types defined AND mob type IN deny-types → DENY</li>
 *   <li>If mob-allow-vanilla=deny AND mob is vanilla → DENY</li>
 *   <li>Otherwise → ALLOW</li>
 * </ol>
 */
public class MobSpawnFilterListener implements Listener {

    private final MobRegionCache cache;

    public MobSpawnFilterListener(MobRegionCache cache) {
        this.cache = cache;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Location loc = event.getLocation();
        if (loc == null) {
            return;
        }

        MobRegionCache.MobFilterData filterData = getFilterData(loc);
        if (filterData == null || !filterData.filterEnabled()) {
            return;
        }

        EntityType type = event.getEntityType();
        String typeName = type.name();

        // Priority 1: Check whitelist (mob-allow-types)
        if (!filterData.allowTypes().isEmpty()) {
            if (!matchesType(typeName, filterData.allowTypes())) {
                event.setCancelled(true);
                return;
            }
            // Whitelist matched - allow spawn, skip other checks
            return;
        }

        // Priority 2: Check blacklist (mob-deny-types)
        if (!filterData.denyTypes().isEmpty()) {
            if (matchesType(typeName, filterData.denyTypes())) {
                event.setCancelled(true);
                return;
            }
        }

        // Priority 3: Check vanilla restriction (mob-allow-vanilla)
        if (!filterData.allowVanilla() && isVanillaMob(type)) {
            event.setCancelled(true);
            return;
        }
    }

    /**
     * Checks if the given mob type name matches any entry in the filter set.
     * Supports both exact match and case-insensitive match.
     */
    private boolean matchesType(String typeName, Set<String> filterSet) {
        for (String filter : filterSet) {
            if (typeName.equalsIgnoreCase(filter) || typeName.toUpperCase().equals(filter.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if an entity type is a vanilla Minecraft mob.
     * Vanilla mobs are standard Minecraft hostile and passive mobs.
     */
    private boolean isVanillaMob(EntityType type) {
        return switch (type) {
            // Hostile mobs
            case ZOMBIE, SKELETON, CREEPER, SPIDER, CAVE_SPIDER,
                 ENDERMAN, WITCH, SLIME, SILVERFISH, PHANTOM,
                 DROWNED, HUSK, STRAY, VINDICATOR, EVOKER, PILLAGER,
                 RAVAGER, VEX, SHULKER, GUARDIAN, ELDER_GUARDIAN,
                 BLAZE, GHAST, MAGMA_CUBE, WITHER_SKELETON, ZOMBIFIED_PIGLIN,
                 PIGLIN, PIGLIN_BRUTE, HOGLIN, ZOGLIN, WARDEN -> true;

            // Passive/neutral mobs
            case COW, PIG, SHEEP, CHICKEN, HORSE, DONKEY, MULE,
                 SKELETON_HORSE, ZOMBIE_HORSE, LLAMA, TRADER_LLAMA,
                 WOLF, CAT, OCELOT, PANDA, TURTLE, DOLPHIN, SQUID,
                 GLOW_SQUID, AXOLOTL, FROG, TADPOLE, ALLAY,
                 VILLAGER, WANDERING_TRADER, IRON_GOLEM, SNOW_GOLEM -> true;

            // Boss mobs
            case ENDER_DRAGON, WITHER -> true;

            // Aquatic mobs
            case COD, SALMON, PUFFERFISH, TROPICAL_FISH -> true;

            // Other
            case BAT, RABBIT, BEE, GOAT, FOX, PARROT,
                 STRIDER, SNIFFER, CAMEL -> true;

            default -> false;
        };
    }

    /**
     * Gets the filter data for the region at the given location.
     * Returns null if no region or no filter data found.
     */
    private MobRegionCache.MobFilterData getFilterData(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }

        try {
            // Get region manager for this world using WorldGuard
            var adaptedWorld = BukkitAdapter.adapt(world);
            com.sk89q.worldguard.protection.managers.RegionManager rm = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(adaptedWorld);

            if (rm == null) {
                return null;
            }

            // Get all regions and find the one containing the location
            // Use a try-catch to handle BlockVector3 instantiation issues
            com.sk89q.worldguard.protection.regions.ProtectedRegion region = null;
            try {
                // Safely instantiate BlockVector3 and check containment
                var vectorClass = Class.forName("com.sk89q.worldedit.math.BlockVector3");
                var constructor = vectorClass.getDeclaredConstructor(int.class, int.class, int.class);
                constructor.setAccessible(true);
                var vec = (com.sk89q.worldedit.math.BlockVector3) constructor.newInstance(
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
                );

                for (var r : rm.getRegions().values()) {
                    try {
                        if (r.contains(vec)) {
                            region = r;
                            break;
                        }
                    } catch (Exception e) {
                        // Skip regions that fail containment check
                    }
                }
            } catch (Exception e) {
                // BlockVector3 not available, skip filtering
            }

            if (region == null) {
                return null;
            }

            MobRegionCache.RegionEntry entry = cache.getRegionEntry(world.getName(), region.getId());
            return entry != null ? entry.filterData() : null;
        } catch (Exception e) {
            // Region lookup failed, skip filtering
            return null;
        }
    }
}

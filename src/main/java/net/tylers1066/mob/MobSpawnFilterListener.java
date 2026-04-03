package net.tylers1066.mob;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.tylers1066.utils.WGUtils;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Set;

/**
 * Filters natural mob spawning based on WorldGuard region flags, while always
 * allowing MythicMobs (and other plugin-driven) spawns through.
 *
 * <p>Supported flags:
 * <ul>
 *   <li>{@code mob-spawn-filter allow} — enables filtering in the region</li>
 *   <li>{@code mob-allow-types} — whitelist of Bukkit entity type names (e.g. COW, PIG)</li>
 *   <li>{@code mob-deny-types} — blacklist checked only when allowTypes is empty</li>
 *   <li>{@code mob-allow-vanilla deny} — blocks all vanilla mobs</li>
 * </ul>
 *
 * <p>Filter logic (applied in order):
 * <ol>
 *   <li>SpawnReason.CUSTOM (MythicMobs / plugin spawns) → always allowed; WorldGuard
 *       cancellation is reversed if needed.</li>
 *   <li>Event already cancelled by WorldGuard or another plugin → left cancelled.</li>
 *   <li>mob-allow-types defined: allow only listed types, deny everything else.</li>
 *   <li>mob-deny-types defined: deny listed types, allow everything else.</li>
 *   <li>mob-allow-vanilla deny: deny all vanilla mobs.</li>
 * </ol>
 *
 * <p>This listener runs at {@link EventPriority#HIGH} (after WorldGuard's LOW) and
 * does <em>not</em> use {@code ignoreCancelled}, so it can both inspect and reverse
 * cancellations made by WorldGuard.
 */
public class MobSpawnFilterListener implements Listener {

    private final MobRegionCache cache;

    public MobSpawnFilterListener(MobRegionCache cache) {
        this.cache = cache;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // SpawnReason.CUSTOM is used by MythicMobs and other plugin-driven spawners.
        // These must never be blocked by our filter, and WorldGuard cancellations
        // must be reversed so the entity can actually appear.
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(false);
            return;
        }

        // Any other cancellation (WorldGuard mob-spawning deny, etc.) is respected.
        if (event.isCancelled()) {
            return;
        }

        // Apply region-based filter to natural spawns.
        Location loc = event.getLocation();
        MobRegionCache.MobFilterData filterData = getFilterData(loc);
        if (filterData == null) {
            return; // No filter configured at this location.
        }

        EntityType type = event.getEntityType();
        String typeName = type.name();

        // Priority 1: whitelist (mob-allow-types).
        if (!filterData.allowTypes().isEmpty()) {
            if (!matchesType(typeName, filterData.allowTypes())) {
                event.setCancelled(true);
            }
            // Whether matched or not, whitelist is the final word — skip other checks.
            return;
        }

        // Priority 2: blacklist (mob-deny-types).
        if (!filterData.denyTypes().isEmpty()) {
            if (matchesType(typeName, filterData.denyTypes())) {
                event.setCancelled(true);
                return;
            }
        }

        // Priority 3: vanilla restriction (mob-allow-vanilla deny).
        if (!filterData.allowVanilla() && isVanillaMob(type)) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the filter data for the highest-priority region at {@code loc} that
     * has {@code mob-spawn-filter allow}, or {@code null} if none.
     */
    private MobRegionCache.MobFilterData getFilterData(Location loc) {
        if (loc.getWorld() == null) {
            return null;
        }
        String worldName = loc.getWorld().getName();
        for (ProtectedRegion region : WGUtils.getApplicableRegions(loc).getRegions()) {
            MobRegionCache.RegionEntry entry = cache.getRegionEntry(worldName, region.getId());
            if (entry != null && entry.filterData() != null && entry.filterData().filterEnabled()) {
                return entry.filterData();
            }
        }
        return null;
    }

    private boolean matchesType(String typeName, Set<String> filterSet) {
        for (String filter : filterSet) {
            if (typeName.equalsIgnoreCase(filter)) {
                return true;
            }
        }
        return false;
    }

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

            // Other vanilla mobs
            case BAT, RABBIT, BEE, GOAT, FOX, PARROT,
                 STRIDER, SNIFFER, CAMEL -> true;

            default -> false;
        };
    }
}

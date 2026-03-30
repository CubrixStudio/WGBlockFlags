package net.tylers1066.mob;

import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.tylers1066.utils.WGUtils;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies per-region and per-entity-type drop multipliers when a mob dies.
 *
 * <p>Works independently of MythicMobs — any entity death inside a region
 * that has {@code mob-drop-multiplier} or {@code mob-drop-rates} set is affected.
 *
 * <p>Resolution order (highest-priority WorldGuard region first):
 * <ol>
 *   <li>Per-entity-type rate from {@code mob-drop-rates}
 *       (Bukkit entity type name, e.g. {@code "zombie:200"})</li>
 *   <li>Global rate from {@code mob-drop-multiplier}</li>
 *   <li>100% (no change) if neither flag is set</li>
 * </ol>
 */
public class MobDropListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getDrops().isEmpty()) {
            return;
        }

        Entity entity = event.getEntity();
        String entityTypeName = entity.getType().name().toLowerCase(Locale.ROOT);

        ApplicableRegionSet regions = WGUtils.getApplicableRegions(entity.getLocation());

        for (ProtectedRegion region : regions.getRegions()) {
            // 1. Per-entity-type rate takes priority.
            String rawRates = region.getFlag(MobSpawnFlags.MOB_DROP_RATES);
            if (rawRates != null) {
                Integer typeRate = parseRate(rawRates, entityTypeName);
                if (typeRate != null) {
                    applyMultiplier(event.getDrops(), typeRate);
                    return;
                }
            }

            // 2. Global multiplier fallback.
            Integer global = region.getFlag(MobSpawnFlags.MOB_DROP_MULTIPLIER);
            if (global != null) {
                applyMultiplier(event.getDrops(), global);
                return;
            }
        }
        // No flag found — drops unchanged.
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Parses {@code "zombie:200,skeleton:150"} and returns the multiplier for {@code key},
     * or {@code null} if the key is not present.
     */
    private @Nullable Integer parseRate(String rawRates, String key) {
        for (String entry : rawRates.split(",")) {
            String[] parts = entry.trim().split(":", 2);
            if (parts.length == 2 && parts[0].trim().equalsIgnoreCase(key)) {
                try {
                    return Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignored) {
                    // Malformed value — skip this entry.
                }
            }
        }
        return null;
    }

    /**
     * Scales the quantity of every item in the drops list by {@code multiplier / 100}.
     *
     * <ul>
     *   <li>0 → clears all drops.</li>
     *   <li>100 → no change.</li>
     *   <li>200 → doubles every drop quantity.</li>
     *   <li>150 → 1.5×: always at least floor, 50% chance of one extra (probabilistic rounding).</li>
     * </ul>
     *
     * Stacks whose scaled quantity rounds to zero are removed from the list.
     */
    private void applyMultiplier(List<ItemStack> drops, int multiplier) {
        if (multiplier == 100) {
            return;
        }
        if (multiplier <= 0) {
            drops.clear();
            return;
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        drops.removeIf(stack -> {
            if (stack == null) return true;
            int oldAmt = stack.getAmount();
            int scaled = oldAmt * multiplier;
            int base = scaled / 100;
            int remainder = scaled % 100;
            int newAmt = base + (rng.nextInt(100) < remainder ? 1 : 0);
            if (newAmt <= 0) {
                return true; // remove this stack
            }
            stack.setAmount(Math.min(newAmt, stack.getMaxStackSize()));
            return false;
        });
    }
}

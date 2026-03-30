package net.tylers1066.farm;

import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.tylers1066.utils.WGUtils;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies per-region and per-crop-type drop multipliers when a crop is broken.
 *
 * <p>Listens on {@link BlockDropItemEvent} (Paper API) at {@code NORMAL} priority
 * so that other plugins can still modify drops before or after us.
 *
 * <p>Resolution order (highest-priority WorldGuard region first):
 * <ol>
 *   <li>Per-crop-type rate from {@code farm-drop-rates} (e.g. {@code "wheat:200"})</li>
 *   <li>Global rate from {@code farm-drop-multiplier}</li>
 *   <li>100% (no change) if neither flag is set</li>
 * </ol>
 */
public class FarmDropListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        if (!CropUtils.isCrop(event.getBlock())) {
            return;
        }
        if (event.getItems().isEmpty()) {
            return;
        }

        Material cropType = event.getBlock().getType();
        String cropName = cropType.name().toLowerCase(Locale.ROOT);

        ApplicableRegionSet regions = WGUtils.getApplicableRegions(event.getBlock().getLocation());

        for (ProtectedRegion region : regions.getRegions()) {
            // 1. Per-crop-type rate takes priority.
            String rawRates = region.getFlag(FarmFlags.FARM_DROP_RATES);
            if (rawRates != null) {
                Integer typeRate = parseRate(rawRates, cropName);
                if (typeRate != null) {
                    applyMultiplier(event.getItems(), typeRate);
                    return;
                }
            }

            // 2. Global multiplier fallback.
            Integer global = region.getFlag(FarmFlags.FARM_DROP_MULTIPLIER);
            if (global != null) {
                applyMultiplier(event.getItems(), global);
                return;
            }
        }
        // No flag found — drops unchanged.
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Parses {@code "wheat:200,carrots:150"} and returns the multiplier for {@code key},
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
     * Scales the quantity of every item in the list by {@code multiplier / 100}.
     *
     * <ul>
     *   <li>0 → clears all drops.</li>
     *   <li>100 → no change.</li>
     *   <li>200 → doubles every drop quantity.</li>
     *   <li>150 → 1.5×: each item always gets at least floor(old × 1.5),
     *       with a 50% chance of one extra item (probabilistic rounding).</li>
     * </ul>
     *
     * Items whose scaled quantity rounds down to zero are removed from the list.
     */
    private void applyMultiplier(List<Item> items, int multiplier) {
        if (multiplier == 100) {
            return;
        }
        if (multiplier <= 0) {
            items.clear();
            return;
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        items.removeIf(item -> {
            ItemStack stack = item.getItemStack().clone();
            int oldAmt = stack.getAmount();
            // Integer part + probabilistic fractional remainder.
            int scaled = oldAmt * multiplier;
            int base = scaled / 100;
            int remainder = scaled % 100;
            int newAmt = base + (rng.nextInt(100) < remainder ? 1 : 0);
            if (newAmt <= 0) {
                return true; // remove this drop entirely
            }
            stack.setAmount(Math.min(newAmt, stack.getMaxStackSize()));
            item.setItemStack(stack);
            return false;
        });
    }
}

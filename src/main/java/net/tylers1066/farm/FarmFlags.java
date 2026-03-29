package net.tylers1066.farm;

import com.sk89q.worldguard.protection.flags.IntegerFlag;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import net.tylers1066.flags.helper.BlockMaterialFlag;
import org.bukkit.Material;


public class FarmFlags {

    /**
     * Enables automatic crop growth in the region.
     * Set to {@code allow} to activate auto-grow.
     * Usage: {@code /rg flag <region> farm-autogrow allow}
     */
    public static final StateFlag FARM_AUTOGROW = new StateFlag("farm-autogrow", false);

    /**
     * Ticks between each forced growth cycle for crops in the region.
     * Overrides the global {@code farm.grow-interval} config value.
     * Usage: {@code /rg flag <region> farm-grow-interval 200}
     */
    public static final IntegerFlag FARM_GROW_INTERVAL = new IntegerFlag("farm-grow-interval");

    /**
     * Enables automatic replanting after a crop is broken in the region.
     * Set to {@code allow} to activate auto-replant.
     * Usage: {@code /rg flag <region> farm-autoreplant allow}
     */
    public static final StateFlag FARM_AUTOREPLANT = new StateFlag("farm-autoreplant", false);

    /**
     * The set of crop materials managed by the farm system in this region.
     * When empty or not set, all crops are included.
     * Usage: {@code /rg flag <region> farm-crops WHEAT CARROTS POTATOES}
     */
    public static final SetFlag<Material> FARM_CROPS = new SetFlag<>("farm-crops", new BlockMaterialFlag(null));

    /**
     * Protects non-mature crops from being broken by players.
     * When set to {@code allow}, players can only harvest fully-grown crops.
     * Usage: {@code /rg flag <region> farm-protect-crops allow}
     */
    public static final StateFlag FARM_PROTECT_CROPS = new StateFlag("farm-protect-crops", false);

    /**
     * Time-of-day restriction for auto-grow: {@code "any"} (default), {@code "day"}, or {@code "night"}.
     * Usage: {@code /rg flag <region> farm-active-time day}
     */
    public static final StringFlag FARM_ACTIVE_TIME = new StringFlag("farm-active-time");

    /**
     * Weather restriction for auto-grow: {@code "any"} (default), {@code "clear"}, or {@code "rain"}.
     * Usage: {@code /rg flag <region> farm-active-weather clear}
     */
    public static final StringFlag FARM_ACTIVE_WEATHER = new StringFlag("farm-active-weather");

    /**
     * Maximum height for upward vertical crops (sugar cane, cactus, bamboo, kelp, twisting vines).
     * Overrides the natural default height cap.
     * Usage: {@code /rg flag <region> farm-max-height 5}
     */
    public static final IntegerFlag FARM_MAX_HEIGHT = new IntegerFlag("farm-max-height");

    public static int count() {
        return 8;
    }

    private FarmFlags() {}
}

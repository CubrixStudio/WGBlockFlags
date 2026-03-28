package net.tylers1066.farm;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class CropUtils {

    /**
     * Materials that grow vertically (upward). Only the bottom block is tracked.
     * Walking up to the top block allows bonemeal/manual growth.
     */
    public static final Set<Material> VERTICAL_CROPS = EnumSet.of(
            Material.SUGAR_CANE,
            Material.BAMBOO,
            Material.BAMBOO_SAPLING,
            Material.CACTUS,
            Material.KELP,
            Material.KELP_PLANT,
            Material.TWISTING_VINES,
            Material.TWISTING_VINES_PLANT
    );

    /** Maximum natural height per vertical crop material (for manual fallback growth). */
    private static final Map<Material, Integer> VERTICAL_MAX_HEIGHT = new EnumMap<>(Material.class);

    static {
        VERTICAL_MAX_HEIGHT.put(Material.CACTUS, 3);
        VERTICAL_MAX_HEIGHT.put(Material.SUGAR_CANE, 3);
    }

    private CropUtils() {}

    /**
     * Returns true if the block is a growable crop — either Ageable (wheat, carrot, etc.)
     * or a vertical crop (sugar cane, bamboo, cactus, kelp, twisting vines).
     */
    public static boolean isCrop(Block block) {
        if (block.getBlockData() instanceof Ageable) {
            return true;
        }
        return VERTICAL_CROPS.contains(block.getType());
    }

    /**
     * Returns true if the block is a fully-grown Ageable crop.
     * Always returns false for vertical crops (they grow indefinitely).
     */
    public static boolean isFullyGrown(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return false;
    }

    /**
     * Returns true if the block is a vertical crop type.
     */
    public static boolean isVerticalCrop(Material type) {
        return VERTICAL_CROPS.contains(type);
    }

    /**
     * Returns true if this vertical crop block is the bottom of its column.
     * Used during chunk scanning to avoid tracking the same column multiple times.
     */
    public static boolean isVerticalCropBottom(Block block) {
        if (!VERTICAL_CROPS.contains(block.getType())) {
            return false;
        }
        return !VERTICAL_CROPS.contains(block.getRelative(BlockFace.DOWN).getType());
    }

    /**
     * Advances the crop by one growth stage.
     * <ul>
     *   <li>Vertical crops: walks to the top of the column and places a new block above.</li>
     *   <li>Ageable crops: increments age by one (predictable, single-stage).</li>
     * </ul>
     */
    public static void advanceGrowth(Block block) {
        // Vertical crops must be handled first — bamboo and kelp are also Ageable,
        // but growing them vertically (adding blocks on top) is the correct behaviour.
        if (VERTICAL_CROPS.contains(block.getType())) {
            growManual(block, getColumnTop(block));
            return;
        }
        // Ageable crops: single-stage increment
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) {
                ageable.setAge(ageable.getAge() + 1);
                block.setBlockData(ageable);
            }
        }
    }

    /**
     * Replants the crop at the given block location.
     * The block must currently be AIR (check before calling).
     * For Ageable crops, sets age to 0. For vertical crops, just places the block.
     */
    public static void replant(Block block, Material cropType) {
        block.setType(cropType);
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            ageable.setAge(0);
            block.setBlockData(ageable);
        }
    }

    /**
     * Returns true if auto-replant can be performed here.
     * Checks that the broken crop's required support block is still present below.
     */
    public static boolean canReplant(Block brokenBlock) {
        Material type = brokenBlock.getType();
        // Only Ageable crops are supported for auto-replant.
        if (!(brokenBlock.getBlockData() instanceof Ageable)) {
            return false;
        }
        Block below = brokenBlock.getRelative(BlockFace.DOWN);
        return isValidSoil(type, below.getType());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Walks upward from the given block to find the topmost block of the vertical crop. */
    private static Block getColumnTop(Block bottom) {
        Block top = bottom;
        while (VERTICAL_CROPS.contains(top.getRelative(BlockFace.UP).getType())) {
            top = top.getRelative(BlockFace.UP);
        }
        return top;
    }

    /**
     * Places a new block on top of the vertical crop column.
     * Handles special cases:
     * <ul>
     *   <li>KELP tip becomes KELP_PLANT and a new KELP appears above.</li>
     *   <li>TWISTING_VINES tip becomes TWISTING_VINES_PLANT and a new TWISTING_VINES appears above.</li>
     *   <li>BAMBOO_SAPLING converts to BAMBOO directly (no new block).</li>
     *   <li>Other crops: place same material above.</li>
     * </ul>
     */
    private static void growManual(Block bottom, Block top) {
        Material topType = top.getType();

        // BAMBOO_SAPLING → convert to BAMBOO in-place (no block above yet)
        if (topType == Material.BAMBOO_SAPLING) {
            top.setType(Material.BAMBOO);
            return;
        }

        Block above = top.getRelative(BlockFace.UP);
        if (above.getType() != Material.AIR) {
            return;
        }

        // Respect height limit for height-capped crops.
        Material bottomType = bottom.getType();
        Integer maxHeight = VERTICAL_MAX_HEIGHT.get(bottomType);
        if (maxHeight != null && countColumnHeight(bottom) >= maxHeight) {
            return;
        }

        // KELP: convert tip to body, place new growable tip above.
        if (topType == Material.KELP) {
            top.setType(Material.KELP_PLANT);
            above.setType(Material.KELP);
            return;
        }

        // TWISTING_VINES: same pattern as KELP.
        if (topType == Material.TWISTING_VINES) {
            top.setType(Material.TWISTING_VINES_PLANT);
            above.setType(Material.TWISTING_VINES);
            return;
        }

        // Default: place same type above.
        above.setType(topType);
    }

    /** Counts the total height of a vertical crop column starting from the bottom block. */
    private static int countColumnHeight(Block bottom) {
        int height = 0;
        Block current = bottom;
        while (VERTICAL_CROPS.contains(current.getType())) {
            height++;
            current = current.getRelative(BlockFace.UP);
        }
        return height;
    }

    /**
     * Returns true if the given soil material is a valid support for the given crop type.
     * Only used for Ageable crop replant validation.
     */
    private static boolean isValidSoil(Material cropType, Material soilType) {
        return switch (cropType) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, MELON_STEM, PUMPKIN_STEM,
                    TORCHFLOWER_CROP, PITCHER_CROP -> soilType == Material.FARMLAND;
            case NETHER_WART -> soilType == Material.SOUL_SAND || soilType == Material.SOUL_SOIL;
            case SWEET_BERRY_BUSH -> soilType == Material.GRASS_BLOCK || soilType == Material.DIRT
                    || soilType == Material.COARSE_DIRT || soilType == Material.PODZOL
                    || soilType == Material.ROOTED_DIRT || soilType == Material.FARMLAND
                    || soilType == Material.MOSS_BLOCK || soilType == Material.MUD
                    || soilType == Material.MUDDY_MANGROVE_ROOTS;
            case COCOA -> false; // cocoa grows on jungle logs — too complex for auto-replant
            default -> true; // unknown Ageable crop: allow by default
        };
    }
}

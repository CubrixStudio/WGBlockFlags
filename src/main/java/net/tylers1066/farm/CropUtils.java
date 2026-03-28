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
     * Explicit allowlist of Ageable farm crops.
     *
     * <p>Using an allowlist (rather than {@code instanceof Ageable}) prevents accidental
     * tracking of non-crop Ageable blocks like FIRE, FROSTED_ICE, CHORUS_FLOWER, etc.
     *
     * <p>Supported crops:
     * <ul>
     *   <li>Farmland crops: wheat, carrots, potatoes, beetroots, melon/pumpkin stems,
     *       torchflower, pitcher plant</li>
     *   <li>Special crops: nether wart, sweet berry bush, cocoa</li>
     * </ul>
     */
    public static final Set<Material> AGEABLE_CROPS = EnumSet.of(
            // — farmland crops —
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.MELON_STEM,
            Material.PUMPKIN_STEM,
            Material.TORCHFLOWER_CROP,
            Material.PITCHER_CROP,
            // — special crops —
            Material.NETHER_WART,
            Material.SWEET_BERRY_BUSH,
            Material.COCOA
    );

    /**
     * Vertical crops that grow upward.
     * Only the bottom (base) block is tracked; growth is applied at the column top.
     */
    public static final Set<Material> UPWARD_CROPS = EnumSet.of(
            Material.SUGAR_CANE,
            Material.BAMBOO,
            Material.BAMBOO_SAPLING,
            Material.CACTUS,
            Material.KELP,
            Material.KELP_PLANT,
            Material.TWISTING_VINES,
            Material.TWISTING_VINES_PLANT
    );

    /**
     * Vertical crops that grow downward (hang from a ceiling block).
     * The topmost block (anchor) is tracked; growth is applied at the bottom tip.
     *
     * <p>Includes both WEEPING_VINES/CAVE_VINES (the growing tip) and their _PLANT
     * counterparts (the body above the tip), so that column-walk logic works correctly.
     */
    public static final Set<Material> DOWNWARD_CROPS = EnumSet.of(
            Material.WEEPING_VINES,
            Material.WEEPING_VINES_PLANT,
            Material.CAVE_VINES,
            Material.CAVE_VINES_PLANT
    );

    /**
     * Union of all vertically-growing crop materials.
     * Used for column-walk checks and the {@link #isCrop} test.
     */
    public static final Set<Material> VERTICAL_CROPS;

    /** Maximum natural column height for height-capped upward crops. */
    private static final Map<Material, Integer> VERTICAL_MAX_HEIGHT = new EnumMap<>(Material.class);

    static {
        Set<Material> all = EnumSet.copyOf(UPWARD_CROPS);
        all.addAll(DOWNWARD_CROPS);
        VERTICAL_CROPS = all; // EnumSet is mutable — callers treat this as read-only

        VERTICAL_MAX_HEIGHT.put(Material.CACTUS, 3);
        VERTICAL_MAX_HEIGHT.put(Material.SUGAR_CANE, 3);
    }

    private CropUtils() {}

    // -------------------------------------------------------------------------
    // Public queries
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the block is a growable farm crop — either an explicit
     * Ageable crop (wheat, carrots, …) or any vertical crop (sugar cane, kelp,
     * weeping vines, cave vines, …).
     */
    public static boolean isCrop(Block block) {
        return AGEABLE_CROPS.contains(block.getType())
                || VERTICAL_CROPS.contains(block.getType());
    }

    /**
     * Returns {@code true} if the block is a fully-grown Ageable crop ready for harvest.
     * Always returns {@code false} for vertical crops — they grow indefinitely (or until
     * physically blocked) and should never be skipped by the scheduler.
     */
    public static boolean isFullyGrown(Block block) {
        if (VERTICAL_CROPS.contains(block.getType())) {
            return false;
        }
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return false;
    }

    /** Returns {@code true} if the material belongs to any vertical crop family. */
    public static boolean isVerticalCrop(Material type) {
        return VERTICAL_CROPS.contains(type);
    }

    /**
     * Returns {@code true} if this block is the one that should be tracked for its
     * vertical crop column.
     *
     * <ul>
     *   <li><b>Upward crops</b> (sugar cane, bamboo, kelp, twisting vines, …): the
     *       bottom (base) block — i.e., the block directly below is <em>not</em> a
     *       vertical crop.</li>
     *   <li><b>Downward crops</b> (weeping vines, cave vines): the top (anchor) block
     *       that is attached to the ceiling — i.e., the block directly above is
     *       <em>not</em> a vertical crop.</li>
     * </ul>
     */
    public static boolean isVerticalCropBottom(Block block) {
        Material type = block.getType();
        if (!VERTICAL_CROPS.contains(type)) {
            return false;
        }
        if (DOWNWARD_CROPS.contains(type)) {
            // Downward crops: anchor = topmost block (nothing above it is a vine)
            return !VERTICAL_CROPS.contains(block.getRelative(BlockFace.UP).getType());
        }
        // Upward crops: base = bottommost block (nothing below it is a crop)
        return !VERTICAL_CROPS.contains(block.getRelative(BlockFace.DOWN).getType());
    }

    // -------------------------------------------------------------------------
    // Growth
    // -------------------------------------------------------------------------

    /**
     * Advances the crop by one growth stage.
     * <ul>
     *   <li>Downward crops (weeping vines, cave vines): walks to the column tip and
     *       places a new block one step below it.</li>
     *   <li>Upward crops (sugar cane, bamboo, kelp, …): walks to the column top and
     *       places a new block above.</li>
     *   <li>Ageable crops (wheat, carrots, …): increments age by one stage.</li>
     * </ul>
     */
    public static void advanceGrowth(Block block) {
        Material type = block.getType();

        // Downward crops must be checked before upward — they are mutually exclusive sets.
        if (DOWNWARD_CROPS.contains(type)) {
            growDownward(block);
            return;
        }

        // Upward crops (bamboo / kelp are also Ageable, but columnar growth is correct).
        if (UPWARD_CROPS.contains(type)) {
            growManual(block, getColumnTop(block));
            return;
        }

        // Ageable crops: single-stage increment.
        // Use applyPhysics=false so the crop is never removed by physics checks
        // (e.g. dry farmland), keeping the forced-grow independent of server gamerules.
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) {
                ageable.setAge(ageable.getAge() + 1);
                block.setBlockData(ageable, false);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Replant
    // -------------------------------------------------------------------------

    /**
     * Replants the crop at the given block location.
     * The block must currently be AIR (caller's responsibility to check).
     * Sets age to 0 for Ageable crops; for vertical crops places the block type directly.
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
     * Returns {@code true} if auto-replant can be performed here.
     * Checks that the required soil block is still present below the broken crop.
     * Only Ageable crops support auto-replant; vertical crops are excluded.
     */
    public static boolean canReplant(Block brokenBlock) {
        Material type = brokenBlock.getType();
        if (!AGEABLE_CROPS.contains(type)) {
            return false;
        }
        Block below = brokenBlock.getRelative(BlockFace.DOWN);
        return isValidSoil(type, below.getType());
    }

    // -------------------------------------------------------------------------
    // Private — upward growth
    // -------------------------------------------------------------------------

    /** Walks upward from the given block to find the topmost block of an upward column. */
    private static Block getColumnTop(Block bottom) {
        Block top = bottom;
        while (UPWARD_CROPS.contains(top.getRelative(BlockFace.UP).getType())) {
            top = top.getRelative(BlockFace.UP);
        }
        return top;
    }

    /**
     * Places a new block on top of an upward vertical crop column.
     *
     * <ul>
     *   <li>BAMBOO_SAPLING → converts to BAMBOO in-place (no block above needed).</li>
     *   <li>KELP tip → converts to KELP_PLANT, places new KELP tip above.</li>
     *   <li>TWISTING_VINES tip → converts to TWISTING_VINES_PLANT, places new tip above.</li>
     *   <li>Other crops (sugar cane, cactus, bamboo) → places same type above.</li>
     * </ul>
     */
    private static void growManual(Block bottom, Block top) {
        Material topType = top.getType();

        // BAMBOO_SAPLING → convert to full BAMBOO in-place (the sapling IS the first block)
        if (topType == Material.BAMBOO_SAPLING) {
            top.setType(Material.BAMBOO);
            return;
        }

        Block above = top.getRelative(BlockFace.UP);
        if (above.getType() != Material.AIR) {
            return; // physically blocked
        }

        // Respect height limits for height-capped crops (cactus = 3, sugar cane = 3).
        Material bottomType = bottom.getType();
        Integer maxHeight = VERTICAL_MAX_HEIGHT.get(bottomType);
        if (maxHeight != null && countColumnHeight(bottom) >= maxHeight) {
            return;
        }

        // KELP: convert growing tip to body, place new tip above.
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

        // Default: sugar cane, cactus, bamboo — place same type above.
        above.setType(topType);
    }

    /** Counts the total height of an upward crop column starting from the bottom block. */
    private static int countColumnHeight(Block bottom) {
        int height = 0;
        Block current = bottom;
        while (UPWARD_CROPS.contains(current.getType())) {
            height++;
            current = current.getRelative(BlockFace.UP);
        }
        return height;
    }

    // -------------------------------------------------------------------------
    // Private — downward growth
    // -------------------------------------------------------------------------

    /**
     * Grows a downward-hanging vine column one block toward the ground.
     *
     * <p>The {@code anchor} is the topmost block of the column (tracked, position fixed).
     * This method walks downward to find the current growing tip, converts it to a body
     * block, and places a new tip one block below.
     *
     * <p>Supported downward crops:
     * <ul>
     *   <li>WEEPING_VINES (nether, tip) → WEEPING_VINES_PLANT (body), new WEEPING_VINES below.</li>
     *   <li>CAVE_VINES (overworld ceiling, tip) → CAVE_VINES_PLANT (body), new CAVE_VINES below.</li>
     * </ul>
     */
    private static void growDownward(Block anchor) {
        // Walk down from the anchor to find the growing tip.
        Block tip = anchor;
        while (DOWNWARD_CROPS.contains(tip.getRelative(BlockFace.DOWN).getType())) {
            tip = tip.getRelative(BlockFace.DOWN);
        }

        Block below = tip.getRelative(BlockFace.DOWN);
        if (below.getType() != Material.AIR) {
            return; // physically blocked — floor or non-air block below
        }

        Material tipType = tip.getType();
        if (tipType == Material.WEEPING_VINES) {
            tip.setType(Material.WEEPING_VINES_PLANT);
            below.setType(Material.WEEPING_VINES);
        } else if (tipType == Material.CAVE_VINES) {
            tip.setType(Material.CAVE_VINES_PLANT);
            below.setType(Material.CAVE_VINES);
        }
        // If the tip is a _PLANT variant (column was modified externally), skip silently.
    }

    // -------------------------------------------------------------------------
    // Private — soil validation (replant)
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the given soil material is a valid support for the crop.
     */
    private static boolean isValidSoil(Material cropType, Material soilType) {
        return switch (cropType) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS,
                    MELON_STEM, PUMPKIN_STEM,
                    TORCHFLOWER_CROP, PITCHER_CROP ->
                    soilType == Material.FARMLAND;

            case NETHER_WART ->
                    soilType == Material.SOUL_SAND || soilType == Material.SOUL_SOIL;

            case SWEET_BERRY_BUSH ->
                    soilType == Material.GRASS_BLOCK
                    || soilType == Material.DIRT
                    || soilType == Material.COARSE_DIRT
                    || soilType == Material.PODZOL
                    || soilType == Material.ROOTED_DIRT
                    || soilType == Material.FARMLAND
                    || soilType == Material.MOSS_BLOCK
                    || soilType == Material.MUD
                    || soilType == Material.MUDDY_MANGROVE_ROOTS;

            case COCOA -> false; // grows on jungle logs — placement is too complex for auto-replant

            default -> true; // unknown Ageable crop: allow by default
        };
    }
}

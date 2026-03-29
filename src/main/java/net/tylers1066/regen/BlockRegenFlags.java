package net.tylers1066.regen;

import com.sk89q.worldguard.protection.flags.IntegerFlag;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import net.tylers1066.flags.helper.BlockMaterialFlag;
import org.bukkit.Material;

public class BlockRegenFlags {

    /**
     * Enables automatic block regeneration after a player breaks a block in the region.
     * Set to {@code allow} to activate.
     * Usage: {@code /rg flag <region> block-regen allow}
     */
    public static final StateFlag BLOCK_REGEN = new StateFlag("block-regen", false);

    /**
     * Ticks before a broken block regenerates.
     * Overrides the global {@code block-regen.default-delay} config value.
     * Usage: {@code /rg flag <region> block-regen-delay 1200}
     */
    public static final IntegerFlag BLOCK_REGEN_DELAY = new IntegerFlag("block-regen-delay");

    /**
     * Set of block materials that will regenerate when broken.
     * When empty or not set, all broken blocks regenerate.
     * Usage: {@code /rg flag <region> block-regen-materials stone,coal_ore,iron_ore}
     */
    public static final SetFlag<Material> BLOCK_REGEN_MATERIALS = new SetFlag<>("block-regen-materials", new BlockMaterialFlag(null));

    public static int count() {
        return 3;
    }

    private BlockRegenFlags() {}
}

package net.tylers1066.flags;

import com.sk89q.worldguard.protection.flags.SetFlag;
import net.tylers1066.flags.helper.BlockMaterialFlag;
import org.bukkit.Material;

public class Flags {
    // General allow/deny for all operations
    public static final SetFlag<Material> ALLOW_BLOCKS = new SetFlag<>("allow-blocks", new BlockMaterialFlag(null));
    public static final SetFlag<Material> DENY_BLOCKS = new SetFlag<>("deny-blocks", new BlockMaterialFlag(null));

    // Placement specific
    public static final SetFlag<Material> ALLOW_BLOCK_PLACE = new SetFlag<>("allow-block-place", new BlockMaterialFlag(null));
    public static final SetFlag<Material> DENY_BLOCK_PLACE = new SetFlag<>("deny-block-place", new BlockMaterialFlag(null));

    // Breaking specific
    public static final SetFlag<Material> ALLOW_BLOCK_BREAK = new SetFlag<>("allow-block-break", new BlockMaterialFlag(null));
    public static final SetFlag<Material> DENY_BLOCK_BREAK = new SetFlag<>("deny-block-break", new BlockMaterialFlag(null));

    // Interaction specific (doors, buttons, levers, chests, etc.)
    public static final SetFlag<Material> ALLOW_BLOCK_INTERACT = new SetFlag<>("allow-block-interact", new BlockMaterialFlag(null));
    public static final SetFlag<Material> DENY_BLOCK_INTERACT = new SetFlag<>("deny-block-interact", new BlockMaterialFlag(null));

    public static int count() {
        return 8;
    }

    private Flags() {}
}

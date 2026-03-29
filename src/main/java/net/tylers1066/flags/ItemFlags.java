package net.tylers1066.flags;

import com.sk89q.worldguard.protection.flags.SetFlag;
import net.tylers1066.flags.helper.BlockMaterialFlag;
import org.bukkit.Material;

public class ItemFlags {

    /**
     * Set of item materials players are prevented from picking up in the region.
     * Usage: {@code /rg flag <region> deny-item-pickup diamond emerald}
     */
    public static final SetFlag<Material> DENY_ITEM_PICKUP = new SetFlag<>("deny-item-pickup", new BlockMaterialFlag(null));

    /**
     * Set of item materials players are prevented from dropping in the region.
     * Usage: {@code /rg flag <region> deny-item-drop diamond_sword}
     */
    public static final SetFlag<Material> DENY_ITEM_DROP = new SetFlag<>("deny-item-drop", new BlockMaterialFlag(null));

    public static int count() {
        return 2;
    }

    private ItemFlags() {}
}

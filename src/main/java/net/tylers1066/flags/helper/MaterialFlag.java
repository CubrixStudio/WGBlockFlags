package net.tylers1066.flags.helper;

import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

public class MaterialFlag extends Flag<Material> {

    public MaterialFlag(String name) {
        super(name);
    }

    @Override
    public Material parseInput(FlagContext flagContext) throws InvalidFlagFormat {
        String input = flagContext.getUserInput().trim();
        Material material = Material.matchMaterial(input);
        if (material != null) {
            return material;
        }
        throw new InvalidFlagFormat("Unknown material '" + input
                + "'. Please refer to https://jd.papermc.io/paper/1.21.4/org/bukkit/Material.html for valid names.");
    }

    @Override
    public Material unmarshal(@Nullable Object o) {
        if (o == null) {
            return null;
        }
        return Material.matchMaterial(o.toString());
    }

    @Override
    public Object marshal(Material material) {
        return material.name();
    }
}

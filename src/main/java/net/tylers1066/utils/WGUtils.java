package net.tylers1066.utils;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.FlagValueCalculator;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.protection.util.NormativeOrders;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WGUtils {

    private WGUtils() {}

    public static ApplicableRegionSet getApplicableRegions(@NotNull Location loc) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        return container.createQuery().getApplicableRegions(BukkitAdapter.adapt(loc));
    }

    public static <T> @Nullable T queryValue(@NotNull Player player, @NotNull World world,
                                              @NotNull Set<ProtectedRegion> regions, @NotNull Flag<T> flag) {
        LocalPlayer localPlayer = wrapPlayer(player);
        return createFlagValueCalculator(player, world, regions, flag).queryValue(localPlayer, flag);
    }

    public static boolean canBuild(@NotNull Player player, @NotNull Block block) {
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        LocalPlayer localPlayer = wrapPlayer(player);
        StateFlag.State state = query.queryState(BukkitAdapter.adapt(block.getLocation()), localPlayer, Flags.BUILD);
        return state != StateFlag.State.DENY;
    }

    private static LocalPlayer wrapPlayer(@NotNull Player player) {
        return WorldGuardPlugin.inst().wrapPlayer(player);
    }

    private static <T> FlagValueCalculator createFlagValueCalculator(
            @NotNull Player player, @NotNull World world,
            @NotNull Set<ProtectedRegion> regions, @NotNull Flag<T> flag) {

        List<ProtectedRegion> checkForRegions = new ArrayList<>();
        for (ProtectedRegion region : regions) {
            if (!hasBypass(player, world, region, flag)) {
                checkForRegions.add(region);
            }
        }

        NormativeOrders.sort(checkForRegions);

        ProtectedRegion global = WorldGuard.getInstance().getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(world))
                .getRegion(ProtectedRegion.GLOBAL_REGION);

        if (global != null && hasBypass(player, world, global, flag)) {
            global = null;
        }

        return new FlagValueCalculator(checkForRegions, global);
    }

    private static boolean hasBypass(@NotNull Player player, @NotNull World world,
                                     @NotNull ProtectedRegion region, @NotNull Flag<?> flag) {
        return player.hasPermission("worldguard.region.bypass." + world.getName()
                + "." + region.getId() + "." + flag.getName());
    }
}

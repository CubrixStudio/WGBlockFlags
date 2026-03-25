package net.tylers1066.listener;

import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.kyori.adventure.text.Component;
import net.tylers1066.WGBlockFlags;
import net.tylers1066.config.PluginConfig;
import net.tylers1066.flags.Flags;
import net.tylers1066.utils.WGUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public abstract class AbstractBlockListener implements Listener {
    protected final WGBlockFlags plugin;
    private final Map<UUID, Long> messageCooldowns = new HashMap<>();

    protected AbstractBlockListener(WGBlockFlags plugin) {
        this.plugin = plugin;
    }

    protected enum FlagAction {
        PLACE, BREAK, INTERACT
    }

    /**
     * Evaluates flags for a given block action and returns the result.
     *
     * @param player  the player performing the action
     * @param type    the block material type
     * @param regions the applicable WorldGuard regions
     * @param action  the type of action (PLACE, BREAK, INTERACT)
     * @return Event.Result.ALLOW, DENY, or DEFAULT if no flag matched
     */
    protected Event.Result evaluateFlags(Player player, Material type,
                                         ApplicableRegionSet regions, FlagAction action) {
        PluginConfig config = plugin.getPluginConfig();
        Set<ProtectedRegion> regionSet = regions.getRegions();

        // 1. Check allow-blocks (general allow for all actions)
        Set<Material> materials = WGUtils.queryValue(player, player.getWorld(), regionSet, Flags.ALLOW_BLOCKS);
        if (materials != null && (materials.contains(type) || materials.contains(Material.AIR))) {
            debug("ALLOW_BLOCKS matched for " + player.getName() + " - " + type);
            return Event.Result.ALLOW;
        }

        // 2. Check deny-blocks (general deny for all actions)
        materials = WGUtils.queryValue(player, player.getWorld(), regionSet, Flags.DENY_BLOCKS);
        if (materials != null && (materials.contains(type) || materials.contains(Material.AIR))) {
            debug("DENY_BLOCKS matched for " + player.getName() + " - " + type);
            return Event.Result.DENY;
        }

        // 3. Check action-specific flags
        SetFlag<Material> allowFlag = getSpecificAllowFlag(action);
        SetFlag<Material> denyFlag = getSpecificDenyFlag(action);

        if (allowFlag != null) {
            materials = WGUtils.queryValue(player, player.getWorld(), regionSet, allowFlag);
            if (materials != null && materials.contains(type)) {
                debug(allowFlag.getName() + " matched for " + player.getName() + " - " + type);
                return Event.Result.ALLOW;
            }
        }

        if (denyFlag != null) {
            materials = WGUtils.queryValue(player, player.getWorld(), regionSet, denyFlag);
            if (materials != null && (materials.contains(type) || materials.contains(Material.AIR))) {
                debug(denyFlag.getName() + " matched for " + player.getName() + " - " + type);
                return Event.Result.DENY;
            }
        }

        return Event.Result.DEFAULT;
    }

    protected void sendDenyMessage(Player player, Material type, FlagAction action) {
        PluginConfig config = plugin.getPluginConfig();
        long cooldown = config.getMessageCooldownMs();

        if (cooldown > 0) {
            long now = System.currentTimeMillis();
            Long lastSent = messageCooldowns.get(player.getUniqueId());
            if (lastSent != null && (now - lastSent) < cooldown) {
                return;
            }
            messageCooldowns.put(player.getUniqueId(), now);
        }

        Component message = switch (action) {
            case PLACE -> config.getDenyPlaceMessage(type);
            case BREAK -> config.getDenyBreakMessage(type);
            case INTERACT -> config.getDenyInteractMessage(type);
        };

        if (message != null) {
            player.sendMessage(message);
        }
    }

    protected void debug(String msg) {
        if (plugin.getPluginConfig().isDebug()) {
            plugin.getLogger().info("[DEBUG] " + msg);
        }
    }

    private SetFlag<Material> getSpecificAllowFlag(FlagAction action) {
        return switch (action) {
            case PLACE -> Flags.ALLOW_BLOCK_PLACE;
            case BREAK -> Flags.ALLOW_BLOCK_BREAK;
            case INTERACT -> Flags.ALLOW_BLOCK_INTERACT;
        };
    }

    private SetFlag<Material> getSpecificDenyFlag(FlagAction action) {
        return switch (action) {
            case PLACE -> Flags.DENY_BLOCK_PLACE;
            case BREAK -> Flags.DENY_BLOCK_BREAK;
            case INTERACT -> Flags.DENY_BLOCK_INTERACT;
        };
    }
}

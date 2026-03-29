package net.tylers1066.listener;

import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.tylers1066.WGBlockFlags;
import net.tylers1066.flags.ItemFlags;
import net.tylers1066.utils.WGUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public class ItemListener implements Listener {

    private final WGBlockFlags plugin;

    public ItemListener(WGBlockFlags plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.hasPermission("wgblockflags.item.pickup.bypass")) {
            return;
        }
        Material itemType = event.getItem().getItemStack().getType();
        Location loc = event.getItem().getLocation();
        ApplicableRegionSet regions = WGUtils.getApplicableRegions(loc);

        for (ProtectedRegion region : regions.getRegions()) {
            Set<Material> denied = region.getFlag(ItemFlags.DENY_ITEM_PICKUP);
            if (denied != null && denied.contains(itemType)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("wgblockflags.item.drop.bypass")) {
            return;
        }
        Material itemType = event.getItemDrop().getItemStack().getType();
        Location loc = player.getLocation();
        ApplicableRegionSet regions = WGUtils.getApplicableRegions(loc);

        for (ProtectedRegion region : regions.getRegions()) {
            Set<Material> denied = region.getFlag(ItemFlags.DENY_ITEM_DROP);
            if (denied != null && denied.contains(itemType)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}

package net.tylers1066.events;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.tylers1066.events.RegionEventsCache.RegionEventData;
import net.tylers1066.events.RegionEventsCache.RegionEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.time.Duration;
import java.util.*;

public class RegionEventsListener implements Listener {

    private final RegionEventsCache cache;

    /** Per-player set of region IDs currently occupied (worldName:regionId). */
    private final Map<UUID, Set<String>> playerRegions = new HashMap<>();

    public RegionEventsListener(RegionEventsCache cache) {
        this.cache = cache;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        // Only process if the block position changed.
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        handleTransition(event.getPlayer(), from, to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        handleTransition(event.getPlayer(), from, to);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerRegions.remove(event.getPlayer().getUniqueId());
    }

    /** Clears all tracked region sets (called on module disable/reload). */
    public void clear() {
        playerRegions.clear();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void handleTransition(Player player, Location from, Location to) {
        if (to.getWorld() == null) return;

        Set<String> oldRegions = playerRegions.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        Set<String> newRegions = getRegionKeys(to);

        // Find entered and exited regions.
        Set<String> entered = new HashSet<>(newRegions);
        entered.removeAll(oldRegions);

        Set<String> exited = new HashSet<>(oldRegions);
        exited.removeAll(newRegions);

        playerRegions.put(player.getUniqueId(), newRegions);

        String worldName = to.getWorld().getName();
        List<RegionEntry> entries = cache.getEventEntries(worldName);

        for (RegionEntry entry : entries) {
            String key = worldName + ":" + entry.region().getId();
            RegionEventData data = entry.data();

            if (entered.contains(key)) {
                fireEnterEvents(player, data);
            }
            if (exited.contains(key)) {
                fireExitEvents(player, data);
            }
        }
    }

    private Set<String> getRegionKeys(Location loc) {
        if (loc.getWorld() == null) return Collections.emptySet();
        RegionManager rm = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(loc.getWorld()));
        if (rm == null) return Collections.emptySet();

        Set<String> keys = new HashSet<>();
        String worldName = loc.getWorld().getName();
        com.sk89q.worldedit.math.BlockVector3 bv = com.sk89q.worldedit.math.BlockVector3.at(
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        for (ProtectedRegion region : rm.getApplicableRegions(bv)) {
            keys.add(worldName + ":" + region.getId());
        }
        return keys;
    }

    private void fireEnterEvents(Player player, RegionEventData data) {
        String name = player.getName();

        if (data.enterCommand() != null) {
            String cmd = data.enterCommand().replace("%player%", name);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        if (data.enterMessage() != null) {
            player.sendMessage(colorize(data.enterMessage().replace("%player%", name)));
        }

        if (data.enterTitle() != null) {
            String[] parts = data.enterTitle().split(";", 2);
            Component title = colorize(parts[0].replace("%player%", name));
            Component subtitle = parts.length > 1
                    ? colorize(parts[1].replace("%player%", name))
                    : Component.empty();
            player.showTitle(Title.title(title, subtitle,
                    Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(1))));
        }

        if (data.enterActionbar() != null) {
            player.sendActionBar(colorize(data.enterActionbar().replace("%player%", name)));
        }
    }

    private void fireExitEvents(Player player, RegionEventData data) {
        String name = player.getName();

        if (data.exitCommand() != null) {
            String cmd = data.exitCommand().replace("%player%", name);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        if (data.exitMessage() != null) {
            player.sendMessage(colorize(data.exitMessage().replace("%player%", name)));
        }
    }

    private Component colorize(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}

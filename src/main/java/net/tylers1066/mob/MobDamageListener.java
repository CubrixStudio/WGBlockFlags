package net.tylers1066.mob;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Allows players to damage mobs that are tracked by the farm-zone spawn system,
 * regardless of WorldGuard region protection.
 *
 * <p>WorldGuard cancels entity-damage events for non-members at NORMAL priority.
 * By running at HIGH with ignoreCancelled=false we re-allow damage specifically
 * for zone mobs so players can farm them without needing region membership.
 */
public class MobDamageListener implements Listener {

    private final MobModule module;

    public MobDamageListener(MobModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        MobSpawnManager mgr = module.getManager();
        if (mgr == null) return;

        if (!mgr.isTracked(event.getEntity().getUniqueId())) return;

        event.setCancelled(false);
    }
}

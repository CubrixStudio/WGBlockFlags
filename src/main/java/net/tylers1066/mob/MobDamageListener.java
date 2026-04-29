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
 * <p>WorldGuard cancels entity-damage events at NORMAL priority. Running at
 * HIGHEST with ignoreCancelled=false ensures we always execute last and can
 * definitively re-allow damage on zone mobs for any player.
 */
public class MobDamageListener implements Listener {

    private final MobModule module;

    public MobDamageListener(MobModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        MobSpawnManager mgr = module.getManager();
        if (mgr == null) return;

        if (!mgr.isTracked(event.getEntity().getUniqueId())) return;

        event.setCancelled(false);
    }
}

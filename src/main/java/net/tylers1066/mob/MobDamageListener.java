package net.tylers1066.mob;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Allows players to damage farm-zone mobs regardless of WorldGuard region protection.
 *
 * <p>Two-stage approach without any permission manipulation:
 * <ol>
 *   <li>LOWEST — cancel before other plugins run. Plugins using
 *       {@code ignoreCancelled=true} (standard WorldGuard at HIGH) will skip
 *       the event entirely and never send a deny message.</li>
 *   <li>HIGHEST — re-allow after all plugins have run so damage is applied.</li>
 * </ol>
 *
 * <p>If messages still appear, the WorldGuard region needs:
 * {@code /rg flag <zone> entity-damage allow}
 */
public class MobDamageListener implements Listener {

    private final MobModule module;

    public MobDamageListener(MobModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityDamageEarly(EntityDamageByEntityEvent event) {
        if (!isTrackedPlayerAttack(event)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamageLate(EntityDamageByEntityEvent event) {
        if (!isTrackedPlayerAttack(event)) return;
        event.setCancelled(false);
    }

    private boolean isTrackedPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return false;
        MobSpawnManager mgr = module.getManager();
        return mgr != null && mgr.isTracked(event.getEntity().getUniqueId());
    }
}

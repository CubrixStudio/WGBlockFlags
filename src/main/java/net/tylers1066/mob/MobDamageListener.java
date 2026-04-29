package net.tylers1066.mob;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Allows players to damage farm-zone mobs without WorldGuard spam.
 *
 * <p>Two-stage approach:
 * <ol>
 *   <li>LOWEST — cancel the event before WorldGuard sees it. WorldGuard runs at
 *       HIGH with {@code ignoreCancelled=true}, so it skips the event entirely
 *       and never sends "Sorry, but you can't harm that here."</li>
 *   <li>HIGHEST — re-allow the event after all other plugins have processed it,
 *       so the damage is actually applied to the mob.</li>
 * </ol>
 */
public class MobDamageListener implements Listener {

    private final MobModule module;

    public MobDamageListener(MobModule module) {
        this.module = module;
    }

    /** Stage 1 — cancel early so WorldGuard (HIGH, ignoreCancelled=true) skips entirely. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityDamageEarly(EntityDamageByEntityEvent event) {
        if (!isTrackedPlayerAttack(event)) return;
        event.setCancelled(true);
    }

    /** Stage 2 — re-allow after all protection plugins have run. */
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

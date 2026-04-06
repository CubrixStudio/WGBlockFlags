package net.tylers1066.mob;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Decrements the per-zone population counter when a tracked mob dies.
 *
 * <p>References {@link MobModule} (not {@link MobSpawnManager} directly) so that
 * it always calls the <em>current</em> manager even after a {@code /wgbf reload},
 * which replaces the manager instance.
 */
public class MobZoneDeathListener implements Listener {

    private final MobModule module;

    public MobZoneDeathListener(MobModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        MobSpawnManager mgr = module.getManager();
        if (mgr != null) {
            mgr.recordDeath(event.getEntity().getUniqueId());
        }
    }
}

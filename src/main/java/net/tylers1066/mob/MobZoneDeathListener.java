package net.tylers1066.mob;

import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Decrements the per-zone population counter when a tracked mob is removed from
 * the world for any reason (death, despawn, plugin command, etc.) except chunk
 * unload — unloaded mobs persist on disk and come back when the chunk reloads.
 *
 * <p>References {@link MobModule} so it always uses the current manager after
 * a {@code /wgbf reload}.
 */
public class MobZoneDeathListener implements Listener {

    private final MobModule module;

    public MobZoneDeathListener(MobModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.getCause() == EntityRemoveEvent.Cause.UNLOAD) {
            return;
        }
        MobSpawnManager mgr = module.getManager();
        if (mgr != null) {
            mgr.recordDeath(event.getEntity().getUniqueId());
        }
    }
}

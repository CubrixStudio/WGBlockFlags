package net.tylers1066.mob;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Allows players to damage farm-zone mobs without WorldGuard (or similar) spam.
 *
 * <p>Two combined defences:
 * <ol>
 *   <li>LOWEST cancel — skips listeners registered with {@code ignoreCancelled=true}
 *       (standard WorldGuard uses this at HIGH). No message is ever sent.</li>
 *   <li>Temporary {@code worldguard.region.bypass.<world>} permission — silences
 *       plugins that use {@code ignoreCancelled=false} and still read permissions
 *       before sending a deny message.</li>
 *   <li>HIGHEST re-allow — ensures the damage is applied regardless of what
 *       other plugins did between LOWEST and HIGHEST.</li>
 * </ol>
 */
public class MobDamageListener implements Listener {

    private final MobModule module;
    /** Temporary bypass attachments keyed by player UUID, removed at HIGHEST. */
    private final Map<UUID, PermissionAttachment> bypassAttachments = new HashMap<>();

    public MobDamageListener(MobModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityDamageEarly(EntityDamageByEntityEvent event) {
        if (!isTrackedPlayerAttack(event)) return;
        Player player = (Player) event.getDamager();

        // Cancel early — plugins with ignoreCancelled=true (e.g. WorldGuard) will skip.
        event.setCancelled(true);

        // Temporarily grant WG bypass permission in case the plugin uses ignoreCancelled=false
        // and sends the deny message before checking the cancel flag.
        String bypassPerm = "worldguard.region.bypass." + event.getEntity().getWorld().getName();
        PermissionAttachment att = player.addAttachment(module.getPlugin(), bypassPerm, true);
        bypassAttachments.put(player.getUniqueId(), att);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamageLate(EntityDamageByEntityEvent event) {
        if (!isTrackedPlayerAttack(event)) return;
        Player player = (Player) event.getDamager();

        // Re-allow so damage is actually applied.
        event.setCancelled(false);

        // Remove the temporary bypass now that all protection plugins have run.
        PermissionAttachment att = bypassAttachments.remove(player.getUniqueId());
        if (att != null) {
            player.removeAttachment(att);
        }
    }

    private boolean isTrackedPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return false;
        MobSpawnManager mgr = module.getManager();
        return mgr != null && mgr.isTracked(event.getEntity().getUniqueId());
    }
}

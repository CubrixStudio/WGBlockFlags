package net.tylers1066.mob;

import net.tylers1066.WGBlockFlags;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lets any player damage any mob, regardless of WorldGuard region protection,
 * while still respecting Citizens NPC protection.
 *
 * <p>Mechanism: scoped permission bypass. At {@link EventPriority#LOWEST} the
 * listener grants {@code worldguard.region.bypass.<world>} as a temporary
 * {@link PermissionAttachment}; at {@link EventPriority#MONITOR} the
 * attachment is removed. Bukkit fires the listener chain synchronously, so
 * the bypass exists only for the duration of one damage event — the player
 * has no special permissions outside that single check.
 *
 * <p>Excluded targets:
 * <ul>
 *   <li>Citizens NPCs (detected via the {@code NPC} metadata key) — they keep
 *       WG protection so quest givers / merchants stay safe.</li>
 *   <li>Players — PvP stays under WorldGuard's control.</li>
 *   <li>Non-{@link Mob} entities (armor stands, item frames, vehicles).</li>
 * </ul>
 */
public class MobDamageListener implements Listener {

    private final WGBlockFlags plugin;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public MobDamageListener(MobModule module) {
        this.plugin = module.getPlugin();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDamageEarly(EntityDamageByEntityEvent event) {
        if (!shouldBypass(event)) return;

        Player player = (Player) event.getDamager();
        UUID uuid = player.getUniqueId();

        // Defensive: clear any stale attachment from a previous event that
        // didn't reach the MONITOR cleanup (e.g. another listener threw).
        PermissionAttachment existing = attachments.remove(uuid);
        if (existing != null) {
            player.removeAttachment(existing);
        }

        PermissionAttachment attach = player.addAttachment(plugin);
        attach.setPermission("worldguard.region.bypass." + player.getWorld().getName(), true);
        attachments.put(uuid, attach);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDamageLate(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        PermissionAttachment attach = attachments.remove(player.getUniqueId());
        if (attach != null) {
            player.removeAttachment(attach);
        }
    }

    private boolean shouldBypass(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return false;
        Entity target = event.getEntity();
        if (!(target instanceof Mob)) return false;
        if (target.hasMetadata("NPC")) return false;
        return true;
    }
}

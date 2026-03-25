package net.tylers1066.listener;

import com.sk89q.worldguard.bukkit.event.block.BreakBlockEvent;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import net.tylers1066.WGBlockFlags;
import net.tylers1066.utils.WGUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

public class BreakListener extends AbstractBlockListener {

    public BreakListener(WGBlockFlags plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BreakBlockEvent e) {
        if (!(e.getCause().getRootCause() instanceof Player player)) {
            return;
        }

        for (Block b : e.getBlocks()) {
            if (!WGUtils.canBuild(player, b)) {
                continue;
            }

            Material type = b.getType();
            ApplicableRegionSet regions = WGUtils.getApplicableRegions(b.getLocation());
            Event.Result result = evaluateFlags(player, type, regions, FlagAction.BREAK);

            if (result == Event.Result.ALLOW) {
                if (e.getResult() == Event.Result.DEFAULT) {
                    e.setResult(Event.Result.ALLOW);
                }
                return;
            } else if (result == Event.Result.DENY) {
                e.setResult(Event.Result.DENY);
                sendDenyMessage(player, type, FlagAction.BREAK);
                return;
            }
        }
    }
}

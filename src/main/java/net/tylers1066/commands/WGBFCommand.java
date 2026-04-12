package net.tylers1066.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tylers1066.WGBlockFlags;
import net.tylers1066.config.LanguageConfig;
import net.tylers1066.config.PluginConfig;
import net.tylers1066.mob.MobModule;
import net.tylers1066.mob.MobSpawnManager;
import net.tylers1066.mob.MobSpawnManager.ZoneSummary;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class WGBFCommand implements CommandExecutor {
    private final WGBlockFlags plugin;

    public WGBFCommand(WGBlockFlags plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        LanguageConfig lang = plugin.getLanguageConfig();

        if (!sender.hasPermission("wgblockflags.admin")) {
            lang.send(sender, "cmd-no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadPluginConfig();
                lang.send(sender, "cmd-reload-success");
            }
            case "info" -> sendInfo(sender);
            case "mobs" -> sendMobs(sender);
            case "help" -> sendHelp(sender);
            default -> lang.send(sender, "cmd-unknown-subcommand");
        }
        return true;
    }

    private void sendMobs(CommandSender sender) {
        MobModule mobModule = plugin.getMobModule();
        MobSpawnManager manager = (mobModule != null) ? mobModule.getManager() : null;

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Zones de Spawn — MythicMobs", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));

        if (manager == null) {
            sender.sendMessage(Component.text("  Module désactivé (MythicMobs absent ou non supporté).",
                    NamedTextColor.RED));
            sender.sendMessage(Component.empty());
            return;
        }

        List<ZoneSummary> zones = manager.getZoneSummaries();
        if (zones.isEmpty()) {
            sender.sendMessage(Component.text("  Aucune zone configurée avec mob-autospawn=allow.",
                    NamedTextColor.GRAY));
            sender.sendMessage(Component.empty());
            return;
        }

        for (ZoneSummary zone : zones) {
            boolean full = zone.current() >= zone.maxMobs();
            NamedTextColor countColor = full ? NamedTextColor.RED : NamedTextColor.GREEN;

            String mobList = String.join(", ", zone.mobTypes());

            sender.sendMessage(
                Component.text("  " + zone.regionId(), NamedTextColor.YELLOW)
                    .append(Component.text(" [" + zone.worldName() + "]", NamedTextColor.DARK_GRAY))
                    .append(Component.text("  ", NamedTextColor.WHITE))
                    .append(Component.text(mobList, NamedTextColor.AQUA))
                    .append(Component.text("  ", NamedTextColor.WHITE))
                    .append(Component.text(zone.current() + "/" + zone.maxMobs(), countColor))
            );
        }
        sender.sendMessage(Component.empty());
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("WGBlockFlags Commands", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("/wgbf reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Recharger la configuration", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wgbf info", NamedTextColor.YELLOW)
                .append(Component.text(" - Informations sur le plugin", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wgbf mobs", NamedTextColor.YELLOW)
                .append(Component.text(" - Population des zones de spawn MythicMobs", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wgbf help", NamedTextColor.YELLOW)
                .append(Component.text(" - Afficher cette aide", NamedTextColor.GRAY)));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Available Flags:", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("  allow-blocks, deny-blocks", NamedTextColor.AQUA)
                .append(Component.text(" - All operations", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  allow-block-place, deny-block-place", NamedTextColor.AQUA)
                .append(Component.text(" - Block placement", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  allow-block-break, deny-block-break", NamedTextColor.AQUA)
                .append(Component.text(" - Block breaking", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  allow-block-interact, deny-block-interact", NamedTextColor.AQUA)
                .append(Component.text(" - Block interaction", NamedTextColor.GRAY)));
        sender.sendMessage(Component.empty());
    }

    private void sendInfo(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("WGBlockFlags", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)
                .append(Component.text(" v" + plugin.getDescription().getVersion(), NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("Flags registered: ", NamedTextColor.GRAY)
                .append(Component.text("8", NamedTextColor.WHITE)));
        PluginConfig cfg = plugin.getPluginConfig();
        sender.sendMessage(Component.text("Debug: ", NamedTextColor.GRAY)
                .append(debugStatus("farm",   cfg.isDebugFarm()))
                .append(Component.text("  "))
                .append(debugStatus("mob",    cfg.isDebugMob()))
                .append(Component.text("  "))
                .append(debugStatus("blocks", cfg.isDebugBlocks())));
        sender.sendMessage(Component.empty());
    }

    private static Component debugStatus(String label, boolean enabled) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(enabled ? "ON" : "OFF",
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
    }
}

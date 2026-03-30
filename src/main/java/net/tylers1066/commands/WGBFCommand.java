package net.tylers1066.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tylers1066.WGBlockFlags;
import net.tylers1066.config.LanguageConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

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
            case "help" -> sendHelp(sender);
            default -> lang.send(sender, "cmd-unknown-subcommand");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("WGBlockFlags Commands", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("/wgbf reload", NamedTextColor.YELLOW)
                .append(Component.text(" - Reload the configuration", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wgbf info", NamedTextColor.YELLOW)
                .append(Component.text(" - Show plugin information", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/wgbf help", NamedTextColor.YELLOW)
                .append(Component.text(" - Show this help message", NamedTextColor.GRAY)));
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
        sender.sendMessage(Component.text("Debug mode: ", NamedTextColor.GRAY)
                .append(Component.text(plugin.getPluginConfig().isDebug() ? "Enabled" : "Disabled",
                        plugin.getPluginConfig().isDebug() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(Component.empty());
    }
}

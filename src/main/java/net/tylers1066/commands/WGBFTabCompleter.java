package net.tylers1066.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WGBFTabCompleter implements TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("reload", "info", "help");

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("wgblockflags.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(input))
                    .toList();
        }

        return List.of();
    }
}

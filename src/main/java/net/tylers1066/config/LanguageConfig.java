package net.tylers1066.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads and provides all player-facing messages from {@code language.yml}.
 *
 * <p>On first load the embedded default {@code language.yml} is copied to the
 * plugin data folder. Subsequent reloads re-read the file, so server operators
 * can edit it without restarting.
 *
 * <p>Usage:
 * <pre>{@code
 * // Send a message with a placeholder replaced:
 * lang.send(player, "deny-break", "{block}", "stone");
 *
 * // Get a Component (null if the message is empty / key missing):
 * Component c = lang.get("farm-protect-immature");
 * }</pre>
 */
public class LanguageConfig {

    private final JavaPlugin plugin;
    private YamlConfiguration config;
    private YamlConfiguration defaults;

    public LanguageConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Reloads the language file from disk.
     * Called automatically on construction and by {@link net.tylers1066.WGBlockFlags#reloadPluginConfig()}.
     */
    public void reload() {
        // Ensure the file exists in the data folder.
        File langFile = new File(plugin.getDataFolder(), "language.yml");
        if (!langFile.exists()) {
            plugin.saveResource("language.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(langFile);

        // Load embedded defaults so missing keys still work after partial edits.
        InputStream defaultStream = plugin.getResource("language.yml");
        if (defaultStream != null) {
            defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
        }
    }

    /**
     * Returns the message for {@code key} with placeholders replaced,
     * or {@code null} if the message is set to {@code ""} or the key does not exist.
     *
     * @param key          the language key (e.g. {@code "deny-break"})
     * @param replacements alternating placeholder/value pairs
     *                     (e.g. {@code "{block}", "stone"})
     */
    public @Nullable Component get(String key, String... replacements) {
        String raw = config.getString(key, null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }

    /**
     * Sends the message for {@code key} to {@code sender} with placeholders replaced.
     * Does nothing if the message is empty or the key is missing.
     */
    public void send(CommandSender sender, String key, String... replacements) {
        Component msg = get(key, replacements);
        if (msg != null) {
            sender.sendMessage(msg);
        }
    }
}

package net.tylers1066.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class PluginConfig {
    private final String denyPlaceMessage;
    private final String denyBreakMessage;
    private final String denyInteractMessage;
    private final boolean debug;
    private final long messageCooldownMs;

    public PluginConfig(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        this.denyPlaceMessage = config.getString("messages.deny-place",
                "&cYou are not allowed to place &e{block} &chere.");
        this.denyBreakMessage = config.getString("messages.deny-break",
                "&cYou are not allowed to break &e{block} &chere.");
        this.denyInteractMessage = config.getString("messages.deny-interact",
                "&cYou are not allowed to interact with &e{block} &chere.");
        this.debug = config.getBoolean("debug", false);
        this.messageCooldownMs = config.getLong("message-cooldown", 2) * 1000L;
    }

    public Component getDenyPlaceMessage(Material block) {
        return formatMessage(denyPlaceMessage, block);
    }

    public Component getDenyBreakMessage(Material block) {
        return formatMessage(denyBreakMessage, block);
    }

    public Component getDenyInteractMessage(Material block) {
        return formatMessage(denyInteractMessage, block);
    }

    public boolean isDebug() {
        return debug;
    }

    public long getMessageCooldownMs() {
        return messageCooldownMs;
    }

    private Component formatMessage(String message, Material block) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        String formatted = message
                .replace("{block}", formatMaterialName(block));
        return LegacyComponentSerializer.legacyAmpersand().deserialize(formatted);
    }

    private String formatMaterialName(Material material) {
        return material.name().toLowerCase().replace('_', ' ');
    }
}

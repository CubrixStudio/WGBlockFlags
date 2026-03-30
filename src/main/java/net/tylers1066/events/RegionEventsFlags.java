package net.tylers1066.events;

import com.sk89q.worldguard.protection.flags.StringFlag;

public class RegionEventsFlags {

    /**
     * Console command executed when a player enters the region.
     * {@code %player%} is replaced with the player's name.
     * Usage: {@code /rg flag <region> region-enter-command "broadcast %player% entered the zone"}
     */
    public static final StringFlag REGION_ENTER_COMMAND = new StringFlag("region-enter-command");

    /**
     * Console command executed when a player exits the region.
     * {@code %player%} is replaced with the player's name.
     * Usage: {@code /rg flag <region> region-exit-command "broadcast %player% left the zone"}
     */
    public static final StringFlag REGION_EXIT_COMMAND = new StringFlag("region-exit-command");

    /**
     * Chat message sent to the player when they enter the region.
     * Supports {@code &} color codes and {@code %player%}.
     * Usage: {@code /rg flag <region> region-enter-message "&aWelcome to the farm zone!"}
     */
    public static final StringFlag REGION_ENTER_MESSAGE = new StringFlag("region-enter-message");

    /**
     * Chat message sent to the player when they exit the region.
     * Supports {@code &} color codes and {@code %player%}.
     * Usage: {@code /rg flag <region> region-exit-message "&7You are leaving the farm zone."}
     */
    public static final StringFlag REGION_EXIT_MESSAGE = new StringFlag("region-exit-message");

    /**
     * Title shown to the player when they enter the region.
     * Format: {@code "Title;Subtitle"} (split on {@code ;}). Subtitle is optional.
     * Supports {@code &} color codes.
     * Usage: {@code /rg flag <region> region-enter-title "Farm Zone;&aWelcome!"}
     */
    public static final StringFlag REGION_ENTER_TITLE = new StringFlag("region-enter-title");

    /**
     * Action-bar text shown to the player when they enter the region.
     * Supports {@code &} color codes.
     * Usage: {@code /rg flag <region> region-enter-actionbar "&e✦ Protected Zone ✦"}
     */
    public static final StringFlag REGION_ENTER_ACTIONBAR = new StringFlag("region-enter-actionbar");

    public static int count() {
        return 6;
    }

    private RegionEventsFlags() {}
}

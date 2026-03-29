package net.tylers1066.mob;

import com.sk89q.worldguard.protection.flags.IntegerFlag;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;

public class MobSpawnFlags {

    /**
     * Enables automatic MythicMobs spawning in the region.
     * Set to {@code allow} to activate.
     * Usage: {@code /rg flag <region> mob-autospawn allow}
     */
    public static final StateFlag MOB_AUTOSPAWN = new StateFlag("mob-autospawn", false);

    /**
     * The set of MythicMobs mob names to spawn (case-sensitive).
     * Must not be empty for the system to activate.
     * Usage: {@code /rg flag <region> mob-spawn-mobs SkeletonKing Zombie_Warrior}
     */
    public static final SetFlag<String> MOB_SPAWN_MOBS = new SetFlag<>("mob-spawn-mobs", new StringFlag(null));

    /**
     * Ticks between each spawn cycle for this region.
     * Overrides the global {@code mob-spawn.spawn-interval} config default.
     * Usage: {@code /rg flag <region> mob-spawn-interval 400}
     */
    public static final IntegerFlag MOB_SPAWN_INTERVAL = new IntegerFlag("mob-spawn-interval");

    /**
     * Maximum number of mobs from this region's list that can be alive simultaneously
     * inside the region before spawning is suppressed.
     * Usage: {@code /rg flag <region> mob-spawn-max 5}
     */
    public static final IntegerFlag MOB_SPAWN_MAX = new IntegerFlag("mob-spawn-max");

    /**
     * Number of mobs to attempt spawning per cycle (capped by {@link #MOB_SPAWN_MAX}).
     * Usage: {@code /rg flag <region> mob-spawn-count 2}
     */
    public static final IntegerFlag MOB_SPAWN_COUNT = new IntegerFlag("mob-spawn-count");

    /**
     * Minimum mob level for spawned mobs. The actual level is chosen randomly in
     * [{@link #MOB_SPAWN_LEVEL_MIN}, {@link #MOB_SPAWN_LEVEL_MAX}].
     * Usage: {@code /rg flag <region> mob-spawn-level-min 5}
     */
    public static final IntegerFlag MOB_SPAWN_LEVEL_MIN = new IntegerFlag("mob-spawn-level-min");

    /**
     * Maximum mob level for spawned mobs.
     * Usage: {@code /rg flag <region> mob-spawn-level-max 10}
     */
    public static final IntegerFlag MOB_SPAWN_LEVEL_MAX = new IntegerFlag("mob-spawn-level-max");

    /**
     * Time-of-day restriction: {@code "any"} (default), {@code "day"}, or {@code "night"}.
     * Usage: {@code /rg flag <region> mob-spawn-time night}
     */
    public static final StringFlag MOB_SPAWN_TIME = new StringFlag("mob-spawn-time");

    /**
     * Weather restriction: {@code "any"} (default), {@code "clear"}, or {@code "rain"}.
     * Usage: {@code /rg flag <region> mob-spawn-weather rain}
     */
    public static final StringFlag MOB_SPAWN_WEATHER = new StringFlag("mob-spawn-weather");

    public static int count() {
        return 9;
    }

    private MobSpawnFlags() {}
}

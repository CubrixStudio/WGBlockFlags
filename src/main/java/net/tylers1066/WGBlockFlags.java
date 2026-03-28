package net.tylers1066;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import net.tylers1066.commands.WGBFCommand;
import net.tylers1066.commands.WGBFTabCompleter;
import net.tylers1066.config.PluginConfig;
import net.tylers1066.farm.FarmFlags;
import net.tylers1066.farm.FarmModule;
import net.tylers1066.flags.Flags;
import net.tylers1066.mob.MobModule;
import net.tylers1066.mob.MobSpawnFlags;
import net.tylers1066.listener.BreakListener;
import net.tylers1066.listener.InteractListener;
import net.tylers1066.listener.PlaceListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class WGBlockFlags extends JavaPlugin {
    private static WGBlockFlags instance;
    private PluginConfig pluginConfig;
    private FarmModule farmModule;
    private MobModule mobModule;

    @Override
    public void onLoad() {
        instance = this;
        FlagRegistry flagRegistry = WorldGuard.getInstance().getFlagRegistry();
        registerFlag(flagRegistry, Flags.ALLOW_BLOCKS);
        registerFlag(flagRegistry, Flags.ALLOW_BLOCK_PLACE);
        registerFlag(flagRegistry, Flags.ALLOW_BLOCK_BREAK);
        registerFlag(flagRegistry, Flags.ALLOW_BLOCK_INTERACT);
        registerFlag(flagRegistry, Flags.DENY_BLOCKS);
        registerFlag(flagRegistry, Flags.DENY_BLOCK_PLACE);
        registerFlag(flagRegistry, Flags.DENY_BLOCK_BREAK);
        registerFlag(flagRegistry, Flags.DENY_BLOCK_INTERACT);

        // Farm flags
        registerFlag(flagRegistry, FarmFlags.FARM_AUTOGROW);
        registerFlag(flagRegistry, FarmFlags.FARM_GROW_INTERVAL);
        registerFlag(flagRegistry, FarmFlags.FARM_AUTOREPLANT);
        registerFlag(flagRegistry, FarmFlags.FARM_CROPS);
        registerFlag(flagRegistry, FarmFlags.FARM_PROTECT_CROPS);

        // Mob spawn flags
        registerFlag(flagRegistry, MobSpawnFlags.MOB_AUTOSPAWN);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_SPAWN_MOBS);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_SPAWN_INTERVAL);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_SPAWN_MAX);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_SPAWN_COUNT);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_SPAWN_LEVEL_MIN);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_SPAWN_LEVEL_MAX);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_SPAWN_TIME);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = new PluginConfig(this);

        getServer().getPluginManager().registerEvents(new PlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new BreakListener(this), this);
        getServer().getPluginManager().registerEvents(new InteractListener(this), this);

        PluginCommand cmd = getCommand("wgbf");
        if (cmd != null) {
            cmd.setExecutor(new WGBFCommand(this));
            cmd.setTabCompleter(new WGBFTabCompleter());
        }

        farmModule = new FarmModule(this);
        farmModule.enable();

        mobModule = new MobModule(this);
        mobModule.enable();

        getLogger().info("WGBlockFlags v" + getDescription().getVersion() + " enabled - "
                + (Flags.count() + FarmFlags.count() + MobSpawnFlags.count()) + " flags registered");
    }

    @Override
    public void onDisable() {
        if (farmModule != null) {
            farmModule.disable();
        }
        if (mobModule != null) {
            mobModule.disable();
        }
        getLogger().info("WGBlockFlags disabled");
    }

    public static WGBlockFlags getInstance() {
        return instance;
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public void reloadPluginConfig() {
        reloadConfig();
        pluginConfig = new PluginConfig(this);
        if (farmModule != null) {
            farmModule.reload();
        }
        if (mobModule != null) {
            mobModule.reload();
        }
    }

    public FarmModule getFarmModule() {
        return farmModule;
    }

    public MobModule getMobModule() {
        return mobModule;
    }

    private void registerFlag(FlagRegistry registry, Flag<?> flag) {
        try {
            registry.register(flag);
        } catch (FlagConflictException e) {
            getLogger().severe("Failed to register flag '" + flag.getName()
                    + "', already registered as '" + registry.get(flag.getName()).getClass().getName() + "'");
        }
    }
}

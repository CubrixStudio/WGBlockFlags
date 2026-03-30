package net.tylers1066;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import net.tylers1066.commands.WGBFCommand;
import net.tylers1066.commands.WGBFTabCompleter;
import net.tylers1066.config.LanguageConfig;
import net.tylers1066.config.PluginConfig;
import net.tylers1066.events.RegionEventsFlags;
import net.tylers1066.events.RegionEventsModule;
import net.tylers1066.farm.FarmFlags;
import net.tylers1066.farm.FarmModule;
import net.tylers1066.flags.Flags;
import net.tylers1066.flags.ItemFlags;
import net.tylers1066.listener.BreakListener;
import net.tylers1066.listener.InteractListener;
import net.tylers1066.listener.ItemListener;
import net.tylers1066.listener.PlaceListener;
import net.tylers1066.mob.MobModule;
import net.tylers1066.mob.MobSpawnFlags;
import net.tylers1066.regen.BlockRegenFlags;
import net.tylers1066.regen.BlockRegenModule;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class WGBlockFlags extends JavaPlugin {
    private static WGBlockFlags instance;
    private PluginConfig pluginConfig;
    private LanguageConfig languageConfig;
    private FarmModule farmModule;
    private MobModule mobModule;
    private BlockRegenModule blockRegenModule;
    private RegionEventsModule regionEventsModule;

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
        registerFlag(flagRegistry, FarmFlags.FARM_ACTIVE_TIME);
        registerFlag(flagRegistry, FarmFlags.FARM_ACTIVE_WEATHER);
        registerFlag(flagRegistry, FarmFlags.FARM_MAX_HEIGHT);
        registerFlag(flagRegistry, FarmFlags.FARM_DROP_MULTIPLIER);
        registerFlag(flagRegistry, FarmFlags.FARM_DROP_RATES);

        // Mob spawn flags
        registerFlag(flagRegistry, MobSpawnFlags.MOB_AUTOSPAWN);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_SPAWN_MOBS);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_SPAWN_INTERVAL);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_SPAWN_MAX);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_SPAWN_COUNT);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_SPAWN_LEVEL_MIN);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_SPAWN_LEVEL_MAX);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_SPAWN_TIME);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_SPAWN_WEATHER);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_DROP_MULTIPLIER);
        registerFlag(flagRegistry, MobSpawnFlags.MOB_DROP_RATES);

        // Item control flags
        registerFlag(flagRegistry, ItemFlags.DENY_ITEM_PICKUP);
        registerFlag(flagRegistry, ItemFlags.DENY_ITEM_DROP);

        // Block regen flags
        registerFlag(flagRegistry, BlockRegenFlags.BLOCK_REGEN);
        registerFlag(flagRegistry, BlockRegenFlags.BLOCK_REGEN_DELAY);
        registerFlag(flagRegistry, BlockRegenFlags.BLOCK_REGEN_MATERIALS);

        // Region events flags
        registerFlag(flagRegistry, RegionEventsFlags.REGION_ENTER_COMMAND);
        registerFlag(flagRegistry, RegionEventsFlags.REGION_EXIT_COMMAND);
        registerFlag(flagRegistry, RegionEventsFlags.REGION_ENTER_MESSAGE);
        registerFlag(flagRegistry, RegionEventsFlags.REGION_EXIT_MESSAGE);
        registerFlag(flagRegistry, RegionEventsFlags.REGION_ENTER_TITLE);
        registerFlag(flagRegistry, RegionEventsFlags.REGION_ENTER_ACTIONBAR);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = new PluginConfig(this);
        languageConfig = new LanguageConfig(this);

        getServer().getPluginManager().registerEvents(new PlaceListener(this), this);
        getServer().getPluginManager().registerEvents(new BreakListener(this), this);
        getServer().getPluginManager().registerEvents(new InteractListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemListener(this), this);

        PluginCommand cmd = getCommand("wgbf");
        if (cmd != null) {
            cmd.setExecutor(new WGBFCommand(this));
            cmd.setTabCompleter(new WGBFTabCompleter());
        }

        farmModule = new FarmModule(this);
        farmModule.enable();

        mobModule = new MobModule(this);
        mobModule.enable();

        blockRegenModule = new BlockRegenModule(this);
        blockRegenModule.enable();

        regionEventsModule = new RegionEventsModule(this);
        regionEventsModule.enable();

        int totalFlags = Flags.count() + FarmFlags.count() + MobSpawnFlags.count()
                + ItemFlags.count() + BlockRegenFlags.count() + RegionEventsFlags.count();
        getLogger().info("WGBlockFlags v" + getDescription().getVersion() + " enabled - "
                + totalFlags + " flags registered");
    }

    @Override
    public void onDisable() {
        if (farmModule != null) {
            farmModule.disable();
        }
        if (mobModule != null) {
            mobModule.disable();
        }
        if (blockRegenModule != null) {
            blockRegenModule.disable();
        }
        if (regionEventsModule != null) {
            regionEventsModule.disable();
        }
        getLogger().info("WGBlockFlags disabled");
    }

    public static WGBlockFlags getInstance() {
        return instance;
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public LanguageConfig getLanguageConfig() {
        return languageConfig;
    }

    public void reloadPluginConfig() {
        reloadConfig();
        pluginConfig = new PluginConfig(this);
        languageConfig.reload();
        if (farmModule != null) {
            farmModule.reload();
        }
        if (mobModule != null) {
            mobModule.reload();
        }
        if (blockRegenModule != null) {
            blockRegenModule.reload();
        }
        if (regionEventsModule != null) {
            regionEventsModule.reload();
        }
    }

    public FarmModule getFarmModule() {
        return farmModule;
    }

    public MobModule getMobModule() {
        return mobModule;
    }

    public BlockRegenModule getBlockRegenModule() {
        return blockRegenModule;
    }

    public RegionEventsModule getRegionEventsModule() {
        return regionEventsModule;
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

# WGBlockFlags

Advanced WorldGuard region block management plugin for Minecraft Paper 1.21.4+

A continuation of [WorldGuard Block Restricter](https://dev.bukkit.org/projects/worldguard-block-restricter/).

## Features

### 🌾 Farm Zone Management
Automatically manage crop growth and harvesting within WorldGuard regions:

- **Auto-Grow**: Crops grow automatically at configurable intervals (default: 20 seconds)
- **Auto-Replant**: Harvested crops are automatically replanted with seeds
- **Crop Protection**: Prevent players from breaking immature crops in protected farm zones
- **Multiple Crop Support**: Wheat, carrots, potatoes, beetroots, melons, pumpkins, nether wart, sugar cane, bamboo, cactus, kelp, sweet berries, cocoa, weeping/cave vines, and more

### 👹 MythicMobs Spawn Management
Control custom mob spawning in WorldGuard regions:

- **Auto-Spawn**: Custom MythicMobs spawn automatically at configurable intervals
- **Per-Region Settings**: Configure spawn rate, count, and max mobs per region
- **Mob Type Filtering**: Select which MythicMobs spawn in each region
- **Time & Weather Conditions**: Restrict spawning to day/night or clear/rain weather

### 🔁 Block Regeneration
Automatically restore broken blocks after a configurable delay:

- **Configurable Delay**: Per-region delay before regeneration (default 60 seconds)
- **Material Filtering**: Specify which blocks regenerate (empty = all blocks)
- **Player-Place Awareness**: Blocks placed by players are excluded from regen

### 📦 Item Control
Restrict item pickup and drop in specific regions:

- **Deny Pickup**: Prevent players from picking up specific item types
- **Deny Drop**: Prevent players from dropping specific item types
- **Bypass Permissions**: Ops can bypass restrictions via permission nodes

### 📢 Region Events
Execute actions when players enter or exit WorldGuard regions:

- **Enter/Exit Commands**: Execute console commands on region transitions
- **Enter/Exit Messages**: Send colored chat messages to the player
- **Enter Titles**: Display title + subtitle on region entry
- **Enter Action-Bar**: Show action-bar text on region entry
- **Placeholder Support**: Use `%player%` in all text/commands

### 🛡️ Block Protection
Granular block control within WorldGuard regions:

- **Allow/Deny Blocks**: Restrict specific block types from being placed or broken
- **Block-Specific Actions**: Control placement, breaking, and interaction separately
- **Dual-Flag System**: Use both allow and deny flags for flexible permission control

## Installation

1. Download the latest build from [GitHub Actions](https://github.com/TylerS1066/WGBlockFlags/releases)
2. Place the JAR in your server's `plugins/` folder
3. Restart the server
4. Configure `plugins/WGBlockFlags/config.yml` (created on first run)

## Requirements

- **Minecraft**: 1.21.4+ (Paper/Spigot)
- **WorldGuard**: 7.0.12+
- **WorldEdit**: 7.3.6+
- **Optional**: MythicMobs 5.x (for mob spawn features)

## Configuration

### config.yml

```yaml
debug: false

# Messages for block protection
messages:
  deny-place: "&cYou are not allowed to place &e{block} &chere."
  deny-break: "&cYou are not allowed to break &e{block} &chere."
  deny-interact: "&cYou are not allowed to interact with &e{block} &chere."
  message-cooldown: 2 # seconds between repeated messages

# Farm zone settings
farm:
  grow-interval: 400 # ticks (20 = 1 second, 400 = 20 seconds default)
  min-grow-interval: 20 # minimum allowed interval per region
  replant-only-mature: true # only replant fully-grown crops when harvested
  suppress-drops-on-replant: false # remove seeds/items on auto-replant

# MythicMobs spawn settings
mob-spawn:
  spawn-interval: 400 # ticks between spawn attempts
  max-mobs: 5 # maximum mobs in a region
  spawn-count: 1 # mobs per spawn attempt
  default-level-min: 1 # default mob level range
  default-level-max: 1
  spawn-attempts: 20 # block checks per spawn attempt

# Block regeneration settings
block-regen:
  default-delay: 1200 # ticks (20 = 1 second, 1200 = 60 seconds)
```

## Permissions

| Permission | Description | Default |
|---|---|---|
| `wgblockflags.admin` | Access admin commands (/wgbf) | Op |
| `wgblockflags.farm.harvest` | Break immature crops in protected farms | Op |
| `wgblockflags.item.pickup.bypass` | Bypass `deny-item-pickup` restrictions | Op |
| `wgblockflags.item.drop.bypass` | Bypass `deny-item-drop` restrictions | Op |

## Commands

### /wgbf

Main command for WGBlockFlags administration.

**Subcommands:**
- `/wgbf reload` - Reload configuration (no server restart needed)
- `/wgbf info` - Show plugin information and flag count
- `/wgbf help` - Display command help

## WorldGuard Flags

### Block Protection Flags

| Flag | Type | Default | Usage |
|---|---|---|---|
| `allow-blocks` | Set of Materials | - | `/rg flag <region> allow-blocks dirt,grass` |
| `allow-block-place` | Set of Materials | - | Allow placement of specific blocks |
| `allow-block-break` | Set of Materials | - | Allow breaking of specific blocks |
| `allow-block-interact` | Set of Materials | - | Allow interaction with specific blocks |
| `deny-blocks` | Set of Materials | - | Prevent all actions on specific blocks |
| `deny-block-place` | Set of Materials | - | Prevent placement of specific blocks |
| `deny-block-break` | Set of Materials | - | Prevent breaking of specific blocks |
| `deny-block-interact` | Set of Materials | - | Prevent interaction with specific blocks |

### Farm Zone Flags

| Flag | Type | Default | Usage |
|---|---|---|---|
| `farm-autogrow` | State (allow/deny) | deny | Enable auto-grow in region |
| `farm-grow-interval` | Integer (ticks) | config default | Growth speed per region |
| `farm-autoreplant` | State (allow/deny) | deny | Enable auto-replant in region |
| `farm-crops` | Set of Materials | all | Crops managed in region |
| `farm-protect-crops` | State (allow/deny) | deny | Prevent breaking immature crops |
| `farm-active-time` | String | any | Grow only at `day`, `night`, or `any` |
| `farm-active-weather` | String | any | Grow only in `clear`, `rain`, or `any` weather |
| `farm-max-height` | Integer | crop default | Max height for vertical crops (sugar cane, cactus…) |
| `farm-drop-multiplier` | Integer (%) | 100 | Global drop rate for all crops (100=normal, 200=double, 0=no drops) |
| `farm-drop-rates` | String | — | Per-crop drop rates: `"wheat:200,carrots:150,potatoes:0"` |

### MythicMobs Flags

| Flag | Type | Default | Usage |
|---|---|---|---|
| `mob-autospawn` | State (allow/deny) | deny | Enable auto-spawn in region |
| `mob-spawn-mobs` | Set of Strings | - | MythicMobs to spawn (by name) |
| `mob-spawn-interval` | Integer (ticks) | config default | Spawn attempt frequency |
| `mob-spawn-max` | Integer | config default | Max mobs in region |
| `mob-spawn-count` | Integer | config default | Mobs per spawn attempt |
| `mob-spawn-level-min` | Integer | config default | Minimum mob level |
| `mob-spawn-level-max` | Integer | config default | Maximum mob level |
| `mob-spawn-time` | String | any | Spawn time (`day`/`night`/`any`) |
| `mob-spawn-weather` | String | any | Spawn weather (`clear`/`rain`/`any`) |
| `mob-drop-multiplier` | Integer (%) | 100 | Global drop rate for all mobs (100=normal, 200=double, 0=no drops) |
| `mob-drop-rates` | String | — | Per-entity-type drop rates: `"zombie:200,skeleton:150,creeper:0"` |

### Item Control Flags

| Flag | Type | Default | Usage |
|---|---|---|---|
| `deny-item-pickup` | Set of Materials | - | Prevent picking up specific items |
| `deny-item-drop` | Set of Materials | - | Prevent dropping specific items |

### Block Regeneration Flags

| Flag | Type | Default | Usage |
|---|---|---|---|
| `block-regen` | State (allow/deny) | deny | Enable block regen in region |
| `block-regen-delay` | Integer (ticks) | config default | Ticks before block regenerates |
| `block-regen-materials` | Set of Materials | all | Which blocks regenerate |

### Region Events Flags

| Flag | Type | Default | Usage |
|---|---|---|---|
| `region-enter-command` | String | - | Console command on player entry |
| `region-exit-command` | String | - | Console command on player exit |
| `region-enter-message` | String | - | Chat message on player entry |
| `region-exit-message` | String | - | Chat message on player exit |
| `region-enter-title` | String | - | Title on entry: `"Title;Subtitle"` |
| `region-enter-actionbar` | String | - | Action-bar text on entry |

## Usage Examples

### Farm Zone Setup

Create a region with automatic crop growth and protection:

```
/rg define farmzone
/rg flag farmzone farm-autogrow allow
/rg flag farmzone farm-autoreplant allow
/rg flag farmzone farm-protect-crops allow
/rg flag farmzone farm-grow-interval 200
/rg flag farmzone farm-crops wheat,carrots,potatoes,beetroots
```

Now crops in this region grow every 10 seconds, replant automatically, and cannot be broken while immature.

### Block Protection

Prevent a specific block type from being placed:

```
/rg flag myregion deny-block-place bedrock,obsidian
```

Allow only specific blocks to be broken:

```
/rg flag myregion allow-block-break dirt,grass,stone
```

### Farm Zone with Time & Weather Conditions

Only grow crops during the day in clear weather:

```
/rg flag farmzone farm-autogrow allow
/rg flag farmzone farm-active-time day
/rg flag farmzone farm-active-weather clear
/rg flag farmzone farm-max-height 5
```

### Block Regeneration Zone

Create a mining zone where ores regenerate after 1 minute:

```
/rg define miningzone
/rg flag miningzone block-regen allow
/rg flag miningzone block-regen-delay 1200
/rg flag miningzone block-regen-materials coal_ore,iron_ore,gold_ore,diamond_ore
```

### Item Control Zone

Prevent players from picking up or dropping diamonds in a zone:

```
/rg flag tradezone deny-item-pickup diamond,emerald
/rg flag tradezone deny-item-drop diamond,emerald
```

### Region Events

Show a welcome message and title when entering a dungeon:

```
/rg define dungeon
/rg flag dungeon region-enter-message "&6Welcome to the dungeon, %player%!"
/rg flag dungeon region-exit-message "&7You have left the dungeon."
/rg flag dungeon region-enter-title "&4Dungeon;&cEnter at your own risk!"
/rg flag dungeon region-enter-actionbar "&e⚔ Danger Zone ⚔"
/rg flag dungeon region-enter-command "broadcast %player% entered the dungeon!"
```

### Custom Drop Rates for Crops

Double all crop drops in a farm zone, but triple wheat specifically:

```
/rg flag farmzone farm-drop-multiplier 200
/rg flag farmzone farm-drop-rates "wheat:300,potatoes:0"
```

`farm-drop-rates` takes per-crop priority over `farm-drop-multiplier`.
Valid crop names: `wheat`, `carrots`, `potatoes`, `beetroots`, `nether_wart`, `sweet_berry_bush`, etc. (Bukkit `Material` names, lowercase).

### Custom Drop Rates for Mobs

Bonus loot from zombies, but no drops from creepers in a dungeon zone:

```
/rg flag dungeon mob-drop-multiplier 150
/rg flag dungeon mob-drop-rates "zombie:300,creeper:0,skeleton:200"
```

`mob-drop-rates` takes per-type priority over `mob-drop-multiplier`.
Valid mob names: Bukkit `EntityType` names in lowercase (`zombie`, `skeleton`, `creeper`, `spider`, `enderman`, etc.).
Both vanilla mobs and MythicMobs spawned entities are affected.

### MythicMobs Spawning

Set up automatic custom mob spawning:

```
/rg define spawnzone
/rg flag spawnzone mob-autospawn allow
/rg flag spawnzone mob-spawn-mobs Guardian,Skeleton_Warrior
/rg flag spawnzone mob-spawn-interval 300
/rg flag spawnzone mob-spawn-max 10
/rg flag spawnzone mob-spawn-count 2
/rg flag spawnzone mob-spawn-level-min 5
/rg flag spawnzone mob-spawn-level-max 10
```

## Debug Mode

Enable debug mode in `config.yml` to see detailed logging:

```yaml
debug: true
```

Then reload: `/wgbf reload`

Debug logs will show:
- Chunks being scanned for crops
- Blocks being tracked for growth
- Spawn attempts and results
- Block protection events

## Version Support

| Branch | Minecraft | WorldGuard | Status |
|---|---|---|---|
| `legacy` | 1.12.2 | 6.2.x | Legacy |
| `main` | 1.21.4+ | 7.0.12+ | **Current** |

## Building

Requirements: Java 21, Maven 3.8+

```bash
mvn clean package
```

The JAR will be in `target/WGBlockFlags-*.jar`

## Architecture

- **Modular Design**: Farm, mob spawn, and block protection are separate, independent modules
- **Track-on-Demand**: Crops only tracked when loaded; unloaded chunks don't consume resources
- **Independent Clock**: Auto-grow uses internal tick counter, works regardless of `randomTickSpeed`
- **Batch Processing**: Chunk scans spread across multiple ticks to prevent TPS drops on reload
- **O(k log n) Scheduling**: Growth queue uses TreeMap, only processes due blocks each tick

## Troubleshooting

### Crops not growing

1. Check that `farm-autogrow=allow` is set on the region: `/rg info <region>`
2. Verify the crop type is in the `farm-crops` list (or list is empty = all crops)
3. Enable debug mode and reload: `/wgbf reload`
4. Look for "Tracked WHEAT at..." messages in logs to confirm crops are being tracked
5. Wait for the configured grow interval (default 20 seconds)

### Mobs not spawning

1. Verify `mob-autospawn=allow` and `mob-spawn-mobs` are set
2. Check that MythicMobs is installed and mobs exist: `/mm mobs` in-game
3. Ensure mob spawn conditions are met (time of day, max mobs not exceeded)
4. Enable debug mode to see spawn attempts

### Performance issues on reload

The plugin batches chunk scans (1 per tick) to prevent TPS drops. Large farms may take 10-60 seconds to fully scan, but without any main-thread freeze.

## Development

- **Language**: Java 21
- **Build System**: Maven
- **Dependencies**: WorldGuard 7.0.12, WorldEdit 7.3.6, Paper 1.21.4 API
- **Latest Release**: See [Releases](https://github.com/TylerS1066/WGBlockFlags/releases)

## License

This project is a continuation of the original WorldGuard Block Restricter. See LICENSE file.

## Contributors

- TylerS1066 (Main Developer)
- SharkBlack3D (Co-Developer)

## Support

For issues and feature requests: [GitHub Issues](https://github.com/TylerS1066/WGBlockFlags/issues)

---

**Last Updated**: March 2026 - Version 2.2 (Drop rate management per zone and per type for crops and mobs)

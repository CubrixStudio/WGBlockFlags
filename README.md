# WGBlockFlags

Plugin Paper 1.21.4+ de gestion avancée des régions WorldGuard.

Continuation de [WorldGuard Block Restricter](https://dev.bukkit.org/projects/worldguard-block-restricter/).

---

## Fonctionnalités

### 🌾 Zones de Farm
Gestion automatique de la pousse et de la récolte des cultures dans des régions WorldGuard.

- **Auto-pousse** : les cultures poussent automatiquement à intervalles configurables
- **Auto-replant** : les cultures récoltées sont replantées automatiquement
- **Protection des cultures** : empêche les joueurs de casser des cultures immatures
- **Multiplicateurs de drops** : bonus/malus de loot par culture, par zone

### 👹 Spawn de Mobs MythicMobs
Spawn automatique de mobs personnalisés dans des zones WorldGuard.

- **Auto-spawn** : les mobs spawnent automatiquement selon un intervalle configurable
- **Cap de population** : limite le nombre de mobs vivants par zone
- **Conditions** : restriction par heure du jour et météo
- **Multiplicateurs de drops** : bonus/malus de loot par type d'entité, par zone

### 🔁 Régénération de Blocs
Restauration automatique des blocs cassés après un délai configurable.

### 📦 Contrôle d'Items
Restriction du ramassage et du dépôt d'items dans des zones spécifiques.

### 📢 Événements de Région
Exécution d'actions lors de l'entrée/sortie de régions WorldGuard.

### 🛡️ Protection de Blocs
Contrôle granulaire des blocs (placement, casse, interaction) par matériau.

---

## Installation

1. Placer le JAR dans le dossier `plugins/` du serveur
2. Redémarrer le serveur
3. Configurer `plugins/WGBlockFlags/config.yml`

## Prérequis

- **Minecraft** : 1.21.4+ (Paper)
- **WorldGuard** : 7.0.12+
- **WorldEdit** : 7.3.6+
- **Optionnel** : MythicMobs 5.x (pour les fonctionnalités de spawn)

---

## Commandes

Permission requise : `wgblockflags.admin` (op par défaut)

| Commande | Description |
|---|---|
| `/wgbf reload` | Recharge la configuration sans redémarrer |
| `/wgbf info` | Affiche la version, les flags enregistrés et l'état du debug |
| `/wgbf mobs` | Liste toutes les zones de spawn avec le nombre de mobs présents |
| `/wgbf help` | Affiche l'aide |

### `/wgbf mobs` — exemple d'affichage

```
Zones de Spawn — MythicMobs
  bearzone [world]    bear            3/10
  crowzone [world]    md_crow_pet     0/2000
  elkzone  [world]    elk_male        5/40
```

- Le compteur est **vert** si la zone n'est pas pleine, **rouge** si elle est au maximum.
- Le compteur est basé sur les UUIDs des mobs spawnés — pas besoin de charger les chunks.

---

## Configuration (`config.yml`)

```yaml
debug:
  farm: false    # Logs de pousse des cultures (très verbeux)
  mob: false     # Logs des cycles de spawn de mobs
  blocks: false  # Logs des vérifications de flags blocs

messages:
  deny-place: "&cVous ne pouvez pas poser &e{block} &ici."
  deny-break: "&cVous ne pouvez pas casser &e{block} &ici."
  deny-interact: "&cVous ne pouvez pas interagir avec &e{block} &ici."
  message-cooldown: 2  # secondes entre deux messages répétés

farm:
  grow-interval: 400       # ticks entre deux poussées (20 ticks = 1 sec)
  min-grow-interval: 20    # intervalle minimum autorisé par région
  replant-only-mature: true
  suppress-drops-on-replant: false

mob-spawn:
  spawn-interval: 400   # ticks entre deux cycles de spawn
  max-mobs: 5           # maximum de mobs par zone
  spawn-count: 1        # mobs tentés par cycle
  default-level-min: 1
  default-level-max: 1
  spawn-attempts: 20    # essais pour trouver une position valide

block-regen:
  default-delay: 1200   # ticks avant régénération (1200 = 60 sec)
```

---

## Permissions

| Permission | Description | Défaut |
|---|---|---|
| `wgblockflags.admin` | Accès aux commandes `/wgbf` | Op |
| `wgblockflags.farm.harvest` | Casser des cultures immatures dans les zones protégées | Op |
| `wgblockflags.item.pickup.bypass` | Ignorer `deny-item-pickup` | Op |
| `wgblockflags.item.drop.bypass` | Ignorer `deny-item-drop` | Op |

---

## Flags WorldGuard

### Protection de Blocs

| Flag | Type | Description |
|---|---|---|
| `allow-blocks` | Set de Matériaux | Autoriser toutes les actions sur ces blocs |
| `allow-block-place` | Set de Matériaux | Autoriser la pose de ces blocs |
| `allow-block-break` | Set de Matériaux | Autoriser la casse de ces blocs |
| `allow-block-interact` | Set de Matériaux | Autoriser l'interaction avec ces blocs |
| `deny-blocks` | Set de Matériaux | Interdire toutes les actions sur ces blocs |
| `deny-block-place` | Set de Matériaux | Interdire la pose de ces blocs |
| `deny-block-break` | Set de Matériaux | Interdire la casse de ces blocs |
| `deny-block-interact` | Set de Matériaux | Interdire l'interaction avec ces blocs |

```
/rg flag <region> deny-block-place bedrock,obsidian
/rg flag <region> allow-block-break dirt,grass,stone
```

---

### Zones de Farm

| Flag | Type | Défaut | Description |
|---|---|---|---|
| `farm-autogrow` | State | deny | Active la pousse automatique |
| `farm-grow-interval` | Integer (ticks) | config | Vitesse de pousse |
| `farm-autoreplant` | State | deny | Active le replant automatique |
| `farm-crops` | Set de Matériaux | tous | Cultures gérées dans la zone |
| `farm-protect-crops` | State | deny | Empêche la casse des cultures immatures |
| `farm-active-time` | String | any | Pousse uniquement `day`, `night` ou `any` |
| `farm-active-weather` | String | any | Pousse uniquement par `clear`, `rain` ou `any` |
| `farm-max-height` | Integer | défaut culture | Hauteur max pour les cultures verticales |
| `farm-drop-multiplier` | Integer (%) | 100 | Multiplicateur global de drops (100=normal, 200=double) |
| `farm-drop-rates` | String | — | Multiplicateurs par culture : `"wheat:200,carrots:150"` |

```
/rg flag farmzone farm-autogrow allow
/rg flag farmzone farm-autoreplant allow
/rg flag farmzone farm-protect-crops allow
/rg flag farmzone farm-grow-interval 200
/rg flag farmzone farm-crops wheat,carrots,potatoes
/rg flag farmzone farm-active-time day
/rg flag farmzone farm-drop-multiplier 200
/rg flag farmzone farm-drop-rates "wheat:300,potatoes:0"
```

---

### Spawn de Mobs MythicMobs

| Flag | Type | Défaut | Description |
|---|---|---|---|
| `mob-autospawn` | State | deny | Active le spawn automatique |
| `mob-spawn-mobs` | Set de Strings | — | Noms internes MythicMobs à spawner |
| `mob-spawn-interval` | Integer (ticks) | config | Fréquence de spawn |
| `mob-spawn-max` | Integer | config | Maximum de mobs dans la zone |
| `mob-spawn-count` | Integer | config | Mobs spawnés par cycle |
| `mob-spawn-level-min` | Integer | config | Niveau minimum des mobs |
| `mob-spawn-level-max` | Integer | config | Niveau maximum des mobs |
| `mob-spawn-time` | String | any | Spawn uniquement `day`, `night` ou `any` |
| `mob-spawn-weather` | String | any | Spawn uniquement par `clear`, `rain` ou `any` |
| `mob-drop-multiplier` | Integer (%) | 100 | Multiplicateur global de drops de mobs |
| `mob-drop-rates` | String | — | Multiplicateurs par type Bukkit : `"zombie:200,skeleton:150"` |
| `mob-spawn-filter` | State | deny | Active le filtre de spawn naturel |
| `mob-allow-types` | Set de Strings | — | Whitelist de types Bukkit autorisés à spawner naturellement |
| `mob-deny-types` | Set de Strings | — | Blacklist de types Bukkit bloqués |
| `mob-allow-vanilla` | State | allow | `deny` = bloque tous les mobs vanilla dans la zone |

> **Noms de mobs** : les noms sont insensibles à la casse et les tirets/underscores sont équivalents (`bear-polar` = `bear_polar`).

```
/rg flag bearzone mob-autospawn allow
/rg flag bearzone mob-spawn-mobs bear
/rg flag bearzone mob-spawn-interval 200
/rg flag bearzone mob-spawn-max 10
/rg flag bearzone mob-spawn-count 2
/rg flag bearzone mob-spawn-level-min 1
/rg flag bearzone mob-spawn-level-max 5
/rg flag bearzone mob-spawn-time any
/rg flag bearzone mob-drop-multiplier 150

# Vérifier l'état de la zone :
/wgbf mobs
```

---

### Contrôle d'Items

| Flag | Type | Description |
|---|---|---|
| `deny-item-pickup` | Set de Matériaux | Empêche le ramassage de ces items |
| `deny-item-drop` | Set de Matériaux | Empêche le dépôt de ces items |

```
/rg flag tradezone deny-item-pickup diamond,emerald
/rg flag tradezone deny-item-drop diamond,emerald
```

---

### Régénération de Blocs

| Flag | Type | Défaut | Description |
|---|---|---|---|
| `block-regen` | State | deny | Active la régénération |
| `block-regen-delay` | Integer (ticks) | config | Délai avant régénération |
| `block-regen-materials` | Set de Matériaux | tous | Blocs qui régénèrent |

```
/rg flag miningzone block-regen allow
/rg flag miningzone block-regen-delay 1200
/rg flag miningzone block-regen-materials coal_ore,iron_ore,gold_ore,diamond_ore
```

---

### Événements de Région

| Flag | Type | Description |
|---|---|---|
| `region-enter-command` | String | Commande console à l'entrée |
| `region-exit-command` | String | Commande console à la sortie |
| `region-enter-message` | String | Message chat à l'entrée |
| `region-exit-message` | String | Message chat à la sortie |
| `region-enter-title` | String | Titre à l'entrée : `"Titre;Sous-titre"` |
| `region-enter-actionbar` | String | Texte action-bar à l'entrée |

> `%player%` est remplacé par le nom du joueur dans tous les textes et commandes.

```
/rg flag dungeon region-enter-message "&6Bienvenue dans le donjon, %player% !"
/rg flag dungeon region-exit-message "&7Vous avez quitté le donjon."
/rg flag dungeon region-enter-title "&4Donjon;&cEntrez à vos risques !"
/rg flag dungeon region-enter-actionbar "&e⚔ Zone Dangereuse ⚔"
/rg flag dungeon region-enter-command "broadcast %player% est entré dans le donjon !"
```

---

## Mode Debug

Activer le debug par catégorie dans `config.yml` :

```yaml
debug:
  farm: true    # Logs des cultures (chunks scannés, blocs suivis)
  mob: true     # Logs des cycles de spawn (intervalle, capacité, position)
  blocks: true  # Logs des vérifications de flags blocs
```

Puis recharger : `/wgbf reload`

---

## Dépannage

### Les cultures ne poussent pas
1. Vérifier `farm-autogrow=allow` sur la région : `/rg info <region>`
2. Vérifier que le type de culture est dans `farm-crops` (ou liste vide = tous)
3. Activer `debug.farm: true` et recharger pour voir les logs

### Les mobs ne spawnent pas
1. Vérifier `mob-autospawn=allow` et `mob-spawn-mobs` : `/rg info <region>`
2. Vérifier que les mobs existent dans MythicMobs : `/mm mobs`
3. Utiliser `/wgbf mobs` pour voir la population actuelle de chaque zone
4. Activer `debug.mob: true` pour voir les cycles de spawn en détail

### Performances
- Le scan des chunks de farm est étalé sur plusieurs ticks (1 par tick) pour éviter les drops de TPS
- Les zones de spawn ne maintiennent pas de chunks chargés en permanence

---

## Build

Prérequis : Java 21, Maven 3.8+

```bash
mvn clean package
```

Le JAR sera dans `target/WGBlockFlags-*.jar`

---

## Contributeurs

- TylerS1066 (Développeur principal)
- SharkBlack3D (Co-développeur)

## Support

Problèmes et suggestions : [GitHub Issues](https://github.com/TylerS1066/WGBlockFlags/issues)

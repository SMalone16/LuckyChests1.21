# LuckyChests 1.21

A Paper 1.21.11 classroom plugin for the Pawling Eaglercraft server.

Lucky Chests appear on the surface of newly generated chunks. All three types look like ordinary chests until they are opened for the first time:

- **Awesome** — 1–4 different enchanted diamond weapons, tools, or armor pieces, plus a cross-mod support cache: the full vanilla ingredient set for one EaglerAirplane Plane Kit, two 8-minute Water Breathing potions for EaglerSpace, a shield, and two golden apples.
- **Good** — 1–10 iron ingots, 48 torches, and 12–24 cooked beef, plus 1 iron block, 1 redstone block, one 3-minute Water Breathing potion, and one random bonus (diamond, shield, or golden apple).
- **Bad** — a shield plus five skeletons spawned within roughly five blocks.

Chest type and opened state are stored with Paper persistent data, so server restarts do not retrigger a chest.

## Compatibility

- Paper 1.21.11
- Java 21
- Designed for the classroom server's Eaglercraft 1.12.2 client compatibility layer.
- Generated chests are limited to Y 254 or lower by default.
- Loot deliberately uses items and enchantments that older clients can represent.

## Cross-mod support

LuckyChests reads no APIs from the other classroom plugins, so it remains safe to run by itself. The bonus loot is made entirely from vanilla items that the current server-side mods already understand:

- **EaglerAirplaneMod:** Awesome chests contain the exact recipe ingredients for one Plane Kit: 4 iron blocks, 2 diamonds, 2 redstone blocks, and 1 furnace. Good chests provide partial progress toward that recipe.
- **EaglerSpace:** Good chests include a 3-minute Water Breathing potion; Awesome chests include two 8-minute Water Breathing potions. Space Mode treats Water Breathing as an oxygen supply.
- **EaglerZombiesFall26 / EaglerLocustFall26:** shields and golden apples provide useful survival support.
- **TripleJump, Eaglervators, and EaglerSoccer:** these currently do not consume special inventory items, so LuckyChests does not invent unnecessary mod-specific loot for them.

Set `cross-mod-loot.enabled: false` in `config.yml` to restore the original loot-only behavior.

## Testing in-game

Operators can create controlled test chests:

```text
/luckychest spawn awesome
/luckychest spawn good
/luckychest spawn bad
/luckychest spawn random
```

Look at a Lucky Chest and run:

```text
/luckychest inspect
```

to see its hidden type and whether it has already been triggered.

## Configuration

Edit `plugins/LuckyChests/config.yml` after the plugin has run once.

The default generation chance is 8% per newly generated chunk. The chance, placement attempts, vertical range, enabled worlds, and relative type weights are configurable.

## Build

GitHub Actions builds the plugin automatically using Java 21 and Paper 1.21.11. The current compiled JAR is kept at:

```text
dist/LuckyChests-1.0.0.jar
```

The classroom server repository is configured to fetch this JAR automatically during startup.

# ChunkCap

A Paper server plugin that enforces a configurable **per-chunk maximum for each selected block type**. For example, limit each chunk to 16 hoppers and 8 spawners.

## Commands

- `/chunklimit set <block> <max-per-chunk>` — set/update a cap (`0` prevents placement)
- `/chunklimit remove <block>` — remove a cap
- `/chunklimit list` — show configured caps
- `/chunklimit check <block>` — count the block type in your current chunk (players only)
- `/chunklimit antilag <true|false>` — toggle your personal low-particle outline mode
- `/chunklimit antilagcolor <color>` — choose its color; supports 16 common colors and their `light_`/`dark_` variants, such as `light_red`, `blue` or `dark_purple`
- `/chunklimit theme <theme>` — choose one of 15 animated outline palettes while AntiLag is off: aurora, ocean, sunset, forest, royal, ember, glacier, candy, toxic, galaxy, gold, cherry, pastel, monochrome, or neon

Your mode, color, and theme are saved per player UUID in `plugins/ChunkCap/player-settings.yml`
- `/chunklimit reload` — reload `plugins/ChunkCap/config.yml`

Chunk administration commands have separate permission nodes:

- `chunkcap.set` — set or change a block cap
- `chunkcap.remove` — remove a block cap
- `chunkcap.list` — list configured caps
- `chunkcap.check` — check the current chunk's count
- `chunkcap.reload` — reload the configuration
- `chunkcap.antilag` — toggle a player's personal low-particle outline mode
- `chunkcap.antilagcolor` — change a player's personal outline color
- `chunkcap.theme` — choose a player's personal animated outline theme

The five administration nodes default to server operators. The three appearance-setting nodes default to all players, preserving access to personal settings. LuckPerms can deny each appearance setting separately. `chunkcap.admin` grants every command permission and `chunkcap.bypass`.

With LuckPerms, grant only the access needed or deny a personal setting:

- `/lp group builders permission set chunkcap.set true`
- `/lp user <player> permission set chunkcap.check true`
- `/lp group default permission set chunkcap.antilag false`
- `/lp user <player> permission set chunkcap.theme false`
- `/lp user <player> permission set chunkcap.admin true`

Players with `chunkcap.bypass` can place capped blocks without being blocked; those blocks still count toward the chunk total. `chunkcap.admin` grants this bypass too. To grant it separately, use `/lp user <player> permission set chunkcap.bypass true`.

Vanilla block-editing commands such as `/setblock`, `/fill`, `/clone`, and `/place`, along with WorldEdit-style `//...` commands, bypass the placement limit. Chunk counts are cleared after recognized commands so later checks rescan edited chunks.

Example: `/chunklimit set hopper 16`

## Block names

Blocks are always named by their Minecraft name — the same one used by `/give` and
`/setblock` — so a hopper is `hopper`, grass is `grass_block` and a diamond block is
`diamond_block`. That name is what appears in `/chunklimit list` and in the messages when a
cap is set, removed, checked or reached.

Tab completion works like `/give`: it suggests the namespaced ID, such as
`minecraft:hopper` or `minecraft:diamond_block`. Typing `dia` or `minecraft:dia`
both offer `minecraft:diamond_block`.

Commands and `config.yml` accept the Minecraft name, and also accept the Bukkit material
name (`HOPPER`) or the namespaced id (`minecraft:hopper`), so existing configs and habits
keep working. Config entries are rewritten in Minecraft form the next time a cap changes.

## Build and install

Requires Java 21 and Maven. From this directory run:

```sh
mvn package
```

Copy `target/ChunkCap.jar` to the server's `plugins/` folder, then restart. This project targets the Paper 1.21.11 API and Java 21.

## Counting behavior

The first check in a chunk scans its current blocks, and placement checks rescan the target chunk before enforcing a cap. Run `/chunklimit antilag true` to use a sparse static outline with your selected color; use `false` to restore the animated, block-shaped outline with your selected theme. Each player's settings are saved by UUID in `plugins/ChunkCap/player-settings.yml`. Cached counts are invalidated after placement attempts (including cancelled ones), breaks, explosions, burning, block forming/fading, piston movement, growth, fertilizing, spreading, entity block changes, and recognized block-editing commands. A rescan counts paired blocks once: doors and other bisected blocks count from their lower half, and beds count from their foot half. The limit is enforced for player placements and block-form events.

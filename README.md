# ChunkCap

A Paper server plugin that enforces a configurable **per-chunk maximum for each selected block type**. For example, limit each chunk to 16 hoppers and 8 spawners.

## Commands

- `/chunklimit set <block> <max-per-chunk>` — set/update a cap (`0` prevents placement)
- `/chunklimit remove <block>` — remove a cap
- `/chunklimit list` — show configured caps
- `/chunklimit check <block>` — count the block type in your current chunk (players only)
- `/chunklimit reload` — reload `plugins/ChunkCap/config.yml`

All commands require `chunkcap.admin` (default: server operators).

Players with `chunkcap.bypass` can place capped blocks without being blocked; those blocks still count toward the chunk total. `chunkcap.admin` inherits this bypass by default. Grant the bypass separately with a permissions plugin if needed, for example: `/lp user <player> permission set chunkcap.bypass true`.

Vanilla block-editing commands such as `/setblock`, `/fill`, `/clone`, and `/place`, along with WorldEdit-style `//...` commands, bypass the placement limit. Chunk counts are cleared after recognized commands so later checks rescan edited chunks.

Example: `/chunklimit set HOPPER 16`

## Build and install

Requires Java 21 and Maven. From this directory run:

```sh
mvn package
```

Copy `target/ChunkCap-1.0.0.jar` to the server's `plugins/` folder, then restart. This project targets the Paper 1.21.11 API and Java 21.

## Counting behavior

The first check in a chunk scans that chunk's current blocks, so blocks already present are included. Counts are cached for loaded chunks and updated for normal block placement/breaking, explosions, burning, block forming/fading, then discarded when a chunk unloads. The limit is enforced for block placement and block-form events. Recognized vanilla and WorldEdit-style block-editing commands bypass the limit and clear cached counts for a rescan. Other direct world edits performed by plugins may not be observed until `/chunklimit reload` clears the cache and reloads the configured limits.

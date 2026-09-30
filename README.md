# ChunkCap

A Paper server plugin that enforces a configurable **per-chunk maximum for each selected block type**. For example, limit each chunk to 16 hoppers and 8 spawners.

## Commands

- `/chunklimit set <block> <max-per-chunk>` — set/update a cap (`0` prevents placement)
- `/chunklimit remove <block>` — remove a cap
- `/chunklimit list` — show configured caps
- `/chunklimit check <block>` — count the block type in your current chunk (players only)
- `/chunklimit reload` — reload `plugins/ChunkCap/config.yml`

All commands require `chunkcap.admin` (default: server operators).

Example: `/chunklimit set HOPPER 16`

## Build and install

Requires Java 21 and Maven. From this directory run:

```sh
mvn package
```

Copy `target/ChunkCap-1.0.0.jar` to the server's `plugins/` folder, then restart. This project targets the Paper 1.21.11 API and Java 21.

## Counting behavior

The first check in a chunk scans that chunk's current blocks, so blocks already present are included. Counts are cached for loaded chunks and updated for normal block placement/breaking, explosions, burning, block forming/fading, then discarded when a chunk unloads. The limit is enforced for block placement and block-form events. Direct world edits performed by other plugins/commands while a chunk is cached (for example, WorldEdit or `/setblock`) are not automatically observed; `/chunklimit reload` clears the cache and reloads the configured limits.

## Build on GitHub Actions

1. Create a GitHub repository and upload the **contents** of this `chunk-cap` folder to the repository root (so `pom.xml` is at the top level and `.github/workflows/build.yml` is included).
2. Commit/push the files. The **Build ChunkCap** workflow runs automatically on push. You can also open the repository's **Actions** tab, select **Build ChunkCap**, and choose **Run workflow**.
3. Open the completed workflow run and download the `ChunkCap-1.0.0` artifact. The plugin JAR inside is `ChunkCap-1.0.0.jar`.
4. Put that JAR in your Paper server's `plugins` folder and restart the server.

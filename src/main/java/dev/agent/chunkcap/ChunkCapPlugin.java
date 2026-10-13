package dev.agent.chunkcap;

import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ChunkCapPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final String NAMESPACE = "minecraft:";
    private static final List<String> OUTLINE_THEMES = List.of(
            "aurora", "ocean", "sunset", "forest", "royal",
            "ember", "glacier", "candy", "toxic", "galaxy",
            "gold", "cherry", "pastel", "monochrome", "neon");
    private static final Map<String, Color> ANTI_LAG_COLORS = createAntiLagColors();

    private static Map<String, Color> createAntiLagColors() {
        Map<String, Color> baseColors = Map.ofEntries(
                Map.entry("red", Color.fromRGB(230, 57, 53)),
                Map.entry("orange", Color.fromRGB(245, 124, 0)),
                Map.entry("yellow", Color.fromRGB(253, 216, 53)),
                Map.entry("lime", Color.fromRGB(174, 234, 0)),
                Map.entry("green", Color.fromRGB(67, 160, 71)),
                Map.entry("teal", Color.fromRGB(0, 150, 136)),
                Map.entry("cyan", Color.fromRGB(0, 188, 212)),
                Map.entry("blue", Color.fromRGB(30, 136, 229)),
                Map.entry("purple", Color.fromRGB(142, 36, 170)),
                Map.entry("magenta", Color.fromRGB(216, 27, 96)),
                Map.entry("pink", Color.fromRGB(236, 64, 122)),
                Map.entry("brown", Color.fromRGB(121, 85, 72)),
                Map.entry("gray", Color.fromRGB(117, 117, 117)),
                Map.entry("white", Color.WHITE),
                Map.entry("black", Color.BLACK),
                Map.entry("gold", Color.fromRGB(255, 193, 7))
        );
        Map<String, Color> colors = new HashMap<>();
        baseColors.forEach((name, color) -> {
            colors.put(name, color);
            colors.put("light_" + name, lighten(color));
            colors.put("dark_" + name, darken(color));
        });
        return Map.copyOf(colors);
    }

    private static Color lighten(Color color) {
        return Color.fromRGB(
                color.getRed() + (int) Math.round((255 - color.getRed()) * 0.5),
                color.getGreen() + (int) Math.round((255 - color.getGreen()) * 0.5),
                color.getBlue() + (int) Math.round((255 - color.getBlue()) * 0.5));
    }

    private static Color darken(Color color) {
        return Color.fromRGB(
                (int) Math.round(color.getRed() * 0.55),
                (int) Math.round(color.getGreen() * 0.55),
                (int) Math.round(color.getBlue() * 0.55));
    }

    private static OutlineTheme outlineTheme(String name) {
        return switch (name == null ? "aurora" : name.toLowerCase(Locale.ROOT)) {
            case "ocean" -> new OutlineTheme(
                    Color.fromRGB(30, 145, 255), Color.fromRGB(20, 225, 220), Color.fromRGB(160, 255, 245));
            case "sunset" -> new OutlineTheme(
                    Color.fromRGB(255, 86, 65), Color.fromRGB(255, 160, 45), Color.fromRGB(255, 236, 115));
            case "forest" -> new OutlineTheme(
                    Color.fromRGB(40, 180, 90), Color.fromRGB(30, 220, 130), Color.fromRGB(190, 255, 100));
            case "royal" -> new OutlineTheme(
                    Color.fromRGB(150, 70, 255), Color.fromRGB(220, 70, 230), Color.fromRGB(120, 200, 255));
            case "ember" -> new OutlineTheme(
                    Color.fromRGB(255, 77, 46), Color.fromRGB(255, 159, 28), Color.fromRGB(255, 224, 138));
            case "glacier" -> new OutlineTheme(
                    Color.fromRGB(73, 184, 255), Color.fromRGB(110, 235, 255), Color.fromRGB(242, 255, 255));
            case "candy" -> new OutlineTheme(
                    Color.fromRGB(255, 79, 163), Color.fromRGB(184, 108, 255), Color.fromRGB(112, 244, 255));
            case "toxic" -> new OutlineTheme(
                    Color.fromRGB(184, 255, 0), Color.fromRGB(55, 240, 125), Color.fromRGB(234, 255, 0));
            case "galaxy" -> new OutlineTheme(
                    Color.fromRGB(110, 68, 255), Color.fromRGB(212, 75, 255), Color.fromRGB(84, 213, 255));
            case "gold" -> new OutlineTheme(
                    Color.fromRGB(217, 139, 0), Color.fromRGB(255, 212, 59), Color.fromRGB(255, 242, 173));
            case "cherry" -> new OutlineTheme(
                    Color.fromRGB(194, 24, 58), Color.fromRGB(255, 83, 112), Color.fromRGB(255, 193, 204));
            case "pastel" -> new OutlineTheme(
                    Color.fromRGB(196, 156, 255), Color.fromRGB(154, 203, 255), Color.fromRGB(255, 209, 231));
            case "monochrome" -> new OutlineTheme(
                    Color.fromRGB(119, 122, 128), Color.fromRGB(200, 204, 210), Color.fromRGB(255, 255, 255));
            case "neon" -> new OutlineTheme(
                    Color.fromRGB(255, 0, 212), Color.fromRGB(0, 240, 255), Color.fromRGB(199, 255, 0));
            default -> new OutlineTheme(
                    Color.fromRGB(255, 70, 125), Color.fromRGB(160, 85, 255), Color.fromRGB(105, 245, 255));
        };
    }

    private static String displayOption(String option) {
        return Arrays.stream(option.split("_"))
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(Collectors.joining(" "));
    }
    private static final String MESSAGE_PREFIX = "§x§0§0§F§F§4§4A§x§0§7§E§9§3§4p§x§0§D§D§3§2§4f§x§1§4§B§D§1§4e§x§1§A§A§7§0§4l §7| ";
    private final Map<Material, Integer> limits = new EnumMap<>(Material.class);
    private final Map<ChunkKey, EnumMap<Material, Integer>> countCache = new HashMap<>();
    private File playerSettingsFile;
    private FileConfiguration playerSettings;
    private boolean cacheClearScheduled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        playerSettingsFile = new File(getDataFolder(), "player-settings.yml");
        playerSettings = YamlConfiguration.loadConfiguration(playerSettingsFile);
        loadLimits();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("chunklimit") != null) {
            getCommand("chunklimit").setExecutor(this);
            getCommand("chunklimit").setTabCompleter(this);
        }
        getLogger().info(MESSAGE_PREFIX + "ChunkCap enabled with " + limits.size() + " configured block limit(s).");
    }

    private void loadLimits() {
        limits.clear();
        var section = getConfig().getConfigurationSection("limits");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Material material = matchBlockName(key);
            int cap = section.getInt(key, -1);
            if (!isVanillaBlock(material) || cap < 0) {
                getLogger().warning(MESSAGE_PREFIX + "Ignoring invalid limit entry: " + key + " = " + section.get(key));
                continue;
            }
            limits.put(material, cap);
        }
        countCache.clear();
    }

    private void saveLimits() {
        getConfig().set("limits", null);
        for (Map.Entry<Material, Integer> entry : limits.entrySet()) {
            getConfig().set("limits." + blockName(entry.getKey()), entry.getValue());
        }
        saveConfig();
        countCache.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        boolean personalOption = Set.of("antilag", "antilagcolor", "color", "theme").contains(subcommand);
        boolean helpCommand = subcommand.equals("help");
        if (!personalOption && !helpCommand && !sender.hasPermission("chunkcap.admin")) {
            sendMessage(sender, "§cYou do not have permission to manage chunk limits.");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                if (args.length != 3) {
                    sendMessage(sender, "§eUsage: /" + label + " set <block> <max-per-chunk>");
                    return true;
                }
                Material material = parseBlock(args[1]);
                if (material == null) {
                    sendMessage(sender, "§cUnknown block: " + args[1]);
                    return true;
                }
                int cap;
                try {
                    cap = Integer.parseInt(args[2]);
                    if (cap < 0) throw new NumberFormatException();
                } catch (NumberFormatException exception) {
                    sendMessage(sender, "§cThe limit must be a whole number of 0 or greater.");
                    return true;
                }
                limits.put(material, cap);
                saveLimits();
                sendMessage(sender, "§aChunk limit for §f" + blockName(material) + "§a set to §f" + cap + "§a.");
                return true;
            }
            case "remove", "unset" -> {
                if (args.length != 2) {
                    sendMessage(sender, "§eUsage: /" + label + " remove <block>");
                    return true;
                }
                Material material = parseBlock(args[1]);
                if (material == null || limits.remove(material) == null) {
                    sendMessage(sender, "§cThat block does not have a configured limit.");
                    return true;
                }
                saveLimits();
                sendMessage(sender, "§aRemoved the chunk limit for §f" + blockName(material) + "§a.");
                return true;
            }
            case "list" -> {
                if (limits.isEmpty()) {
                    sendMessage(sender, "§7No per-chunk block limits are configured.");
                } else {
                    sendMessage(sender, "§6ChunkCap limits (per chunk):");
                    new TreeMap<String, Integer>(Comparator.naturalOrder()) {{
                        limits.forEach((material, cap) -> put(blockName(material), cap));
                    }}.forEach((name, cap) -> sendMessage(sender, "§e - §f" + name + "§7: " + cap));
                }
                return true;
            }
            case "check" -> {
                if (args.length != 2 || !(sender instanceof Player player)) {
                    sendMessage(sender, "§eUsage: /" + label + " check <block> (players only)");
                    return true;
                }
                Material material = parseBlock(args[1]);
                Integer cap = material == null ? null : limits.get(material);
                if (cap == null) {
                    sendMessage(sender, "§cThat block has no configured limit.");
                    return true;
                }
                Chunk chunk = player.getLocation().getChunk();
                int count = getCount(chunk, material);
                double ratio = cap == 0 ? (count > 0 ? 1.0 : 0.0) : Math.min(1.0, (double) count / cap);
                int filled = (int) Math.round(ratio * 10.0);
                String barColor = count >= cap ? "§c" : ratio >= 0.8 ? "§e" : "§a";
                StringBuilder bar = new StringBuilder();
                for (int i = 0; i < 10; i++) bar.append(i < filled ? barColor + "■" : "§8■");
                String status = cap == 0 ? "§cDISABLED" : count >= cap ? "§cFULL" : ratio >= 0.8 ? "§eALMOST FULL" : "§aOK";
                sendMessage(sender, "§8§m--------------------");
                sendMessage(sender, "§6§lChunkCap §8» §f" + blockName(material));
                sendMessage(sender, "§7Usage: " + bar + " §f" + count + "§7/§f" + cap);
                sendMessage(sender, "§7Status: " + status);
                sendMessage(sender, "§7Chunk: §f" + chunk.getX() + "§7, §f" + chunk.getZ());
                sendMessage(sender, "§8§m--------------------");
                return true;
            }
            case "antilag" -> {
                if (!(sender instanceof Player player)) {
                    sendMessage(sender, "§cOnly players can change their personal Anti-lag setting.");
                    return true;
                }
                if (args.length != 2 || !(args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("false"))) {
                    sendMessage(sender, "§eUsage: /" + label + " antilag <true|false>");
                    return true;
                }
                boolean enabled = Boolean.parseBoolean(args[1]);
                if (!savePlayerSetting(player, "anti-lag." + player.getUniqueId(), enabled)) return true;
                sendMessage(player, enabled
                        ? "§aYour Anti-lag mode is enabled. Block warnings use your selected outline color."
                        : "§aYour Anti-lag mode is disabled. Block warnings use your selected animated theme.");
                return true;
            }
            case "antilagcolor", "color" -> {
                if (!(sender instanceof Player player)) {
                    sendMessage(sender, "§cOnly players can change their personal Anti-lag color.");
                    return true;
                }
                if (args.length < 2) {
                    sendMessage(player, "§eUsage: /" + label + " antilagcolor <color>");
                    return true;
                }
                String colorName = String.join("_", Arrays.copyOfRange(args, 1, args.length))
                        .toLowerCase(Locale.ROOT).replace('-', '_');
                if (!ANTI_LAG_COLORS.containsKey(colorName)) {
                    sendMessage(player, "§cUnknown color. Try red, light_red, dark_red, blue, light_blue or dark_blue.");
                    return true;
                }
                if (!savePlayerSetting(player, "anti-lag-color." + player.getUniqueId(), colorName)) return true;
                sendMessage(player, "§aYour Anti-lag outline color is now §f" + displayOption(colorName) + "§a.");
                return true;
            }
            case "theme" -> {
                if (!(sender instanceof Player player)) {
                    sendMessage(sender, "§cOnly players can change their personal animated theme.");
                    return true;
                }
                if (args.length != 2 || !OUTLINE_THEMES.contains(args[1].toLowerCase(Locale.ROOT))) {
                    sendMessage(player, "§eUsage: /" + label + " theme <" + String.join("|", OUTLINE_THEMES) + ">");
                    return true;
                }
                String theme = args[1].toLowerCase(Locale.ROOT);
                if (!savePlayerSetting(player, "outline-theme." + player.getUniqueId(), theme)) return true;
                sendMessage(player, "§aYour animated outline theme is now §f" + displayOption(theme) + "§a.");
                return true;
            }
            case "reload" -> {
                reloadConfig();
                playerSettings = YamlConfiguration.loadConfiguration(playerSettingsFile);
                loadLimits();
                sendMessage(sender, "§aChunkCap configuration reloaded.");
                return true;
            }
            case "help" -> {
                sendHelp(sender, label);
                return true;
            }
            default -> {
                sendHelp(sender, label);
                return true;
            }
        }
    }

    private boolean savePlayerSetting(Player player, String path, Object value) {
        Object previousValue = playerSettings.get(path);
        playerSettings.set(path, value);
        try {
            playerSettings.save(playerSettingsFile);
            return true;
        } catch (IOException exception) {
            playerSettings.set(path, previousValue);
            getLogger().severe("Could not save player setting for " + player.getUniqueId() + ": " + exception.getMessage());
            sendMessage(player, "§cCould not save your setting. Please try again later.");
            return false;
        }
    }

    private static void sendMessage(CommandSender recipient, String message) {
        recipient.sendMessage(MESSAGE_PREFIX + message);
    }

    private void showBlockOutline(Player player, Block block) {
        if (playerSettings.getBoolean("anti-lag." + player.getUniqueId(), false)) {
            showAntiLagOutline(player, block);
            return;
        }

        var origin = block.getLocation();
        OutlineTheme theme = outlineTheme(playerSettings.getString("outline-theme." + player.getUniqueId(), "aurora"));
        Particle.DustOptions baseDust = new Particle.DustOptions(theme.base(), 0.38f);
        Particle.DustOptions trailDust = new Particle.DustOptions(theme.trail(), 0.46f);
        Particle.DustOptions leadingDust = new Particle.DustOptions(theme.leading(), 0.72f);
        boolean hopper = block.getType() == Material.HOPPER;
        int frames = 6;
        double edgeSpacing = hopper ? 0.24 : 0.16;

        // Collision shapes contain one or more block-local boxes, so multipart blocks
        // (such as hoppers and stairs) keep their actual silhouette.
        var collisionBoxes = block.getCollisionShape().getBoundingBoxes();
        List<BoundingBox> boxes;
        if (collisionBoxes.isEmpty()) {
            // Some decorative blocks, including redstone wire, have no collision shape.
            // Block#getBoundingBox is world-relative; convert it to block-local coordinates.
            var fallback = block.getBoundingBox().clone();
            fallback.shift(-origin.getX(), -origin.getY(), -origin.getZ());
            boxes = List.of(fallback);
        } else {
            boxes = List.copyOf(collisionBoxes);
        }

        new BukkitRunnable() {
            private int frame = 0;

            @Override
            public void run() {
                if (!player.isOnline() || frame >= frames) {
                    cancel();
                    return;
                }

                // Ease the leading edge upward so the sweep starts and finishes smoothly.
                double progress = (double) (frame + 1) / frames;
                double rise = progress * progress * (3.0 - 2.0 * progress);
                for (int boxIndex = 0; boxIndex < boxes.size(); boxIndex++) {
                    BoundingBox box = boxes.get(boxIndex);
                    double topY = box.getMinY() + (box.getMaxY() - box.getMinY()) * rise;
                    boolean sparkFrame = frame == frames - 1;
                    drawRisingBorder(player, origin, box, topY, frame == 0, sparkFrame, edgeSpacing, baseDust, trailDust, leadingDust);
                }
                frame++;
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void showAntiLagOutline(Player player, Block block) {
        var origin = block.getLocation();
        String colorName = playerSettings.getString("anti-lag-color." + player.getUniqueId(), "red");
        Color outlineColor = ANTI_LAG_COLORS.getOrDefault(colorName.toLowerCase(Locale.ROOT), ANTI_LAG_COLORS.get("red"));
        Particle.DustOptions redDust = new Particle.DustOptions(outlineColor, 0.42f);
        double minX = 0;
        double minY = 0;
        double minZ = 0;
        double maxX = 1;
        double maxY = 1;
        double maxZ = 1;
        double spacing = 0.32;

        // Low-particle fallback: a static red wireframe cube with widely spaced dots.
        drawHorizontalBorder(player, origin, minX, maxX, minZ, maxZ, minY, redDust, spacing);
        drawHorizontalBorder(player, origin, minX, maxX, minZ, maxZ, maxY, redDust, spacing);
        for (int xSide = 0; xSide <= 1; xSide++) {
            for (int zSide = 0; zSide <= 1; zSide++) {
                double x = xSide == 0 ? minX : maxX;
                double z = zSide == 0 ? minZ : maxZ;
                drawBorderLine(player, origin, x, minY, z, x, maxY, z, redDust, spacing);
            }
        }
    }

    private void drawRisingBorder(Player player, org.bukkit.Location origin, BoundingBox box,
                                  double topY, boolean firstFrame, boolean finalFrame, double edgeSpacing,
                                  Particle.DustOptions baseDust, Particle.DustOptions trailDust,
                                  Particle.DustOptions leadingDust) {
        double minX = box.getMinX();
        double minY = box.getMinY();
        double minZ = box.getMinZ();
        double maxX = box.getMaxX();
        double maxZ = box.getMaxZ();

        // A pink base, violet rising edges, and a bright cyan scan line keep the effect
        // vivid while every particle remains on the block's actual border.
        if (firstFrame) {
            drawHorizontalBorder(player, origin, minX, maxX, minZ, maxZ, minY, baseDust, edgeSpacing);
        }
        drawHorizontalBorder(player, origin, minX, maxX, minZ, maxZ, topY, leadingDust, edgeSpacing);

        for (int xSide = 0; xSide <= 1; xSide++) {
            for (int zSide = 0; zSide <= 1; zSide++) {
                double x = xSide == 0 ? minX : maxX;
                double z = zSide == 0 ? minZ : maxZ;
                drawBorderLine(player, origin, x, minY, z, x, topY, z, trailDust, edgeSpacing);
                if (finalFrame) {
                    player.spawnParticle(Particle.ELECTRIC_SPARK, origin.clone().add(x, topY, z),
                            1, 0, 0, 0, 0);
                }
            }
        }
    }

    private void drawHorizontalBorder(Player player, org.bukkit.Location origin,
                                      double minX, double maxX, double minZ, double maxZ,
                                      double y, Particle.DustOptions dust, double edgeSpacing) {
        drawBorderLine(player, origin, minX, y, minZ, maxX, y, minZ, dust, edgeSpacing);
        drawBorderLine(player, origin, minX, y, maxZ, maxX, y, maxZ, dust, edgeSpacing);
        drawBorderLine(player, origin, minX, y, minZ, minX, y, maxZ, dust, edgeSpacing);
        drawBorderLine(player, origin, maxX, y, minZ, maxX, y, maxZ, dust, edgeSpacing);
    }

    private void drawBorderLine(Player player, org.bukkit.Location origin,
                                double startX, double startY, double startZ,
                                double endX, double endY, double endZ,
                                Particle.DustOptions dust, double edgeSpacing) {
        double length = Math.sqrt(
                (endX - startX) * (endX - startX)
                        + (endY - startY) * (endY - startY)
                        + (endZ - startZ) * (endZ - startZ));
        int steps = Math.max(1, (int) Math.ceil(length / edgeSpacing));
        for (int step = 0; step <= steps; step++) {
            double t = (double) step / steps;
            player.spawnParticle(
                    Particle.DUST,
                    origin.clone().add(
                            startX + (endX - startX) * t,
                            startY + (endY - startY) * t,
                            startZ + (endZ - startZ) * t),
                    1, 0, 0, 0, 0, dust);
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sendMessage(sender, "§8§m--------------------");
        sendMessage(sender, "§6§lChunkCap §7- Per-chunk block limits");
        sendMessage(sender, "§e/" + label + " set <block> <max> §8» §7Set a cap (0 disables placement)");
        sendMessage(sender, "§e/" + label + " remove <block> §8» §7Remove a cap");
        sendMessage(sender, "§e/" + label + " list §8» §7List configured caps");
        sendMessage(sender, "§e/" + label + " check <block> §8» §7Show usage for this chunk");
        sendMessage(sender, "§e/" + label + " antilag <true|false> §8» §7Toggle your low-particle outline");
        sendMessage(sender, "§e/" + label + " antilagcolor <color> §8» §7Set its color and shade");
        sendMessage(sender, "§e/" + label + " theme <theme> §8» §7Choose an animated outline theme");
        sendMessage(sender, "§8§7Themes: aurora, ocean, sunset, forest, royal, ember, glacier, candy, toxic, galaxy");
        sendMessage(sender, "§8§7More: gold, cherry, pastel, monochrome, neon");
        sendMessage(sender, "§8§7Colors: red, light_red, dark_red (and other color names)");
        sendMessage(sender, "§e/" + label + " reload §8» §7Reload config.yml");
        sendMessage(sender, "§8§m--------------------");
    }

    private Material parseBlock(String name) {
        Material material = matchBlockName(name);
        return isVanillaBlock(material) ? material : null;
    }

    /**
     * Resolves a block from a Minecraft block name such as {@code hopper}, {@code grass_block},
     * {@code minecraft:hopper} or {@code grass block}, and from the Bukkit enum name ({@code HOPPER},
     * {@code GRASS_BLOCK}). Returns null when the name does not match a block.
     */
    private static Material matchBlockName(String name) {
        if (name == null) return null;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(NAMESPACE)) normalized = normalized.substring(NAMESPACE.length());
        Material material = Material.matchMaterial(normalized);
        if (material == null) {
            material = Material.matchMaterial(normalized.replace(' ', '_').replace('-', '_'));
        }
        return material;
    }

    /** Blocks are always referred to by their Minecraft name, e.g. {@code hopper} or {@code grass_block}. */
    private static String blockName(Material material) {
        // Legacy (pre-flattening) materials have no key, so fall back to their enum name.
        if (material.isLegacy()) return material.name().toLowerCase(Locale.ROOT);
        return material.getKey().getKey();
    }

    /** The namespaced ID used by /give and tab completion, e.g. {@code minecraft:diamond_block}. */
    private static String blockId(Material material) {
        return NAMESPACE + blockName(material);
    }

    /** A block that still exists in modern Minecraft, i.e. not one of the pre-flattening legacy materials. */
    private static boolean isVanillaBlock(Material material) {
        return material != null && material.isBlock() && !material.isLegacy();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return matching(List.of("set", "remove", "list", "check", "antilag", "antilagcolor", "theme", "reload", "help"), args[0]);
        if (args.length == 2 && List.of("set", "remove", "unset", "check").contains(args[0].toLowerCase(Locale.ROOT))) {
            // Suggest namespaced item IDs the way /give does (minecraft:diamond_block).
            List<String> ids = args[0].equalsIgnoreCase("set")
                    ? Arrays.stream(Material.values()).filter(ChunkCapPlugin::isVanillaBlock).map(ChunkCapPlugin::blockId).sorted().collect(Collectors.toList())
                    : limits.keySet().stream().map(ChunkCapPlugin::blockId).sorted().collect(Collectors.toList());
            return matchingBlockIds(ids, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("antilag")) return matching(List.of("true", "false"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("antilagcolor")) {
            return matching(ANTI_LAG_COLORS.keySet().stream().sorted().collect(Collectors.toList()), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("theme")) return matching(OUTLINE_THEMES, args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) return List.of("16", "32", "64", "128");
        return List.of();
    }

    private List<String> matching(List<String> options, String prefix) {
        String start = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(start)).limit(100).collect(Collectors.toList());
    }

    /**
     * Filters namespaced block IDs like /give does: {@code dia}, {@code minecraft:dia} and
     * {@code minecraft:diamond_block} all match {@code minecraft:diamond_block}. A typed prefix
     * without a namespace is also matched against the bare name.
     */
    private static List<String> matchingBlockIds(List<String> ids, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        boolean bareName = !prefix.contains(":");
        return ids.stream()
                .filter(id -> id.startsWith(prefix) || (bareName && id.substring(NAMESPACE.length()).startsWith(prefix)))
                .limit(100)
                .collect(Collectors.toList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceCheck(BlockPlaceEvent event) {
        // Permission holders may place capped blocks without restriction; their blocks still count.
        if (event.getPlayer().hasPermission("chunkcap.bypass")) return;
        Material material = event.getBlockPlaced().getType();
        Integer cap = limits.get(material);
        if (cap == null) return;
        // Rescan before enforcing the cap so stale cache entries cannot allow extra blocks.
        countCache.remove(ChunkKey.of(event.getBlockPlaced().getChunk()));
        int current = getCount(event.getBlockPlaced().getChunk(), material);
        // The place event runs after the block state is applied, so current already includes this placement.
        if (current > cap) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            sendMessage(player, "§c§lChunkCap §8» §cChunk limit reached! §7Max: §f" + cap + " §7" + blockName(material) + ".");
            showBlockOutline(player, event.getBlockPlaced());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBlockCommand(PlayerCommandPreprocessEvent event) {
        if (isBlockEditingCommand(event.getMessage())) clearCountCacheNextTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleBlockCommand(ServerCommandEvent event) {
        if (isBlockEditingCommand(event.getCommand())) clearCountCacheNextTick();
    }

    private boolean isBlockEditingCommand(String rawCommand) {
        String command = rawCommand.trim();
        // WorldEdit-style commands (//set, //replace, //paste, etc.)
        if (command.startsWith("//")) return true;
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isEmpty()) return false;
        String root = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int namespaceSeparator = root.lastIndexOf(':');
        if (namespaceSeparator >= 0) root = root.substring(namespaceSeparator + 1);
        return Set.of("setblock", "fill", "clone", "place").contains(root);
    }

    private void clearCountCacheNextTick() {
        // Coalesce same-tick edits and rescan their final world state on the next count check.
        if (cacheClearScheduled) return;
        cacheClearScheduled = true;
        getServer().getScheduler().runTask(this, () -> {
            countCache.clear();
            cacheClearScheduled = false;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        clearCountCacheNextTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        clearCountCacheNextTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        clearCountCacheNextTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        clearCountCacheNextTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent event) {
        clearCountCacheNextTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        clearCountCacheNextTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        clearCountCacheNextTick();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlaceCount(BlockPlaceEvent event) {
        // Clear for accepted and cancelled attempts, then again after Bukkit finalizes the event.
        countCache.clear();
        clearCountCacheNextTick();
    }

    private static boolean countsAsBlock(BlockData data) {
        if (data instanceof Bisected bisected && bisected.getHalf() == Bisected.Half.TOP) return false;
        return !(data instanceof Bed bed) || bed.getPart() != Bed.Part.HEAD;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        clearCountCacheNextTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        clearCountCacheNextTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        clearCountCacheNextTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        clearCountCacheNextTick();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFormCheck(BlockFormEvent event) {
        Material before = event.getBlock().getType();
        Material after = event.getNewState().getType();
        Integer cap = limits.get(after);
        if (before != after && cap != null && getCount(event.getBlock().getChunk(), after) >= cap) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        clearCountCacheNextTick();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        clearCountCacheNextTick();
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        countCache.remove(ChunkKey.of(event.getChunk()));
    }

    private int getCount(Chunk chunk, Material material) {
        return getCounts(chunk).getOrDefault(material, 0);
    }

    private EnumMap<Material, Integer> getCounts(Chunk chunk) {
        return countCache.computeIfAbsent(ChunkKey.of(chunk), ignored -> scanChunk(chunk));
    }

    private EnumMap<Material, Integer> scanChunk(Chunk chunk) {
        EnumMap<Material, Integer> found = new EnumMap<>(Material.class);
        if (limits.isEmpty()) return found;
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        Set<Material> tracked = limits.keySet();
        for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Block block = world.getBlockAt(baseX + x, y, baseZ + z);
                    Material type = block.getType();
                    if (tracked.contains(type) && countsAsBlock(block.getBlockData())) {
                        found.merge(type, 1, Integer::sum);
                    }
                }
            }
        }
        return found;
    }

    private record OutlineTheme(Color base, Color trail, Color leading) {}

    private record ChunkKey(UUID worldId, int x, int z) {
        static ChunkKey of(Chunk chunk) {
            return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        }
    }
}
 

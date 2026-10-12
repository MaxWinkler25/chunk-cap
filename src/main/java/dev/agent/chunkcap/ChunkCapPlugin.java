package dev.agent.chunkcap;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
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
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

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
    private static final String MESSAGE_PREFIX = "§x§0§0§F§F§4§4A§x§0§7§E§9§3§4p§x§0§D§D§3§2§4f§x§1§4§B§D§1§4e§x§1§A§A§7§0§4l §7| ";

    private final Map<Material, Integer> limits = new EnumMap<>(Material.class);
    private final Map<ChunkKey, EnumMap<Material, Integer>> countCache = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
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
                int count = getCount(player.getLocation().getChunk(), material);
                sendMessage(sender, "§f" + blockName(material) + "§7 in this chunk: §f" + count + "§7 / §f" + cap);
                return true;
            }
            case "reload" -> {
                reloadConfig();
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

    private static void sendMessage(CommandSender recipient, String message) {
        recipient.sendMessage(MESSAGE_PREFIX + message);
    }

    private void sendHelp(CommandSender sender, String label) {
        sendMessage(sender, "§6ChunkCap commands:");
        sendMessage(sender, "§e/" + label + " set <block> <max> §7- Set a per-chunk cap (0 blocks placement)");
        sendMessage(sender, "§e/" + label + " remove <block> §7- Remove a cap");
        sendMessage(sender, "§e/" + label + " list §7- Show all caps");
        sendMessage(sender, "§e/" + label + " check <block> §7- Count it in your current chunk");
        sendMessage(sender, "§e/" + label + " reload §7- Reload config.yml");
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
        if (args.length == 1) return matching(List.of("set", "remove", "list", "check", "reload", "help"), args[0]);
        if (args.length == 2 && List.of("set", "remove", "unset", "check").contains(args[0].toLowerCase(Locale.ROOT))) {
            // Suggest namespaced item IDs the way /give does (minecraft:diamond_block).
            List<String> ids = args[0].equalsIgnoreCase("set")
                    ? Arrays.stream(Material.values()).filter(ChunkCapPlugin::isVanillaBlock).map(ChunkCapPlugin::blockId).sorted().collect(Collectors.toList())
                    : limits.keySet().stream().map(ChunkCapPlugin::blockId).sorted().collect(Collectors.toList());
            return matchingBlockIds(ids, args[1]);
        }
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
        int placing = placementCount(event);
        int current = getCount(event.getBlockPlaced().getChunk(), material);
        if (current + placing > cap) {
            event.setCancelled(true);
            sendMessage(event.getPlayer(), "§cThis chunk has reached its limit of §f" + cap + " " + blockName(material) + "§c.");
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
        // Command edits bypass placement events. Rescan edited chunks on their next count check.
        getServer().getScheduler().runTask(this, countCache::clear);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceCount(BlockPlaceEvent event) {
        Material material = event.getBlockPlaced().getType();
        if (limits.containsKey(material)) {
            addCached(event.getBlockPlaced(), material, placementCount(event));
        }
    }

    private int placementCount(BlockPlaceEvent event) {
        if (event instanceof BlockMultiPlaceEvent multi) {
            return Math.max(1, multi.getReplacedBlockStates().size());
        }
        return 1;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Material material = event.getBlock().getType();
        if (limits.containsKey(material)) addCached(event.getBlock(), material, -1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        for (Block block : event.blockList()) removeFromCacheIfKnown(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        for (Block block : event.blockList()) removeFromCacheIfKnown(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        removeFromCacheIfKnown(event.getBlock());
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
        updateCachedTransition(event.getBlock(), event.getNewState().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        updateCachedTransition(event.getBlock(), event.getNewState().getType());
    }

    private void updateCachedTransition(Block block, Material after) {
        Material before = block.getType();
        if (before == after) return;
        if (limits.containsKey(before)) addCached(block, before, -1);
        if (limits.containsKey(after)) addCached(block, after, 1);
    }

    private void removeFromCacheIfKnown(Block block) {
        Material material = block.getType();
        if (limits.containsKey(material)) addCached(block, material, -1);
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
                    Material type = world.getBlockAt(baseX + x, y, baseZ + z).getType();
                    if (tracked.contains(type)) found.merge(type, 1, Integer::sum);
                }
            }
        }
        return found;
    }

    private void addCached(Block block, Material material, int amount) {
        EnumMap<Material, Integer> counts = countCache.get(ChunkKey.of(block.getChunk()));
        if (counts == null) return; // An uncached chunk will be counted from the world when next queried.
        int updated = Math.max(0, counts.getOrDefault(material, 0) + amount);
        counts.put(material, updated);
    }

    private record ChunkKey(UUID worldId, int x, int z) {
        static ChunkKey of(Chunk chunk) {
            return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        }
    }
}
 

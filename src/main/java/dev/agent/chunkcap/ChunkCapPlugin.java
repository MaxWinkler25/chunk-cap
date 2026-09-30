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
        getLogger().info("ChunkCap enabled with " + limits.size() + " configured block limit(s).");
    }

    private void loadLimits() {
        limits.clear();
        var section = getConfig().getConfigurationSection("limits");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            int cap = section.getInt(key, -1);
            if (material == null || !material.isBlock() || cap < 0) {
                getLogger().warning("Ignoring invalid limit entry: " + key + " = " + section.get(key));
                continue;
            }
            limits.put(material, cap);
        }
        countCache.clear();
    }

    private void saveLimits() {
        getConfig().set("limits", null);
        for (Map.Entry<Material, Integer> entry : limits.entrySet()) {
            getConfig().set("limits." + entry.getKey().name(), entry.getValue());
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
                    sender.sendMessage("§eUsage: /" + label + " set <block> <max-per-chunk>");
                    return true;
                }
                Material material = parseBlock(args[1]);
                if (material == null) {
                    sender.sendMessage("§cUnknown block: " + args[1]);
                    return true;
                }
                int cap;
                try {
                    cap = Integer.parseInt(args[2]);
                    if (cap < 0) throw new NumberFormatException();
                } catch (NumberFormatException exception) {
                    sender.sendMessage("§cThe limit must be a whole number of 0 or greater.");
                    return true;
                }
                limits.put(material, cap);
                saveLimits();
                sender.sendMessage("§aChunk limit for §f" + material.name() + "§a set to §f" + cap + "§a.");
                return true;
            }
            case "remove", "unset" -> {
                if (args.length != 2) {
                    sender.sendMessage("§eUsage: /" + label + " remove <block>");
                    return true;
                }
                Material material = parseBlock(args[1]);
                if (material == null || limits.remove(material) == null) {
                    sender.sendMessage("§cThat block does not have a configured limit.");
                    return true;
                }
                saveLimits();
                sender.sendMessage("§aRemoved the chunk limit for §f" + material.name() + "§a.");
                return true;
            }
            case "list" -> {
                if (limits.isEmpty()) {
                    sender.sendMessage("§7No per-chunk block limits are configured.");
                } else {
                    sender.sendMessage("§6ChunkCap limits (per chunk):");
                    new TreeMap<String, Integer>(Comparator.naturalOrder()) {{
                        limits.forEach((material, cap) -> put(material.name(), cap));
                    }}.forEach((name, cap) -> sender.sendMessage("§e - §f" + name + "§7: " + cap));
                }
                return true;
            }
            case "check" -> {
                if (args.length != 2 || !(sender instanceof Player player)) {
                    sender.sendMessage("§eUsage: /" + label + " check <block> (players only)");
                    return true;
                }
                Material material = parseBlock(args[1]);
                Integer cap = material == null ? null : limits.get(material);
                if (cap == null) {
                    sender.sendMessage("§cThat block has no configured limit.");
                    return true;
                }
                int count = getCount(player.getLocation().getChunk(), material);
                sender.sendMessage("§f" + material.name() + "§7 in this chunk: §f" + count + "§7 / §f" + cap);
                return true;
            }
            case "reload" -> {
                reloadConfig();
                loadLimits();
                sender.sendMessage("§aChunkCap configuration reloaded.");
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

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§6ChunkCap commands:");
        sender.sendMessage("§e/" + label + " set <block> <max> §7- Set a per-chunk cap (0 blocks placement)");
        sender.sendMessage("§e/" + label + " remove <block> §7- Remove a cap");
        sender.sendMessage("§e/" + label + " list §7- Show all caps");
        sender.sendMessage("§e/" + label + " check <block> §7- Count it in your current chunk");
        sender.sendMessage("§e/" + label + " reload §7- Reload config.yml");
    }

    private Material parseBlock(String name) {
        Material material = Material.matchMaterial(name);
        return material != null && material.isBlock() ? material : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return matching(List.of("set", "remove", "list", "check", "reload", "help"), args[0]);
        if (args.length == 2 && List.of("set", "remove", "unset", "check").contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> names = args[0].equalsIgnoreCase("set")
                    ? java.util.Arrays.stream(Material.values()).filter(Material::isBlock).map(Material::name).collect(Collectors.toList())
                    : limits.keySet().stream().map(Material::name).sorted().collect(Collectors.toList());
            return matching(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) return List.of("16", "32", "64", "128");
        return List.of();
    }

    private List<String> matching(List<String> options, String prefix) {
        String start = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(start)).limit(100).collect(Collectors.toList());
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
            event.getPlayer().sendMessage("§cThis chunk has reached its limit of §f" + cap + " " + material.name() + "§c.");
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
 

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

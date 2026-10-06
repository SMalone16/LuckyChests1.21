package org.pawling.luckychests;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class LuckyChestsPlugin extends JavaPlugin implements Listener, TabExecutor {

    private enum LuckyType {
        AWESOME,
        GOOD,
        BAD
    }

    private NamespacedKey typeKey;
    private NamespacedKey openedKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        typeKey = new NamespacedKey(this, "lucky_chest_type");
        openedKey = new NamespacedKey(this, "lucky_chest_opened");

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("luckychest") != null) {
            getCommand("luckychest").setExecutor(this);
            getCommand("luckychest").setTabCompleter(this);
        }

        getLogger().info("LuckyChests enabled. New chunks can now generate Lucky Chests.");
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) {
            return;
        }

        World world = event.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL || !isEnabledWorld(world)) {
            return;
        }

        double chance = clamp(getConfig().getDouble("spawn-chance-per-new-chunk", 0.08), 0.0, 1.0);
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }

        Chunk chunk = event.getChunk();

        // Wait one tick so vanilla chunk population is fully finished before we place a chest.
        getServer().getScheduler().runTask(this, () -> tryPlaceLuckyChest(chunk, ThreadLocalRandom.current()));
    }

    private boolean isEnabledWorld(World world) {
        List<String> enabledWorlds = getConfig().getStringList("enabled-worlds");
        return enabledWorlds.isEmpty() || enabledWorlds.contains(world.getName());
    }

    private void tryPlaceLuckyChest(Chunk chunk, Random random) {
        World world = chunk.getWorld();
        if (!world.isChunkLoaded(chunk.getX(), chunk.getZ())) {
            return;
        }

        int attempts = Math.max(1, getConfig().getInt("placement-attempts", 8));
        int minY = Math.max(world.getMinHeight() + 1, getConfig().getInt("minimum-y", 1));
        int maxY = Math.min(world.getMaxHeight() - 2, getConfig().getInt("maximum-y", 254));

        for (int attempt = 0; attempt < attempts; attempt++) {
            // Stay two blocks away from chunk borders so we do not accidentally merge
            // with a chest in a neighboring chunk.
            int x = (chunk.getX() << 4) + 2 + random.nextInt(12);
            int z = (chunk.getZ() << 4) + 2 + random.nextInt(12);
            int surfaceY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            int chestY = surfaceY + 1;

            if (chestY < minY || chestY > maxY) {
                continue;
            }

            Block ground = world.getBlockAt(x, chestY - 1, z);
            Block target = world.getBlockAt(x, chestY, z);
            Block above = world.getBlockAt(x, chestY + 1, z);

            if (!ground.getType().isSolid() || !target.isEmpty() || !above.isEmpty()) {
                continue;
            }

            if (hasAdjacentChest(target)) {
                continue;
            }

            placeLuckyChest(target, chooseType(random));
            getLogger().fine("Generated Lucky Chest at " + x + ", " + chestY + ", " + z);
            return;
        }
    }

    private boolean hasAdjacentChest(Block block) {
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            Material type = block.getRelative(face).getType();
            if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
                return true;
            }
        }
        return false;
    }

    private LuckyType chooseType(Random random) {
        int awesome = Math.max(0, getConfig().getInt("type-weights.awesome", 1));
        int good = Math.max(0, getConfig().getInt("type-weights.good", 1));
        int bad = Math.max(0, getConfig().getInt("type-weights.bad", 1));
        int total = awesome + good + bad;

        if (total <= 0) {
            return LuckyType.GOOD;
        }

        int roll = random.nextInt(total);
        if (roll < awesome) {
            return LuckyType.AWESOME;
        }
        if (roll < awesome + good) {
            return LuckyType.GOOD;
        }
        return LuckyType.BAD;
    }

    private void placeLuckyChest(Block block, LuckyType type) {
        block.setType(Material.CHEST, false);

        if (!(block.getState() instanceof Chest chest)) {
            getLogger().warning("Could not create Lucky Chest at " + block.getLocation());
            return;
        }

        PersistentDataContainer pdc = chest.getPersistentDataContainer();
        pdc.set(typeKey, PersistentDataType.STRING, type.name());
        pdc.set(openedKey, PersistentDataType.BYTE, (byte) 0);
        chest.update(true, false);
    }

    @EventHandler
    public void onLuckyChestOpen(InventoryOpenEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Chest chest)) {
            return;
        }

        PersistentDataContainer pdc = chest.getPersistentDataContainer();
        String storedType = pdc.get(typeKey, PersistentDataType.STRING);
        Byte opened = pdc.get(openedKey, PersistentDataType.BYTE);

        if (storedType == null || (opened != null && opened == (byte) 1)) {
            return;
        }

        LuckyType type;
        try {
            type = LuckyType.valueOf(storedType);
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Lucky Chest at " + chest.getLocation() + " has an invalid type: " + storedType);
            return;
        }

        // Mark first so a second simultaneous open cannot trigger the chest twice.
        pdc.set(openedKey, PersistentDataType.BYTE, (byte) 1);
        chest.update(true, false);

        Inventory inventory = event.getInventory();
        inventory.clear();
        Random random = ThreadLocalRandom.current();

        switch (type) {
            case AWESOME -> fillAwesomeChest(inventory, random);
            case GOOD -> fillGoodChest(inventory, random);
            case BAD -> {
                inventory.addItem(new ItemStack(Material.SHIELD, 1));
                Block chestBlock = chest.getBlock();
                getServer().getScheduler().runTask(this, () -> spawnSkeletonTrap(chestBlock, random));
            }
        }
    }

    private void fillAwesomeChest(Inventory inventory, Random random) {
        List<Material> pool = new ArrayList<>(List.of(
                Material.DIAMOND_SWORD,
                Material.DIAMOND_AXE,
                Material.DIAMOND_PICKAXE,
                Material.DIAMOND_SHOVEL,
                Material.DIAMOND_HELMET,
                Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS,
                Material.DIAMOND_BOOTS
        ));

        Collections.shuffle(pool, random);
        int count = 1 + random.nextInt(4);

        for (int i = 0; i < count; i++) {
            inventory.addItem(createEnchantedDiamondItem(pool.get(i), random));
        }

        if (isCrossModLootEnabled()) {
            addAwesomeCrossModLoot(inventory);
        }
    }

    private ItemStack createEnchantedDiamondItem(Material material, Random random) {
        ItemStack item = new ItemStack(material, 1);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1 + random.nextInt(3));

        switch (material) {
            case DIAMOND_SWORD -> {
                item.addUnsafeEnchantment(Enchantment.SHARPNESS, 2 + random.nextInt(4));
                if (random.nextBoolean()) {
                    item.addUnsafeEnchantment(Enchantment.LOOTING, 1 + random.nextInt(3));
                }
            }
            case DIAMOND_AXE -> {
                item.addUnsafeEnchantment(Enchantment.SHARPNESS, 2 + random.nextInt(4));
                item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 2 + random.nextInt(4));
            }
            case DIAMOND_PICKAXE, DIAMOND_SHOVEL -> {
                item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 2 + random.nextInt(4));
                if (random.nextBoolean()) {
                    item.addUnsafeEnchantment(Enchantment.FORTUNE, 1 + random.nextInt(3));
                }
            }
            case DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS ->
                    item.addUnsafeEnchantment(Enchantment.PROTECTION, 1 + random.nextInt(4));
            default -> {
                // Every material in the pool is handled above.
            }
        }

        return item;
    }

    private void fillGoodChest(Inventory inventory, Random random) {
        inventory.addItem(
                new ItemStack(Material.IRON_INGOT, 1 + random.nextInt(10)),
                new ItemStack(Material.TORCH, 48),
                new ItemStack(Material.COOKED_BEEF, 12 + random.nextInt(13))
        );

        if (isCrossModLootEnabled()) {
            addGoodCrossModLoot(inventory, random);
        }
    }

    private boolean isCrossModLootEnabled() {
        return getConfig().getBoolean("cross-mod-loot.enabled", true);
    }

    private void addGoodCrossModLoot(Inventory inventory, Random random) {
        // Plane progression: useful vanilla resources that match the EaglerAirplane recipe.
        inventory.addItem(
                new ItemStack(Material.IRON_BLOCK, 1),
                new ItemStack(Material.REDSTONE_BLOCK, 1),
                createWaterBreathingPotion(180)
        );

        // One extra survival/progression reward. These are useful with the current
        // airplane, zombie-temple, and Locust classroom plugins, but remain normal
        // vanilla items when those plugins are not selected for the session.
        switch (random.nextInt(3)) {
            case 0 -> inventory.addItem(new ItemStack(Material.DIAMOND, 1));
            case 1 -> inventory.addItem(new ItemStack(Material.SHIELD, 1));
            default -> inventory.addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
        }
    }

    private void addAwesomeCrossModLoot(Inventory inventory) {
        // A complete set of vanilla ingredients for one Plane Kit:
        // I D I / R F R / I D I
        inventory.addItem(
                new ItemStack(Material.IRON_BLOCK, 4),
                new ItemStack(Material.DIAMOND, 2),
                new ItemStack(Material.REDSTONE_BLOCK, 2),
                new ItemStack(Material.FURNACE, 1),

                // EaglerSpace treats Water Breathing as a continuous oxygen supply.
                createWaterBreathingPotion(480),
                createWaterBreathingPotion(480),

                // Extra protection for the zombie-temple and Locust encounters.
                new ItemStack(Material.SHIELD, 1),
                new ItemStack(Material.GOLDEN_APPLE, 2)
        );
    }

    private ItemStack createWaterBreathingPotion(int durationSeconds) {
        ItemStack potion = new ItemStack(Material.POTION, 1);
        if (potion.getItemMeta() instanceof PotionMeta meta) {
            meta.addCustomEffect(
                    new PotionEffect(PotionEffectType.WATER_BREATHING, durationSeconds * 20, 0),
                    true
            );
            meta.setDisplayName(ChatColor.AQUA + "Oxygen Supply (" + (durationSeconds / 60) + " min)");
            potion.setItemMeta(meta);
        }
        return potion;
    }

    private void spawnSkeletonTrap(Block chestBlock, Random random) {
        World world = chestBlock.getWorld();

        for (int i = 0; i < 5; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = 1.5 + random.nextDouble() * 3.5;

            int x = (int) Math.floor(chestBlock.getX() + 0.5 + Math.cos(angle) * radius);
            int z = (int) Math.floor(chestBlock.getZ() + 0.5 + Math.sin(angle) * radius);
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;

            if (y > 255) {
                y = Math.min(255, chestBlock.getY());
            }

            world.spawnEntity(
                    new org.bukkit.Location(world, x + 0.5, y, z + 0.5),
                    EntityType.SKELETON
            );
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("LuckyChests testing commands must be run by a player.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player, label);
            return true;
        }

        if (args[0].equalsIgnoreCase("spawn")) {
            LuckyType type = LuckyType.GOOD;

            if (args.length >= 2) {
                String requested = args[1].toUpperCase(Locale.ROOT);
                if (requested.equals("RANDOM")) {
                    type = chooseType(ThreadLocalRandom.current());
                } else {
                    try {
                        type = LuckyType.valueOf(requested);
                    } catch (IllegalArgumentException exception) {
                        player.sendMessage(ChatColor.RED + "Type must be awesome, good, bad, or random.");
                        return true;
                    }
                }
            }

            Block base = player.getTargetBlockExact(6);
            if (base == null) {
                player.sendMessage(ChatColor.RED + "Look at a block within 6 blocks and try again.");
                return true;
            }

            Block target = base.getRelative(BlockFace.UP);
            if (!target.isEmpty() || !target.getRelative(BlockFace.UP).isEmpty()) {
                player.sendMessage(ChatColor.RED + "There is not enough empty space above that block.");
                return true;
            }

            if (target.getY() > 254) {
                player.sendMessage(ChatColor.RED + "For Eaglercraft compatibility, test chests must be at Y 254 or lower.");
                return true;
            }

            placeLuckyChest(target, type);
            player.sendMessage(ChatColor.GOLD + "Spawned a " + type.name() + " Lucky Chest.");
            return true;
        }

        if (args[0].equalsIgnoreCase("inspect")) {
            Block block = player.getTargetBlockExact(6);
            if (block == null || !(block.getState() instanceof Chest chest)) {
                player.sendMessage(ChatColor.RED + "Look directly at a chest within 6 blocks.");
                return true;
            }

            String type = chest.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
            Byte opened = chest.getPersistentDataContainer().get(openedKey, PersistentDataType.BYTE);

            if (type == null) {
                player.sendMessage(ChatColor.YELLOW + "That is a normal chest, not a Lucky Chest.");
            } else {
                player.sendMessage(ChatColor.AQUA + "Lucky Chest: " + type
                        + " | opened=" + (opened != null && opened == (byte) 1));
            }
            return true;
        }

        sendUsage(player, label);
        return true;
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage(ChatColor.YELLOW + "/" + label + " spawn <awesome|good|bad|random>");
        player.sendMessage(ChatColor.YELLOW + "/" + label + " inspect");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("spawn", "inspect");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return List.of("awesome", "good", "bad", "random");
        }
        return List.of();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

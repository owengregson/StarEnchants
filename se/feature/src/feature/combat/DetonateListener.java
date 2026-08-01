package feature.combat;

import engine.effect.kind.EnchantLevels;
import engine.sink.SinkEnv;
import engine.sink.SinkFactory;
import engine.sink.SinkReadback;
import feature.compat.Hands;
import item.view.ItemViewCache;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import platform.item.Inventories;
import platform.protect.ProtectionService;
import platform.resolve.RegistryResolvers;

/** Cosmic Detonate and Atomic Detonate's oriented multi-block mining implementation. */
public final class DetonateListener implements Listener {

    private static final String DETONATE = "enchants/detonate";
    private static final String ATOMIC = "enchants/atomic-detonate";
    private static final String AUTO_SMELT = "enchants/auto-smelt";
    private static final String FUSE = "enchants/fuse";
    private static final String TELEPATHY = "enchants/telepathy";
    private static final String EXPERT = "enchants/explosives-expert";

    private static final Set<String> PICKAXE_BLOCKS = Set.of(
            "SANDSTONE", "STONE", "COBBLESTONE", "MOSSY_COBBLESTONE",
            "SMOOTH_BRICK", "STONE_BRICKS", "OBSIDIAN", "MOB_SPAWNER", "SPAWNER", "COAL_ORE",
            "IRON_ORE", "GOLD_ORE", "DIAMOND_ORE", "LAPIS_ORE", "REDSTONE_ORE",
            "EMERALD_ORE", "NETHERRACK");
    private static final Set<String> SPADE_BLOCKS = Set.of(
            "DIRT", "SAND", "GRAVEL", "GRASS", "GRASS_BLOCK", "MYCEL", "MYCELIUM", "CLAY");
    private static final Set<String> ATOMIC_VOID_DROPS = Set.of(
            "COBBLESTONE", "STONE", "GRAVEL", "DIRT", "GRASS", "GRASS_BLOCK", "SANDSTONE", "NETHERRACK");

    private final ItemViewCache views;
    private final Hands hands;
    private final SinkFactory sinks;
    private final SinkEnv env;
    private final ProtectionService protection;
    private final Map<UUID, BlockFace> faces = new ConcurrentHashMap<>();
    private final int explosionParticle;
    private final int itemBreakSound;

    public DetonateListener(ItemViewCache views, Hands hands, SinkFactory sinks, SinkEnv env,
                            ProtectionService protection, RegistryResolvers resolvers) {
        this.views = Objects.requireNonNull(views, "views");
        this.hands = Objects.requireNonNull(hands, "hands");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        this.env = Objects.requireNonNull(env, "env");
        this.protection = Objects.requireNonNull(protection, "protection");
        Objects.requireNonNull(resolvers, "resolvers");
        explosionParticle = resolvers.particle("EXPLOSION_LARGE").orElse(-1);
        itemBreakSound = resolvers.sound("ITEM_BREAK").orElse(-1);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!hands.isMainHand(event) || event.getClickedBlock() == null || event.getBlockFace() == null) {
            return;
        }
        ItemStack held = hands.mainHand(event.getPlayer());
        int atomic = CosmicTierGate.tierSixPlusEnabled(event.getPlayer()) ? level(held, ATOMIC) : 0;
        if (level(held, DETONATE) > 0 || atomic > 0) {
            faces.put(event.getPlayer().getUniqueId(), event.getBlockFace());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = hands.mainHand(player);
        int atomicLevel = CosmicTierGate.tierSixPlusEnabled(player) ? level(tool, ATOMIC) : 0;
        int detonateLevel = level(tool, DETONATE);
        if (atomicLevel <= 0 && detonateLevel <= 0) {
            return;
        }
        Block origin = event.getBlock();
        if (originBlocked(origin.getType())) {
            return;
        }
        boolean atomic = atomicLevel > 0;
        int enchantLevel = atomic ? atomicLevel : detonateLevel;
        BlockFace face = faces.getOrDefault(player.getUniqueId(), inferFace(player, origin));
        int depth = atomic ? Math.min(9, 3 + enchantLevel) : detonateDepth(enchantLevel);
        if (depth <= 0) {
            return;
        }

        boolean pickaxe = tool != null && tool.getType().name().contains("_PICKAXE");
        boolean spade = tool != null && (tool.getType().name().contains("_SPADE")
                || tool.getType().name().contains("_SHOVEL"));
        boolean expert = atomic && CosmicTierGate.tierSixPlusEnabled(player)
                && EnchantLevels.worn(player, EXPERT) > 0;
        int autoSmelt = level(tool, AUTO_SMELT);
        boolean fuse = level(tool, FUSE) > 0;
        boolean telepathy = level(tool, TELEPATHY) > 0;
        boolean silentlyRemoved = false;
        boolean naturallyBroken = false;
        SinkReadback sink = sinks.create(env);

        for (Block block : volume(origin, face, enchantLevel, depth, atomic)) {
            if (sameBlock(block, origin) || !eligible(player, block, pickaxe, spade, expert, atomic)) {
                continue; // bug fix: the normal event owns the origin drop exactly once
            }
            Material original = block.getType();
            String originalName = original.name();
            java.util.Collection<ItemStack> naturalDrops = block.getDrops(tool);
            if (explosionParticle >= 0) {
                sink.particle(block.getLocation().add(0.0, 0.5, 0.0), explosionParticle, 1,
                        -1, 0.0, 0.0, 0.0, 0.025);
            }
            if (naturalDrops.isEmpty()) {
                if (atomic && ATOMIC_VOID_DROPS.contains(originalName)) {
                    block.setType(Material.AIR, false);
                    silentlyRemoved = true;
                } else {
                    block.breakNaturally(tool);
                    naturallyBroken = true;
                }
                continue;
            }

            // The source accidentally kept only toArray()[0]. Intended multi-block mining preserves every drop.
            block.setType(Material.AIR, false);
            silentlyRemoved = true;
            for (ItemStack raw : naturalDrops) {
                ItemStack drop = raw.clone();
                if (fuse && autoSmelt > 0 && (originalName.equals("IRON_ORE") || originalName.equals("GOLD_ORE"))) {
                    Material ingot = Material.matchMaterial(originalName.equals("IRON_ORE")
                            ? "IRON_INGOT" : "GOLD_INGOT");
                    if (ingot != null) {
                        drop.setType(ingot);
                        drop.setAmount(autoSmelt);
                    }
                }
                if (isRails(drop.getType()) || (atomic && ATOMIC_VOID_DROPS.contains(originalName))) {
                    continue;
                }
                if (telepathy) {
                    Inventories.giveOrDrop(player, drop);
                } else {
                    player.getWorld().dropItem(player.getLocation(), drop);
                }
            }
        }

        if (silentlyRemoved && !naturallyBroken && pickaxe) {
            damageToolOnce(player, tool, sink);
        }
        sink.flush();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        faces.remove(event.getPlayer().getUniqueId());
    }

    private int level(ItemStack item, String key) {
        return item == null ? 0 : views.of(item).combat().enchants().getOrDefault(key, 0);
    }

    private boolean eligible(Player player, Block block, boolean pickaxe, boolean spade,
                             boolean expert, boolean atomic) {
        Material type = block.getType();
        String name = type.name();
        int y = block.getY();
        if (type == Material.AIR || y <= 0 || y >= block.getWorld().getMaxHeight()
                || blacklisted(name) || (!expert && PICKAXE_BLOCKS.contains(name) && !pickaxe)
                || (!expert && SPADE_BLOCKS.contains(name) && !spade)
                || protectedAbove(block.getRelative(BlockFace.UP).getType().name())
                || !protection.allows(player.getUniqueId(), block.getLocation())) {
            return false;
        }
        return !atomic || y > 0;
    }

    private static boolean originBlocked(Material material) {
        String name = material.name();
        return name.contains("COMPARATOR") || name.equals("SPONGE");
    }

    private static boolean blacklisted(String name) {
        return name.equals("LADDER") || name.equals("GLOWSTONE") || name.equals("BEDROCK")
                || name.equals("WATER") || name.equals("STATIONARY_WATER")
                || name.equals("LAVA") || name.equals("STATIONARY_LAVA")
                || name.equals("OBSIDIAN") || name.equals("SPONGE") || name.equals("HOPPER")
                || name.equals("ANVIL") || name.contains("COMPARATOR") || name.contains("_DOOR")
                || name.equals("WOOD_DOOR") || name.equals("IRON_DOOR_BLOCK")
                || name.equals("SIGN") || name.equals("SIGN_POST") || name.endsWith("_SIGN")
                || name.endsWith("_WALL_SIGN");
    }

    private static boolean protectedAbove(String name) {
        return name.contains("COMPARATOR") || name.contains("DIODE") || name.contains("REPEATER");
    }

    private static boolean isRails(Material material) {
        return material.name().equals("RAILS") || material.name().equals("RAIL");
    }

    @SuppressWarnings("deprecation")
    private void damageToolOnce(Player player, ItemStack tool, SinkReadback sink) {
        if (tool == null || tool.getType().getMaxDurability() <= 0) {
            return;
        }
        if (tool.getDurability() >= tool.getType().getMaxDurability() - 1) {
            if (hands.mainHand(player) == tool || tool.equals(hands.mainHand(player))) {
                hands.setMainHand(player, null);
            } else {
                player.getInventory().removeItem(tool);
            }
            player.updateInventory();
            if (itemBreakSound >= 0) {
                sink.privateSound(player, itemBreakSound, 1.0f, 1.0f);
            }
        } else {
            tool.setDurability((short) (tool.getDurability() + 1));
        }
    }

    private static int detonateDepth(int level) {
        double roll = ThreadLocalRandom.current().nextDouble();
        if (level == 1 || level == 2) {
            return roll < level * 0.33 ? 1 : 0;
        }
        if (level == 3) {
            return 1;
        }
        if (level == 4 || level == 5) {
            return roll < (level - 3) * 0.33 ? 2 : 1;
        }
        if (level == 6) {
            return 2;
        }
        if (level == 7 || level == 8) {
            return roll < (level - 6) * 0.33 ? 3 : 2;
        }
        return level == 9 ? 3 : 0;
    }

    private static Set<Block> volume(Block origin, BlockFace face, int level, int depth, boolean atomic) {
        int negative = atomic ? switch (level) {
            case 1 -> 1;
            case 2, 3 -> 2;
            case 4 -> 3;
            default -> 2;
        } : 1;
        int positive = atomic ? switch (level) {
            case 1, 2 -> 2;
            case 3, 4 -> 3;
            default -> 1;
        } : 1;
        BlockFace inward = face.getOppositeFace();
        Set<Block> blocks = new LinkedHashSet<>();
        for (int d = 0; d < depth; d++) {
            Block center = origin.getRelative(inward, d);
            for (int a = -negative; a <= positive; a++) {
                for (int b = -negative; b <= positive; b++) {
                    blocks.add(plane(center, face, a, b));
                }
            }
        }
        return blocks;
    }

    private static Block plane(Block center, BlockFace face, int a, int b) {
        return switch (face) {
            case UP, DOWN -> center.getRelative(a, 0, b);
            case EAST, WEST -> center.getRelative(0, a, b);
            case NORTH, SOUTH -> center.getRelative(a, b, 0);
            default -> center;
        };
    }

    private static boolean sameBlock(Block a, Block b) {
        return a.getWorld().equals(b.getWorld()) && a.getX() == b.getX()
                && a.getY() == b.getY() && a.getZ() == b.getZ();
    }

    private static BlockFace inferFace(Player player, Block origin) {
        Location eye = player.getEyeLocation();
        double dx = eye.getX() - (origin.getX() + 0.5);
        double dy = eye.getY() - (origin.getY() + 0.5);
        double dz = eye.getZ() - (origin.getZ() + 0.5);
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        if (ay >= ax && ay >= az) {
            return dy >= 0 ? BlockFace.UP : BlockFace.DOWN;
        }
        if (ax >= az) {
            return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }
}

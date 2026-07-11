package engine.sink;

import java.util.List;
import java.util.Objects;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import platform.caps.Regions;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import platform.text.Colors;
import schema.spec.HandleCategory;

/**
 * The modern (Paper 1.17.1 → 26.1.x + Folia) concrete {@link Sink} — the era leaf over {@link DispatchSinkBase}.
 * The plan/batching, {@link engine.interact.DamageFold} bookkeeping, the stores, and every era-neutral intent
 * body live in the shared base (ADR-0036); this leaf implements only the modern platform edge: version-volatile
 * ids resolve through {@link RuntimeHandles} (§9), particles/sounds/attributes go through the modern Bukkit API,
 * and durability rides {@link Damageable} item meta. Same-FQN counterpart to the {@code overlay/legacy} impl
 * (which wraps {@code RenameResolvers} + {@code v1_8_R3} NMS).
 */
public final class ModernDispatchSink extends DispatchSinkBase {

    private final RuntimeHandles handles;

    /**
     * Sharing the stores (via the per-boot {@link SinkEnv}) is what makes the KNOCKBACK_CONTROL / KEEP_ON_DEATH /
     * TELEBLOCK / IMMUNE flags a hit writes visible to the separate knockback / death / teleport / damage events'
     * listeners.
     */
    public ModernDispatchSink(RuntimeHandles handles, SinkEnv env) {
        super(env);
        this.handles = Objects.requireNonNull(handles, "handles");
    }

    // ── Interned-id resolution (modern: RuntimeHandles casts to the live Bukkit object) ───────────

    @Override
    protected Material material(int id) {
        return handles.material(id);
    }

    @Override
    protected Sound sound(int id) {
        return handles.sound(id);
    }

    @Override
    protected PotionEffectType potionEffect(int id) {
        return handles.potionEffect(id);
    }

    @Override
    protected EntityType entityType(int id) {
        return handles.entityType(id);
    }

    @Override
    protected boolean isAir(Material material) {
        return material.isAir();
    }

    @Override
    protected boolean isItemMaterial(Material material) {
        return material.isItem();
    }

    // ── Entity / health leaves ─────────────────────────────────────────────────────────────────

    @Override
    protected void applyInvulnerable(LivingEntity target, boolean invulnerable) {
        target.setInvulnerable(invulnerable);
    }

    @Override
    protected void applyNoAi(LivingEntity entity) {
        entity.setAI(false); // Bukkit 1.9+, present across the whole modern floor
    }

    @Override
    protected void applyBarShape(World world, int x, int y, int z,
                                 boolean north, boolean south, boolean east, boolean west) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getBlockData() instanceof MultipleFacing bars) {
            bars.setFace(BlockFace.NORTH, north);
            bars.setFace(BlockFace.SOUTH, south);
            bars.setFace(BlockFace.EAST, east);
            bars.setFace(BlockFace.WEST, west);
            block.setBlockData(bars, false); // no physics — the temp ledger owns this tile's lifecycle
        }
    }

    @Override
    protected void mountEntity(Entity vehicle, Entity passenger) {
        vehicle.addPassenger(passenger);
    }

    @Override
    protected void applySaddle(LivingEntity entity) {
        if (entity instanceof AbstractHorse horse) {
            horse.setTamed(true); // an untamed horse ignores rider steering even when saddled
            horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        }
    }

    @Override
    protected boolean hasMaxHealthAttribute(LivingEntity entity) {
        return maxHealthAttribute(entity) != null;
    }

    @Override
    protected double maxHealthBase(LivingEntity entity) {
        AttributeInstance maxHealth = maxHealthAttribute(entity);
        return maxHealth != null ? maxHealth.getBaseValue() : 0.0;
    }

    @Override
    protected void setMaxHealthBase(LivingEntity entity, double value) {
        AttributeInstance maxHealth = maxHealthAttribute(entity);
        if (maxHealth != null) {
            maxHealth.setBaseValue(value);
        }
    }

    /** The max-health attribute instance for {@code entity}, resolved version-adaptively, or {@code null}. */
    private AttributeInstance maxHealthAttribute(LivingEntity entity) {
        Object attribute = handles.resolveByName(HandleCategory.ATTRIBUTE, "GENERIC_MAX_HEALTH");
        return attribute instanceof Attribute resolved ? entity.getAttribute(resolved) : null;
    }

    @Override
    @SuppressWarnings({"deprecation", "removal"}) // the UUID AttributeModifier ctor: deprecated-not-removed across the range
    protected void setWornMaxHealthModifier(Player player, double total) {
        AttributeInstance maxHealth = maxHealthAttribute(player);
        if (maxHealth == null) {
            return;
        }
        // Replace-by-identity: drop OUR modifier (including a stale one a crash left in playerdata), then
        // re-add at the new total. Never touches the base or any other plugin's modifiers.
        for (AttributeModifier modifier : List.copyOf(maxHealth.getModifiers())) {
            if (WORN_MAX_HEALTH_ID.equals(modifier.getUniqueId())) {
                maxHealth.removeModifier(modifier);
            }
        }
        if (total > 0.0) {
            maxHealth.addModifier(new AttributeModifier(WORN_MAX_HEALTH_ID, WORN_MAX_HEALTH_NAME, total,
                    AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    @Override
    @SuppressWarnings("deprecation") // getMaxHealth: deprecated-not-removed across the whole range.
    protected double maxHealth(LivingEntity entity) {
        AttributeInstance maxHealth = maxHealthAttribute(entity);
        return maxHealth != null ? maxHealth.getValue() : entity.getMaxHealth();
    }

    @Override
    protected void applySpawnHealth(LivingEntity entity, double health) {
        AttributeInstance maxHealth = maxHealthAttribute(entity);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
        }
        entity.setHealth(Math.min(health, maxHealth(entity)));
    }

    // ── Teleport leaves ──────────────────────────────────────────────────────────────────────────

    @Override
    protected void teleportTo(Entity target, Location dest) {
        // Teleports are async on Folia; teleportAsync is correct on Paper too and present on the floor API.
        target.teleportAsync(dest);
    }

    /** Whether {@code dest} has body room (feet + head passable) and an unobstructed sight line from {@code from}. */
    @Override
    protected boolean isSafeDestination(Location dest, Location from) {
        // Cold teleport path — a cross-region / unloaded read is not provably safe → caller uses the fallback.
        return Regions.read("DispatchSink.isSafeDestination", () -> {
            World world = dest.getWorld();
            if (world == null) {
                return false;
            }
            Block feet = dest.getBlock();
            Block head = feet.getRelative(0, 1, 0);
            if (!feet.isPassable() || !head.isPassable()) {
                return false;
            }
            if (from != null && from.getWorld() == world) {
                Vector dir = dest.toVector().subtract(from.toVector());
                double dist = dir.length();
                if (dist > 1.0e-4) {
                    RayTraceResult hit = world.rayTraceBlocks(from, dir.normalize(), dist,
                            FluidCollisionMode.NEVER, true);
                    if (hit != null && hit.getHitBlock() != null) {
                        return false; // a wall stands between the player and the destination
                    }
                }
            }
            return true;
        }, false);
    }

    // ── Inventory / durability leaves (modern: main-hand slot + Damageable item meta) ─────────────

    @Override
    protected ItemStack mainHand(Player target) {
        return target.getInventory().getItemInMainHand();
    }

    @Override
    protected void setMainHand(Player target, ItemStack item) {
        target.getInventory().setItemInMainHand(item);
    }

    @Override
    protected ItemStack mainHand(EntityEquipment equipment) {
        return equipment.getItemInMainHand();
    }

    @Override
    protected void setMainHand(EntityEquipment equipment, ItemStack item) {
        equipment.setItemInMainHand(item);
    }

    @Override
    protected boolean applyRepair(ItemStack item, int amount) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int repaired = amount < 0 ? 0 : Math.max(0, damageable.getDamage() - amount);
            damageable.setDamage(repaired);
            item.setItemMeta(meta);
            return true;
        }
        return false;
    }

    @Override
    protected boolean applyDamage(ItemStack item, int amount) {
        if (item == null || amount <= 0) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            int worn = Math.min(item.getType().getMaxDurability(), damageable.getDamage() + amount);
            damageable.setDamage(worn);
            item.setItemMeta(meta);
            return true;
        }
        return false;
    }

    @Override
    protected void adjustArmorDurability(LivingEntity entity, int amount, boolean repair) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        ItemStack[] armor = equipment.getArmorContents();
        boolean changed = false;
        for (ItemStack piece : armor) {
            if (piece == null) {
                continue;
            }
            ItemMeta meta = piece.getItemMeta();
            if (meta instanceof Damageable damageable) {
                int current = damageable.getDamage();
                int next;
                if (repair) {
                    next = amount < 0 ? 0 : Math.max(0, current - amount);
                } else {
                    next = Math.min(piece.getType().getMaxDurability(), current + amount);
                }
                damageable.setDamage(next);
                piece.setItemMeta(meta);
                changed = true;
            }
        }
        if (changed) {
            equipment.setArmorContents(armor);
        }
    }

    // ── World / spawn leaves ───────────────────────────────────────────────────────────────────

    @Override
    protected void setGuardTarget(Entity spawned, LivingEntity target) {
        if (spawned instanceof Mob mob) {
            // setTarget only stores the reference; the AI runs on the mob's own (spawn) region, so this is
            // not a cross-region read of the attacker.
            mob.setTarget(target);
        }
    }

    @Override
    protected FallingBlock spawnFallingBlock(World world, Location loc, Material material) {
        return world.spawnFallingBlock(loc, material.createBlockData());
    }

    @Override
    protected void doExplosion(World world, Location at, double power, boolean breakBlocks) {
        world.createExplosion(at, (float) power, false, breakBlocks);
    }

    // ── Particle / dust leaves (modern Bukkit particle API) ───────────────────────────────────────

    @Override
    public void particle(Location at, int particleId, int count) {
        particle(at, particleId, count, -1, 0.0, 0.0, 0.0); // addon/no-block path: a plain point burst
    }

    @Override
    public void particle(Location at, int particleId, int count, int blockMaterialId,
                         double offsetX, double offsetY, double offsetZ) {
        Location pos = at.clone(); // own the point: a WAIT tier can defer this to a later tick
        regionOp(pos, () -> spawnParticleAt(pos, particleId, count, blockMaterialId, offsetX, offsetY, offsetZ));
    }

    @Override
    public void particle(LivingEntity target, int particleId, int count, int blockMaterialId,
                         double offsetX, double offsetY, double offsetZ) {
        // Entity-anchored (the PARTICLE who-slot): read the target's mid-body AT DISPATCH on its own region thread.
        entityOp(target, () -> spawnParticleAt(midBody(target), particleId, count, blockMaterialId,
                offsetX, offsetY, offsetZ));
    }

    /** The burst anchor for an entity: its feet + half its height, so a burst frames the body rather than the ground. */
    private static Location midBody(LivingEntity target) {
        return target.getLocation().add(0.0, target.getHeight() * 0.5, 0.0);
    }

    /** Spawn a resolved particle at {@code at} with the given per-axis spread, carrying BLOCK_CRACK/BLOCK_DUST block
     *  data when a material is given (zero offsets reproduce the old point burst exactly). */
    private void spawnParticleAt(Location at, int particleId, int count, int blockMaterialId,
                                double offsetX, double offsetY, double offsetZ) {
        Particle resolved = handles.particle(particleId);
        World world = at.getWorld();
        if (resolved == null || world == null) {
            return;
        }
        if (blockMaterialId >= 0) {
            Material block = material(blockMaterialId);
            if (block != null && block.isBlock()) {
                try {
                    world.spawnParticle(resolved, at, count, offsetX, offsetY, offsetZ, block.createBlockData());
                    return;
                } catch (IllegalArgumentException notBlockData) {
                    // the resolved particle takes no block data — fall through to a plain burst
                }
            }
        }
        world.spawnParticle(resolved, at, count, offsetX, offsetY, offsetZ);
    }

    @Override
    public void dust(Location at, int particleId, int r, int g, int b, float size, int count) {
        regionOp(at, () -> {
            Particle resolved = handles.particle(particleId);
            World world = at.getWorld();
            if (resolved == null || world == null) {
                return;
            }
            Color color = Color.fromRGB(clampChannel(r), clampChannel(g), clampChannel(b));
            float scale = size <= 0f ? 1f : size;
            int n = Math.max(1, count);
            try {
                world.spawnParticle(resolved, at, n, 0.0, 0.0, 0.0, 0.0, new Particle.DustOptions(color, scale));
            } catch (IllegalArgumentException notDust) {
                world.spawnParticle(resolved, at, n); // the resolved particle takes no colour data — plain burst
            }
        });
    }

    // ── Player-feedback leaves (modern Spigot chat / title API) ───────────────────────────────────

    @Override
    @SuppressWarnings("deprecation") // spigot().sendMessage(ChatMessageType, BaseComponent): the floor-stable action-bar path.
    public void actionBar(Player target, String message) {
        // The Spigot chat API is the one action-bar path stable across the whole 1.17.1 → 26.1.x range.
        // Translate '&' → '§' first — fromLegacyText parses '§', not '&'.
        String text = Colors.translate(message);
        entityOp(target, () -> target.spigot().sendMessage(
                ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text)));
    }

    @Override
    @SuppressWarnings("deprecation") // sendTitle(String, String, int, int, int): deprecated-not-removed across the whole range.
    public void title(Player target, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        // 5-arg String sendTitle is the one title path stable across the range (no Adventure Title API on
        // the spigot-mapped floor). Translate '&' → '§' so colour codes render, not show literally.
        String t = Colors.translate(title);
        String s = Colors.translate(subtitle);
        entityOp(target, () -> target.sendTitle(t, s, fadeIn, stay, fadeOut));
    }
}

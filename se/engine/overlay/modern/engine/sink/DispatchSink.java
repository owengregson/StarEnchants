package engine.sink;

import engine.stores.ImmuneStore;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.SuppressionStore;
import engine.stores.TeleblockStore;
import engine.stores.VarStore;
import java.util.Objects;
import java.util.function.LongSupplier;
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
import org.bukkit.block.Block;
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
import platform.economy.EconomyService;
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
public final class DispatchSink extends DispatchSinkBase {

    private final RuntimeHandles handles;

    /** The test default — economy/soul are no-ops, the stores are throwaways. */
    public DispatchSink(RuntimeHandles handles) {
        this(handles, EconomyService.NONE, SoulDebit.NONE, new VarStore(), new SuppressionStore(), () -> 0L);
    }

    public DispatchSink(RuntimeHandles handles, EconomyService economy) {
        this(handles, economy, SoulDebit.NONE, new VarStore(), new SuppressionStore(), () -> 0L);
    }

    public DispatchSink(RuntimeHandles handles, EconomyService economy, SoulDebit souls,
                        VarStore vars, SuppressionStore suppression, LongSupplier nowTicks) {
        this(handles, economy, souls, vars, suppression, new KnockbackControlStore(), nowTicks);
    }

    public DispatchSink(RuntimeHandles handles, EconomyService economy, SoulDebit souls,
                        VarStore vars, SuppressionStore suppression, KnockbackControlStore knockback,
                        LongSupplier nowTicks) {
        this(handles, economy, souls, vars, suppression, knockback, new KeepOnDeathStore(), nowTicks);
    }

    /**
     * Sharing the stores is what makes the KNOCKBACK_CONTROL / KEEP_ON_DEATH flags a hit writes visible to the
     * separate knockback / death events' listeners.
     */
    public DispatchSink(RuntimeHandles handles, EconomyService economy, SoulDebit souls,
                        VarStore vars, SuppressionStore suppression, KnockbackControlStore knockback,
                        KeepOnDeathStore keepOnDeath, LongSupplier nowTicks) {
        this(handles, economy, souls, vars, suppression, knockback, keepOnDeath,
                new TeleblockStore(), new ImmuneStore(), nowTicks);
    }

    /**
     * The full sink. The shared {@link TeleblockStore}/{@link ImmuneStore} the TELEBLOCK / IMMUNE flags write
     * are read back by the teleport / damage listeners on their separate events; shorter ctors default these to
     * throwaways, so those flags are inert unless the real stores are threaded in.
     */
    public DispatchSink(RuntimeHandles handles, EconomyService economy, SoulDebit souls,
                        VarStore vars, SuppressionStore suppression, KnockbackControlStore knockback,
                        KeepOnDeathStore keepOnDeath, TeleblockStore teleblock, ImmuneStore immune,
                        LongSupplier nowTicks) {
        super(economy, souls, vars, suppression, knockback, keepOnDeath, teleblock, immune, nowTicks);
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
    public void addMaxHealth(LivingEntity target, double amount) {
        entityOp(target, () -> {
            // Shifts the base value directly; unequip restoration of this delta lands with WornState (§5.5).
            AttributeInstance maxHealth = maxHealthAttribute(target);
            if (maxHealth != null) {
                maxHealth.setBaseValue(Math.max(1.0, maxHealth.getBaseValue() + amount));
            }
        });
    }

    @Override
    public void drainMaxHealth(LivingEntity target, double fraction, double baseline, double flat, int durationTicks) {
        entityOp(target, () -> {
            AttributeInstance maxHealth = maxHealthAttribute(target);
            if (maxHealth == null) {
                return;
            }
            double overhealth = maxHealth.getBaseValue() - baseline;
            double drain = overhealth * fraction + flat;
            if (drain <= 0) {
                return; // no overhealth to take
            }
            double newBase = Math.max(1.0, maxHealth.getBaseValue() - drain);
            double removed = maxHealth.getBaseValue() - newBase; // exact delta (also when the clamp bit)
            maxHealth.setBaseValue(newBase);
            if (target.getHealth() > maxHealth.getValue()) {
                target.setHealth(Math.max(0.0, maxHealth.getValue())); // clamp current down to the new cap
            }
            if (durationTicks > 0) {
                Scheduling.onEntityLater(target, durationTicks, () -> {
                    AttributeInstance mh = maxHealthAttribute(target);
                    if (mh != null) {
                        mh.setBaseValue(mh.getBaseValue() + removed); // add back exactly what was drained
                    }
                });
            }
        });
    }

    /** The max-health attribute instance for {@code entity}, resolved version-adaptively, or {@code null}. */
    private AttributeInstance maxHealthAttribute(LivingEntity entity) {
        Object attribute = handles.resolveByName(HandleCategory.ATTRIBUTE, "GENERIC_MAX_HEALTH");
        return attribute instanceof Attribute resolved ? entity.getAttribute(resolved) : null;
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
        try {
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
        } catch (RuntimeException unreadable) {
            return false; // cross-region / unloaded chunk → not provably safe → caller uses the fallback
        }
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
        regionOp(at, () -> {
            Particle resolved = handles.particle(particleId);
            World world = at.getWorld();
            if (resolved != null && world != null) {
                world.spawnParticle(resolved, at, count);
            }
        });
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

package engine.sink;

import engine.interact.DamageFold;
import engine.stores.CooldownStore;
import engine.stores.ImmuneStore;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.SuppressionStore;
import engine.stores.TeleblockStore;
import engine.stores.VarStore;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import net.minecraft.server.v1_8_R3.ChatComponentText;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.EnumParticle;
import net.minecraft.server.v1_8_R3.GenericAttributes;
import net.minecraft.server.v1_8_R3.PacketPlayOutChat;
import net.minecraft.server.v1_8_R3.PacketPlayOutTitle;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldParticles;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import platform.economy.EconomyService;
import platform.resolve.RenameResolvers;
import platform.sched.Scheduling;
import schema.spec.HandleCategory;

/**
 * The legacy (1.8.9 / {@code v1_8_R3}) concrete {@link Sink} — the same-FQN counterpart to the modern
 * {@code overlay/modern} impl, swapping the modern Bukkit API for CraftBukkit 1.8.8 + NMS where 1.8 lacks
 * the floor surface (docs/legacy-1.8.9-codeshare-design.md §3.5). Behaviourally a mirror of the modern
 * sink — the inline-vs-deferred discipline, the {@link DispatchPlan}, the {@link DamageFold}, the stores,
 * and the readback semantics are identical, all shared from {@code src/}; only the leaf mutations differ.
 *
 * <p>Two interned-id surfaces diverge from modern. First, the ctor takes {@link RenameResolvers} (which can
 * turn an interned id back into its 1.8-era <em>name</em>) instead of {@code RuntimeHandles} (modern-only —
 * it casts to {@code Particle}/{@code Attribute}, neither of which exists on 1.8). Second, an id is resolved
 * to a live value here: name via {@link RenameResolvers#nameOf}, then a 1.8 lookup ({@code Material.getMaterial},
 * {@code PotionEffectType.getByName}, {@code Sound.valueOf}, …). A miss yields {@code null} and that one intent
 * is silently skipped, exactly as modern (the §9 compile-time warn already fired).
 *
 * <p>1.8 gaps handled below: no Bukkit {@code Particle} (NMS {@code PacketPlayOutWorldParticles}); no
 * {@code attribute} package ({@code setMaxHealth} / NMS {@code GenericAttributes}); no
 * {@code meta.Damageable} (durability is on the {@code ItemStack} itself); no off-hand (main hand only); no
 * {@code teleportAsync} (synchronous {@code teleport}); no {@code spigot()}/Adventure (NMS chat/title packets);
 * no {@code Entity.setInvulnerable} (NMS {@code invulnerable} field).
 *
 * <p>One instance per event; not thread-safe by design — filled and flushed on the single firing thread.
 */
public final class DispatchSink implements SinkReadback {

    private static final Logger LOG = System.getLogger("StarEnchants.Sink");

    /** The 1.8 NMS {@code Entity.invulnerable} flag is private; cache the reflective handle once. */
    private static volatile Field nmsInvulnerableField;

    private final RenameResolvers resolvers;
    private final EconomyService economy;
    private final SoulDebit souls;
    private final VarStore vars;
    private final SuppressionStore suppression;
    private final KnockbackControlStore knockback;
    private final KeepOnDeathStore keepOnDeath;
    private final LongSupplier nowTicks;
    private final DispatchPlan plan = new DispatchPlan();
    private final DamageFold fold;

    private final TeleblockStore teleblock;
    private final ImmuneStore immune;

    private boolean cancelled;
    private boolean armorIgnored;
    private boolean smeltRequested;
    private boolean teleportDropsRequested;
    private boolean seekRequested;
    private boolean flushed;
    private int delayTicks;

    /**
     * §N anti-cheat movement exemption (ADR-0027): invoked before StarEnchants moves a PLAYER (VELOCITY /
     * TELEPORT) so a bundled anti-cheat bridge can briefly exempt them, preventing false flags. Static no-op
     * default (inert in tests, free per event).
     */
    private static volatile java.util.function.Consumer<Player> movementExemption = player -> { };

    /** Install the anti-cheat movement-exemption hook (boot-time). A {@code null} hook resets to no-op. */
    public static void movementExemption(java.util.function.Consumer<Player> hook) {
        movementExemption = hook == null ? player -> { } : hook;
    }

    /** Exempt {@code target} from anti-cheat movement checks if it is a player (runs on the target thread). */
    private static void exemptMovement(Entity target) {
        if (target instanceof Player) {
            movementExemption.accept((Player) target);
        }
    }

    /** The test default — economy/soul are no-ops, the stores are throwaways. */
    public DispatchSink(RenameResolvers resolvers) {
        this(resolvers, EconomyService.NONE, SoulDebit.NONE, new VarStore(), new SuppressionStore(), () -> 0L);
    }

    public DispatchSink(RenameResolvers resolvers, EconomyService economy) {
        this(resolvers, economy, SoulDebit.NONE, new VarStore(), new SuppressionStore(), () -> 0L);
    }

    public DispatchSink(RenameResolvers resolvers, EconomyService economy, SoulDebit souls,
                        VarStore vars, SuppressionStore suppression, LongSupplier nowTicks) {
        this(resolvers, economy, souls, vars, suppression, new KnockbackControlStore(), nowTicks);
    }

    public DispatchSink(RenameResolvers resolvers, EconomyService economy, SoulDebit souls,
                        VarStore vars, SuppressionStore suppression, KnockbackControlStore knockback,
                        LongSupplier nowTicks) {
        this(resolvers, economy, souls, vars, suppression, knockback, new KeepOnDeathStore(), nowTicks);
    }

    /**
     * Sharing the stores is what makes the KNOCKBACK_CONTROL / KEEP_ON_DEATH flags a hit writes visible to the
     * separate knockback / death events' listeners.
     */
    public DispatchSink(RenameResolvers resolvers, EconomyService economy, SoulDebit souls,
                        VarStore vars, SuppressionStore suppression, KnockbackControlStore knockback,
                        KeepOnDeathStore keepOnDeath, LongSupplier nowTicks) {
        this(resolvers, economy, souls, vars, suppression, knockback, keepOnDeath, nowTicks,
                () -> DamageFold.DEFAULT_MAX_HEROIC_OUTGOING_FACTOR);
    }

    /**
     * The heroic outgoing-damage ceiling (§F/ADR-0021) is a supplier so {@code /se reload} re-tunes the bound;
     * the eight-arg ctor defaults it to {@link DamageFold#DEFAULT_MAX_HEROIC_OUTGOING_FACTOR}.
     */
    public DispatchSink(RenameResolvers resolvers, EconomyService economy, SoulDebit souls,
                        VarStore vars, SuppressionStore suppression, KnockbackControlStore knockback,
                        KeepOnDeathStore keepOnDeath, LongSupplier nowTicks,
                        java.util.function.DoubleSupplier maxHeroicOutgoing) {
        this(resolvers, economy, souls, vars, suppression, knockback, keepOnDeath,
                new TeleblockStore(), new ImmuneStore(), nowTicks, maxHeroicOutgoing);
    }

    /**
     * The full sink. The shared {@link TeleblockStore}/{@link ImmuneStore} the TELEBLOCK / IMMUNE flags write
     * are read back by the teleport / damage listeners on their separate events; shorter ctors default these to
     * throwaways, so those flags are inert unless the real stores are threaded in.
     */
    public DispatchSink(RenameResolvers resolvers, EconomyService economy, SoulDebit souls,
                        VarStore vars, SuppressionStore suppression, KnockbackControlStore knockback,
                        KeepOnDeathStore keepOnDeath, TeleblockStore teleblock, ImmuneStore immune,
                        LongSupplier nowTicks, java.util.function.DoubleSupplier maxHeroicOutgoing) {
        this.resolvers = Objects.requireNonNull(resolvers, "resolvers");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.souls = Objects.requireNonNull(souls, "souls");
        this.vars = Objects.requireNonNull(vars, "vars");
        this.suppression = Objects.requireNonNull(suppression, "suppression");
        this.knockback = Objects.requireNonNull(knockback, "knockback");
        this.keepOnDeath = Objects.requireNonNull(keepOnDeath, "keepOnDeath");
        this.teleblock = Objects.requireNonNull(teleblock, "teleblock");
        this.immune = Objects.requireNonNull(immune, "immune");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.fold = new DamageFold(Objects.requireNonNull(maxHeroicOutgoing, "maxHeroicOutgoing"));
    }

    // ── Read-backs (called by the firing system, never by an effect) ─────────────────────────────

    /** The damage arbiter for this event; the trigger listener folds it onto the event once (§6.1). */
    @Override
    public DamageFold fold() {
        return fold;
    }

    /** Whether an effect asked for the triggering event to be cancelled (§3.6 event control). */
    @Override
    public boolean cancelled() {
        return cancelled;
    }

    /** Whether an effect asked the triggering hit to ignore armor (§ combat-flags). Read by the combat dispatcher. */
    @Override
    public boolean armorIgnored() {
        return armorIgnored;
    }

    /** Whether an effect asked the triggering block-break to auto-smelt (SMELT). Read by the MINE dispatcher. */
    @Override
    public boolean smeltRequested() {
        return smeltRequested;
    }

    /** Whether an effect asked the broken block's drops to go to the breaker's inventory (TELEPORT_DROPS). */
    @Override
    public boolean teleportDropsRequested() {
        return teleportDropsRequested;
    }

    /** Whether an effect asked the fired projectile to home onto a target (AUTO_LOCK). Read by the bow dispatcher. */
    @Override
    public boolean seekRequested() {
        return seekRequested;
    }

    /** Schedule every deferred intent on its owning thread; call once after the gate walk. Idempotent. */
    @Override
    public void flush() {
        if (flushed) {
            return;
        }
        flushed = true;
        plan.flush();
    }

    /**
     * Set the {@code WAIT} delay (in ticks) applied to the world-mutation intents of subsequent effects, until
     * changed again (§3.6); identical semantics to the modern impl. Negative values clamp to 0.
     */
    @Override
    public void delay(int ticks) {
        this.delayTicks = Math.max(0, ticks);
    }

    private void entityOp(Entity target, Runnable op) {
        if (target != null) {
            plan.onEntity(target, op, delayTicks); // always the entity's own thread — never inline (may be cross-region)
        }
    }

    private void regionOp(Location at, Runnable op) {
        if (at != null) {
            plan.onRegion(at, op, delayTicks); // always the location's region thread — never inline
        }
    }

    private void globalOp(Runnable op) {
        plan.onGlobal(op, delayTicks);
    }

    // ── Damage arbiter: contribute deltas, never setDamage (§6.1) ────────────────────────────────

    @Override
    public void addOutgoingDamage(double percent) {
        fold.addOutgoing(percent);
    }

    @Override
    public void addDamageReduction(double percent) {
        fold.addReduction(percent);
    }

    @Override
    public void addFlatDamage(double amount) {
        fold.addFlatDamage(amount);
    }

    @Override
    public void addFlatReduction(double amount) {
        fold.addFlatReduction(amount);
    }

    @Override
    public void addHeroicOutgoing(double percent) {
        fold.addHeroicOutgoing(percent);
    }

    @Override
    public void addHeroicReduction(double percent) {
        fold.addHeroicReduction(percent);
    }

    // ── Entity intents ───────────────────────────────────────────────────────────────────────────

    @Override
    public void damage(LivingEntity target, double amount) {
        entityOp(target, () -> target.damage(amount));
    }

    @Override
    public void heal(LivingEntity target, double amount) {
        entityOp(target, () -> target.setHealth(Math.min(target.getHealth() + amount, maxHealth(target))));
    }

    @Override
    public void setHealth(LivingEntity target, double health) {
        entityOp(target, () -> target.setHealth(Math.max(0.0, Math.min(health, maxHealth(target)))));
    }

    @Override
    public void kill(LivingEntity target) {
        entityOp(target, () -> target.setHealth(0.0));
    }

    @Override
    public void extinguish(LivingEntity target) {
        entityOp(target, () -> target.setFireTicks(0));
    }

    @Override
    public void fillAir(LivingEntity target) {
        entityOp(target, () -> target.setRemainingAir(target.getMaximumAir()));
    }

    @Override
    public void feed(Player target, int foodPoints) {
        entityOp(target, () -> target.setFoodLevel(Math.min(20, target.getFoodLevel() + foodPoints)));
    }

    @Override
    public void repairHand(Player target, int amount) {
        entityOp(target, () -> {
            ItemStack item = target.getInventory().getItemInHand(); // 1.8: main hand only
            if (applyRepair(item, amount)) {
                target.getInventory().setItemInHand(item);
            }
        });
    }

    @Override
    public void damageHand(Player target, int amount) {
        entityOp(target, () -> {
            ItemStack item = target.getInventory().getItemInHand(); // 1.8: main hand only
            if (applyDamage(item, amount)) {
                target.getInventory().setItemInHand(item);
            }
        });
    }

    @Override
    public void giveExp(Player target, int amount) {
        entityOp(target, () -> target.giveExp(amount));
    }

    @Override
    public void takeExp(Player target, int amount) {
        entityOp(target, () -> target.giveExp(-amount));
    }

    @Override
    public void takeFood(Player target, int foodPoints) {
        entityOp(target, () -> target.setFoodLevel(Math.max(0, target.getFoodLevel() - foodPoints)));
    }

    @Override
    public void knockback(Entity target, Location from, double strength) {
        Location origin = from.clone();
        entityOp(target, () -> {
            exemptMovement(target); // §N: let a bundled anti-cheat ignore this engine-applied knockback
            Vector delta = target.getLocation().toVector().subtract(origin.toVector());
            Vector direction = delta.lengthSquared() > 1.0e-6 ? delta.normalize() : new Vector(0, 1, 0);
            target.setVelocity(target.getVelocity().add(direction.multiply(strength)));
        });
    }

    @Override
    public void setFlight(Player target, int durationTicks) {
        entityOp(target, () -> {
            target.setAllowFlight(true);
            target.setFlying(true);
            if (durationTicks >= 0) {
                Scheduling.onEntityLater(target, durationTicks, () -> clearTemporaryFlight(target));
            }
        });
    }

    @Override
    public void movementSpeed(Player target, double speed, int durationTicks) {
        entityOp(target, () -> {
            target.setWalkSpeed((float) Math.max(-1.0, Math.min(1.0, speed)));
            if (durationTicks >= 0) {
                // Restore the vanilla default (0.2) rather than the captured prior value, so re-firing the
                // buff before it elapses can never leak an inflated speed upward.
                Scheduling.onEntityLater(target, durationTicks, () -> target.setWalkSpeed(0.2f));
            }
        });
    }

    @Override
    public void invincible(LivingEntity target, int durationTicks) {
        entityOp(target, () -> {
            setNmsInvulnerable(target, true);
            if (durationTicks >= 0) {
                Scheduling.onEntityLater(target, durationTicks, () -> setNmsInvulnerable(target, false));
            }
        });
    }

    @Override
    public void addMaxHealth(LivingEntity target, double amount) {
        // 1.8: shift the base max-health value directly; unequip restoration of this delta lands with WornState.
        entityOp(target, () -> {
            net.minecraft.server.v1_8_R3.AttributeInstance maxHealth = maxHealthInstance(target);
            if (maxHealth != null) {
                maxHealth.setValue(Math.max(1.0, maxHealth.getValue() + amount));
            }
        });
    }

    @Override
    public void damageArmor(LivingEntity target, int amount) {
        entityOp(target, () -> adjustArmorDurability(target, amount, false));
    }

    @Override
    public void repairArmor(Player target, int amount) {
        entityOp(target, () -> adjustArmorDurability(target, amount, true));
    }

    @Override
    public void potion(LivingEntity target, int potionEffectId, int amplifier, int durationTicks) {
        entityOp(target, () -> {
            PotionEffectType type = potionEffect(potionEffectId);
            if (type != null) {
                target.addPotionEffect(new PotionEffect(type, durationTicks, amplifier));
            }
        });
    }

    @Override
    public void removePotion(LivingEntity target, int potionEffectId) {
        entityOp(target, () -> {
            PotionEffectType type = potionEffect(potionEffectId);
            if (type != null) {
                target.removePotionEffect(type);
            }
        });
    }

    @Override
    public void cure(LivingEntity target) {
        // Snapshot the active types first: removePotionEffect mutates the live collection.
        entityOp(target, () -> {
            for (PotionEffect active : List.copyOf(target.getActivePotionEffects())) {
                target.removePotionEffect(active.getType());
            }
        });
    }

    @Override
    public void disarm(LivingEntity target) {
        entityOp(target, () -> {
            EntityEquipment equipment = target.getEquipment();
            if (equipment == null) {
                return;
            }
            ItemStack held = equipment.getItemInHand(); // 1.8 main hand
            if (held == null || held.getType() == Material.AIR) {
                return;
            }
            equipment.setItemInHand(null);
            World world = target.getWorld();
            if (world != null) {
                world.dropItemNaturally(target.getLocation(), held);
            }
        });
    }

    @Override
    public void removeArmor(LivingEntity target) {
        entityOp(target, () -> {
            EntityEquipment equipment = target.getEquipment();
            if (equipment == null) {
                return;
            }
            ItemStack[] worn = equipment.getArmorContents(); // [boots, leggings, chestplate, helmet]
            int[] filled = new int[worn.length];
            int n = 0;
            for (int i = 0; i < worn.length; i++) {
                if (worn[i] != null && worn[i].getType() != Material.AIR) {
                    filled[n++] = i;
                }
            }
            if (n == 0) {
                return;
            }
            int slot = filled[ThreadLocalRandom.current().nextInt(n)];
            ItemStack piece = worn[slot];
            worn[slot] = null;
            equipment.setArmorContents(worn);
            World world = target.getWorld();
            if (world != null) {
                world.dropItemNaturally(target.getLocation(), piece);
            }
        });
    }

    @Override
    public void ignite(Entity target, int durationTicks) {
        entityOp(target, () -> target.setFireTicks(Math.max(0, durationTicks)));
    }

    @Override
    public void lightningAndDamage(LivingEntity target, double amount) {
        entityOp(target, () -> {
            World world = target.getWorld();
            if (world != null) {
                world.strikeLightning(target.getLocation());
            }
            target.damage(amount);
        });
    }

    @Override
    public void launch(Entity target, double x, double y, double z) {
        entityOp(target, () -> {
            exemptMovement(target); // §N: let a bundled anti-cheat ignore this engine-applied velocity
            target.setVelocity(target.getVelocity().add(new Vector(x, y, z)));
        });
    }

    @Override
    public void teleport(Entity target, Location to) {
        // 1.8: no teleportAsync — synchronous teleport, already on the target's own thread via Scheduling.
        // Clone the destination: a WAIT tier can defer this, so the captured target must be an owned snapshot.
        Location dest = to.clone();
        entityOp(target, () -> {
            exemptMovement(target); // §N: let a bundled anti-cheat ignore this engine-applied teleport
            target.teleport(dest);
        });
    }

    // ── World / block intents ────────────────────────────────────────────────────────────────────

    @Override
    public void spawnEntity(Location at, int entityTypeId, int count, int ttlTicks, double health, UUID ownerId) {
        Location origin = at.clone(); // own the spawn point: a WAIT tier can defer this to a later tick
        regionOp(origin, () -> {
            EntityType type = entityType(entityTypeId);
            World world = origin.getWorld();
            if (type == null || world == null || count <= 0) {
                return;
            }
            for (int i = 0; i < count; i++) {
                Entity spawned = world.spawnEntity(origin, type);
                if (health > 0 && spawned instanceof LivingEntity) {
                    applySpawnHealth((LivingEntity) spawned, health);
                }
                if (ownerId != null && spawned instanceof Tameable) {
                    // Owned/tamed summon: resolve by the Tameable CAPABILITY (stable across the range), never a
                    // volatile constant. setOwner accepts an offline AnimalTamer; tame so it sticks.
                    Tameable tame = (Tameable) spawned;
                    tame.setOwner(Bukkit.getOfflinePlayer(ownerId));
                    tame.setTamed(true);
                }
                if (ttlTicks > 0) {
                    Scheduling.onEntityLater(spawned, ttlTicks, spawned::remove);
                }
            }
        });
    }

    @Override
    public void guard(LivingEntity target, Location at, int entityTypeId, int count, int ttlTicks, String name) {
        Location origin = at.clone(); // own the spawn point: a WAIT tier can defer this to a later tick
        regionOp(origin, () -> {
            EntityType type = entityType(entityTypeId);
            World world = origin.getWorld();
            if (type == null || world == null || count <= 0) {
                return;
            }
            for (int i = 0; i < count; i++) {
                Entity spawned = world.spawnEntity(origin, type);
                if (target != null && spawned instanceof Creature) {
                    // 1.8: the targeting interface is Creature (no Mob). setTarget only stores the reference;
                    // the AI runs on the mob's own (spawn) region, so this is not a cross-region read.
                    ((Creature) spawned).setTarget(target);
                }
                applyGuardName(spawned, name);
                if (ttlTicks > 0) {
                    Scheduling.onEntityLater(spawned, ttlTicks, spawned::remove);
                }
            }
        });
    }

    /** Apply an optional custom name (with {@code &}-colour codes) to a freshly-summoned guard. */
    private static void applyGuardName(Entity entity, String name) {
        if (name != null && !name.isEmpty()) {
            entity.setCustomName(ChatColor.translateAlternateColorCodes('&', name));
            entity.setCustomNameVisible(true);
        }
    }

    @Override
    public void explode(Location at, double power, boolean breakBlocks) {
        regionOp(at, () -> {
            World world = at.getWorld();
            if (world != null) {
                // 1.8: createExplosion(loc, power, fire) has no block-break flag; the coord overload
                // (x,y,z,power,fire,breakBlocks) does, so route through it to honour breakBlocks.
                world.createExplosion(at.getX(), at.getY(), at.getZ(), (float) power, false, breakBlocks);
            }
        });
    }

    @Override
    public void firework(Location at, int power) {
        regionOp(at, () -> {
            World world = at.getWorld();
            if (world == null) {
                return;
            }
            Firework firework = world.spawn(at, Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.setPower(Math.max(0, Math.min(power, 127)));
            meta.addEffect(FireworkEffect.builder()
                    .withColor(Color.AQUA, Color.WHITE)
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .flicker(true)
                    .build());
            firework.setFireworkMeta(meta);
        });
    }

    @Override
    public void launchProjectile(Player shooter, int entityTypeId, int count, double speed) {
        entityOp(shooter, () -> {
            EntityType type = entityType(entityTypeId);
            World world = shooter.getWorld();
            if (type == null || world == null || count <= 0) {
                return;
            }
            Location eye = shooter.getEyeLocation();
            Vector base = eye.getDirection().normalize().multiply(speed);
            for (int i = 0; i < count; i++) {
                Entity entity = world.spawnEntity(eye, type);
                Vector velocity = base.clone();
                if (count > 1) {
                    // A small spread so a volley fans out instead of stacking on one line.
                    velocity.add(new Vector(
                            (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.2,
                            (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.1,
                            (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.2));
                }
                entity.setVelocity(velocity);
                if (entity instanceof Projectile) {
                    ((Projectile) entity).setShooter(shooter);
                }
            }
        });
    }

    @Override
    public void blockChange(Location at, int blockDataId) {
        // Handle is treated as a Material (covers the common case); full BlockData with states is a follow-up.
        regionOp(at, () -> {
            Material material = material(blockDataId);
            if (material != null && material.isBlock()) {
                at.getBlock().setType(material);
            }
        });
    }

    @Override
    public void breakBlock(Location at, boolean drops) {
        regionOp(at, () -> {
            Block block = at.getBlock();
            if (block.getType() == Material.AIR) {
                return;
            }
            if (drops) {
                block.breakNaturally(); // yields the block's natural drops at its location
            } else {
                block.setType(Material.AIR);
            }
        });
    }

    @Override
    public void tempPlatform(Location center, int materialId, int radius, int durationTicks, int replaceMode) {
        Location origin = center.clone(); // own the centre: a WAIT tier can defer this to a later tick
        regionOp(origin, () -> {
            Material material = material(materialId);
            World world = origin.getWorld();
            if (material == null || !material.isBlock() || world == null) {
                return;
            }
            int y = origin.getBlockY() - 1; // the layer under the target's feet
            int cx = origin.getBlockX();
            int cz = origin.getBlockZ();
            List<BlockState> prior = new java.util.ArrayList<>();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = world.getBlockAt(cx + dx, y, cz + dz);
                    if (canReplace(block, replaceMode)) {
                        prior.add(block.getState()); // capture for the revert
                        block.setType(material, false);
                    }
                }
            }
            if (durationTicks > 0 && !prior.isEmpty()) {
                // Best-effort revert on the same region thread; restores each captured prior block.
                Scheduling.onRegionLater(origin, durationTicks, () -> prior.forEach(s -> s.update(true, false)));
            }
        });
    }

    /** Whether a temp-platform may overwrite this block: 0 = air only, 1 = air/liquid, 2 = anything. */
    private static boolean canReplace(Block block, int replaceMode) {
        switch (replaceMode) {
            case 0:
                return block.getType() == Material.AIR;
            case 1:
                return block.getType() == Material.AIR || block.isLiquid();
            default:
                return true;
        }
    }

    @Override
    public void dropItem(Location at, int materialId, int count) {
        regionOp(at, () -> {
            Material material = material(materialId);
            World world = at.getWorld();
            if (material != null && material != Material.AIR && world != null && count > 0) {
                world.dropItemNaturally(at, new ItemStack(material, count));
            }
        });
    }

    @Override
    public void sound(Location at, int soundId, float volume, float pitch) {
        regionOp(at, () -> {
            Sound resolved = sound(soundId);
            World world = at.getWorld();
            if (resolved != null && world != null) {
                world.playSound(at, resolved, volume, pitch);
            }
        });
    }

    @Override
    public void giveItem(Player target, int materialId, int count) {
        entityOp(target, () -> {
            Material material = material(materialId);
            if (material == null || material == Material.AIR || count <= 0) {
                return;
            }
            ItemStack stack = new ItemStack(material, count);
            for (ItemStack extra : target.getInventory().addItem(stack).values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), extra);
            }
        });
    }

    @Override
    public void removeItem(Player target, int materialId, int count) {
        entityOp(target, () -> {
            Material material = material(materialId);
            if (material != null && material != Material.AIR && count > 0) {
                target.getInventory().removeItem(new ItemStack(material, count));
            }
        });
    }

    @Override
    public void particle(Location at, int particleId, int count) {
        // 1.8: no Bukkit Particle — spawn via the NMS particle packet sent to players in the same world.
        regionOp(at, () -> {
            EnumParticle resolved = particle(particleId);
            World world = at.getWorld();
            if (resolved == null || world == null) {
                return;
            }
            PacketPlayOutWorldParticles packet = new PacketPlayOutWorldParticles(
                    resolved, true,
                    (float) at.getX(), (float) at.getY(), (float) at.getZ(),
                    0f, 0f, 0f, // no offset spread
                    0f,         // particle data/speed
                    Math.max(1, count));
            for (Player viewer : world.getPlayers()) {
                if (viewer.getLocation().distanceSquared(at) <= 64 * 64) { // vanilla long-distance cutoff
                    sendPacket(viewer, packet);
                }
            }
        });
    }

    // ── Player feedback ──────────────────────────────────────────────────────────────────────────

    @Override
    public void message(Player target, String message) {
        // Translate legacy '&' codes to '§' so feedback shows coloured, not literal "&c&l…" (mirrors
        // applyGuardName above; behavioural mirror of the modern overlay).
        String text = legacyColor(message);
        entityOp(target, () -> target.sendMessage(text));
    }

    @Override
    public void actionBar(Player target, String message) {
        // 1.8: no spigot()/Adventure — action bar is a chat packet with type byte 2. ChatComponentText
        // renders '§' codes, so translate '&' → '§' first.
        String text = legacyColor(message);
        entityOp(target, () -> sendPacket(target,
                new PacketPlayOutChat(new ChatComponentText(text), (byte) 2)));
    }

    @Override
    public void title(Player target, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        // 1.8: no 5-arg sendTitle — send the TIMES then the TITLE/SUBTITLE title packets directly.
        // Translate '&' → '§' so colour codes render, not show literally.
        String t = legacyColor(title);
        String s = legacyColor(subtitle);
        entityOp(target, () -> {
            sendPacket(target, new PacketPlayOutTitle(fadeIn, stay, fadeOut));
            if (t != null) {
                sendPacket(target, new PacketPlayOutTitle(
                        PacketPlayOutTitle.EnumTitleAction.TITLE, new ChatComponentText(t)));
            }
            if (s != null) {
                sendPacket(target, new PacketPlayOutTitle(
                        PacketPlayOutTitle.EnumTitleAction.SUBTITLE, new ChatComponentText(s)));
            }
        });
    }

    /** Legacy '&' → '§' colour translation, null-safe (some title/subtitle paths pass null). */
    private static String legacyColor(String text) {
        return text == null ? null : ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public void consoleCommand(String command) {
        globalOp(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    // ── Economy intents ──────────────────────────────────────────────────────────────────────────

    @Override
    public void giveMoney(Player target, double amount) {
        if (target == null) {
            return;
        }
        UUID id = target.getUniqueId();
        globalOp(() -> economy.deposit(id, amount));
    }

    @Override
    public void takeMoney(Player target, double amount) {
        if (target == null) {
            return;
        }
        UUID id = target.getUniqueId();
        globalOp(() -> economy.withdraw(id, amount));
    }

    @Override
    public void stealMoneyPercent(Player from, Player to, double fraction) {
        if (from == null || to == null || fraction <= 0) {
            return;
        }
        UUID fromId = from.getUniqueId();
        UUID toId = to.getUniqueId();
        double frac = Math.min(1.0, fraction); // never take more than the whole balance
        globalOp(() -> {
            double amount = economy.balance(fromId) * frac;
            if (amount > 0 && economy.withdraw(fromId, amount)) {
                economy.deposit(toId, amount);
            }
        });
    }

    // ── Soul intents ───────────────────────────────────────────────────────────────────────────

    @Override
    public void removeSouls(Player holder, UUID gemId, int amount) {
        if (holder == null || gemId == null || amount <= 0) {
            return;
        }
        entityOp(holder, () -> souls.debit(holder, gemId, amount));
    }

    @Override
    public void removeSoulsFrom(Player target, int amount) {
        if (target == null || amount <= 0) {
            return;
        }
        entityOp(target, () -> souls.debitTarget(target, amount));
    }

    // ── Variable intents ───────────────────────────────────────────────────────────────────────

    @Override
    public void setVar(Player target, String name, String value, int ttlTicks) {
        if (target == null || name == null) {
            return;
        }
        vars.set(target.getUniqueId(), name, value, nowTicks.getAsLong(), ttlTicks);
    }

    @Override
    public void invertVar(Player target, String name) {
        if (target == null || name == null) {
            return;
        }
        vars.invert(target.getUniqueId(), name, nowTicks.getAsLong());
    }

    // ── Suppression intents ──────────────────────────────────────────────────────────────────────

    @Override
    public void suppress(Player target, int scopeKind, int scopeId, int durationTicks) {
        if (target == null || scopeId < 0) {
            return;
        }
        suppression.suppress(target.getUniqueId(), CooldownStore.key(scopeKind, scopeId),
                nowTicks.getAsLong(), durationTicks);
    }

    // ── Event control ──────────────────────────────────────────────────────────────────────────

    @Override
    public void cancelEvent() {
        cancelled = true;
    }

    @Override
    public void ignoreArmor() {
        armorIgnored = true;
    }

    @Override
    public void controlKnockback(LivingEntity victim, double multiplier, int ttlTicks) {
        if (victim == null) {
            return;
        }
        knockback.control(victim.getUniqueId(), multiplier, nowTicks.getAsLong(), ttlTicks);
    }

    @Override
    public void keepOnDeath(Player target, int ttlTicks) {
        if (target == null) {
            return;
        }
        keepOnDeath.keep(target.getUniqueId(), nowTicks.getAsLong(), ttlTicks);
    }

    @Override
    public void teleblock(Player target, int durationTicks) {
        if (target == null) {
            return;
        }
        teleblock.block(target.getUniqueId(), nowTicks.getAsLong(), durationTicks);
    }

    @Override
    public void immune(Player target, int damageType, int durationTicks) {
        if (target == null) {
            return;
        }
        immune.immune(target.getUniqueId(), ImmuneStore.Type.of(damageType), nowTicks.getAsLong(), durationTicks);
    }

    @Override
    public void smelt() {
        smeltRequested = true;
    }

    @Override
    public void teleportDrops() {
        teleportDropsRequested = true;
    }

    @Override
    public void seek() {
        seekRequested = true;
    }

    // ── Interned-id resolution (1.8: name via RenameResolvers, then a 1.8 lookup) ─────────────────

    /** The 1.8 {@link Material} for an interned material id, or {@code null} on a miss. */
    private Material material(int id) {
        String name = resolvers.nameOf(HandleCategory.MATERIAL, id);
        return name == null ? null : Material.getMaterial(name);
    }

    /** The 1.8 {@link Sound} for an interned sound id, or {@code null} on a miss / unknown enum constant. */
    private Sound sound(int id) {
        String name = resolvers.nameOf(HandleCategory.SOUND, id);
        if (name == null) {
            return null;
        }
        try {
            return Sound.valueOf(name); // 1.8 Sound is a plain enum
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** The 1.8 {@link PotionEffectType} for an interned id, or {@code null} on a miss. */
    private PotionEffectType potionEffect(int id) {
        String name = resolvers.nameOf(HandleCategory.POTION_EFFECT, id);
        return name == null ? null : PotionEffectType.getByName(name);
    }

    /** The 1.8 NMS {@link EnumParticle} for an interned id, or {@code null} on a miss / unknown constant. */
    private EnumParticle particle(int id) {
        String name = resolvers.nameOf(HandleCategory.PARTICLE, id);
        if (name == null) {
            return null;
        }
        try {
            return EnumParticle.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** The 1.8 {@link EntityType} for an interned id, by enum then by lowercase name, or {@code null}. */
    @SuppressWarnings("deprecation") // fromName(String): the 1.8 legacy entity-name lookup.
    private EntityType entityType(int id) {
        String name = resolvers.nameOf(HandleCategory.ENTITY_TYPE, id);
        if (name == null) {
            return null;
        }
        try {
            return EntityType.valueOf(name);
        } catch (IllegalArgumentException notEnum) {
            return EntityType.fromName(name.toLowerCase()); // legacy entity name form
        }
    }

    /** The 1.8 {@link Enchantment} for an interned id, or {@code null} on a miss (currently unused by any intent). */
    @SuppressWarnings("unused")
    private Enchantment enchantment(int id) {
        String name = resolvers.nameOf(HandleCategory.ENCHANTMENT, id);
        return name == null ? null : Enchantment.getByName(name);
    }

    // ── Cross-version helpers ────────────────────────────────────────────────────────────────────

    /** The NMS max-health attribute instance for {@code entity}, or {@code null}. */
    private static net.minecraft.server.v1_8_R3.AttributeInstance maxHealthInstance(LivingEntity entity) {
        if (!(entity instanceof CraftLivingEntity)) {
            return null;
        }
        return ((CraftLivingEntity) entity).getHandle().getAttributeInstance(GenericAttributes.maxHealth);
    }

    /** The entity's maximum health (attribute on 1.8 lives behind the Damageable accessor). */
    @SuppressWarnings("deprecation") // getMaxHealth: the 1.8 accessor.
    private static double maxHealth(LivingEntity entity) {
        net.minecraft.server.v1_8_R3.AttributeInstance maxHealth = maxHealthInstance(entity);
        return maxHealth != null ? maxHealth.getValue() : entity.getMaxHealth();
    }

    /** Set a freshly-spawned living entity's max + current health (SPAWN_ENTITY's {@code health} param). */
    @SuppressWarnings("deprecation") // setMaxHealth/getMaxHealth: the 1.8 accessors.
    private static void applySpawnHealth(LivingEntity entity, double health) {
        net.minecraft.server.v1_8_R3.AttributeInstance maxHealth = maxHealthInstance(entity);
        if (maxHealth != null) {
            maxHealth.setValue(health);
        } else {
            entity.setMaxHealth(health);
        }
        entity.setHealth(Math.min(health, maxHealth(entity)));
    }

    /** Flip the NMS {@code Entity.invulnerable} flag (1.8 has no public {@code setInvulnerable}). */
    private static void setNmsInvulnerable(LivingEntity entity, boolean invulnerable) {
        if (!(entity instanceof CraftLivingEntity)) {
            return;
        }
        net.minecraft.server.v1_8_R3.Entity handle = ((CraftLivingEntity) entity).getHandle();
        try {
            invulnerableField(handle).setBoolean(handle, invulnerable);
        } catch (ReflectiveOperationException unreachable) {
            LOG.log(Level.WARNING, "could not set 1.8 NMS invulnerable flag", unreachable);
        }
    }

    private static Field invulnerableField(net.minecraft.server.v1_8_R3.Entity handle)
            throws NoSuchFieldException {
        Field cached = nmsInvulnerableField;
        if (cached != null) {
            return cached;
        }
        // Declared on net.minecraft.server.Entity; walk the chain in case of a relocated handle subtype.
        for (Class<?> type = handle.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field found = type.getDeclaredField("invulnerable");
                found.setAccessible(true);
                nmsInvulnerableField = found; // stable for the JVM's lifetime
                return found;
            } catch (NoSuchFieldException keepWalking) {
                // on a superclass
            }
        }
        throw new NoSuchFieldException("invulnerable");
    }

    /** Repair one item by {@code amount} (negative = full); returns whether the durability changed. */
    private static boolean applyRepair(ItemStack item, int amount) {
        if (item == null || !isDamageable(item)) {
            return false;
        }
        short current = item.getDurability();
        short repaired = amount < 0 ? 0 : (short) Math.max(0, current - amount);
        item.setDurability(repaired);
        return true;
    }

    /** Wear one item down by {@code amount} (clamped to its max durability); returns whether it changed. */
    private static boolean applyDamage(ItemStack item, int amount) {
        if (item == null || amount <= 0 || !isDamageable(item)) {
            return false;
        }
        short worn = (short) Math.min(item.getType().getMaxDurability(), item.getDurability() + amount);
        item.setDurability(worn);
        return true;
    }

    /** Adjust every worn armour piece's durability — repair when {@code repair}, else damage it. */
    private static void adjustArmorDurability(LivingEntity entity, int amount, boolean repair) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        ItemStack[] armor = equipment.getArmorContents();
        boolean changed = false;
        for (ItemStack piece : armor) {
            if (piece == null || !isDamageable(piece)) {
                continue;
            }
            short current = piece.getDurability();
            short next;
            if (repair) {
                next = amount < 0 ? 0 : (short) Math.max(0, current - amount);
            } else {
                next = (short) Math.min(piece.getType().getMaxDurability(), current + amount);
            }
            piece.setDurability(next);
            changed = true;
        }
        if (changed) {
            equipment.setArmorContents(armor);
        }
    }

    /** 1.8: durability lives on the {@code ItemStack}; a positive max durability means the item wears. */
    private static boolean isDamageable(ItemStack item) {
        return item.getType().getMaxDurability() > 0;
    }

    /** Strip temporarily-granted flight, but never from a player who can fly by game mode. */
    private static void clearTemporaryFlight(Player player) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    /** Send an NMS packet to {@code player} via its connection (the 1.8 action-bar/title/particle path). */
    private static void sendPacket(Player player, net.minecraft.server.v1_8_R3.Packet<?> packet) {
        if (player instanceof CraftPlayer) {
            EntityPlayer handle = ((CraftPlayer) player).getHandle();
            if (handle.playerConnection != null) {
                handle.playerConnection.sendPacket(packet);
            }
        }
    }
}

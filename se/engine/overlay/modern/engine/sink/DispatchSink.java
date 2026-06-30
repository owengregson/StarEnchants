package engine.sink;

import engine.interact.DamageFold;
import engine.stores.CooldownStore;
import engine.stores.ImmuneStore;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.SuppressionStore;
import engine.stores.TeleblockStore;
import engine.stores.VarStore;
import java.util.List;
import java.util.Objects;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import platform.economy.EconomyService;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import schema.spec.HandleCategory;

/**
 * The concrete {@link Sink} — the single mutation boundary and the only engine code that knows about threads
 * (§3.5–3.6). Two kinds of intent:
 * <ul>
 *   <li><strong>Inline feedback</strong> — the damage-fold contributions and {@code cancelEvent} feed back
 *       into the Bukkit event being processed, so they accumulate synchronously on the firing thread and the
 *       trigger listener reads them back ({@link #fold()}, {@link #cancelled()}); they never schedule.</li>
 *   <li><strong>World mutations</strong> — everything else: captured into the {@link DispatchPlan} and routed
 *       to the thread owning the target, flushed batched after the gate walk. NEVER run inline — the target is
 *       frequently a different entity/region than the firing one (a defender retaliating, an AoE bystander), so
 *       inlining would be a cross-region access on Folia. The declared affinity is advisory, not a licence to
 *       skip the hop.</li>
 * </ul>
 *
 * <p>Version-volatile referents arrive as interned ids, resolved through {@link RuntimeHandles} at apply time
 * on the correct thread (§9). An id that does not resolve yields {@code null} and that one intent is silently
 * skipped — the §9 warn already fired at compile time, so a runtime miss is a can't-happen on a stable server.
 *
 * <p>One instance per event; not thread-safe by design — filled and flushed on the single firing thread (§6),
 * scheduled batches run later on their own threads over immutable captured primitives.
 */
public final class DispatchSink implements SinkReadback {

    private final RuntimeHandles handles;
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
    private static volatile java.util.function.Consumer<org.bukkit.entity.Player> movementExemption = player -> { };

    /** Install the anti-cheat movement-exemption hook (boot-time). A {@code null} hook resets to no-op. */
    public static void movementExemption(java.util.function.Consumer<org.bukkit.entity.Player> hook) {
        movementExemption = hook == null ? player -> { } : hook;
    }

    /** Exempt {@code target} from anti-cheat movement checks if it is a player (runs on the target thread). */
    private static void exemptMovement(Entity target) {
        if (target instanceof org.bukkit.entity.Player player) {
            movementExemption.accept(player);
        }
    }

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
        this(handles, economy, souls, vars, suppression, knockback, keepOnDeath, nowTicks,
                () -> DamageFold.DEFAULT_MAX_HEROIC_OUTGOING_FACTOR);
    }

    /**
     * The heroic outgoing-damage ceiling (§F/ADR-0021) is a supplier so {@code /se reload} re-tunes the bound;
     * the eight-arg ctor defaults it to {@link DamageFold#DEFAULT_MAX_HEROIC_OUTGOING_FACTOR}.
     */
    public DispatchSink(RuntimeHandles handles, EconomyService economy, SoulDebit souls,
                        VarStore vars, SuppressionStore suppression, KnockbackControlStore knockback,
                        KeepOnDeathStore keepOnDeath, LongSupplier nowTicks,
                        java.util.function.DoubleSupplier maxHeroicOutgoing) {
        this(handles, economy, souls, vars, suppression, knockback, keepOnDeath,
                new TeleblockStore(), new ImmuneStore(), nowTicks, maxHeroicOutgoing);
    }

    /**
     * The full sink. The shared {@link TeleblockStore}/{@link ImmuneStore} the TELEBLOCK / IMMUNE flags write
     * are read back by the teleport / damage listeners on their separate events; shorter ctors default these to
     * throwaways, so those flags are inert unless the real stores are threaded in.
     */
    public DispatchSink(RuntimeHandles handles, EconomyService economy, SoulDebit souls,
                        VarStore vars, SuppressionStore suppression, KnockbackControlStore knockback,
                        KeepOnDeathStore keepOnDeath, TeleblockStore teleblock, ImmuneStore immune,
                        LongSupplier nowTicks, java.util.function.DoubleSupplier maxHeroicOutgoing) {
        this.handles = Objects.requireNonNull(handles, "handles");
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
    public DamageFold fold() {
        return fold;
    }

    /** Whether an effect asked for the triggering event to be cancelled (§3.6 event control). */
    public boolean cancelled() {
        return cancelled;
    }

    /** Whether an effect asked the triggering hit to ignore armor (§ combat-flags). Read by the combat dispatcher. */
    public boolean armorIgnored() {
        return armorIgnored;
    }

    /** Whether an effect asked the triggering block-break to auto-smelt (SMELT). Read by the MINE dispatcher. */
    public boolean smeltRequested() {
        return smeltRequested;
    }

    /** Whether an effect asked the broken block's drops to go to the breaker's inventory (TELEPORT_DROPS). */
    public boolean teleportDropsRequested() {
        return teleportDropsRequested;
    }

    /** Whether an effect asked the fired projectile to home onto a target (AUTO_LOCK). Read by the bow dispatcher. */
    public boolean seekRequested() {
        return seekRequested;
    }

    /** Schedule every deferred intent on its owning thread; call once after the gate walk. Idempotent. */
    public void flush() {
        if (flushed) {
            return;
        }
        flushed = true;
        plan.flush();
    }

    /**
     * Set the {@code WAIT} delay (in ticks) applied to the world-mutation intents of subsequent
     * effects, until changed again (§3.6). The {@link engine.run.AbilityExecutor} calls this with each
     * effect's accumulated {@code WAIT} before running it, so the effect's intents dispatch that many
     * ticks after the hit — resolved now (on the firing thread), mutated later (on the owner's thread).
     *
     * <p>Only world mutations honour the delay. Inline feedback — the damage {@link #fold()} and
     * {@link #cancelEvent()} — feeds back into the firing Bukkit event, which no longer exists once a
     * delayed tier fires; a {@code WAIT} before a damage-arbiter contribution is therefore a no-op on
     * the delay (the contribution still applies to the original hit). Negative values clamp to 0.
     */
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
        // Global work (e.g. console commands) always routes to the global region thread, never
        // inline on a firing region thread — even under a CONTEXT_LOCAL ability — so Folia's
        // global-region invariants hold. flush() always runs after the gate walk, so it is not lost.
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
            ItemStack item = target.getInventory().getItemInMainHand();
            if (applyRepair(item, amount)) {
                target.getInventory().setItemInMainHand(item);
            }
        });
    }

    @Override
    public void damageHand(Player target, int amount) {
        entityOp(target, () -> {
            ItemStack item = target.getInventory().getItemInMainHand();
            if (applyDamage(item, amount)) {
                target.getInventory().setItemInMainHand(item);
            }
        });
    }

    @Override
    public void giveExp(Player target, int amount) {
        entityOp(target, () -> target.giveExp(amount));
    }

    @Override
    public void takeExp(Player target, int amount) {
        // Player.giveExp accepts a negative delta; the server clamps total XP at zero across the whole
        // range — the same code path as giveExp, so XP routing stays on the entity's region thread.
        entityOp(target, () -> target.giveExp(-amount));
    }

    @Override
    public void takeFood(Player target, int foodPoints) {
        entityOp(target, () -> target.setFoodLevel(Math.max(0, target.getFoodLevel() - foodPoints)));
    }

    @Override
    public void knockback(Entity target, Location from, double strength) {
        // Clone `from`: a WAIT tier can defer this to a later tick, so the captured origin must be an owned
        // snapshot. `target.getLocation()` is read inside the body, which runs on the target's own thread.
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
            target.setInvulnerable(true);
            if (durationTicks >= 0) {
                Scheduling.onEntityLater(target, durationTicks, () -> target.setInvulnerable(false));
            }
        });
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
            PotionEffectType type = handles.potionEffect(potionEffectId);
            if (type != null) {
                target.addPotionEffect(new PotionEffect(type, durationTicks, amplifier));
            }
        });
    }

    @Override
    public void removePotion(LivingEntity target, int potionEffectId) {
        entityOp(target, () -> {
            PotionEffectType type = handles.potionEffect(potionEffectId);
            if (type != null) {
                target.removePotionEffect(type);
            }
        });
    }

    @Override
    public void cure(LivingEntity target) {
        // Snapshot the active types first: removePotionEffect mutates the live collection, so
        // iterating it directly while removing would be a concurrent-modification hazard.
        entityOp(target, () -> {
            for (PotionEffect active : List.copyOf(target.getActivePotionEffects())) {
                target.removePotionEffect(active.getType());
            }
        });
    }

    @Override
    public void cureByCategory(LivingEntity target, int category) {
        // Snapshot first (removePotionEffect mutates the live collection); remove only the matching bucket.
        entityOp(target, () -> {
            for (PotionEffect active : List.copyOf(target.getActivePotionEffects())) {
                if (PotionCategories.matches(category, active.getType())) {
                    target.removePotionEffect(active.getType());
                }
            }
        });
    }

    @Override
    public void disarm(LivingEntity target) {
        // Runs on the target's own thread (entityOp), so reading its equipment + dropping at its
        // location is region-correct — never a cross-region read.
        entityOp(target, () -> {
            EntityEquipment equipment = target.getEquipment();
            if (equipment == null) {
                return;
            }
            ItemStack held = equipment.getItemInMainHand();
            if (held == null || held.getType().isAir()) {
                return;
            }
            equipment.setItemInMainHand(null);
            World world = target.getWorld();
            if (world != null) {
                world.dropItemNaturally(target.getLocation(), held);
            }
        });
    }

    @Override
    public void removeArmor(LivingEntity target) {
        // Runs on the target's own thread (entityOp): reading its equipment + dropping at its location is
        // region-correct.
        entityOp(target, () -> {
            EntityEquipment equipment = target.getEquipment();
            if (equipment == null) {
                return;
            }
            ItemStack[] worn = equipment.getArmorContents(); // [boots, leggings, chestplate, helmet]
            int[] filled = new int[worn.length];
            int n = 0;
            for (int i = 0; i < worn.length; i++) {
                if (worn[i] != null && !worn[i].getType().isAir()) {
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
                // damage <= 0 is a cosmetic bolt only — no vanilla ~5 dmg / fire (yijki Divine Shield, any flair).
                if (amount > 0) {
                    world.strikeLightning(target.getLocation());
                } else {
                    world.strikeLightningEffect(target.getLocation());
                }
            }
            if (amount > 0) {
                target.damage(amount);
            }
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
        // Teleports are async on Folia; teleportAsync is correct on Paper too and present on the floor API.
        // Clone the destination: a WAIT tier can defer this to a later tick, so the captured target must
        // be an owned snapshot the caller cannot mutate before the hop lands.
        Location dest = to.clone();
        entityOp(target, () -> {
            exemptMovement(target); // §N: let a bundled anti-cheat ignore this engine-applied teleport
            target.teleportAsync(dest);
        });
    }

    @Override
    public void teleportSafe(Entity target, Location preferred, Location fallback, Location sightFrom) {
        Location pref = preferred == null ? null : preferred.clone();
        Location fb = fallback == null ? null : fallback.clone();
        Location sight = sightFrom == null ? null : sightFrom.clone();
        entityOp(target, () -> {
            exemptMovement(target); // §N: let a bundled anti-cheat ignore this engine-applied teleport
            Location dest = pref != null && isSafeDestination(pref, sight) ? pref : fb;
            if (dest != null) {
                target.teleportAsync(dest);
            }
        });
    }

    /** Whether {@code dest} has body room (feet + head passable) and an unobstructed sight line from {@code from}. */
    private static boolean isSafeDestination(Location dest, Location from) {
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

    // ── World / block intents ────────────────────────────────────────────────────────────────────

    @Override
    public void spawnEntity(Location at, int entityTypeId, int count, int ttlTicks, double health, UUID ownerId) {
        Location origin = at.clone(); // own the spawn point: a WAIT tier can defer this to a later tick
        regionOp(origin, () -> {
            EntityType type = handles.entityType(entityTypeId);
            World world = origin.getWorld();
            if (type == null || world == null || count <= 0) {
                return;
            }
            for (int i = 0; i < count; i++) {
                Entity spawned = world.spawnEntity(origin, type);
                if (health > 0 && spawned instanceof LivingEntity living) {
                    applySpawnHealth(living, health);
                }
                if (ownerId != null && spawned instanceof org.bukkit.entity.Tameable tame) {
                    // Owned/tamed summon: resolve by the Tameable CAPABILITY (a stable interface across the
                    // range), never a volatile constant. setOwner accepts an offline AnimalTamer; tame so it sticks.
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
            EntityType type = handles.entityType(entityTypeId);
            World world = origin.getWorld();
            if (type == null || world == null || count <= 0) {
                return;
            }
            for (int i = 0; i < count; i++) {
                Entity spawned = world.spawnEntity(origin, type);
                if (target != null && spawned instanceof Mob mob) {
                    // Path to + attack the attacker. setTarget only stores the reference; the AI runs on the
                    // mob's own (spawn) region, so this is not a cross-region read of the attacker.
                    mob.setTarget(target);
                }
                applyGuardName(spawned, name);
                if (ttlTicks > 0) {
                    Scheduling.onEntityLater(spawned, ttlTicks, spawned::remove);
                }
            }
        });
    }

    /** Apply an optional custom name (with {@code &}-colour codes) to a freshly-summoned guard. */
    @SuppressWarnings("deprecation") // setCustomName(String): deprecated-not-removed across the whole 1.17.1→26.1.x range.
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
                world.createExplosion(at, (float) power, false, breakBlocks);
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
            EntityType type = handles.entityType(entityTypeId);
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
                if (entity instanceof Projectile projectile) {
                    projectile.setShooter(shooter);
                }
            }
        });
    }

    @Override
    public void blockChange(Location at, int blockDataId) {
        // Handle is treated as a Material (covers the common case); full BlockData with states is a follow-up.
        regionOp(at, () -> {
            Material material = handles.material(blockDataId);
            if (material != null && material.isBlock()) {
                at.getBlock().setType(material);
            }
        });
    }

    @Override
    public void breakBlock(Location at, boolean drops) {
        regionOp(at, () -> {
            Block block = at.getBlock();
            if (block.getType().isAir()) {
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
            Material material = handles.material(materialId);
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
        return switch (replaceMode) {
            case 0 -> block.getType().isAir();
            case 1 -> block.getType().isAir() || block.isLiquid();
            default -> true;
        };
    }

    @Override
    public void dropItem(Location at, int materialId, int count) {
        regionOp(at, () -> {
            Material material = handles.material(materialId);
            World world = at.getWorld();
            if (material != null && material.isItem() && world != null && count > 0) {
                world.dropItemNaturally(at, new ItemStack(material, count));
            }
        });
    }

    @Override
    public void sound(Location at, int soundId, float volume, float pitch) {
        regionOp(at, () -> {
            Sound resolved = handles.sound(soundId);
            World world = at.getWorld();
            if (resolved != null && world != null) {
                world.playSound(at, resolved, volume, pitch);
            }
        });
    }

    @Override
    public void giveItem(Player target, int materialId, int count) {
        // Runs on the target's own thread (entityOp): reading + mutating their inventory and dropping
        // overflow at their location is region-correct.
        entityOp(target, () -> {
            Material material = handles.material(materialId);
            if (material == null || !material.isItem() || count <= 0) {
                return;
            }
            ItemStack stack = new ItemStack(material, count);
            target.getInventory().addItem(stack).values()
                    .forEach(extra -> target.getWorld().dropItemNaturally(target.getLocation(), extra));
        });
    }

    @Override
    public void removeItem(Player target, int materialId, int count) {
        entityOp(target, () -> {
            Material material = handles.material(materialId);
            if (material != null && material.isItem() && count > 0) {
                target.getInventory().removeItem(new ItemStack(material, count));
            }
        });
    }

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

    /** Clamp an authored 0-255 colour channel into range. */
    private static int clampChannel(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // ── Player feedback ──────────────────────────────────────────────────────────────────────────

    @Override
    public void message(Player target, String message) {
        // Translate legacy '&' codes to '§' so feedback shows coloured, not literal "&c&l…" — the floor-safe
        // legacy-code stance, like applyGuardName above (ChatColor here, since the engine module can't see
        // item.render.Colors; same result for the standard 0-9/a-f/k-o/r codes).
        String text = legacyColor(message);
        entityOp(target, () -> target.sendMessage(text));
    }

    @Override
    @SuppressWarnings("deprecation") // spigot().sendMessage(ChatMessageType, BaseComponent): the floor-stable action-bar path.
    public void actionBar(Player target, String message) {
        // The Spigot chat API is the one action-bar path stable across the whole 1.17.1 → 26.1.x range.
        // Translate '&' → '§' first — fromLegacyText parses '§', not '&'.
        String text = legacyColor(message);
        entityOp(target, () -> target.spigot().sendMessage(
                ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text)));
    }

    @Override
    @SuppressWarnings("deprecation") // sendTitle(String, String, int, int, int): deprecated-not-removed across the whole range.
    public void title(Player target, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        // 5-arg String sendTitle is the one title path stable across the range (no Adventure Title API on
        // the spigot-mapped floor). Translate '&' → '§' so colour codes render, not show literally.
        String t = legacyColor(title);
        String s = legacyColor(subtitle);
        entityOp(target, () -> target.sendTitle(t, s, fadeIn, stay, fadeOut));
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
        // Capture the UUID on the firing thread (immutable, thread-safe); the economy call runs on the
        // global thread — never touching the live player object off its region.
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
        // Read-balance + withdraw + deposit in ONE global-thread task so no other money op interleaves;
        // deposit only what was actually charged (withdraw is all-or-nothing).
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
        // Route to the HOLDER's own thread (not global like money): the debit write-throughs the gem's PDC
        // wherever it sits in the holder's inventory, which is region-bound on Folia. The in-memory authority
        // debit drains the holder's gems least-first inside SoulDebit.debit on that thread.
        entityOp(holder, () -> souls.debit(holder, gemId, amount));
    }

    @Override
    public void removeSoulsFrom(Player target, int amount) {
        if (target == null || amount <= 0) {
            return;
        }
        // Route to the TARGET's own thread: the debit collaborator resolves the target's active gem from the
        // soul-mode store and write-throughs its PDC, which is region-bound to where the gem sits.
        entityOp(target, () -> souls.debitTarget(target, amount));
    }

    // ── Variable intents ───────────────────────────────────────────────────────────────────────

    @Override
    public void setVar(Player target, String name, String value, int ttlTicks) {
        if (target == null || name == null) {
            return;
        }
        // Per-player in-memory state, not a world mutation: the VarStore is a ConcurrentHashMap, so writing
        // it on the firing thread is Folia-safe (the UUID is captured here; no live cross-region entity read).
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
        // Per-player in-memory state keyed by the (scopeKind, scopeId) cooldown-scope packing — the same
        // key gate 5 reads for the suppressed abilities. The store is concurrent, so writing it on the
        // firing thread is Folia-safe (only the target's UUID is captured; no cross-region entity read).
        suppression.suppress(target.getUniqueId(), CooldownStore.key(scopeKind, scopeId),
                nowTicks.getAsLong(), durationTicks);
    }

    @Override
    public void suppressImmune(Player target, boolean on) {
        if (target != null) {
            // Per-player flag in the shared SuppressionStore, read by suppress()'s write-veto. The UUID is
            // captured here → Folia-safe on the firing thread (no cross-region entity read, no scheduler hop).
            suppression.setImmune(target.getUniqueId(), on);
        }
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
        // Per-victim in-memory flag read later by the knockback listener (a separate Bukkit event from this
        // hit). The store is concurrent and only the victim's UUID is captured here, so writing it on the
        // firing thread is Folia-safe — no cross-region live entity read, no scheduler hop.
        knockback.control(victim.getUniqueId(), multiplier, nowTicks.getAsLong(), ttlTicks);
    }

    @Override
    public void keepOnDeath(Player target, int ttlTicks) {
        if (target == null) {
            return;
        }
        // Per-player in-memory flag read later by the death listener (a separate Bukkit event). The store is
        // concurrent and only the player's UUID is captured here, so writing it on the firing thread is
        // Folia-safe — no cross-region live entity read, no scheduler hop.
        keepOnDeath.keep(target.getUniqueId(), nowTicks.getAsLong(), ttlTicks);
    }

    @Override
    public void teleblock(Player target, int durationTicks) {
        if (target == null) {
            return;
        }
        // Per-player timed flag read later by the teleport/launch listener (a separate Bukkit event). Concurrent
        // store, UUID captured here → Folia-safe on the firing thread.
        teleblock.block(target.getUniqueId(), nowTicks.getAsLong(), durationTicks);
    }

    @Override
    public void immune(Player target, int damageType, int durationTicks) {
        if (target == null) {
            return;
        }
        // Per-player timed flag read later by the damage listener (a separate Bukkit event from the hit that
        // armed it). Concurrent store, UUID captured here → Folia-safe on the firing thread.
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

    // ── Cross-version helpers ────────────────────────────────────────────────────────────────────

    /** The max-health attribute instance for {@code entity}, resolved version-adaptively, or {@code null}. */
    private AttributeInstance maxHealthAttribute(LivingEntity entity) {
        Object attribute = handles.resolveByName(HandleCategory.ATTRIBUTE, "GENERIC_MAX_HEALTH");
        return attribute instanceof Attribute resolved ? entity.getAttribute(resolved) : null;
    }

    /** The entity's maximum health via the attribute, falling back to the floor accessor. */
    @SuppressWarnings("deprecation") // getMaxHealth: deprecated-not-removed across the whole range.
    private double maxHealth(LivingEntity entity) {
        AttributeInstance maxHealth = maxHealthAttribute(entity);
        return maxHealth != null ? maxHealth.getValue() : entity.getMaxHealth();
    }

    /** Set a freshly-spawned living entity's max + current health (SPAWN_ENTITY's {@code health} param). */
    private void applySpawnHealth(LivingEntity entity, double health) {
        AttributeInstance maxHealth = maxHealthAttribute(entity);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
        }
        entity.setHealth(Math.min(health, maxHealth(entity)));
    }

    /** Repair one item by {@code amount} (negative = full); returns whether the meta changed. */
    private static boolean applyRepair(ItemStack item, int amount) {
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

    /** Wear one item down by {@code amount} (clamped to its max durability); returns whether it changed. */
    private static boolean applyDamage(ItemStack item, int amount) {
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

    /** Adjust every worn armour piece's durability — repair when {@code repair}, else damage it. */
    private static void adjustArmorDurability(LivingEntity entity, int amount, boolean repair) {
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

    /** Strip temporarily-granted flight, but never from a player who can fly by game mode. */
    private static void clearTemporaryFlight(Player player) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }
}

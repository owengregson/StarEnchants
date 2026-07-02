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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
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
import platform.sched.Scheduling;
import platform.sched.TaskHandle;
import platform.text.Colors;

/**
 * The era-neutral core of the concrete {@link Sink} — the single mutation boundary and the only engine code
 * that knows about threads (§3.5–3.6). Both overlay {@code DispatchSink} leaves ({@code overlay/modern} on
 * the Bukkit API, {@code overlay/legacy} on {@code v1_8_R3} NMS) extend this class and implement ONLY the
 * platform-edge leaves below; the plan/batching, {@link DamageFold} bookkeeping, the stores, the readback
 * semantics, and every intent body expressible through those leaves live here once
 * (ADR-0036; docs/legacy-1.8.9-codeshare-design.md §3.5). Two kinds of intent:
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
 * <p>Version-volatile referents arrive as interned ids and are resolved through the era leaves
 * ({@link #material(int)}, {@link #sound(int)}, {@link #potionEffect(int)}, {@link #entityType(int)}) on the
 * correct thread (§9). An id that does not resolve yields {@code null} and that one intent is silently skipped
 * — the §9 warn already fired at compile time, so a runtime miss is a can't-happen on a stable server.
 *
 * <p>One instance per event; not thread-safe by design — filled and flushed on the single firing thread (§6),
 * scheduled batches run later on their own threads over immutable captured primitives.
 */
public abstract class DispatchSinkBase implements SinkReadback {

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
    private double expMultiplier = 1.0;
    private boolean flushed;
    private int delayTicks;

    /**
     * §N anti-cheat movement exemption (ADR-0027): invoked before StarEnchants moves a PLAYER (VELOCITY /
     * TELEPORT) so a bundled anti-cheat bridge can briefly exempt them, preventing false flags. Static no-op
     * default (inert in tests, free per event).
     */
    private static volatile Consumer<Player> movementExemption = player -> { };

    /** Install the anti-cheat movement-exemption hook (boot-time). A {@code null} hook resets to no-op. */
    public static void movementExemption(Consumer<Player> hook) {
        movementExemption = hook == null ? player -> { } : hook;
    }

    /** Exempt {@code target} from anti-cheat movement checks if it is a player (runs on the target thread). */
    private static void exemptMovement(Entity target) {
        if (target instanceof Player player) {
            movementExemption.accept(player);
        }
    }

    protected DispatchSinkBase(EconomyService economy, SoulDebit souls, VarStore vars, SuppressionStore suppression,
                               KnockbackControlStore knockback, KeepOnDeathStore keepOnDeath, TeleblockStore teleblock,
                               ImmuneStore immune, LongSupplier nowTicks) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.souls = Objects.requireNonNull(souls, "souls");
        this.vars = Objects.requireNonNull(vars, "vars");
        this.suppression = Objects.requireNonNull(suppression, "suppression");
        this.knockback = Objects.requireNonNull(knockback, "knockback");
        this.keepOnDeath = Objects.requireNonNull(keepOnDeath, "keepOnDeath");
        this.teleblock = Objects.requireNonNull(teleblock, "teleblock");
        this.immune = Objects.requireNonNull(immune, "immune");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.fold = new DamageFold();
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

    /** The accumulated EXP_MULTIPLY factor for an EXP_GAIN activation (1.0 = unchanged). Read by the EXP_GAIN dispatcher. */
    @Override
    public double expMultiplier() {
        return expMultiplier;
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
    @Override
    public void delay(int ticks) {
        this.delayTicks = Math.max(0, ticks);
    }

    /** Route an intent to the entity's own thread — never inline (the target may be cross-region on Folia). */
    protected void entityOp(Entity target, Runnable op) {
        if (target != null) {
            plan.onEntity(target, op, delayTicks);
        }
    }

    /** Route an intent to the location's region thread — never inline. */
    protected void regionOp(Location at, Runnable op) {
        if (at != null) {
            plan.onRegion(at, op, delayTicks);
        }
    }

    protected void globalOp(Runnable op) {
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
            ItemStack item = mainHand(target);
            if (applyRepair(item, amount)) {
                setMainHand(target, item);
            }
        });
    }

    @Override
    public void damageHand(Player target, int amount) {
        entityOp(target, () -> {
            ItemStack item = mainHand(target);
            if (applyDamage(item, amount)) {
                setMainHand(target, item);
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
    public void flyMode(Player target, boolean allow) {
        entityOp(target, () -> {
            if (allow) {
                GameMode mode = target.getGameMode();
                if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
                    target.setAllowFlight(true); // allow flight; don't force them airborne
                }
            } else {
                clearTemporaryFlight(target); // survival/adventure only: stop + disallow
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
            applyInvulnerable(target, true);
            if (durationTicks >= 0) {
                Scheduling.onEntityLater(target, durationTicks, () -> applyInvulnerable(target, false));
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
    public void potionLock(LivingEntity target, int potionEffectId, int durationTicks) {
        entityOp(target, () -> {
            PotionEffectType type = potionEffect(potionEffectId);
            if (type == null) {
                return;
            }
            target.removePotionEffect(type); // strip now
            if (durationTicks <= 0) {
                return; // a one-shot strip, no lock window
            }
            // Continuously deny: re-strip every tick until the window elapses (the locked set is tiny;
            // removePotionEffect on an absent effect is a cheap no-op). The handle is captured so both the
            // window backstop and an early world-exit cancel it — the Paper timer is not entity-tied (it would
            // otherwise re-strip a logged-out player), while on Folia the entity task stops on its own.
            TaskHandle[] handle = new TaskHandle[1];
            handle[0] = Scheduling.repeatingEntity(target, 1L, 1L, () -> {
                if (!target.isValid()) {
                    if (handle[0] != null) {
                        handle[0].cancel();
                    }
                    return;
                }
                target.removePotionEffect(type);
            });
            Scheduling.onEntityLater(target, durationTicks, () -> {
                if (handle[0] != null) {
                    handle[0].cancel(); // end the lock at the window's close
                }
            });
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
    public void mark(LivingEntity victim, UUID marker, double percent, int durationTicks) {
        if (victim != null && marker != null) {
            // Per-(victim, marker) flag in the static registry, consulted by the fold on the marker's later
            // hits. UUIDs captured here → Folia-safe inline write (no cross-region entity read, no scheduler hop).
            DamageMarks.mark(victim.getUniqueId(), marker, percent / 100.0, durationTicks * 50L); // ticks → ms
        }
    }

    @Override
    public void markZone(Location center, UUID owner, double radius, int durationTicks) {
        if (center == null || owner == null || center.getWorld() == null) {
            return;
        }
        // Inline per-owner registry write (no entity hop): the centre was resolved on the firing thread, so
        // reading its world id + x/z here is region-correct. Consulted later by the %victim.inzone% fact.
        OwnerZones.mark(owner, center.getWorld().getUID(), center.getX(), center.getZ(),
                radius, durationTicks * 50L); // ticks → ms
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
            ItemStack held = mainHand(equipment);
            if (held == null || isAir(held.getType())) {
                return;
            }
            setMainHand(equipment, null);
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
                if (worn[i] != null && !isAir(worn[i].getType())) {
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
    public void swapEquipment(Player target, int slotIndex, int materialId, int durationTicks) {
        entityOp(target, () -> {
            Material placeholder = material(materialId);
            ItemStack[] armor = target.getInventory().getArmorContents();
            if (placeholder == null || slotIndex < 0 || slotIndex >= armor.length) {
                return;
            }
            ItemStack original = armor[slotIndex];
            if (!TempEquip.swap(target.getUniqueId(), slotIndex, original == null ? null : original.clone())) {
                return; // a swap is already active on this slot — never double-swap
            }
            armor[slotIndex] = new ItemStack(placeholder);
            target.getInventory().setArmorContents(armor);
            if (durationTicks > 0) {
                UUID id = target.getUniqueId();
                Scheduling.onEntityLater(target, durationTicks, () -> restoreSwap(target, id, slotIndex, placeholder));
            }
        });
    }

    /** Restore a swapped slot to its original, but only while it is still our placeholder (don't clobber a re-equip). */
    private static void restoreSwap(Player target, UUID id, int slotIndex, Material placeholder) {
        ItemStack original = TempEquip.end(id, slotIndex);
        if (original == null) {
            return; // already ended (the death/quit listener restored it)
        }
        ItemStack[] armor = target.getInventory().getArmorContents();
        if (slotIndex < armor.length && armor[slotIndex] != null && armor[slotIndex].getType() == placeholder) {
            armor[slotIndex] = TempEquip.isAir(original) ? null : original;
            target.getInventory().setArmorContents(armor);
        }
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
        // Clone the destination: a WAIT tier can defer this to a later tick, so the captured target must
        // be an owned snapshot the caller cannot mutate before the hop lands. The era leaf performs the
        // actual teleport (async on Folia/modern, synchronous on 1.8) on the target's own thread.
        Location dest = to.clone();
        entityOp(target, () -> {
            exemptMovement(target); // §N: let a bundled anti-cheat ignore this engine-applied teleport
            teleportTo(target, dest);
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
                teleportTo(target, dest);
            }
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
                if (health > 0 && spawned instanceof LivingEntity living) {
                    applySpawnHealth(living, health);
                }
                if (ownerId != null && spawned instanceof Tameable tame) {
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
            EntityType type = entityType(entityTypeId);
            World world = origin.getWorld();
            if (type == null || world == null || count <= 0) {
                return;
            }
            for (int i = 0; i < count; i++) {
                Entity spawned = world.spawnEntity(origin, type);
                if (target != null) {
                    setGuardTarget(spawned, target); // path to + attack the attacker (era-specific targeting API)
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
            entity.setCustomName(Colors.translate(name));
            entity.setCustomNameVisible(true);
        }
    }

    @Override
    public void explode(Location at, double power, boolean breakBlocks) {
        regionOp(at, () -> {
            World world = at.getWorld();
            if (world != null) {
                doExplosion(world, at, power, breakBlocks);
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
            if (isAir(block.getType())) {
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

    @Override
    public void tempBlock(Location at, int materialId, int durationTicks, int replaceMode, boolean unbreakable) {
        Location pos = at.clone(); // own the position: a WAIT tier can defer this to a later tick
        regionOp(pos, () -> {
            Material material = material(materialId);
            World world = pos.getWorld();
            if (material == null || !material.isBlock() || world == null) {
                return;
            }
            Block block = pos.getBlock();
            if (!canReplace(block, replaceMode)) {
                return;
            }
            Material prior = block.getType();
            block.setType(material, false);
            if (durationTicks > 0) {
                // Revert only if the tile is STILL ours — a later placement that overwrote it owns the revert now.
                Scheduling.onRegionLater(pos, durationTicks, () -> {
                    Block current = pos.getBlock();
                    if (current.getType() == material) {
                        current.setType(prior, false);
                    }
                });
            }
        });
    }

    /** Whether a temp-platform may overwrite this block: 0 = air only, 1 = air/liquid, 3 = solid only, 2 = anything. */
    private boolean canReplace(Block block, int replaceMode) {
        return switch (replaceMode) {
            case 0 -> isAir(block.getType());
            case 1 -> isAir(block.getType()) || block.isLiquid();
            case 3 -> block.getType().isSolid(); // solid-only: a footprint replaces the ground it sits on, never air
            default -> true;
        };
    }

    @Override
    public void dropItem(Location at, int materialId, int count) {
        regionOp(at, () -> {
            Material material = material(materialId);
            World world = at.getWorld();
            if (material != null && isItemMaterial(material) && world != null && count > 0) {
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
        // Runs on the target's own thread (entityOp): reading + mutating their inventory and dropping
        // overflow at their location is region-correct.
        entityOp(target, () -> {
            Material material = material(materialId);
            if (material == null || !isItemMaterial(material) || count <= 0) {
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
            Material material = material(materialId);
            if (material != null && isItemMaterial(material) && count > 0) {
                target.getInventory().removeItem(new ItemStack(material, count));
            }
        });
    }

    @Override
    public void fallingBlock(Location at, int materialId, int ttlTicks, UUID owner, double carriedDamage) {
        Location loc = at.clone();
        regionOp(loc, () -> {
            Material material = material(materialId);
            World world = loc.getWorld();
            if (material == null || !material.isBlock() || world == null) {
                return;
            }
            FallingBlock fb = spawnFallingBlock(world, loc, material);
            fb.setDropItem(false);     // never leave an item
            fb.setHurtEntities(false); // no vanilla anvil-style damage — the impact is the IMPACT trigger's effects
            // Track EVERY cosmetic block (owner or not) so the landing listener cancels its placement; an owner
            // additionally drives the IMPACT abilities. A FALLING_BLOCK is always cosmetic and must never stick.
            FallingBlockCasts.bind(fb.getUniqueId(), owner, carriedDamage);
            if (ttlTicks > 0) {
                UUID fbId = fb.getUniqueId();
                Scheduling.onEntityLater(fb, ttlTicks, () -> { // never landed (void/edge) → forget + clean up
                    FallingBlockCasts.forget(fbId);
                    fb.remove();
                });
            }
        });
    }

    /** Clamp an authored 0-255 colour channel into range. */
    protected static int clampChannel(int v) {
        return Math.max(0, Math.min(255, v));
    }

    // ── Player feedback ──────────────────────────────────────────────────────────────────────────

    @Override
    public void message(Player target, String message) {
        // Translate legacy '&' codes to '§' so feedback shows coloured, not literal "&c&l…" — the floor-safe
        // legacy-code stance, through the shared platform.text.Colors (ADR-0033).
        String text = Colors.translate(message);
        entityOp(target, () -> target.sendMessage(text));
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
    public void suppressImmune(Player target, int chance) {
        if (target != null) {
            // Per-player immunity CHANCE in the shared SuppressionStore, rolled by suppress()'s write-veto. The
            // UUID is captured here → Folia-safe on the firing thread (no cross-region entity read, no scheduler hop).
            suppression.setImmune(target.getUniqueId(), chance);
        }
    }

    // ── Event control ──────────────────────────────────────────────────────────────────────────

    @Override
    public void cancelEvent() {
        cancelled = true;
    }

    @Override
    public void multiplyExp(double factor) {
        if (factor >= 0) {
            expMultiplier *= factor; // inline read-back: the EXP_GAIN dispatcher scales the event's amount, never grants new XP
        }
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

    /** Strip temporarily-granted flight, but never from a player who can fly by game mode. */
    private static void clearTemporaryFlight(Player player) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    // ── Era leaves — the platform edge each overlay implements (docs/legacy-1.8.9-codeshare-design.md §3.5) ──

    /** The {@link Material} for an interned material id, or {@code null} on a miss. */
    protected abstract Material material(int id);

    /** The {@link Sound} for an interned sound id, or {@code null} on a miss. */
    protected abstract Sound sound(int id);

    /** The {@link PotionEffectType} for an interned potion-effect id, or {@code null} on a miss. */
    protected abstract PotionEffectType potionEffect(int id);

    /** The {@link EntityType} for an interned entity-type id, or {@code null} on a miss. */
    protected abstract EntityType entityType(int id);

    /**
     * Whether {@code material} is any air variant. Modern honours {@code CAVE_AIR}/{@code VOID_AIR} via
     * {@code Material#isAir}; 1.8 has only {@code AIR}. Kept a leaf so neither era's exact set changes.
     */
    protected abstract boolean isAir(Material material);

    /**
     * Whether {@code material} can exist as a dropped/inventory item. Modern uses {@code Material#isItem};
     * 1.8 has no such predicate and approximates it as "not AIR". Kept a leaf to preserve each era's behaviour.
     */
    protected abstract boolean isItemMaterial(Material material);

    /** The entity's maximum health (attribute on modern; NMS {@code GenericAttributes} on 1.8). */
    protected abstract double maxHealth(LivingEntity entity);

    /** Set a freshly-spawned living entity's max + current health (SPAWN_ENTITY's {@code health} param). */
    protected abstract void applySpawnHealth(LivingEntity entity, double health);

    /**
     * Set/clear the entity's invulnerability. Modern calls {@code setInvulnerable}; 1.8 (which lacks it)
     * flips the NMS {@code Entity.invulnerable} field. Runs on the target's own thread.
     */
    protected abstract void applyInvulnerable(LivingEntity target, boolean invulnerable);

    /**
     * Teleport {@code target} to {@code dest} on the target's own thread — async on Folia/modern
     * ({@code teleportAsync}), synchronous on 1.8. {@code dest} is an owned snapshot (already cloned).
     */
    protected abstract void teleportTo(Entity target, Location dest);

    /**
     * Whether {@code dest} is a safe teleport destination (feet + head clear). Modern additionally rejects a
     * destination with a wall on the sight line from {@code from} (may be {@code null}); 1.8 ignores {@code from}.
     */
    protected abstract boolean isSafeDestination(Location dest, Location from);

    /** The player's main-hand item ({@code getItemInMainHand} on modern; {@code getItemInHand} on 1.8). */
    protected abstract ItemStack mainHand(Player target);

    /** Write the player's main-hand item ({@code setItemInMainHand} on modern; {@code setItemInHand} on 1.8). */
    protected abstract void setMainHand(Player target, ItemStack item);

    /** The main-hand item of an {@link EntityEquipment} (main-hand accessor differs by era). */
    protected abstract ItemStack mainHand(EntityEquipment equipment);

    /** Write the main-hand item of an {@link EntityEquipment} (main-hand accessor differs by era). */
    protected abstract void setMainHand(EntityEquipment equipment, ItemStack item);

    /** Repair one item by {@code amount} (negative = full); returns whether it changed (durability API differs by era). */
    protected abstract boolean applyRepair(ItemStack item, int amount);

    /** Wear one item down by {@code amount} (clamped to its max durability); returns whether it changed. */
    protected abstract boolean applyDamage(ItemStack item, int amount);

    /** Adjust every worn armour piece's durability — repair when {@code repair}, else damage it. */
    protected abstract void adjustArmorDurability(LivingEntity entity, int amount, boolean repair);

    /**
     * Make a freshly-summoned guard path to + attack {@code target}. Modern targets via {@code Mob#setTarget};
     * 1.8 (no {@code Mob}) via {@code Creature#setTarget}. Only stores the reference — no cross-region read.
     */
    protected abstract void setGuardTarget(Entity spawned, LivingEntity target);

    /** Spawn a cosmetic falling block of {@code material} at {@code loc} (block-data on modern; data byte on 1.8). */
    protected abstract FallingBlock spawnFallingBlock(World world, Location loc, Material material);

    /** Create an explosion honouring {@code breakBlocks}, never fire (the overload with a block-break flag differs by era). */
    protected abstract void doExplosion(World world, Location at, double power, boolean breakBlocks);
}

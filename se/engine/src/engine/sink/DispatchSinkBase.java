package engine.sink;

import engine.interact.DamageFold;
import engine.stores.CooldownStore;
import engine.stores.DamageCapStore;
import engine.stores.ImmuneStore;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.OutgoingDebuffStore;
import engine.stores.ReflectMarksStore;
import engine.stores.SuppressionStore;
import engine.stores.TeleblockStore;
import engine.stores.VarStore;
import java.util.ArrayList;
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
import org.bukkit.entity.Creeper;
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
import platform.caps.Regions;
import platform.economy.EconomyService;
import platform.item.Inventories;
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
 * <p><strong>Current-health writes are the one carve-out (ADR-0051).</strong> A zero-WAIT {@link #heal}/
 * {@link #setHealth} targeting the declared {@linkplain #eventEntity(LivingEntity) event entity} runs inline at
 * {@link #flush()} — the firing thread owns that entity by definition, and the event's own damage has not been
 * applied yet, so the write participates in the vanilla kill decision instead of racing it (a "blow that would
 * kill you instead heals" save must land pre-death or it resurrects a corpse). Every other current-health write
 * stays deferred but gains an execution-time liveness gate: a target that died before the write lands drops it —
 * the dead stay dead, nothing revives by side effect.
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

    private static final System.Logger LOG = System.getLogger("StarEnchants.Sink");

    private final EconomyService economy;
    private final SoulDebit souls;
    private final VarStore vars;
    private final SuppressionStore suppression;
    private final KnockbackControlStore knockback;
    private final KeepOnDeathStore keepOnDeath;
    private final LongSupplier nowTicks;
    /**
     * §N anti-cheat movement exemption (ADR-0027, ADR-0047): invoked before StarEnchants moves a PLAYER
     * (VELOCITY / TELEPORT) so a bundled anti-cheat bridge can briefly exempt them, preventing false flags.
     * Rides the {@link SinkEnv} as instance wiring (no mutable static installer); {@link SinkEnv#of} supplies
     * the inert no-op default for tests and tester suites.
     */
    private final Consumer<Player> movementExemption;
    private final DispatchPlan plan = new DispatchPlan();
    private final DamageFold fold;

    private final TeleblockStore teleblock;
    private final ImmuneStore immune;
    private final ReflectMarksStore reflectMarks;   // ADR-0049 Hex reflect windows
    private final OutgoingDebuffStore outgoingDebuff; // ADR-0049 Weaken/Destruction outgoing nerfs
    private final DamageCapStore damageCap;          // ADR-0049 Diminish last-taken + armed cap
    /** The ONE per-boot ledger (via {@link SinkEnv}), so temp blocks from separate events compound, not clobber. */
    private final TempBlockLedger<BlockState> tempBlocks;
    /** The ONE per-boot trail memory (via {@link SinkEnv}), so the footprint snake connects across activations. */
    private final TrailWalker trails;
    /** The ONE per-boot timed-revert registry (via {@link SinkEnv}), so the quit drain can restore a logout-stranded buff. */
    private final TimedRevert timedReverts;
    /** LIVE ceiling on one {@code interest_percent} deposit ({@code <= 0} = uncapped) — ADR-0052 Fish. */
    private final java.util.function.DoubleSupplier moneyInterestCap;
    /** The scroll-marker seam {@code STRIP_SCROLL} mutates victim gear through (ADR-0052 Anubis). */
    private final GearProtection gearProtection;

    /** The event's own entity (the combat victim), whose zero-WAIT health writes run inline at flush (ADR-0051). */
    private LivingEntity eventEntity;
    private UUID eventEntityId;
    /** Lazily-allocated same-event health credits, run at flush BEFORE the deferred plan (ADR-0051). */
    private List<Runnable> healthCredits;

    private boolean cancelled;
    private boolean armorIgnored;
    private boolean smeltRequested;
    private boolean teleportDropsRequested;
    private boolean seekRequested;
    private boolean echoRequested; // ADR-0049 ECHO_STRIKE: one extra attacker-side pass over this hit
    private double expMultiplier = 1.0;
    private boolean flushed;
    private int delayTicks;

    /** Exempt {@code target} from anti-cheat movement checks if it is a player (runs on the target thread). */
    private void exemptMovement(Entity target) {
        if (target instanceof Player player) {
            movementExemption.accept(player);
        }
    }

    protected DispatchSinkBase(SinkEnv env) {
        // SinkEnv's compact ctor already null-checks; the nine final fields and every hot-path read stay
        // byte-identical to the old telescoping ctor.
        this.economy = env.economy();
        this.souls = env.souls();
        this.vars = env.stores().vars();
        this.suppression = env.stores().suppression();
        this.knockback = env.stores().knockback();
        this.keepOnDeath = env.stores().keepOnDeath();
        this.teleblock = env.stores().teleblock();
        this.immune = env.stores().immune();
        this.reflectMarks = env.stores().reflectMarks();
        this.outgoingDebuff = env.stores().outgoingDebuff();
        this.damageCap = env.stores().damageCap();
        this.nowTicks = env.nowTicks();
        this.movementExemption = env.movementExemption();
        this.tempBlocks = env.tempBlocks();
        this.trails = env.trails();
        this.timedReverts = env.timedReverts();
        this.moneyInterestCap = env.moneyInterestCap();
        this.gearProtection = env.gearProtection();
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

    /** Whether an effect requested an extra attacker-side echo pass (ECHO_STRIKE). Read by the combat dispatcher. */
    @Override
    public boolean echoRequested() {
        return echoRequested;
    }

    /** Declare the event's own entity: its zero-WAIT health writes run inline at flush (ADR-0051). */
    @Override
    public void eventEntity(LivingEntity entity) {
        this.eventEntity = entity;
        this.eventEntityId = entity == null ? null : entity.getUniqueId();
    }

    /**
     * Schedule every deferred intent on its owning thread; call once after the gate walk. Same-event health
     * credits run first, inline — still inside the firing event, so they precede the event's own outcome
     * (ADR-0051) — with the plan's warn-and-skip isolation (§9). Idempotent.
     */
    @Override
    public void flush() {
        if (flushed) {
            return;
        }
        flushed = true;
        if (healthCredits != null) {
            for (Runnable credit : healthCredits) {
                try {
                    credit.run();
                } catch (RuntimeException failed) {
                    LOG.log(System.Logger.Level.WARNING, "same-event health credit failed at flush", failed);
                }
            }
        }
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

    /**
     * Route a current-health write (ADR-0051): inline at flush when it targets the event entity with no WAIT
     * (the firing thread owns it, and the event's damage has not applied — the write joins the kill decision);
     * deferred behind the liveness gate otherwise, so a target that died first drops the write.
     */
    private void healthWrite(LivingEntity target, Runnable write) {
        if (target == null) {
            return;
        }
        if (delayTicks <= 0 && isEventEntity(target)) {
            if (healthCredits == null) {
                healthCredits = new ArrayList<>(2);
            }
            // Gated too: a double-fired damage event on an already-dying entity must not revive it.
            healthCredits.add(() -> {
                if (alive(target)) {
                    write.run();
                }
            });
            return;
        }
        entityOp(target, () -> {
            if (alive(target)) {
                write.run();
            }
        });
    }

    /** Identity by UUID, not instance — a re-wrapped handle for the same entity still counts. */
    private boolean isEventEntity(LivingEntity target) {
        return target == eventEntity || (eventEntityId != null && eventEntityId.equals(target.getUniqueId()));
    }

    /**
     * The dead stay dead (ADR-0051): checked on the owning thread at execution time, never at emit time.
     * {@code getHealth() > 0} carries the check on 1.8, where a 0-hp player's {@code isDead()} can lag the
     * {@code dead} flag; {@code isValid()} drops writes to removed entities.
     */
    private static boolean alive(LivingEntity target) {
        return target.isValid() && !target.isDead() && target.getHealth() > 0.0;
    }

    /** Route an intent to the location's region thread — never inline. */
    protected void regionOp(Location at, Runnable op) {
        if (at != null) {
            plan.onRegion(at, op, delayTicks);
        }
    }

    /**
     * Schedule a timed-buff teardown that also survives the player logging out mid-window (F07/F08). For a
     * PLAYER target the revert is registered with the {@link TimedRevert} registry under a token; the expiry
     * timer claims that token so it runs exactly once, and the quit drain runs any that the timer never reached
     * — so the reverted flag is what persists to disk. Non-player targets keep the direct schedule (mobs never
     * quit; registering them would leak entries when their region unloads).
     */
    private void revertLater(LivingEntity target, int durationTicks, Runnable revert) {
        if (target instanceof Player player) {
            UUID id = player.getUniqueId();
            long token = timedReverts.begin(id, revert);
            Scheduling.onEntityLater(target, durationTicks, () -> timedReverts.runOnce(id, token));
        } else {
            Scheduling.onEntityLater(target, durationTicks, revert);
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
    public void damagePercentOfMax(LivingEntity target, double percentOfMax) {
        if (percentOfMax <= 0) {
            return;
        }
        // The max-health read and the damage both run on the target's own thread (entityOp) — never a cross-region
        // max-health read. Uses the era-adaptive maxHealth() leaf, so it is version-stable.
        entityOp(target, () -> target.damage(maxHealth(target) * percentOfMax / 100.0));
    }

    @Override
    public void heal(LivingEntity target, double amount) {
        healthWrite(target, () -> target.setHealth(Math.min(target.getHealth() + amount, maxHealth(target))));
    }

    @Override
    public void setHealth(LivingEntity target, double health) {
        healthWrite(target, () -> target.setHealth(Math.max(0.0, Math.min(health, maxHealth(target)))));
    }

    @Override
    public void addMaxHealth(LivingEntity target, double amount) {
        entityOp(target, () -> {
            // Shifts the base value directly; unequip restoration of this delta lands with WornState (§5.5).
            if (hasMaxHealthAttribute(target)) {
                setMaxHealthBase(target, Math.max(1.0, maxHealthBase(target) + amount));
            }
        });
    }

    @Override
    public void drainMaxHealth(LivingEntity target, double fraction, double baseline, double flat, int durationTicks) {
        entityOp(target, () -> {
            if (!hasMaxHealthAttribute(target)) {
                return;
            }
            double base = maxHealthBase(target);
            double overhealth = base - baseline;
            double drain = overhealth * fraction + flat;
            if (drain <= 0) {
                return; // no overhealth to take
            }
            double newBase = Math.max(1.0, base - drain);
            double removed = base - newBase; // exact delta (also when the clamp bit)
            setMaxHealthBase(target, newBase);
            if (target.getHealth() > maxHealth(target)) {
                target.setHealth(Math.max(0.0, maxHealth(target))); // clamp current down to the new cap
            }
            if (durationTicks > 0) {
                // Restore-on-quit too (F07): a victim who combat-logs mid-drain gets the exact removed delta back
                // BEFORE the playerdata save, so the reduction can never be made permanent by pressuring a log-out.
                revertLater(target, durationTicks, () -> {
                    if (hasMaxHealthAttribute(target)) {
                        setMaxHealthBase(target, maxHealthBase(target) + removed); // add back exactly what was drained
                    }
                });
            }
        });
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
    public void transferExp(Player from, Player to, int amount) {
        if (from == null || to == null || amount <= 0) {
            return;
        }
        // Read the victim's real total and debit it atomically on the victim's own region thread, then credit
        // exactly what was withdrawn on the recipient's own thread (never re-entering the already-flushed plan).
        // Clamping to the true total mints nothing off a poor victim and never relies on the negative-giveExp clamp.
        entityOp(from, () -> {
            int amt = Math.max(0, Math.min(amount, totalXpPoints(from)));
            if (amt > 0) {
                from.giveExp(-amt);
                Scheduling.onEntity(to, () -> to.giveExp(amt));
            }
        });
    }

    /**
     * {@code player}'s TOTAL experience in points via the vanilla level→points curve (unchanged since MC 1.8,
     * so era-safe for the shared base) plus the fractional progress into the current level. Preferred over
     * {@link Player#getTotalExperience()}, which is known-stale after {@code setLevel}.
     */
    static int totalXpPoints(Player player) {
        int level = player.getLevel();
        long atLevel;
        if (level <= 16) {
            atLevel = (long) level * level + 6L * level;
        } else if (level <= 31) {
            atLevel = Math.round(2.5 * level * level - 40.5 * level + 360);
        } else {
            atLevel = Math.round(4.5 * level * level - 162.5 * level + 2220);
        }
        return (int) (atLevel + Math.round(player.getExp() * player.getExpToLevel()));
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
                // Revert-on-quit too (F08): a logout mid-window otherwise persists mayfly forever. The quit drain
                // clears it before the save, costing an abuser only the tail of their own buff on a mid-window relog.
                revertLater(target, durationTicks, () -> clearTemporaryFlight(target));
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
                // buff before it elapses can never leak an inflated speed upward. Revert-on-quit too (F08): a
                // logout mid-window would otherwise persist the inflated abilities.walkSpeed to disk.
                revertLater(target, durationTicks, () -> target.setWalkSpeed(0.2f));
            }
        });
    }

    @Override
    public void invincible(LivingEntity target, int durationTicks) {
        entityOp(target, () -> {
            applyInvulnerable(target, true);
            if (durationTicks >= 0) {
                // Revert-on-quit too (F08): a logout mid-window otherwise persists the Invulnerable NBT forever.
                revertLater(target, durationTicks, () -> applyInvulnerable(target, false));
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
    public void markZone(Location center, UUID owner, UUID victim, double radius, int durationTicks) {
        if (center == null || owner == null || center.getWorld() == null) {
            return;
        }
        // Inline per-owner registry write (no entity hop): the centre was resolved on the firing thread, so
        // reading its world id + x/z here is region-correct. Consulted later by the %victim.inzone% fact; the
        // magma-immunity is scoped to `victim`.
        OwnerZones.mark(owner, center.getWorld().getUID(), center.getX(), center.getZ(),
                radius, durationTicks * 50L, victim); // ticks → ms
    }

    @Override
    public void reflectMark(Player afflicted, double percent, int durationTicks) {
        if (afflicted != null) {
            // Per-player window write, inline like mark() (the UUID is captured on the firing thread — no entity
            // hop, no cross-region read). Consulted by CombatDispatch when this player is a later attacker.
            reflectMarks.mark(afflicted.getUniqueId(), percent, nowTicks.getAsLong(), durationTicks);
        }
    }

    @Override
    public void weaken(Player target, double percent, int durationTicks) {
        if (target != null) {
            // Non-stacking outgoing-damage debuff; inline per-player write consulted on the target's later attack side.
            outgoingDebuff.weaken(target.getUniqueId(), percent, nowTicks.getAsLong(), durationTicks);
        }
    }

    @Override
    public void armDamageCap(Player target, double factor, boolean reflectOverflow, int durationTicks) {
        if (target != null) {
            // Cap value fixed AT ARM time from the wearer's last-taken damage (no history → value 0 → arms nothing).
            double value = damageCap.lastTaken(target.getUniqueId()) * factor;
            damageCap.arm(target.getUniqueId(), value, reflectOverflow, nowTicks.getAsLong(), durationTicks);
        }
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
    public void stripScroll(LivingEntity target, boolean holy, boolean includeHand) {
        // Runs on the target's own thread (entityOp): reading + writing its equipment is region-correct, and
        // the strip (marker release + lore recompose) mutates the stack BEFORE the write-back — worn arrays
        // may be copies, so a strip without setArmorContents/setMainHand would be silently lost.
        entityOp(target, () -> {
            EntityEquipment equipment = target.getEquipment();
            if (equipment == null) {
                return;
            }
            ItemStack[] worn = equipment.getArmorContents();
            ItemStack held = includeHand ? mainHand(equipment) : null;
            int[] slots = new int[worn.length + 1];
            int n = 0;
            for (int i = 0; i < worn.length; i++) {
                if (worn[i] != null && gearProtection.isProtected(worn[i], holy)) {
                    slots[n++] = i;
                }
            }
            if (held != null && gearProtection.isProtected(held, holy)) {
                slots[n++] = worn.length; // sentinel: the held item
            }
            if (n == 0) {
                return;
            }
            int pick = slots[ThreadLocalRandom.current().nextInt(n)];
            if (pick == worn.length) {
                if (gearProtection.strip(held, holy)) {
                    setMainHand(equipment, held);
                }
            } else if (gearProtection.strip(worn[pick], holy)) {
                equipment.setArmorContents(worn);
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
            if (!TempEquip.swap(target.getUniqueId(), slotIndex,
                    original == null ? null : original.clone(), placeholder)) {
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

    /**
     * Restore a swapped slot to its original. The original never vanishes (F21): if the victim re-equipped the
     * slot during the window we leave that item alone and hand the original back to their inventory (overflow
     * drops at their feet), reclaiming the one minted placeholder by material in either non-slot case.
     */
    private static void restoreSwap(Player target, UUID id, int slotIndex, Material placeholder) {
        TempEquip.Swap swap = TempEquip.end(id, slotIndex);
        if (swap == null) {
            return; // already ended (the death/quit listener restored it)
        }
        ItemStack original = TempEquip.isAir(swap.original()) ? null : swap.original();
        ItemStack[] armor = target.getInventory().getArmorContents();
        ItemStack inSlot = slotIndex < armor.length ? armor[slotIndex] : null;
        if (slotIndex < armor.length && inSlot != null && inSlot.getType() == placeholder) {
            armor[slotIndex] = original; // still our placeholder — swap it straight back for the original
            target.getInventory().setArmorContents(armor);
            return;
        }
        // The victim moved/removed the pumpkin: reclaim the one minted placeholder from wherever it landed.
        target.getInventory().removeItem(new ItemStack(placeholder, 1));
        if (original == null) {
            return; // the slot was empty pre-swap — nothing to give back
        }
        if (slotIndex < armor.length && TempEquip.isAir(inSlot)) {
            armor[slotIndex] = original; // the slot is free again — restore it to its natural home
            target.getInventory().setArmorContents(armor);
            return;
        }
        Inventories.giveOrDrop(target, original); // re-equipped with something else — never clobber it
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
            World world = origin.getWorld();
            if (world == null || count <= 0) {
                return;
            }
            for (int i = 0; i < count; i++) {
                Entity spawned = spawnTyped(world, origin, entityTypeId);
                if (spawned == null) {
                    return; // unresolvable type on this version — the §9 compile warn already fired
                }
                if (health > 0 && spawned instanceof LivingEntity living) {
                    applySpawnHealth(living, health);
                }
                if (ownerId != null) {
                    // ADR-0049: bind EVERY owned spawn to its owner so a hit on it fires GUARDIAN_HURT (Blood Link);
                    // era-agnostic (no entity PDC), independent of the Tameable tagging below.
                    GuardianCasts.bind(spawned.getUniqueId(), ownerId);
                    if (spawned instanceof Tameable tame) {
                        // Owned/tamed summon: resolve by the Tameable CAPABILITY (a stable interface across the
                        // range), never a volatile constant. setOwner accepts an offline AnimalTamer; tame so it sticks.
                        tame.setOwner(Bukkit.getOfflinePlayer(ownerId));
                        tame.setTamed(true);
                    }
                }
                bindTtlForget(spawned, ttlTicks);
            }
        });
    }

    @Override
    public void spawnSummon(Location at, int entityTypeId, int count, int ttlTicks, double health, UUID ownerId,
                            Player activator, SummonFlags flags) {
        if (flags == null || flags.none()) {
            spawnEntity(at, entityTypeId, count, ttlTicks, health, ownerId); // byte-stable plain path
            return;
        }
        Location origin = at.clone(); // own the spawn point: a WAIT tier can defer this to a later tick
        regionOp(origin, () -> {
            World world = origin.getWorld();
            if (world == null || count <= 0) {
                return;
            }
            for (int i = 0; i < count; i++) {
                Entity spawned = spawnTyped(world, origin, entityTypeId);
                if (spawned == null) {
                    continue; // unresolvable type on this version — the §9 compile warn already fired
                }
                if (health > 0 && spawned instanceof LivingEntity living) {
                    applySpawnHealth(living, health);
                }
                if (flags.powered() && spawned instanceof Creeper creeper) {
                    creeper.setPowered(true); // stable Bukkit API across the whole range incl. 1.8
                }
                if (flags.noAi() && spawned instanceof LivingEntity living) {
                    applyNoAi(living);
                }
                if (flags.saddled() && spawned instanceof LivingEntity living) {
                    applySaddle(living);
                }
                if (ownerId != null) {
                    GuardianCasts.bind(spawned.getUniqueId(), ownerId);
                    if (spawned instanceof Tameable tame) {
                        tame.setOwner(Bukkit.getOfflinePlayer(ownerId));
                        tame.setTamed(true);
                    }
                }
                if (flags.tracked()) {
                    // The summon-guard listener enforces no-target + hit-gated detonation from this registry.
                    PetSummons.bind(spawned.getUniqueId(), flags);
                }
                if (flags.mountActivator() && activator != null) {
                    try {
                        mountEntity(spawned, activator); // spawn is at the activator, so co-region in practice
                    } catch (RuntimeException unreadable) {
                        Regions.swallowed("DispatchSinkBase.spawnSummon.mount", unreadable);
                    }
                }
                bindSummonTtl(spawned, ttlTicks);
            }
        });
    }

    /** Auto-remove a flagged summon after {@code ttlTicks}, forgetting BOTH registries first. */
    private static void bindSummonTtl(Entity spawned, int ttlTicks) {
        if (ttlTicks > 0) {
            UUID spawnedId = spawned.getUniqueId();
            Scheduling.onEntityLater(spawned, ttlTicks, () -> {
                GuardianCasts.forget(spawnedId);
                PetSummons.forget(spawnedId);
                spawned.remove();
            });
        }
    }

    @Override
    public void guard(LivingEntity target, Location at, int entityTypeId, int count, int ttlTicks, String name, UUID owner) {
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
                if (owner != null) {
                    GuardianCasts.bind(spawned.getUniqueId(), owner); // ADR-0049: a hit on the guard fires the owner's GUARDIAN_HURT
                }
                bindTtlForget(spawned, ttlTicks);
            }
        });
    }

    /** Auto-remove {@code spawned} after {@code ttlTicks} (if positive), forgetting any GuardianCasts binding first. */
    private static void bindTtlForget(Entity spawned, int ttlTicks) {
        if (ttlTicks > 0) {
            UUID spawnedId = spawned.getUniqueId();
            Scheduling.onEntityLater(spawned, ttlTicks, () -> {
                GuardianCasts.forget(spawnedId); // harmless no-op for an unbound spawn
                spawned.remove();
            });
        }
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
    public void launchProjectile(Player shooter, int entityTypeId, int count, double speed, double explosiveYield,
                                 boolean incendiary) {
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
                // ADR-0049 Hellfire: an explosive projectile (fireball) gets a level-scaled blast + optional fire.
                // Guarded by the Explosive CAPABILITY (no Fireball class reference), so it is version-stable.
                if (entity instanceof org.bukkit.entity.Explosive explosive) {
                    if (explosiveYield >= 0) {
                        explosive.setYield((float) explosiveYield);
                    }
                    explosive.setIsIncendiary(incendiary);
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
            int typeId = material.ordinal();
            long now = nowTicks.getAsLong();
            UUID worldId = world.getUID();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int bx = cx + dx;
                    int bz = cz + dz;
                    Location tileAt = new Location(world, bx, y, bz);
                    // Re-key EACH tile to its OWN region: the canReplace read, the block set, the shared-ledger
                    // mutation and the revert must all run on the tile's owning thread — never origin's — or a
                    // tile in a neighbouring Folia region has its block AND its ledger Entry touched cross-region.
                    // On Paper onRegion is a direct inline call (unchanged); on Folia only straddle tiles hop.
                    Scheduling.onRegion(tileAt, () -> {
                        Block block = world.getBlockAt(bx, y, bz);
                        if (!canReplace(block, replaceMode)) {
                            return;
                        }
                        if (durationTicks <= 0) {
                            block.setType(material, false); // permanent — untracked (a trap relies on its duration)
                            return;
                        }
                        // Through the shared ledger with its own revert (like tempBlock), so an overlapping
                        // trail/floor compounds instead of clobbering; the revert rides THIS tile's region.
                        TempBlockLedger.Key key = new TempBlockLedger.Key(worldId, bx, y, bz);
                        TempBlockLedger.Pending pending = tempBlocks.place(key, typeId, durationTicks, now);
                        Scheduling.onRegionLater(tileAt, pending.delayTicks(),
                                () -> tempBlocks.revert(key, pending.layerId(), pending.seq(), nowTicks.getAsLong()));
                    });
                }
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
            if (durationTicks <= 0) {
                block.setType(material, false); // permanent — untracked (a trap relies on its duration)
                return;
            }
            // Route through the shared ledger: overlapping placements (a REPEATING trail over a DEFENSE floor)
            // stack as layers and the final revert restores the TRUE original, never an intermediate temp block.
            TempBlockLedger.Key key = new TempBlockLedger.Key(
                    world.getUID(), pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
            TempBlockLedger.Pending pending = tempBlocks.place(key, material.ordinal(), durationTicks, nowTicks.getAsLong());
            Scheduling.onRegionLater(pos, pending.delayTicks(),
                    () -> tempBlocks.revert(key, pending.layerId(), pending.seq(), nowTicks.getAsLong()));
        });
    }

    @Override
    public void tempBox(Location center, int materialId, int width, int height, int depth, int durationTicks,
                        int replaceMode) {
        Location origin = center.clone(); // own the centre: a WAIT tier can defer this to a later tick
        regionOp(origin, () -> {
            Material material = material(materialId);
            World world = origin.getWorld();
            if (material == null || !material.isBlock() || world == null || durationTicks <= 0) {
                return; // a permanent untracked box is never wanted — a trap must always revert
            }
            int baseY = origin.getBlockY();
            int cx = origin.getBlockX();
            int cz = origin.getBlockZ();
            int hx = (width - 1) / 2;
            int hz = (depth - 1) / 2;
            int typeId = material.ordinal();
            long now = nowTicks.getAsLong();
            UUID worldId = world.getUID();
            for (int dx = -hx; dx < width - hx; dx++) {
                for (int dz = -hz; dz < depth - hz; dz++) {
                    for (int dy = 0; dy < height; dy++) {
                        int bx = cx + dx;
                        int by = baseY + dy;
                        int bz = cz + dz;
                        Location tileAt = new Location(world, bx, by, bz);
                        // Re-key EACH tile to its OWN region (the tempPlatform rule): a 3-wide box may straddle
                        // a Folia region boundary, and the read, set, ledger mutation and revert must all run
                        // on the tile's owning thread.
                        Scheduling.onRegion(tileAt, () -> {
                            Block block = world.getBlockAt(bx, by, bz);
                            if (!canReplace(block, replaceMode)) {
                                return;
                            }
                            TempBlockLedger.Key key = new TempBlockLedger.Key(worldId, bx, by, bz);
                            TempBlockLedger.Pending pending = tempBlocks.place(key, typeId, durationTicks, now);
                            Scheduling.onRegionLater(tileAt, pending.delayTicks(),
                                    () -> tempBlocks.revert(key, pending.layerId(), pending.seq(), nowTicks.getAsLong()));
                        });
                    }
                }
            }
        });
    }

    @Override
    public void cage(Location base, LivingEntity first, LivingEntity second, int floorMaterialId,
                     int wallMaterialId, int roofMaterialId, int width, int height, int depth, int durationTicks) {
        Location origin = base.clone(); // own the base: a WAIT tier can defer this to a later tick
        regionOp(origin, () -> {
            Material floor = material(floorMaterialId);
            Material wall = material(wallMaterialId);
            Material roof = material(roofMaterialId);
            World world = origin.getWorld();
            if (floor == null || wall == null || roof == null || world == null || durationTicks <= 0) {
                return;
            }
            int cx = origin.getBlockX();
            int baseY = origin.getBlockY(); // interior floor level; the floor PLATE sits at baseY - 1
            int cz = origin.getBlockZ();
            int hx = (width - 1) / 2;
            int hz = (depth - 1) / 2;
            // Safety first: EVERY cell of the full structure volume (plates + ring + interior) must currently
            // be air, or nothing is placed and no one teleports. The volume read runs on the base's region; a
            // straddling read that faults on Folia aborts the cage rather than half-building it.
            try {
                for (int dx = -hx - 1; dx < width - hx + 1; dx++) {
                    for (int dz = -hz - 1; dz < depth - hz + 1; dz++) {
                        for (int dy = -1; dy <= height; dy++) {
                            if (!canReplace(world.getBlockAt(cx + dx, baseY + dy, cz + dz), 0)) {
                                return;
                            }
                        }
                    }
                }
            } catch (RuntimeException unreadable) {
                Regions.swallowed("DispatchSinkBase.cage.safetyCheck", unreadable);
                return;
            }
            long now = nowTicks.getAsLong();
            UUID worldId = world.getUID();
            for (int dx = -hx - 1; dx < width - hx + 1; dx++) {
                for (int dz = -hz - 1; dz < depth - hz + 1; dz++) {
                    boolean ring = dx < -hx || dx >= width - hx || dz < -hz || dz >= depth - hz;
                    for (int dy = -1; dy <= height; dy++) {
                        Material material;
                        if (dy == -1) {
                            material = floor; // full plate under interior AND ring, so the cage is sealed
                        } else if (dy == height) {
                            material = roof;
                        } else if (ring) {
                            material = wall;
                        } else {
                            continue; // the interior stays air
                        }
                        int bx = cx + dx;
                        int by = baseY + dy;
                        int bz = cz + dz;
                        int typeId = material.ordinal();
                        Location tileAt = new Location(world, bx, by, bz);
                        // Per-tile region re-key (the tempPlatform rule) — a 5-wide structure can straddle.
                        Scheduling.onRegion(tileAt, () -> {
                            Block block = world.getBlockAt(bx, by, bz);
                            if (!canReplace(block, 2)) { // checked-air raced into something: capture + restore it
                                return;
                            }
                            TempBlockLedger.Key key = new TempBlockLedger.Key(worldId, bx, by, bz);
                            TempBlockLedger.Pending pending = tempBlocks.place(key, typeId, durationTicks, now);
                            Scheduling.onRegionLater(tileAt, pending.delayTicks(),
                                    () -> tempBlocks.revert(key, pending.layerId(), pending.seq(), nowTicks.getAsLong()));
                        });
                    }
                }
            }
            // Opposite interior cells on the axis the two parties already stand along, facing each other.
            // Yaw: +X faces west (90), -X faces east (-90), +Z faces north (180), -Z faces south (0).
            double ax = 0;
            double az = 0;
            try {
                Location f = first.getLocation();
                Location s = second.getLocation();
                ax = f.getX() - s.getX();
                az = f.getZ() - s.getZ();
            } catch (RuntimeException unreadable) {
                Regions.swallowed("DispatchSinkBase.cage.axis", unreadable); // fall back to the X axis
            }
            boolean alongX = Math.abs(ax) >= Math.abs(az);
            int sideX = alongX ? (ax >= 0 ? 1 : -1) : 0; // first keeps the side nearer where it stood
            int sideZ = alongX ? 0 : (az >= 0 ? 1 : -1);
            // Hop each party to ITS OWN thread DIRECTLY (the transferExp rule): this body runs during (or
            // after) the plan's region pass, so the plan-backed teleport()/entityOp would append to an
            // already-dispatched batch and be silently dropped — never re-enter the flushed plan.
            cageTeleport(first, cell(world, cx + sideX, baseY, cz + sideZ, yawToward(-sideX, -sideZ)));
            cageTeleport(second, cell(world, cx - sideX, baseY, cz - sideZ, yawToward(sideX, sideZ)));
        });
    }

    /** Direct entity-thread teleport for the cage (anti-cheat-exempted); bypasses the per-event plan. */
    private void cageTeleport(LivingEntity target, Location dest) {
        Scheduling.onEntity(target, () -> {
            exemptMovement(target);
            teleportTo(target, dest);
        });
    }

    /** The centre of an interior cage cell, with a facing yaw and level pitch. */
    private static Location cell(World world, int x, int y, int z, float yaw) {
        return new Location(world, x + 0.5, y, z + 0.5, yaw, 0f);
    }

    /** The yaw that looks along {@code (dx, dz)} — Bukkit yaw: 0 = +Z (south), 90 = -X (west). */
    private static float yawToward(int dx, int dz) {
        if (dx > 0) {
            return -90f; // east
        }
        if (dx < 0) {
            return 90f;  // west
        }
        return dz > 0 ? 0f : 180f; // south / north
    }

    @Override
    public void tempBlockTrail(int trailKeyDefId, UUID walker, Location currentCell, int materialId, int durationTicks) {
        Location pos = currentCell.clone(); // own the cell: a WAIT tier can defer this to a later tick
        // The walk (memory read/write + staircase) runs on the WALKER's own region — regionOp targets the
        // current cell, and a REPEATING trigger fires on the wearer's region, so one key has a single writer.
        regionOp(pos, () -> {
            Material material = material(materialId);
            World world = pos.getWorld();
            if (material == null || !material.isBlock() || world == null || walker == null) {
                return;
            }
            int typeId = material.ordinal();
            UUID worldId = world.getUID();
            for (TrailWalker.Step cell : trails.walk(trailKeyDefId, walker, worldId,
                    pos.getBlockX(), pos.getBlockY(), pos.getBlockZ(), nowTicks.getAsLong())) {
                int bx = cell.x();
                int bz = cell.z();
                int baseY = cell.y();
                // Re-key EACH cell to its OWN region: the ground-snap reads, the block set, the shared-ledger
                // mutation and the revert must all run on the cell's owning thread — a trail line may straddle a
                // Folia region boundary. On Paper onRegion is a direct inline call; on Folia only straddlers hop.
                Location cellAt = new Location(world, bx, baseY, bz);
                Scheduling.onRegion(cellAt, () -> {
                    int y = TrailWalker.snapY(baseY,
                            canReplace(world.getBlockAt(bx, baseY, bz), 3),
                            canReplace(world.getBlockAt(bx, baseY + 1, bz), 3),
                            canReplace(world.getBlockAt(bx, baseY - 1, bz), 3));
                    if (y == TrailWalker.SKIP) {
                        return; // a jump over air — pause the snake, never scaffold
                    }
                    // Solid ground only (mode 3), captured + restored on revert; through the shared ledger so a
                    // trail crossing a magma floor compounds and the final revert restores the true original.
                    TempBlockLedger.Key key = new TempBlockLedger.Key(worldId, bx, y, bz);
                    TempBlockLedger.Pending pending = tempBlocks.place(key, typeId, durationTicks, nowTicks.getAsLong());
                    Location revertAt = new Location(world, bx, y, bz);
                    Scheduling.onRegionLater(revertAt, pending.delayTicks(),
                            () -> tempBlocks.revert(key, pending.layerId(), pending.seq(), nowTicks.getAsLong()));
                });
            }
        });
    }

    /** The temp-block replace gate, consulting the LIVE block; the decision is single-sourced in the ledger. */
    private boolean canReplace(Block block, int replaceMode) {
        Material type = block.getType();
        return TempBlockLedger.replaceable(replaceMode, isAir(type), block.isLiquid(), type.isSolid());
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
            platform.item.Inventories.giveOrDrop(target, stack);
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
    public void fallingBlock(Location at, int materialId, int ttlTicks, UUID owner, UUID target, double carriedDamage) {
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
            FallingBlockCasts.bind(fb.getUniqueId(), owner, target, carriedDamage);
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

    @Override
    public void playerCommand(Player actor, String command) {
        // Runs on the actor's own thread (entityOp): performCommand dispatches as the player, region-correct on
        // Folia. performCommand exists on the 1.8 floor API, so this stays in the shared base.
        entityOp(actor, () -> actor.performCommand(command));
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
    public void transferMoney(Player from, Player to, double amount) {
        if (from == null || to == null || amount <= 0) {
            return;
        }
        UUID fromId = from.getUniqueId();
        UUID toId = to.getUniqueId();
        // Clamp to the victim's live balance BEFORE the withdraw so the (all-or-nothing) withdraw succeeds and
        // the deposit equals exactly what was charged — a broke victim moves nothing rather than minting the
        // full amount. Read-balance + withdraw + deposit in ONE global-thread task so no money op interleaves.
        globalOp(() -> {
            double amt = Math.min(amount, economy.balance(fromId));
            if (amt > 0 && economy.withdraw(fromId, amt)) {
                economy.deposit(toId, amt);
            }
        });
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

    @Override
    public void interestMoneyPercent(Player target, double fraction) {
        if (target == null || fraction <= 0) {
            return;
        }
        UUID targetId = target.getUniqueId();
        double frac = Math.min(1.0, fraction);
        // Read-balance + deposit in ONE global-thread task so no other money op interleaves. Income, not a
        // transfer: the deposit MINTS money. The live cap is read per use so /se reload re-tunes it.
        globalOp(() -> {
            double amount = economy.balance(targetId) * frac;
            double cap = moneyInterestCap.getAsDouble();
            if (cap > 0) {
                amount = Math.min(amount, cap);
            }
            if (amount > 0) {
                economy.deposit(targetId, amount);
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
        suppress(target, scopeKind, scopeId, durationTicks, -1);
    }

    @Override
    public void suppress(Player target, int scopeKind, int scopeId, int durationTicks, int byDefId) {
        if (target == null || scopeId < 0) {
            return;
        }
        // Per-player in-memory state keyed by the (scopeKind, scopeId) cooldown-scope packing — the same
        // key gate 5 reads for the suppressed abilities. The store is concurrent, so writing it on the
        // firing thread is Folia-safe (only the target's UUID is captured; no cross-region entity read).
        // byDefId attributes the window to the emitting DISABLE_* ability (ADR-0045: /se why names it).
        suppression.suppress(target.getUniqueId(), CooldownStore.key(scopeKind, scopeId),
                nowTicks.getAsLong(), durationTicks, byDefId);
    }

    @Override
    public void suppress(Player target, int scopeKind, int scopeId, int durationTicks, int byDefId,
                         boolean nextHit, int charges) {
        if (target == null || scopeId < 0) {
            return;
        }
        long key = CooldownStore.key(scopeKind, scopeId);
        if (nextHit) {
            // ADR-0049 Neutralize: an event-scoped one-shot the combat dispatcher burns after each hit, not by time.
            suppression.armOneShot(target.getUniqueId(), key, charges, byDefId);
        } else {
            suppression.suppress(target.getUniqueId(), key, nowTicks.getAsLong(), durationTicks, byDefId);
        }
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

    @Override
    public void requestEchoStrike() {
        echoRequested = true; // inline read-back: the combat dispatcher re-runs the attacker walk once
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

    /** Whether {@code entity} exposes a max-health attribute this era can shift (else add/drain is a no-op). */
    protected abstract boolean hasMaxHealthAttribute(LivingEntity entity);

    /** The BASE (pre-modifier) max-health — modern {@code AttributeInstance.getBaseValue}; 1.8 NMS {@code getValue}. */
    protected abstract double maxHealthBase(LivingEntity entity);

    /** Set the BASE max-health — modern {@code AttributeInstance.setBaseValue}; 1.8 NMS {@code setValue}. */
    protected abstract void setMaxHealthBase(LivingEntity entity, double value);

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

    /**
     * Spawn one entity of an interned type at {@code at}, or {@code null} when the type does not resolve on
     * this version. The legacy leaf overrides it for types 1.8 spawns under a different shape (ADR-0052:
     * SKELETON_HORSE = a HORSE with the skeleton variant); the default is the plain resolved spawn.
     */
    protected Entity spawnTyped(World world, Location at, int entityTypeId) {
        EntityType type = entityType(entityTypeId);
        return type == null ? null : world.spawnEntity(at, type);
    }

    /** Disable a summon's AI (ADR-0052): modern {@code setAI(false)}; 1.8 via the NMS NoAI tag leaf. */
    protected abstract void applyNoAi(LivingEntity entity);

    /** Seat {@code passenger} on {@code vehicle} (ADR-0052): modern {@code addPassenger}; 1.8 {@code setPassenger}. */
    protected abstract void mountEntity(Entity vehicle, Entity passenger);

    /** Saddle a horse-type summon so it is steerable (ADR-0052); a no-op on a non-horse. */
    protected abstract void applySaddle(LivingEntity entity);

    /** Spawn a cosmetic falling block of {@code material} at {@code loc} (block-data on modern; data byte on 1.8). */
    protected abstract FallingBlock spawnFallingBlock(World world, Location loc, Material material);

    /** Create an explosion honouring {@code breakBlocks}, never fire (the overload with a block-break flag differs by era). */
    protected abstract void doExplosion(World world, Location at, double power, boolean breakBlocks);
}

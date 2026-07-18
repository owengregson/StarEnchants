package engine.sink;

import compile.model.ScopeKinds;
import engine.interact.DamageFold;
import engine.stores.BatteryStore;
import engine.stores.CooldownStore;
import engine.stores.DamageCapStore;
import engine.stores.DisarmWindowStore;
import engine.stores.HitTempoStore;
import engine.stores.ImmuneStore;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.OutgoingDebuffStore;
import engine.stores.RecentAttackersStore;
import engine.stores.ReflectMarksStore;
import engine.stores.SuppressionStore;
import engine.stores.TeleblockStore;
import engine.stores.VarStore;
import engine.stores.WardStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.ToDoubleFunction;
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
import org.bukkit.entity.Bat;
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

    /** The ONE plugin-owned worn max-health modifier's stable identity — text-derived, era-shared, so both
     *  overlays and any future migration find the same modifier in old playerdata. */
    protected static final UUID WORN_MAX_HEALTH_ID =
            UUID.nameUUIDFromBytes("starenchants:worn_max_health".getBytes(StandardCharsets.UTF_8));
    protected static final String WORN_MAX_HEALTH_NAME = "starenchants.worn_max_health";
    /** The worn water-speed modifier's stable identity (ADR-0060) — the WORN_MAX_HEALTH twin. */
    protected static final UUID WORN_WATER_SPEED_ID =
            UUID.nameUUIDFromBytes("starenchants:worn_water_speed".getBytes(StandardCharsets.UTF_8));
    protected static final String WORN_WATER_SPEED_NAME = "starenchants.worn_water_speed";
    /** The frozen-window slow modifier's identity (FREEZE, ADR-0065) — MULTIPLY_SCALAR_1, −slow/100. */
    protected static final UUID FROZEN_SLOW_ID =
            UUID.nameUUIDFromBytes("starenchants:frozen_slow".getBytes(StandardCharsets.UTF_8));
    protected static final String FROZEN_SLOW_NAME = "starenchants.frozen_slow";
    /** The vanilla-frost offset's identity (FREEZE, ADR-0065) — ADD_NUMBER +0.05, cancels tryAddFrost. */
    protected static final UUID FROZEN_FROST_OFFSET_ID =
            UUID.nameUUIDFromBytes("starenchants:frozen_frost_offset".getBytes(StandardCharsets.UTF_8));
    protected static final String FROZEN_FROST_OFFSET_NAME = "starenchants.frozen_frost_offset";
    /** Unlocked-pin slack: outruns aiStep's −2/tick decay so the SYNCED value is exactly max (§1). */
    protected static final int FREEZE_PIN_SLACK = 2;
    /** The Quickening attack-speed modifier's identity (HIT_TEMPO, ADR-0071) — ADD_SCALAR, the 1.9+ swing meter. */
    protected static final UUID REFORGE_TEMPO_ID =
            UUID.nameUUIDFromBytes("starenchants:reforge_tempo".getBytes(StandardCharsets.UTF_8));
    protected static final String REFORGE_TEMPO_NAME = "starenchants.reforge_tempo";

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
    private final WardStore ward; // ADR-0053 mask ward flags
    private final ReflectMarksStore reflectMarks;   // ADR-0049 Hex reflect windows
    private final OutgoingDebuffStore outgoingDebuff; // ADR-0049 Weaken/Destruction outgoing nerfs
    private final DamageCapStore damageCap;          // ADR-0049 Diminish last-taken + armed cap
    private final RecentAttackersStore recentAttackers; // ADR-0068 bat-cloud arm-time seed
    /** The ONE per-boot ledger (via {@link SinkEnv}), so temp blocks from separate events compound, not clobber. */
    private final TempBlockLedger<BlockState> tempBlocks;
    /** The ONE per-boot trail memory (via {@link SinkEnv}), so the footprint snake connects across activations. */
    private final TrailWalker trails;
    /** The ONE per-boot timed-revert registry (via {@link SinkEnv}), so the quit drain can restore a logout-stranded buff. */
    private final TimedRevert timedReverts;
    /** The ONE per-boot combo-DoT park ledger (via {@link SinkEnv}); banks a mid-combo hurt instead of applying it (ADR-0069). */
    private final DotParkLedger dotPark;
    /** LIVE ceiling on one {@code interest_percent} deposit ({@code <= 0} = uncapped) — ADR-0052 Fish. */
    private final java.util.function.DoubleSupplier moneyInterestCap;
    /** The scroll-marker seam {@code STRIP_SCROLL} mutates victim gear through (ADR-0052 Anubis). */
    private final GearProtection gearProtection;
    /** The worn LIGHTNING_MOD channel (ADR-0063): actor UUID → summed boost fraction, read per bolt emit. */
    private final ToDoubleFunction<UUID> lightningBoost;
    /** Quickening tempo windows + stolen-interval stamps (ADR-0071 HIT_TEMPO). */
    private final HitTempoStore hitTempoStore;
    /** Supernova cores (ADR-0071 BATTERY). */
    private final BatteryStore batteryStore;
    /** Unhanding armed windows (ADR-0071 DISARM_SHUFFLE). */
    private final DisarmWindowStore disarmWindowStore;
    /** The ONE per-boot confining-structure registry (via {@link SinkEnv}), so Turnkey can early-restore a trap. */
    private final TrapStructures trapStructures;

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
        this.ward = env.stores().ward();
        this.reflectMarks = env.stores().reflectMarks();
        this.outgoingDebuff = env.stores().outgoingDebuff();
        this.damageCap = env.stores().damageCap();
        this.recentAttackers = env.stores().recentAttackers();
        this.nowTicks = env.nowTicks();
        this.movementExemption = env.movementExemption();
        this.tempBlocks = env.tempBlocks();
        this.trails = env.trails();
        this.timedReverts = env.timedReverts();
        this.dotPark = env.dotPark();
        this.moneyInterestCap = env.moneyInterestCap();
        this.gearProtection = env.gearProtection();
        this.lightningBoost = env.lightningBoost();
        this.hitTempoStore = env.stores().hitTempo();
        this.batteryStore = env.stores().battery();
        this.disarmWindowStore = env.stores().disarmWindows();
        this.trapStructures = env.trapStructures();
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

    @Override
    public void addHeroicReduction(double percent) {
        fold.addHeroicReduction(percent);
    }

    @Override
    public void addHeroicFlatReduction(double amount) {
        fold.addHeroicFlatReduction(amount);
    }

    // ── Entity intents ───────────────────────────────────────────────────────────────────────────

    @Override
    public void damage(LivingEntity target, double amount, LivingEntity attacker) {
        if (target == null) {
            return;
        }
        if (delayTicks <= 0 && isEventEntity(target)) {
            // Same-hit rider (ADR-0054): a zero-WAIT DAMAGE aimed at the event's own entity joins the
            // damage fold instead of issuing a second hurt(). A bare second hurt re-arms the victim's
            // vanilla immunity window (noDamageTicks/lastHurt), so the NEXT melee with a smaller amount
            // is window-rejected with NO event at all — silently eating other plugins' per-hit handling
            // (hit cosmetics, knockback delivery). Folding rides the one event: one hurt, one immunity
            // window, one knockback. The fold is committed by the combat dispatcher after the walks, so
            // a rider on a dodged/cancelled hit dies with its hit — same-hit means same fate.
            // EFFECTIVE units (ADR-0055): the authored amount is what the old bare hurt delivered
            // pre-armor — never the scaled flat bucket, whose attack-scale ride made riders land ~5x.
            fold.addEffectiveDamage(amount);
            return;
        }
        entityOp(target, () -> hurtOrPark(target, amount, attacker));
    }

    @Override
    public void damagePercentOfMax(LivingEntity target, double percentOfMax, LivingEntity attacker) {
        if (target == null || percentOfMax <= 0) {
            return;
        }
        if (delayTicks <= 0 && isEventEntity(target)) {
            // Same-hit rider (ADR-0054, as damage above; EFFECTIVE units per ADR-0055). The firing thread
            // owns the event entity by definition (the ADR-0051 argument), so the max-health read is
            // region-correct inline.
            fold.addEffectiveDamage(maxHealth(target) * percentOfMax / 100.0);
            return;
        }
        // The max-health read and the damage both run on the target's own thread (entityOp) — never a cross-region
        // max-health read. Uses the era-adaptive maxHealth() leaf, so it is version-stable.
        entityOp(target, () -> hurtOrPark(target, maxHealth(target) * percentOfMax / 100.0, attacker));
    }

    /**
     * The one place StarEnchants itself hurts an entity (ADR-0054). Attributed to {@code attacker} when
     * one is in scope, so downstream plugins (era combat, logging, regions) see a real
     * {@code EntityDamageByEntityEvent} with the responsible party instead of an ownerless CUSTOM hurt;
     * bare only when no attacker exists or the attacker IS the target (self-attribution reads as a
     * self-hit and is dropped by combat dispatchers anyway). Runs on the target's owning thread (the
     * plan routed it there), and the attacker handle is only handed to vanilla as the damage source —
     * never dereferenced here — so a cross-region attacker is safe on Folia. The {@link EngineDamage}
     * frame preserves the old bare-damage re-entrancy mechanism: SE's own combat pipeline stands down
     * for the events this call fires.
     */
    private static void hurt(LivingEntity target, double amount, LivingEntity attacker) {
        EngineDamage.hurt(target, amount, attacker);
    }

    /**
     * ADR-0069: an engine hurt that does not ride its own triggering hit is BANKED while Mental holds an
     * active combo on a player target — a mid-combo hurt re-arms the vanilla immunity window (era rule:
     * the next melee lands difference-only with NO knock), starving Mental's shipped-knock combo feed, and
     * an attributed one is melee-shaped by Mental (a phantom knock + tracker takeover). Parked damage joins
     * the victim's next real hit via the fold (CombatDispatch) or the combo-end paced release
     * (ComboDotRelease). Continuation-only: Mental exposes no developing-chain signal, so the formation
     * window before ComboStart is unprotected (ADR-0069 §limitation). Runs on the target's owning thread
     * (the plan routed it here), so the ledger write is region-correct; a non-player target or no active
     * combo applies normally.
     */
    private void hurtOrPark(LivingEntity target, double amount, LivingEntity attacker) {
        if (amount > 0 && target instanceof Player p
                && dotPark.tryPark(p.getUniqueId(),
                        attacker != null && !attacker.equals(target) ? attacker : null,
                        amount, nowTicks.getAsLong())) {
            return;
        }
        hurt(target, amount, attacker);
    }

    @Override
    public void heal(LivingEntity target, double amount) {
        healthWrite(target, () -> target.setHealth(Math.min(target.getHealth() + amount, maxHealth(target))));
    }

    @Override
    public void setHealth(LivingEntity target, double health) {
        healthWrite(target, () -> {
            if (health < target.getHealth() && invincibleSummon(target)) {
                return; // ADR-0052 invincible summon: health-space damage may not lower it (raises stay fine)
            }
            target.setHealth(Math.max(0.0, Math.min(health, maxHealth(target))));
        });
    }

    @Override
    public void addMaxHealth(LivingEntity target, double amount) {
        entityOp(target, () -> {
            // Shifts the base value directly — permanent (no restore channel). Worn HEALTH bonuses never land
            // here: the MaxHealthDriver reconciles them through applyWornMaxHealth's keyed modifier instead.
            if (hasMaxHealthAttribute(target)) {
                setMaxHealthBase(target, Math.max(1.0, maxHealthBase(target) + amount));
            }
        });
    }

    @Override
    public void applyWornMaxHealth(Player target, double total) {
        entityOp(target, () -> {
            if (!hasMaxHealthAttribute(target)) {
                return;
            }
            setWornMaxHealthModifier(target, Math.max(0.0, total));
            if (target.getHealth() > maxHealth(target)) {
                target.setHealth(Math.max(0.0, maxHealth(target))); // clamp current down when the bonus shrank
            }
        });
    }

    @Override
    public void applyWornWaterSpeed(Player target, double total) {
        entityOp(target, () -> setWornWaterSpeedModifier(target, Math.max(0.0, Math.min(1.0, total))));
    }

    @Override
    public void drainMaxHealth(LivingEntity target, double fraction, double baseline, double flat, int durationTicks) {
        entityOp(target, () -> {
            if (!hasMaxHealthAttribute(target)) {
                return;
            }
            // Overhealth is measured on the EFFECTIVE max (base + modifiers), so overhealth that lives in a
            // MODIFIER — a HEALTH_BOOST potion (overload / nature crystal), a named "+hearts" modifier
            // (santa-hat-style) — is seen and taken, not just base shifts (the old base-only read left every
            // modifier-sourced heart untouched: grim/cupid "removed overhealth" but the hearts stayed). The removal
            // is a temporary NEGATIVE max-health modifier, never a base write: a base reduction floors at 1 so it
            // cannot fully offset a large HEALTH_BOOST, and it would fight the WornState base restore; a
            // counter-modifier lowers the effective cap by exactly `drain` whatever the source, and its removal is an
            // exact, idempotent give-back.
            double effective = maxHealth(target);
            double overhealth = effective - baseline;
            double drain = overhealth * fraction + flat;
            drain = Math.min(drain, effective - 1.0); // keep at least half a heart of max — never drain to death
            if (drain <= 0) {
                return; // no overhealth to take
            }
            UUID id = UUID.randomUUID(); // unique per drain → overlapping drains (two Lovestruck hits) each revert alone
            addMaxHealthModifier(target, id, "starenchants:maxhealth_drain", -drain);
            if (target.getHealth() > maxHealth(target)) {
                target.setHealth(Math.max(0.0, maxHealth(target))); // clamp current down to the new cap
            }
            if (durationTicks > 0) {
                // Restore-on-quit too (F07): a victim who combat-logs mid-drain gets the modifier removed BEFORE the
                // playerdata save, so the reduction can never be made permanent by pressuring a log-out — and no
                // permanent max-health modifier ever leaks onto disk. removeMaxHealthModifier is idempotent + exact.
                revertLater(target, durationTicks, () -> removeMaxHealthModifier(target, id));
            }
        });
    }

    @Override
    public void kill(LivingEntity target) {
        entityOp(target, () -> {
            if (invincibleSummon(target)) {
                return; // ADR-0052: KILL (slayer-style execute) may not end an invincible summon
            }
            target.setHealth(0.0);
        });
    }

    /**
     * ADR-0052 INVINCIBLE covers health-space too: the summon-guard listener zeroes every damage EVENT, but
     * {@code kill}/{@code setHealth} never fire one — without this consult a KILL or MODIFY_HEALTH:set slips
     * past the guard and executes the "invincible" summon.
     */
    private static boolean invincibleSummon(LivingEntity target) {
        SummonFlags flags = PetSummons.flags(target.getUniqueId());
        return flags != null && flags.invincible();
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
            // Register the DENIAL window: the modern EntityPotionEffectEvent guard (PotionLockListener) reads this
            // and CANCELS every re-application for the window, so a passive driver re-asserting the buff each tick
            // can never make it stick — the fix for the "flashes in and out" glitch (a same-tick re-strip always
            // lost one tick to the driver). Keyed by the version-stable type name; self-evicts at the deadline.
            LockedPotions.lock(target.getUniqueId(), type.getName(), durationTicks * 50L); // ticks → ms
            // Backstop the guard with the original per-tick re-strip: on 1.8.9 (no EntityPotionEffectEvent) it is the
            // SOLE enforcer, and on modern it belts any application the guard's ADDED/CHANGED/REFRESH cancel misses.
            // The locked set is tiny; removePotionEffect on an absent effect is a cheap no-op. The handle is captured
            // so both the window backstop and an early world-exit cancel it — the Paper timer is not entity-tied (it
            // would otherwise re-strip a logged-out player), while on Folia the entity task stops on its own.
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
    public void freeze(LivingEntity target, int durationTicks, double dotPerTick, int dotPeriodTicks,
                       double slowPercent, boolean neutralizeFrostSlow, LivingEntity attacker) {
        if (target == null || durationTicks <= 0) {
            return;
        }
        int period = Math.max(1, dotPeriodTicks);
        entityOp(target, () -> {
            UUID victim = target.getUniqueId();
            // Wall-clock EXPECTATION of the window, read only by the pin task + damage guard (isFrozen);
            // the DoT cadence and the teardown run in TICK space (the chain below claims a tick budget),
            // so wall/tick drift — catch-up bursts, sustained lag — never adds or drops a DoT tick.
            long deadlineMs = System.currentTimeMillis() + durationTicks * 50L;
            UUID attackerId = attacker != null ? attacker.getUniqueId() : null;
            if (FrozenTargets.refresh(victim, durationTicks, deadlineMs, attackerId, attacker)) {
                return; // refresh-not-stack (owner rule): the live chain reads the extended tick budget
            }
            long gen = FrozenTargets.arm(victim, durationTicks, deadlineMs, attackerId, attacker);
            // EVERY victim rides Paper's freeze-tick LOCK where it exists (guards vanilla's decay AND
            // the burning-entity clear, §1.1/§1.2 — fire coexistence with zero per-tick work); an
            // unlocked re-pin can never hold under fire, because it runs before the entity ticks and
            // the unguarded clear re-zeroes it (+ the 1009 hiss) every tick. freezeLocked persists to
            // NBT (§1.6): a victim unloaded mid-window is un-stranded at entity load by the modern
            // guard listener's reconcile, not by refusing mobs the lock.
            boolean needsPin = freezeVisualStart(target);
            applyFrozenSlow(target, slowPercent, neutralizeFrostSlow);
            TaskHandle[] tasks = new TaskHandle[2];
            Runnable teardown = () -> {
                // Idempotent + generation-guarded: quit drain, natural expiry, death, and the disable
                // sweep may all reach here; only the first run of the CURRENT window acts.
                if (tasks[0] != null) {
                    tasks[0].cancel();
                }
                if (tasks[1] != null) {
                    tasks[1].cancel();
                }
                freezeVisualEnd(target);
                removeFrozenSlow(target);
                FrozenTargets.disarm(victim, gen);
            };
            FrozenTargets.onTeardown(victim, gen, teardown);
            // Quit-safety (players): registered like every timed buff, so a logout mid-window unlocks
            // and strips the modifiers BEFORE the playerdata save (F07/F08).
            long token = -1L;
            if (target instanceof Player) {
                token = timedReverts.begin(victim, teardown);
            }
            long claimed = token;
            if (needsPin) {
                // Unlocked pin (the lock-less 1.17.1 floor): +SLACK outruns the −2/tick decay so the
                // synced value is exactly max (§1.1). Skipped while burning — baseTick would zero it and
                // replay the 1009 extinguish hiss every tick (§1.2); the visual drops until the fire ends.
                tasks[1] = Scheduling.repeatingEntity(target, 1L, 1L, () -> {
                    if (target.isValid() && FrozenTargets.isFrozen(victim, System.currentTimeMillis())) {
                        freezePin(target);
                    }
                });
            }
            tasks[0] = Scheduling.repeatingEntity(target, period, period, () -> {
                // Claim this period slot against the window's TICK budget (boundary-inclusive): the final
                // in-budget slot lands its hurt and tears the window down in the SAME run, so the boundary
                // tick always lands and teardown never races it — identical on every version and on Folia.
                FrozenTargets.Window w = FrozenTargets.chainTick(victim, gen, period);
                if (w != null && target.isValid() && !target.isDead()) {
                    if (dotPerTick > 0) {
                        // ADR-0054 deferred attributed hurt (the bleed path): EngineDamage-framed, so the
                        // combat dispatch stands down (no proc walks, no ReHitGuard stamp, no rage advance)
                        // and downstream sees a real killer-typed event (kill credit; Phoenix runs inline).
                        hurtOrPark(target, dotPerTick, w.attacker());
                    }
                    if (w.hasNextSlot(period)) {
                        return;
                    }
                }
                if (claimed >= 0) {
                    timedReverts.runOnce(victim, claimed); // claim-or-noop: the quit drain may have run it
                } else {
                    teardown.run();
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

    // ── ADR-0071 reforge combat-state intents (Plan C) ──

    @Override
    public void hitTempo(Player holder, int durationTicks, int windowModel, double damageFactor,
                         double attackSpeedBonus) {
        if (holder == null) {
            return;
        }
        // Arm the store INLINE — a thread-free UUID-keyed map write (the armBattery/armDisarmShuffle shape).
        // The combat dispatcher consults this window on the holder's very next melee hit; a plan-deferred
        // entityOp lands NEXT TICK on Folia (the entity scheduler never runs inline), leaving the window
        // invisible for the hit it is meant to tax. Only the 1.9+ attack-speed modifier + its TimedRevert
        // genuinely need the holder's region thread (attribute mutation), so that alone hops via entityOp.
        hitTempoStore.arm(holder.getUniqueId(), nowTicks.getAsLong(), durationTicks, windowModel, damageFactor);
        if (attackSpeedBonus > 0) {
            entityOp(holder, () -> {
                applyTempoAttackSpeed(holder, attackSpeedBonus);
                revertLater(holder, durationTicks, () -> clearTempoAttackSpeed(holder));
            });
        }
    }

    @Override
    public void armBattery(Player holder, double bankFraction, int maxHits) {
        if (holder != null) {
            // Inline per-player core write (the mark() shape); the strike side banks incoming / spends on hit.
            batteryStore.arm(holder.getUniqueId(), bankFraction, maxHits);
        }
    }

    @Override
    public void armDisarmShuffle(Player holder, int durationTicks, double malusFraction) {
        if (holder != null) {
            // Inline per-player one-shot window write; consumed at the landed hit (the mark() shape).
            disarmWindowStore.arm(holder.getUniqueId(), nowTicks.getAsLong(), durationTicks, malusFraction);
        }
    }

    @Override
    public void convertSummons(Player ringer, double radius) {
        if (ringer == null) {
            return;
        }
        // A CONTEXT_LOCAL self ability: this already runs on the ringer's OWN region thread, so enumerate +
        // rebind INLINE — ownership must be visible the instant the bell rings, and a plan-deferred entityOp
        // lands NEXT TICK on Folia (the entity scheduler never runs inline), leaving a converted guard still
        // registered to its old owner when the caller reads back. Snapshot the ring once: the former owner a
        // bell flips a summon onto is by construction within that ring, so resolve them REGION-LOCALLY from
        // this same list (Bukkit.getPlayer misses an actor absent from the online list — a fake/clientless
        // player, an owner mid-relog). Each per-summon entity mutation still hops to the summon's own scheduler.
        UUID ringerId = ringer.getUniqueId();
        List<Entity> nearby = new ArrayList<>(ringer.getNearbyEntities(radius, radius, radius));
        for (Entity near : nearby) {
            UUID id = near.getUniqueId();
            if (near instanceof Bat) {
                // Swarm bats carry no GuardianCasts entry — cloud MEMBERSHIP decides (region-free UUID match).
                SwarmClouds.turnByBat(ringerId, id);
                continue;
            }
            UUID owner = GuardianCasts.owner(id);
            if (owner == null || owner.equals(ringerId) || !(near instanceof LivingEntity)) {
                continue; // a wild/unowned spawn, or already ours
            }
            GuardianCasts.bind(id, ringerId); // ownership + GUARDIAN_HURT flip (concurrent map), visible now
            LivingEntity former = formerOwner(owner, nearby); // in-ring first, else the online player list
            SummonFlags flags = PetSummons.flags(id);
            boolean retarget = former != null && (flags == null || !flags.noTarget());
            // Hop each summon to its OWN scheduler (the cross-entity rule); the former reference is only stored.
            Scheduling.onEntity(near, () -> {
                if (near instanceof Tameable tame) {
                    tame.setOwner(ringer);
                    tame.setTamed(true);
                }
                if (retarget) {
                    setGuardTarget(near, former); // target the FORMER owner (the era leaf guard() uses)
                    holdConvertedTarget(near, former); // and HOLD it against modern AI revalidation (ADR-0071)
                }
            });
        }
    }

    /**
     * Hold a bell-converted summon on its forced {@code target} against modern mob-AI target-goal
     * revalidation (ADR-0071). A manually set target with no anger/revenge memory behind it — a player
     * is not a natural golem/guard/mount target — is dropped within a few ticks on 1.20.5+, so the
     * converted summon would stop attacking its former owner almost immediately (the 1.17.1 floor
     * happens to retain it, which is why only modern regressed). Re-assert the target each tick on the
     * summon's OWN scheduler for a bounded window, then release to natural AI — the summon durably
     * turns on its former owner on every era. Region-safe: {@link #setGuardTarget} only stores the
     * reference (no cross-region read) and the liveness check is region-local; a no-op reinforcement
     * where the target already sticks. Bounded (the summon's TTL outlives it — never a leaked task) and
     * self-cancelling once the summon is gone (the freeze/potion-lock idiom).
     */
    private void holdConvertedTarget(Entity summon, LivingEntity target) {
        TaskHandle[] handle = new TaskHandle[1];
        handle[0] = Scheduling.repeatingEntity(summon, 1L, 1L, () -> {
            if (!summon.isValid()) {
                if (handle[0] != null) {
                    handle[0].cancel();
                }
                return;
            }
            setGuardTarget(summon, target); // re-store the reference the AI keeps dropping
        });
        Scheduling.onEntityLater(summon, CONVERTED_TARGET_HOLD_TICKS, () -> {
            if (handle[0] != null) {
                handle[0].cancel(); // release to natural AI at the window's close
            }
        });
    }

    /**
     * The former owner as a live target for a converted summon (ADR-0071): the in-ring entity carrying that
     * UUID (region-local — robust for an actor absent from the online player list, e.g. a fake/clientless
     * player), else the online player of that UUID (an owner who summoned from outside the bell's ring).
     */
    private static LivingEntity formerOwner(UUID owner, List<Entity> nearby) {
        for (Entity near : nearby) {
            if (near.getUniqueId().equals(owner) && near instanceof LivingEntity living) {
                return living;
            }
        }
        return Bukkit.getPlayer(owner);
    }

    @Override
    public void breakTraps(Player actor) {
        if (actor == null) {
            return;
        }
        entityOp(actor, () -> {
            Location at = actor.getLocation(); // own thread — inline read
            World world = at.getWorld();
            if (world == null) {
                return;
            }
            UUID worldId = world.getUID();
            List<TrapStructures.Structure> confining = trapStructures.confining(
                    actor.getUniqueId(), worldId, at.getBlockX(), at.getBlockY(), at.getBlockZ(),
                    nowTicks.getAsLong());
            for (TrapStructures.Structure structure : confining) {
                trapStructures.remove(structure.id());
                for (int[] tile : structure.tilesSnapshot()) {
                    int tx = tile[0];
                    int ty = tile[1];
                    int tz = tile[2];
                    // reclaim on the tile's OWNING region (the ledger's sanctioned early-restore: all layers
                    // popped, true original back, every pending revert no-ops on the entry-null guard).
                    Scheduling.onRegion(new Location(world, tx, ty, tz),
                            () -> tempBlocks.reclaim(worldId, tx, ty, tz));
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
    public void lightningAndDamage(LivingEntity target, double amount, LivingEntity attacker) {
        // Worn LIGHTNING_MOD channel (ADR-0063): resolve the wearer's boost NOW on the firing thread and
        // capture the scaled primitive into the intent (§3.6 immutable-carrier rule). Clamped at 0 so a
        // full-negation debuff yields a cosmetic bolt, never inverted damage; a cosmetic bolt stays 0.
        double payload = amount > 0 && attacker != null
                ? amount * Math.max(0.0, 1.0 + lightningBoost.applyAsDouble(attacker.getUniqueId()))
                : amount;
        entityOp(target, () -> {
            World world = target.getWorld();
            if (world != null) {
                // damage <= 0 is a cosmetic bolt only — no vanilla ~5 dmg / fire (yijki Divine Shield, any flair).
                if (payload > 0) {
                    world.strikeLightning(target.getLocation());
                } else {
                    world.strikeLightningEffect(target.getLocation());
                }
            }
            if (payload > 0) {
                // A bolt is its own proc, never a same-hit rider (ADR-0054): always a separate,
                // attributed application.
                hurtOrPark(target, payload, attacker);
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
                if (flags.speedMultiplier() > 0 && spawned instanceof LivingEntity living) {
                    applySpawnSpeed(living, flags.speedMultiplier());
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

    /** SPAWN_SWARM's initial outward nudge (blocks/tick) — cosmetic: the ring visibly bursts outward. */
    private static final double SWARM_BURST = 0.2;

    /** How long a bell-converted summon's forced target is re-asserted against modern AI revalidation (ADR-0071). */
    private static final int CONVERTED_TARGET_HOLD_TICKS = 100;

    @Override
    public void spawnSwarm(Location origin, int entityTypeId, int count, double radius, double rise,
                           int ttlTicks, double speedFraction, Player cloudOwner, double cloudRange) {
        Location center = origin.clone(); // own the spawn point: a WAIT tier can defer this to a later tick
        regionOp(center, () -> {
            World world = center.getWorld();
            if (world == null || count <= 0) {
                return;
            }
            boolean cloud = cloudOwner != null;
            UUID ownerId = cloud ? cloudOwner.getUniqueId() : null;
            if (cloud) {
                SwarmClouds.arm(cloudOwner, cloudRange, ttlTicks, nowTicks,
                        () -> recentAttackers.latest(ownerId, nowTicks.getAsLong()));
            }
            double damping = SwarmRing.dampingFactor(speedFraction);
            for (int i = 0; i < count; i++) {
                float yaw = SwarmRing.yawDegrees(i, count);
                // ADR-0068: ring X/Z, scattered Y (rise ± 0.6) — the no-arg TLR roll is the JDG-safe shape.
                double jitter = SwarmRing.yJitter(ThreadLocalRandom.current().nextDouble());
                Location at = center.clone().add(
                        SwarmRing.offsetX(yaw, radius), rise + jitter, SwarmRing.offsetZ(yaw, radius));
                at.setYaw(yaw); // spawn applies the location's yaw → each summon faces outward
                at.setPitch(0.0f);
                Entity spawned = spawnTyped(world, at, entityTypeId);
                if (spawned == null) {
                    return; // unresolvable type on this version — the §9 compile warn already fired
                }
                spawned.setVelocity(new Vector(
                        SwarmRing.offsetX(yaw, SWARM_BURST), 0.0, SwarmRing.offsetZ(yaw, SWARM_BURST)));
                SwarmSpawns.bind(spawned);
                if (cloud) {
                    SwarmClouds.track(ownerId, spawned);
                }
                if (damping < 1.0 || cloud) {
                    armSwarmSteer(spawned, damping, ownerId,
                            SwarmRing.orbitPhase(i, count), SwarmRing.bandHeight(i), nowTicks);
                }
                bindSwarmTtl(spawned, ttlTicks);
            }
        });
    }

    /** Auto-remove a swarm summon after {@code ttlTicks}, forgetting its registry entry first. */
    private static void bindSwarmTtl(Entity spawned, int ttlTicks) {
        if (ttlTicks > 0) {
            UUID spawnedId = spawned.getUniqueId();
            Scheduling.onEntityLater(spawned, ttlTicks, () -> {
                SwarmSpawns.forget(spawnedId);
                spawned.remove();
            });
        }
    }

    /**
     * The per-tick swarm steer (ADR-0068, extending the ADR-0060 damp): Bat-style AI rewrites velocity
     * every tick (v' = v + (signum(target−pos)·s − v)·0.1, javap-verified 1.8.8→26.1.2) and ignores the
     * speed attribute, so both the half-speed damp and the attacker-cloud orbit are per-tick velocity
     * writes on the summon's own entity scheduler. With a live cloud target the bat seeks its slot on
     * the orbit around the attacker's facing pillar (the AI's same-tick write perturbs ours ≤ ~10% —
     * organic flutter, stated honestly); with none it falls back to the pure damp. Reads ONLY its own
     * entity plus the immutable published snapshot — never the owner or attacker (Folia). Self-cancels
     * when the entity dies/despawns (on Paper the fallback scheduler does not stop on entity removal;
     * on Folia it does — the guard covers both).
     */
    private static void armSwarmSteer(Entity spawned, double damping, UUID cloudOwner,
                                      double phase, double band, LongSupplier nowTicks) {
        TaskHandle[] handle = new TaskHandle[1];
        long[] t = new long[1];
        handle[0] = Scheduling.repeatingEntity(spawned, 1L, 1L, () -> {
            if (!spawned.isValid()) {
                SwarmSpawns.forget(spawned.getUniqueId());
                if (handle[0] != null) {
                    handle[0].cancel();
                }
                return;
            }
            t[0]++;
            SwarmClouds.CloudTarget cloud = cloudOwner == null
                    ? null : SwarmClouds.target(cloudOwner, nowTicks.getAsLong());
            if (cloud == null) {
                if (damping < 1.0) {
                    spawned.setVelocity(spawned.getVelocity().multiply(damping));
                }
                return;
            }
            Location at = spawned.getLocation(); // region-local: this task runs on the bat's own thread
            double dx = SwarmRing.orbitX(cloud.x(), phase, t[0]) - at.getX();
            double dy = SwarmRing.orbitY(cloud.y(), band, phase, t[0]) - at.getY();
            double dz = SwarmRing.orbitZ(cloud.z(), phase, t[0]) - at.getZ();
            double clamp = SwarmRing.chaseScale(dx, dy, dz);
            spawned.setVelocity(new Vector(dx * clamp, dy * clamp, dz * clamp));
        });
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
        tempBlock(at, materialId, durationTicks, replaceMode, unbreakable, null);
    }

    @Override
    public void tempBlock(Location at, int materialId, int durationTicks, int replaceMode, boolean unbreakable,
                          UUID confined) {
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
            if (confined != null) {
                // A block in the victim's own cell (ADR-0071 TRAP_BREAK, the Fantasy web): register the placed
                // tile as a one-tile confining structure so Turnkey can early-restore it intact.
                long sid = trapStructures.open(world.getUID(), Set.of(confined), nowTicks.getAsLong(), durationTicks);
                trapStructures.tile(sid, pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
            }
            Scheduling.onRegionLater(pos, pending.delayTicks(),
                    () -> tempBlocks.revert(key, pending.layerId(), pending.seq(), nowTicks.getAsLong()));
        });
    }

    @Override
    public void tempBox(Location center, int materialId, int width, int height, int depth, int durationTicks,
                        int replaceMode) {
        tempBox(center, materialId, width, height, depth, durationTicks, replaceMode, null);
    }

    @Override
    public void tempBox(Location center, int materialId, int width, int height, int depth, int durationTicks,
                        int replaceMode, UUID confined) {
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
            // An entity-anchored box (ADR-0071 TRAP_BREAK, the Spider box) opens ONE confining structure; each
            // successfully-placed tile registers to it so Turnkey can early-restore the whole box intact.
            long sid = confined == null ? -1L
                    : trapStructures.open(worldId, Set.of(confined), now, durationTicks);
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
                            if (sid >= 0) {
                                trapStructures.tile(sid, bx, by, bz); // only tiles the ledger actually placed
                            }
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
            // be air, or nothing is placed and no one teleports. Shared CageGeometry verdict — kept identical
            // to the pets pre-check so the two never disagree on which cells must be clear. The read runs on
            // the base's region; a straddling read that faults on Folia aborts the cage.
            try {
                if (!CageGeometry.volumeClear(world, origin, width, height, depth, b -> canReplace(b, 0))) {
                    return;
                }
            } catch (RuntimeException unreadable) {
                Regions.swallowed("DispatchSinkBase.cage.safetyCheck", unreadable);
                return;
            }
            long now = nowTicks.getAsLong();
            UUID worldId = world.getUID();
            // The cage confines BOTH teleported parties (ADR-0071 TRAP_BREAK): open ONE structure now and
            // register every placed cell below, so either party's Turnkey early-restores the whole cell intact.
            Set<UUID> caged = new HashSet<>(2);
            caged.add(first.getUniqueId());
            caged.add(second.getUniqueId());
            long cageSid = trapStructures.open(worldId, caged, now, durationTicks);
            for (int dx = -hx - 1; dx < width - hx + 1; dx++) {
                for (int dz = -hz - 1; dz < depth - hz + 1; dz++) {
                    boolean ring = ringCell(dx, dz, hx, hz, width, depth);
                    // Wall connections come from GEOMETRY (the ring shape is known), never neighbour reads,
                    // so tile placement order / region boundaries cannot leave a bar unconnected.
                    boolean barNorth = ring && ringCell(dx, dz - 1, hx, hz, width, depth);
                    boolean barSouth = ring && ringCell(dx, dz + 1, hx, hz, width, depth);
                    boolean barEast = ring && ringCell(dx + 1, dz, hx, hz, width, depth);
                    boolean barWest = ring && ringCell(dx - 1, dz, hx, hz, width, depth);
                    for (int dy = -1; dy <= height; dy++) {
                        Material material;
                        boolean isWall = false;
                        if (dy == -1) {
                            material = floor; // full plate under interior AND ring, so the cage is sealed
                        } else if (dy == height) {
                            material = roof;
                        } else if (ring) {
                            material = wall;
                            isWall = true;
                        } else {
                            continue; // the interior stays air
                        }
                        int bx = cx + dx;
                        int by = baseY + dy;
                        int bz = cz + dz;
                        int typeId = material.ordinal();
                        boolean connect = isWall;
                        Location tileAt = new Location(world, bx, by, bz);
                        // Per-tile region re-key (the tempPlatform rule) — a 5-wide structure can straddle.
                        Scheduling.onRegion(tileAt, () -> {
                            Block block = world.getBlockAt(bx, by, bz);
                            if (!canReplace(block, 2)) { // checked-air raced into something: capture + restore it
                                return;
                            }
                            TempBlockLedger.Key key = new TempBlockLedger.Key(worldId, bx, by, bz);
                            TempBlockLedger.Pending pending = tempBlocks.place(key, typeId, durationTicks, now);
                            trapStructures.tile(cageSid, bx, by, bz); // register the placed wall/floor/roof cell
                            if (connect) {
                                // A no-physics place leaves a modern fence-like block unconnected (walk-through
                                // "beams"); flag its along-the-ring faces so the wall is solid.
                                applyBarShape(world, bx, by, bz, barNorth, barSouth, barEast, barWest);
                            }
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

    /** Whether {@code (dx, dz)} is a cage WALL-RING cell: inside the structure span, outside the interior. */
    private static boolean ringCell(int dx, int dz, int hx, int hz, int width, int depth) {
        boolean inSpan = dx >= -hx - 1 && dx <= width - hx && dz >= -hz - 1 && dz <= depth - hz;
        return inSpan && (dx < -hx || dx >= width - hx || dz < -hz || dz >= depth - hz);
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
        suppress(target, scopeKind, scopeId, durationTicks, byDefId, false, 1);
    }

    @Override
    public void suppress(Player target, int scopeKind, int scopeId, int durationTicks, int byDefId,
                         boolean nextHit, int charges) {
        if (target == null || scopeId < 0) {
            return;
        }
        // Per-player in-memory state, so writing it on the firing thread is Folia-safe (only the target's
        // UUID is captured; no cross-region entity read). byDefId attributes the window to the emitting
        // DISABLE_* ability (ADR-0045: /se why names it).
        if (scopeKind == ScopeKinds.KIND) {
            // ADR-0053: scopeId is a dense effect kindId, matched at gate 5 against the ability's compiled
            // effect kind ids — its own store maps, never packed into the cooldown-scope namespace.
            if (nextHit) {
                suppression.armOneShotKind(target.getUniqueId(), scopeId, charges, byDefId);
            } else {
                suppression.suppressKind(target.getUniqueId(), scopeId, nowTicks.getAsLong(), durationTicks, byDefId);
            }
            return;
        }
        // (scopeKind, scopeId) cooldown-scope packing — the same key gate 5 reads for the suppressed abilities.
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
    public void ignoreHeroic() {
        fold.ignoreHeroic(); // per-hit fold scratch: the commit drops the victim's heroic buckets (ADR-0053)
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
    public void ward(Player target, int wardType, int durationTicks, double amount) {
        if (target == null) {
            return;
        }
        // Per-player timed flag read later by a feature guard (a separate Bukkit event from the arming
        // activation). Concurrent store, UUID captured here → Folia-safe on the firing thread (ADR-0053).
        ward.arm(target.getUniqueId(), WardStore.Type.of(wardType), nowTicks.getAsLong(), durationTicks, amount);
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

    // ── ADR-0071 reforge movement intents (Plan B) ──

    @Override
    public void blinkForward(Player actor, Location origin, Vector direction, double maxDistance,
                             int particleId, int r, int g, int b, float size, int count) {
        Location from = origin.clone();
        Vector dir = direction.clone();
        double max = Math.max(0, maxDistance);
        entityOp(actor, () -> {
            // Walk the ray in 0.5-block samples from the actor's own thread; a sample cell must be
            // standable (feet + head passable — the era isSafeDestination leaf with no sight check).
            // Cells are deduped by block coords; the walk stops at the FIRST blocked cell, and the
            // landing is the LAST open cell seen. Adjacent-region block reads are inside the leaf's
            // own Regions.read guard (returns false on fault → treated as blocked → the blink
            // shortens, never crashes: fail-closed).
            Location best = null;
            int lastBx = from.getBlockX();
            int lastBy = from.getBlockY();
            int lastBz = from.getBlockZ();
            for (double d = 0.5; d <= max + 1.0e-9; d += 0.5) {
                Location cell = new Location(from.getWorld(),
                        from.getX() + dir.getX() * d,
                        from.getY() + dir.getY() * d,
                        from.getZ() + dir.getZ() * d,
                        from.getYaw(), from.getPitch());
                int cbx = cell.getBlockX();
                int cby = cell.getBlockY();
                int cbz = cell.getBlockZ();
                if (cbx != lastBx || cby != lastBy || cbz != lastBz) {
                    // A new block cell: standability is re-checked once here (the dedupe only skips the
                    // repeat leaf calls, never the landing advance below), and a blocked cell ends the walk.
                    if (!isSafeDestination(cell, null)) {
                        break; // first wall: never phase into or through terrain
                    }
                    lastBx = cbx;
                    lastBy = cby;
                    lastBz = cbz;
                }
                // The landing is the FARTHEST sample inside an open cell, not the entry edge of it — every
                // sample here lies in an already-validated-open cell, so advance best to it. (Advancing only
                // on new cells would strand the blink at each open cell's near face — a 0.5-block hop.)
                best = cell;
            }
            if (best == null) {
                return;       // point-blank wall: zero blink, attempt spent (authored downside)
            }
            dustDirect(from.clone().add(0, 1, 0), particleId, r, g, b, size, count); // departure puff (actor-region)
            exemptMovement(actor);
            teleportTo(actor, best);
            Location arrival = best.clone().add(0, 1, 0);
            Scheduling.onRegion(arrival, () -> dustDirect(arrival, particleId, r, g, b, size, count));
        });
    }

    @Override
    public void grapple(Player actor, Location eye, LivingEntity victim, Location reelTo, Location hookPoint,
                        int flightTicks, int slowPotionId, int slowAmplifier, int slowTicks,
                        Vector zip, int particleId, int r, int g, int b, float size, double density) {
        Location from = eye.clone();
        Location end = victim == null ? (hookPoint == null ? null : hookPoint.clone()) : null; // victim end read on its own thread
        int flight = Math.max(1, flightTicks);
        if (victim != null) {
            Location dest = reelTo.clone();
            // Line drawn now to the victim's LAST KNOWN point (the kind measured it); yank runs on the
            // victim's own scheduler after the flight — never re-enters this plan (the cage rule).
            entityOp(victim, () -> {
                drawLine(from, victim.getLocation(), particleId, r, g, b, size, density); // victim-thread read: region-correct
                Scheduling.onEntityLater(victim, flight, () -> {
                    if (!victim.isValid()) {
                        return;
                    }
                    exemptMovement(victim);
                    teleportTo(victim, dest);
                    victim.setVelocity(new Vector(0, 0, 0));
                    PotionEffectType slow = potionEffect(slowPotionId);
                    if (slow != null && slowTicks > 0) {
                        victim.addPotionEffect(new PotionEffect(slow, slowTicks, slowAmplifier));
                    }
                });
            });
        } else if (end != null && zip != null) {
            Vector pullV = zip.clone();
            regionOp(end, () -> drawLine(from, end, particleId, r, g, b, size, density));
            entityOp(actor, () -> Scheduling.onEntityLater(actor, flight, () -> {
                if (actor.isValid()) {
                    exemptMovement(actor);
                    actor.setVelocity(pullV);
                }
            }));
        }
    }

    /**
     * A straight dust line from → to at {@code density} motes/block, RUN AFTER FLUSH: each mote fans to its
     * own owning region thread via {@code Scheduling.onRegion} (a ≤14-block line can straddle chunks) and draws
     * directly through the {@code dustDirect} leaf — never the plan-based {@link #dust}, which would append to
     * an already-dispatched batch (the cage rule).
     */
    private void drawLine(Location from, Location to, int particleId, int r, int g, int b,
                          float size, double density) {
        if (from == null || to == null) {
            return;
        }
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) Math.round(dist * density));
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            Location point = new Location(from.getWorld(),
                    from.getX() + dx * t, from.getY() + dy * t, from.getZ() + dz * t);
            Scheduling.onRegion(point, () -> dustDirect(point, particleId, r, g, b, size, 1));
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

    /**
     * Set (or, at {@code total <= 0}, remove) the ONE plugin-owned worn max-health MODIFIER — identity
     * {@link #WORN_MAX_HEALTH_ID}/{@link #WORN_MAX_HEALTH_NAME}, additive. A modifier (never a base shift) so
     * stale playerdata after a crash is discoverable by identity and reconciled on the next refresh — a base
     * shift would be indistinguishable from the player's legitimate base. Modern = Bukkit
     * {@code AttributeModifier}; 1.8 = NMS {@code AttributeModifier} (javap-verified: {@code a(UUID)} get,
     * {@code b(mod)} apply, {@code c(mod)} remove).
     */
    protected abstract void setWornMaxHealthModifier(Player player, double total);

    /**
     * Set (or, at {@code total <= 0}, remove) the ONE plugin-owned worn water-movement-efficiency MODIFIER
     * — identity {@link #WORN_WATER_SPEED_ID}/{@link #WORN_WATER_SPEED_NAME}, additive, the
     * {@link #setWornMaxHealthModifier} contract (ADR-0060). Modern resolves the attribute by NAME
     * ({@code GENERIC_WATER_MOVEMENT_EFFICIENCY}; the alias chain covers the 1.21.3+ rename) and no-ops
     * when it does not exist (pre-1.21); the 1.8 leaf is a recorded no-op (no such attribute).
     */
    protected abstract void setWornWaterSpeedModifier(Player player, double total);

    /** Add a max-health MODIFIER of {@code delta} (ADD_NUMBER) keyed by {@code id} — modern
     *  {@code AttributeInstance.addModifier}; 1.8 NMS {@code AttributeInstance.b(AttributeModifier)}. Idempotent on
     *  {@code id} (removes any prior with the same id first). Lets the drain take overhealth held in OTHER modifiers
     *  (HEALTH_BOOST, the reconciled worn +hearts modifier), which a base write cannot reach. */
    protected abstract void addMaxHealthModifier(LivingEntity entity, UUID id, String name, double delta);

    /** Remove the max-health modifier {@code id} if present — modern {@code getModifiers}+{@code removeModifier};
     *  1.8 NMS {@code a(UUID)}+{@code c(AttributeModifier)}. A no-op when absent (idempotent give-back). */
    protected abstract void removeMaxHealthModifier(LivingEntity entity, UUID id);

    /**
     * Begin the frozen VISUAL (FREEZE, ADR-0065) and report whether a per-tick re-pin is still needed:
     * modern locks + pins when Paper's freeze-tick lock exists (1.18.2+ → {@code false}) and otherwise
     * asks for the pin ({@code true}, the 1.17.1 floor); 1.8.9 is a recorded no-op ({@code false} —
     * nothing to pin). Called for EVERY victim — the lock is the only mechanism that holds the pin
     * under fire, and its NBT persistence is reconciled at entity load (the modern guard listener).
     */
    protected abstract boolean freezeVisualStart(LivingEntity target);

    /** One unlocked re-pin at {@code max + FREEZE_PIN_SLACK}, SKIPPED while the target burns (§1.2 — the
     *  baseTick clear + 1009 hiss). Modern only; a 1.8.9 no-op. */
    protected abstract void freezePin(LivingEntity target);

    /** End the frozen visual: unlock (if locked) + zero the freeze ticks. A 1.8.9 no-op. */
    protected abstract void freezeVisualEnd(LivingEntity target);

    /**
     * Apply the frozen-window slow (FREEZE, ADR-0065): {@link #FROZEN_SLOW_ID} at −{@code slowPercent}/100
     * (multiply-base) and, when {@code neutralizeFrostSlow}, {@link #FROZEN_FROST_OFFSET_ID} at +0.05
     * (add) cancelling vanilla's {@code tryAddFrost} −0.05-at-full-freeze modifier (§1.4) so the authored
     * percent is the real ground slow. Modern = Bukkit MOVEMENT_SPEED modifiers (name-resolved; the
     * 1.21.3 rename rides the alias chain); 1.8 = NMS {@code GenericAttributes.MOVEMENT_SPEED} (no
     * vanilla frost there, so the offset is never applied). Replace-by-identity, idempotent.
     */
    protected abstract void applyFrozenSlow(LivingEntity target, double slowPercent, boolean neutralizeFrostSlow);

    /** Remove both frozen-slow modifiers if present (idempotent give-back). */
    protected abstract void removeFrozenSlow(LivingEntity target);

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

    /**
     * A coloured-dust mote batch at {@code at}, run ON {@code at}'s owning thread with NO plan wrapper — the
     * machine-visual leaf (ADR-0071). The public {@link #dust} intent is {@code regionOp(at, () -> dustDirect(…))};
     * long-lived machine/task closures call this directly on a thread they already own (e.g. a blink walk on the
     * actor's scheduler, a grapple line hopped per straddling chunk), never re-entering an already-flushed plan.
     */
    protected abstract void dustDirect(Location at, int particleId, int r, int g, int b, float size, int count);

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

    /**
     * Give a just-placed fence-like block (the cage's iron bars, ADR-0052) its wall connections toward the
     * flagged faces. Modern stores connections in the block data and a no-physics place leaves them all
     * false — free-standing "beams" a player walks through; 1.8 computes connections client-side, so its
     * leaf is a no-op. Faces come from GEOMETRY (the ring shape is known), never neighbour reads, so tile
     * placement order and region boundaries cannot race it.
     */
    protected abstract void applyBarShape(World world, int x, int y, int z,
                                          boolean north, boolean south, boolean east, boolean west);

    /** Seat {@code passenger} on {@code vehicle} (ADR-0052): modern {@code addPassenger}; 1.8 {@code setPassenger}. */
    protected abstract void mountEntity(Entity vehicle, Entity passenger);

    /** Saddle a horse-type summon so it is steerable (ADR-0052); a no-op on a non-horse. */
    protected abstract void applySaddle(LivingEntity entity);

    /**
     * Scale a summon's movement-speed attribute base by {@code multiplier} (ADR-0052): modern writes the
     * {@code GENERIC_MOVEMENT_SPEED} instance's base; 1.8 the NMS {@code GenericAttributes.MOVEMENT_SPEED}
     * instance. Only called for {@code multiplier > 0}, so a 1.0 is an explicit no-change, not a reset.
     */
    protected abstract void applySpawnSpeed(LivingEntity entity, double multiplier);

    /** Spawn a cosmetic falling block of {@code material} at {@code loc} (block-data on modern; data byte on 1.8). */
    protected abstract FallingBlock spawnFallingBlock(World world, Location loc, Material material);

    /** Create an explosion honouring {@code breakBlocks}, never fire (the overload with a block-break flag differs by era). */
    protected abstract void doExplosion(World world, Location at, double power, boolean breakBlocks);

    /**
     * Reconcile the ONE plugin-owned Quickening attack-speed modifier on {@code target} to
     * {@code addScalar} (ADD_SCALAR: ×(1+addScalar)) — the 1.9+ swing meter, HIT_TEMPO/ADR-0071. Base =
     * recorded no-op: the attribute does not exist below 1.9 (the 1.8.9 tree), where the swing meter it
     * compensates for also does not exist and the i-frame write alone carries the full effect there.
     */
    protected void applyTempoAttackSpeed(Player target, double addScalar) {
    }

    /** Remove the Quickening attack-speed modifier (HIT_TEMPO/ADR-0071); base = recorded no-op (see above). */
    protected void clearTempoAttackSpeed(Player target) {
    }
}

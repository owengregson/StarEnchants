package feature.combat;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.Snapshot;
import engine.interact.ReboundPlan;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.ActorProbe;
import engine.run.FactPopulator;
import engine.sink.CombatTag;
import engine.sink.DamageMarks;
import engine.sink.DotParkLedger;
import engine.sink.EngineDamage;
import engine.sink.SinkEnv;
import engine.sink.SinkReadback;
import engine.sink.SwarmClouds;
import engine.stores.BatteryStore;
import engine.stores.ComboStore;
import engine.stores.DamageCapStore;
import engine.stores.DisarmWindowStore;
import engine.stores.HitTempoStore;
import engine.stores.OutgoingDebuffStore;
import engine.stores.VulnerabilityStore;
import engine.stores.ReboundStore;
import engine.stores.RecentAttackersStore;
import engine.stores.ReflectMarksStore;
import engine.stores.SuppressionStore;
import feature.soul.SoulBinding;
import feature.trigger.TriggerRunner;
import item.worn.WornStateStore;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;
import org.bukkit.Location;
import feature.compat.Projectiles;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import engine.sink.SinkFactory;

/**
 * Turns a Bukkit combat event into ability activations (docs/architecture.md §3.3, §3.6). Reads worn
 * state read-only (never re-resolving a cross-region entity, §3.4) and folds all damage deltas onto
 * the event ONCE (§6.1). Runs on the firing region thread; world-mutating effects are routed to their
 * owning threads by the Sink.
 */
public final class CombatDispatch {

    private final TriggerRunner runner;
    private final SinkFactory sinkFactory;
    private final ContentHolder content;
    // The per-boot sink wiring, threaded to every per-event sink so a write and its separate-event reader share stores.
    private final SinkEnv env;
    // Combat-local consecutive-hit streak (the %combo% fact); cached from the shared aggregate. Only combat
    // writes it, but the composition root registers the aggregate with EngineStoreListener as the single
    // quit-cleanup authority (§5.4).
    private final ComboStore combo;
    // ADR-0049 per-player combat windows, consulted here and written by the sink intents (shared via the env).
    private final RecentAttackersStore recent;    // §2 gank window (Aegis / Anti Gank facts)
    private final ReflectMarksStore reflectMarks;  // §3 Hex reflect
    private final OutgoingDebuffStore outgoingDebuff; // §4 Weaken/Destruction
    private final VulnerabilityStore vulnerability;   // wave 2d: the victim's own incoming-damage mark
    private final DamageCapStore damageCap;         // §5 Diminish
    private final SuppressionStore suppression;      // §7 one-shot Neutralize consume
    // ADR-0071 reforge armed-window stores: consulted at the hit site here, armed by the sink intents (shared via the env).
    private final HitTempoStore hitTempo;           // Quickening tempo window + per-victim stolen stamps
    private final BatteryStore battery;             // Supernova core (banked, discharged on the next hit)
    private final DisarmWindowStore disarmWindows;  // the Unhanding one-shot window
    private final ReboundStore rebounds;            // PROC_REBOUND worn grades (Enchant Reflect)
    private final LongSupplier nowTicks;
    private final java.util.function.DoubleSupplier maxBonusDamage;    // §L config.yml combat.max-bonus-damage (<0 = uncapped)
    private final java.util.function.DoubleSupplier maxBonusReduction; // §L config.yml combat.max-bonus-reduction (<0 = uncapped)
    private final java.util.function.DoubleSupplier attackScale;       // §L config.yml combat.attack-scale (<=0 = neutral 1.0)
    private final java.util.function.BooleanSupplier pvpEnabled;       // §L config.yml combat.pvp
    private final java.util.function.BooleanSupplier pveEnabled;       // §L config.yml combat.pve
    private final int attackTriggerId;
    private final int defenseTriggerId;
    private final int bowTriggerId;     // −1 ⇒ no distinct bow trigger; arrow hits fall back to ATTACK
    private final int tridentTriggerId; // −1 ⇒ no distinct trident trigger; trident hits fall back to ATTACK
    private final int hurtTriggerId;    // −1 ⇒ absent; the all-cause defender walk that rides beside DEFENSE
    private final Projectiles projectiles; // trident/arrow-like typing for the attacker trigger (§4 era seam)
    // §3.7 hit identity: the last landed (victim ← attacker) stamp, discriminating a same-swing re-hit from a
    // distinct hit inside the victim's SHARED i-frame window (which fire/DoT/other attackers also arm).
    private final ReHitGuard reHits = new ReHitGuard();
    // ADR-0069 combo-DoT sync: the shared park ledger, drained into a hit's fold here, plus the paced release
    // armed when leftover buckets remain and no combo is active (the post-combo leftover kick).
    private final DotParkLedger dotPark;
    private final ComboDotRelease dotRelease;

    /** §N friendly-fire gate (ADR-0027): two friendly players get NO SE combat effects. No-op by default. */
    private static volatile java.util.function.BiPredicate<Player, Player> friendlyFire = (attacker, victim) -> false;

    /** Install the friendly-fire gate (boot-time). A {@code null} predicate resets to "never friendly". */
    public static void friendlyFire(java.util.function.BiPredicate<Player, Player> predicate) {
        friendlyFire = predicate == null ? (attacker, victim) -> false : predicate;
    }

    /**
     * Whether two players are friendly under the installed gate (ADR-0071 §2.6): the reforge strike listener's
     * Supernova bank and the Javelin impact read this so nothing lands in a context where the dispatch's own
     * defense-side {@code !friendly} predicate would stand down. A reader — never a second install path.
     */
    public static boolean friendly(Player attacker, Player victim) {
        return friendlyFire.test(attacker, victim);
    }

    /**
     * §L combat.* live caps/gates (max-bonus-damage / max-bonus-reduction, {@code <0} = uncapped;
     * attack-scale = the post-cap attack-side throughput multiplier, {@code <=0} → neutral 1.0;
     * pvp/pve keyed on the victim's player-ness).
     */
    public record Caps(java.util.function.DoubleSupplier maxBonusDamage, java.util.function.DoubleSupplier maxBonusReduction,
                       java.util.function.DoubleSupplier attackScale,
                       java.util.function.BooleanSupplier pvp, java.util.function.BooleanSupplier pve) {
        /** Uncapped, unit-scaled, PvP/PvE on — the wiring the tester suites use (the config defaults a finite outgoing cap). */
        public static Caps unlimited() {
            return new Caps(() -> -1.0, () -> -1.0, () -> 1.0, () -> true, () -> true);
        }
    }

    /**
     * Full dispatch over the shared per-boot {@link SinkEnv}: distinct BOW/TRIDENT triggers ({@code -1} falls
     * those hits back to the Cosmic Enchants-style melee-only ATTACK) + soul binder + live combat {@link Caps}
     * (config.yml {@code combat.*}, §L).
     */
    public CombatDispatch(AbilityExecutor executor, SinkFactory sinkFactory, ActorProbe probe, ContentHolder content,
                          WornStateStore worn, int attackTriggerId, int defenseTriggerId,
                          int bowTriggerId, int tridentTriggerId,
                          Function<Player, Optional<SoulBinding>> soulBinder, SinkEnv env, Caps caps,
                          Projectiles projectiles) {
        this(executor, sinkFactory, probe, content, worn, attackTriggerId, defenseTriggerId, bowTriggerId,
                tridentTriggerId, -1, soulBinder, env, caps, projectiles);
    }

    /** As above, plus the all-cause {@code HURT} id ({@code -1} = absent) that walks beside the DEFENSE side. */
    public CombatDispatch(AbilityExecutor executor, SinkFactory sinkFactory, ActorProbe probe, ContentHolder content,
                          WornStateStore worn, int attackTriggerId, int defenseTriggerId,
                          int bowTriggerId, int tridentTriggerId, int hurtTriggerId,
                          Function<Player, Optional<SoulBinding>> soulBinder, SinkEnv env, Caps caps,
                          Projectiles projectiles) {
        this.sinkFactory = Objects.requireNonNull(sinkFactory, "sinkFactory");
        this.content = Objects.requireNonNull(content, "content");
        this.env = Objects.requireNonNull(env, "env");
        this.projectiles = Objects.requireNonNull(projectiles, "projectiles");
        Objects.requireNonNull(caps, "caps");
        this.combo = env.stores().combo();
        this.recent = env.stores().recentAttackers();
        this.reflectMarks = env.stores().reflectMarks();
        this.outgoingDebuff = env.stores().outgoingDebuff();
        this.vulnerability = env.stores().vulnerability();
        this.damageCap = env.stores().damageCap();
        this.suppression = env.stores().suppression();
        this.hitTempo = env.stores().hitTempo();
        this.battery = env.stores().battery();
        this.disarmWindows = env.stores().disarmWindows();
        this.rebounds = env.stores().rebounds();
        this.nowTicks = env.nowTicks();
        this.maxBonusDamage = caps.maxBonusDamage();
        this.maxBonusReduction = caps.maxBonusReduction();
        this.attackScale = caps.attackScale();
        this.pvpEnabled = caps.pvp();
        this.pveEnabled = caps.pve();
        // Shared VarStore: a condition's %name% reads what an earlier SET_VAR wrote (write side: the per-event sink).
        // Shared RageStackStore too, so %ragestacks% reads the stacks the rage service maintains (§3).
        this.runner = new TriggerRunner(executor, worn, soulBinder, env.nowTicks(),
                FactPopulator.builtin(env.stores(), probe));
        this.attackTriggerId = attackTriggerId;
        this.defenseTriggerId = defenseTriggerId;
        this.bowTriggerId = bowTriggerId;
        this.tridentTriggerId = tridentTriggerId;
        this.hurtTriggerId = hurtTriggerId;
        this.dotPark = env.dotPark();
        this.dotRelease = new ComboDotRelease(env.dotPark(), env.nowTicks());
    }

    /** The combo-DoT paced release (ADR-0069) — read by the ControlsModule install; internal composition. */
    public ComboDotRelease dotRelease() {
        return dotRelease;
    }

    /**
     * PROC_REBOUND's tier lookup: an ability id → its source's rarity-tier WEIGHT, precomputed per snapshot
     * by the composition root and rebound on every reload swap. Derived data, never a compile-model field —
     * and never {@code Library.tierOf}, whose own comment says "never combat" (it linear-scans the catalog).
     * The inert default returns {@code -1} (no tier), which no rebound band contains, so a dispatch nobody
     * bound simply never rebounds.
     */
    private volatile ToIntFunction<Ability> tierWeights = ability -> -1;

    /** Bind the per-snapshot tier index; call at boot AND on every reload swap. */
    public void bindTiers(ToIntFunction<Ability> tierWeights) {
        this.tierWeights = Objects.requireNonNull(tierWeights, "tierWeights");
    }

    // /se damagedebug (ADR-0050 R3): owned here so the readout sees the same fold/caps this dispatch commits.
    private final DamageDebug damageDebug = new DamageDebug();

    /** The per-hit fold readout's toggle surface ({@code /se damagedebug}). */
    public DamageDebug damageDebug() {
        return damageDebug;
    }

    /** The §3.7 hit-identity guard — package-private test seam. */
    ReHitGuard reHits() {
        return reHits;
    }

    /** Dispatch one entity-on-entity hit: run attacker + defender abilities and fold the result. */
    @SuppressWarnings("deprecation") // EntityDamageEvent.DamageModifier.ARMOR/MAGIC: deprecated-not-removed across the whole range (the IGNORE_ARMOR primitive).
    public void onDamage(EntityDamageByEntityEvent event) {
        if (EngineDamage.active()) {
            if (EngineDamage.armorBypassed()) {
                // IGNORE_ARMOR on a walk with no combat event of its own (an IMPACT landing's bite, a forced
                // payload run). The flag can only ever be honoured on an EVENT — the reduction is the server's
                // — so it rides the hurt the sink issues and is spent here, on the very event that hurt fired.
                // Same two modifiers, same reason as the folded path below; only the event differs.
                zeroModifier(event, EntityDamageEvent.DamageModifier.ARMOR);
                zeroModifier(event, EntityDamageEvent.DamageModifier.MAGIC);
            }
            // SE-issued damage (reflects, DoT ticks, lightning bolts — ADR-0054): attributed events are
            // for DOWNSTREAM plugins; our own walks never proc off our own damage. This is the same
            // re-entrancy contract the old bare hurt() enforced structurally (no damager → no dispatch);
            // without it an attributed reflect could proc a reflect, ping-ponging forever.
            return;
        }
        Snapshot snapshot = content.snapshot();
        Ability[] abilities = snapshot.abilities();
        Entity rawDamager = event.getDamager();
        // A projectile attributes the hit to its shooter (region-safe), but RAW type still picks the trigger.
        Entity damager = rawDamager;
        if (rawDamager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        Entity victimEntity = event.getEntity();
        LivingEntity victim = victimEntity instanceof LivingEntity living ? living : null;
        LivingEntity attacker = damager instanceof LivingEntity living ? living : null;
        // Self-inflicted damage (e.g. an ender-pearl teleport, whose projectile's shooter IS the victim) must
        // not fire the player's own ATTACK/DEFENSE effects on themselves — there is no opponent. The
        // projectile→shooter resolution above makes the shooter the `damager`, so a self-pearl reads here as
        // damager == victim; bail before any SE combat work and leave the vanilla damage untouched.
        if (damager == victimEntity
                || (damager instanceof Player dp && victimEntity instanceof Player vp
                        && dp.getUniqueId().equals(vp.getUniqueId()))) {
            return;
        }
        long now = nowTicks.getAsLong();
        UUID attackerId = damager.getUniqueId();     // the resolved source (a projectile's shooter, §2 gank id)
        // §3.7 Proc SE combat EXACTLY ONCE per hit. A stronger blow inside the victim's i-frame window fires a
        // SECOND EntityDamageByEntityEvent for the SAME swing (vanilla's "damage the difference": a crit
        // upgrade, a faster weapon) — but that window is SHARED: fire/poison ticks, engine DoT (ADR-0054) and
        // OTHER attackers arm it too, so in-window alone is not hit identity. Skip only when THIS attacker's
        // own landed hit opened (or last continued) the window; any other in-window hit is a real, distinct
        // hit and runs the full attack+defense walk. A processed in-window hit folds onto the event's damage
        // AS REPORTED — vanilla's difference chunk — leaving the vanilla difference economy untouched. The
        // skip is relayed per-event so MONITOR consumers (rage) stay single-advance with the walk.
        if (victim != null && victim.getNoDamageTicks() > victim.getMaximumNoDamageTicks() / 2) {
            if (reHits.sameHit(victimEntity.getUniqueId(), attackerId, now, victim.getMaximumNoDamageTicks() / 2)) {
                ReHitGuard.markSkipped(event);
                return;
            }
        }
        ReHitGuard.clearSkipped();
        Location at = victimEntity.getLocation();
        // Capture BEFORE the fold mutates it, so the %damage% fact reads the hit's value at activation time.
        double incomingDamage = event.getDamage();
        String causeName = event.getCause().name(); // %damagecause% (e.g. ENTITY_ATTACK, PROJECTILE)
        // %impactheight% / %projectilekind%: differenced HERE, on the firing thread, against the entity that was
        // actually struck. Both combat sides then read the same geometry — the defense context's `victim` is the
        // attacker, so leaving the subtraction to the populator would measure an arrow against its own shooter.
        // Only projectile hits pay for it (one extra Location, alongside the `at` this method already takes).
        double impactHeight = 0.0;
        String projectileKind = "";
        if (rawDamager instanceof Projectile) {
            impactHeight = rawDamager.getLocation().getY() - at.getY();
            projectileKind = projectiles.kindOf(rawDamager);
        }
        int worldId = TriggerRunner.worldId(snapshot, victimEntity.getWorld());

        SinkReadback sink = sinkFactory.create(env);
        sink.fold().caps(maxBonusDamage.getAsDouble(), maxBonusReduction.getAsDouble()); // §L combat caps, live
        sink.fold().attackScale(attackScale.getAsDouble()); // §L combat.attack-scale, live (post-cap, attack side only)
        // ADR-0051: same-hit health writes to the victim land inline at flush — before this event's damage
        // applies — so a defensive heal (Phoenix's death-save) joins the kill decision instead of racing it.
        sink.eventEntity(victim);

        // ADR-0069: DoT damage parked while Mental held a combo on this victim joins THIS hit's damage moment
        // (one event, one immunity window, one knockback — the ADR-0054 rider economy). Only THIS attacker's own
        // buckets plus the unattributed bucket join, so a third party's bleed is never credited to this attacker;
        // their buckets wait for their own hit, the combo-end release, or the post-combo leftover kick below —
        // NEVER a mid-combo release (an attributed release hurt is a melee to Mental; a third-party knock would
        // end the active combo). Re-parked at MONITOR if the event ends cancelled (ComboDotSyncListener).
        if (victimEntity instanceof Player parkVictim) {
            List<DotParkLedger.Bucket> drained = dotPark.drainFor(parkVictim.getUniqueId(), attackerId);
            if (!drained.isEmpty()) {
                double owed = 0.0;
                for (DotParkLedger.Bucket b : drained) {
                    owed += b.amount();
                }
                sink.fold().addEffectiveDamage(owed);
                ParkFlushRelay.mark(event, dotPark, parkVictim.getUniqueId(), drained);
            }
            if (!dotPark.comboActive(parkVictim.getUniqueId(), now) && dotPark.hasParked(parkVictim.getUniqueId())) {
                dotRelease.begin(parkVictim); // post-combo leftover kick: third-party/stale buckets pace out to their owners
            }
        }

        // Combat tag (supreme's out-of-combat fly): both parties count as fighting on any hit between them.
        if (damager instanceof Player ap) {
            CombatTag.tag(ap.getUniqueId());
        }
        if (victimEntity instanceof Player vp) {
            CombatTag.tag(vp.getUniqueId());
        }

        // §2 gank window (ADR-0049): record THIS attacker against a player victim BEFORE the walks, so the
        // victim's %recentattackers%/%attackerindex% facts already include this hit.
        if (victimEntity instanceof Player recorded) {
            recent.record(recorded.getUniqueId(), attackerId, now);
            // Bat cloud (ADR-0068): capture the attacker ENTITY at-hit on the victim's thread; the
            // owner-side publisher reads its pose later under a Regions guard (the ADR-0043 shape).
            SwarmClouds.noteHit(recorded.getUniqueId(), damager, now);
        }

        // PvP/PvE context (config.yml combat.pvp/pve) is decided by the VICTIM's player-ness.
        boolean victimIsPlayer = victimEntity instanceof Player;
        // §N friendly-fire: skip ALL SE combat effects between two friendly players.
        boolean friendly = damager instanceof Player a && victimEntity instanceof Player v && friendlyFire.test(a, v);

        // §5 Diminish (ADR-0049): the victim's armed cap is claimed HERE, ahead of every walk, and applied at
        // the fold-commit below. The claim MUST lead the walks rather than sit beside its use — DAMAGE_CAP arms
        // the store INLINE mid-walk, so claiming at the commit hands each hit the window it just opened for the
        // next one and `duration:` is never consulted. Ahead of the ATTACK side too: a rebounded (PROC_REBOUND)
        // Diminish runs the swapped walk with the victim as actor and arms this same window.
        DamageCapStore.Cap armedCap = victimEntity instanceof Player capVictim
                ? damageCap.consumeArmed(capVictim.getUniqueId(), now) // one-shot: consumed even if unused
                : null;

        // Attack side: self = attacker, target = victim.
        UUID comboActor = null;      // set iff combo.hit ran — a cancelled event rolls the streak back below
        Object comboMark = null;
        ReboundPlan rebound = null;  // PROC_REBOUND: set iff the victim has a grade armed; read at the commit below
        if (damager instanceof Player attackerPlayer && contextEnabled(victimIsPlayer) && !friendly) {
            int attackId = attackTrigger(projectiles, rawDamager, attackTriggerId, bowTriggerId, tridentTriggerId);
            comboActor = attackerPlayer.getUniqueId();
            comboMark = combo.mark(comboActor);
            int streak = combo.hit(attackerPlayer.getUniqueId(), victimEntity.getUniqueId(), now); // %combo% fact, §3.4 — same-target only
            // reaper's Mark of the Reaper: +N% from THIS attacker while the victim is marked by them. Consulted
            // BEFORE the attack abilities run, so a mark this hit sets (the 5% proc) applies only to LATER hits.
            if (victim != null) {
                double markBonus = DamageMarks.bonus(victim.getUniqueId(), attackerPlayer.getUniqueId());
                if (markBonus != 0.0) {
                    sink.fold().addOutgoing(markBonus);
                }
            }
            // §4 WEAKEN / OUTGOING_DEBUFF: the attacker's active non-stacking outgoing-damage debuff, folded
            // once. The cause filter is why the melee/projectile split is read here and not after the reforge
            // block — a projectile-only nerf must leave this attacker's melee alone.
            boolean meleeHit = rawDamager == damager; // a projectile makes damager the shooter, so identity ⇒ melee
            OutgoingDebuffStore.Debuff nerf = outgoingDebuff.active(attackerPlayer.getUniqueId(), now);
            if (nerf != null && nerf.covers(meleeHit
                    ? OutgoingDebuffStore.CAUSE_MELEE : OutgoingDebuffStore.CAUSE_PROJECTILE)) {
                sink.fold().addOutgoing(-nerf.percent() / 100.0);
                if (!nerf.feedback().isEmpty()) {
                    // Emitted at the hit the window actually prices, not when it was armed — the same rule
                    // REFLECT's feedback follows, and the only way a lost swing reads as the debuff's doing.
                    sink.message(attackerPlayer, nerf.feedback());
                }
            }
            // ADR-0071 reforge armed-window consults. Melee-only (the reforge lives on a held melee weapon):
            // rawDamager must be the player, not a projectile. Contributions are optimistic; consumption commits
            // at MONITOR via ReforgeStrikeRelay iff the event survives (ReforgeStrikeListener), so a Dodge/negate
            // never eats a window or a charge. These consults inherit this branch's pvp/pve + friendly gate by
            // construction — reforge windows are SE combat economy and vanish wherever all other SE combat does.
            ReforgeStrikeRelay.Pending reforgePending = null;
            if (meleeHit && victim != null) {
                HitTempoStore.Window tempo = hitTempo.window(attackerPlayer.getUniqueId(), now);
                if (tempo != null) {
                    sink.fold().mulFinal(tempo.damageFactor());
                    reforgePending = ReforgeStrikeRelay.tempo(reforgePending, tempo);
                }
                DisarmWindowStore.Arm unhanding = disarmWindows.armed(attackerPlayer.getUniqueId(), now);
                if (unhanding != null && victimEntity instanceof Player) {
                    sink.fold().mulFinal(1.0 - unhanding.malusFraction());
                    reforgePending = ReforgeStrikeRelay.disarm(reforgePending);
                }
                if (battery.armed(attackerPlayer.getUniqueId()) && victimEntity instanceof Player) {
                    // Player victims only (the disarm gate's rule): the bank charges off PvP hits taken, so
                    // a stray mob swat mid-scrap must not dump it — the armed core waits for a real enemy.
                    sink.fold().addEffectiveDamage(battery.peek(attackerPlayer.getUniqueId()));
                    reforgePending = ReforgeStrikeRelay.battery(reforgePending);
                }
                if (reforgePending != null) {
                    ReforgeStrikeRelay.mark(event, env.stores(), attackerPlayer.getUniqueId(),
                            victimEntity.getUniqueId(), reforgePending);
                }
            }
            int attackerRecent = recent.distinctCount(attackerPlayer.getUniqueId(), now); // how many are ganking the attacker (Anti Gank)
            // PROC_REBOUND (Enchant Reflect): a player victim wearing a grade may take an activating enchant off
            // this walk at gate 9 (ReboundGate) and re-run it with the roles swapped. One map probe on an empty
            // store is what a server with no rebound content pays; the plan itself is built only for a wearer.
            if (victimEntity instanceof Player reflector && rebounds.armed(reflector.getUniqueId())) {
                rebound = new ReboundPlan(rebounds, reflector.getUniqueId(), tierWeights,
                        () -> ThreadLocalRandom.current().nextDouble() * 100.0);
            }
            ActivationContext attackCtx = new ActivationContext(attackerPlayer, victim, null, at, incomingDamage,
                    null, streak, causeName, false, attackerRecent, 0, Double.NaN, impactHeight, projectileKind);
            runner.run(abilities, snapshot.generation(), worldId, attackId, true, attackerPlayer, attackCtx, sink,
                    snapshot.stableKeys(), true, rebound);
            // §8 ECHO_STRIKE (Double Strike): re-run the attacker walk EXACTLY once over the same event/sink/fold.
            // Checked once → run once, so a second ECHO_STRIKE proc in the echo pass cannot request a third pass.
            // The echo pass carries the SAME plan, so its activations are rolled for the rebound independently —
            // two activations of one enchant are two chances to have it turned around, which is what the roll means.
            if (sink.echoRequested()) {
                runner.run(abilities, snapshot.generation(), worldId, attackId, true, attackerPlayer, attackCtx, sink,
                        snapshot.stableKeys(), true, rebound);
            }
            if (rebound != null && rebound.claimedAny() && victimEntity instanceof Player reflector) {
                // The swapped run: actor = the reflector, victim = the attacker who threw the proc. GATELESS —
                // every claimed ability already passed its own chance/cooldown/condition/soul gates on the walk
                // above, and re-gating would charge them to someone who does not own the enchant. Damage-mod
                // contributions land on the sink's attacker-directed fold, committed once at the bottom of this
                // method; ADR-0054's stand-down is untouched, so nothing re-enters this handler.
                int reflectorRecent = recent.distinctCount(reflector.getUniqueId(), now);
                int reflectorAttackerIndex = recent.indexOf(reflector.getUniqueId(), attackerId, now);
                ActivationContext swapped = new ActivationContext(reflector, attacker, attacker, at, incomingDamage,
                        null, 0, causeName, false, reflectorRecent, reflectorAttackerIndex, Double.NaN,
                        impactHeight, projectileKind);
                sink.beginRebound();
                runner.runForced(abilities, worldId, attackId, reflector, swapped, sink, snapshot.stableKeys(),
                        rebound.claimed());
                sink.endRebound();
            }
        }
        // Defense side: self = victim, target = attacker.
        if (victimEntity instanceof Player defenderPlayer && contextEnabled(damager instanceof Player) && !friendly) {
            int defenderRecent = recent.distinctCount(defenderPlayer.getUniqueId(), now);   // distinct attackers on me (Aegis)
            int attackerIndex = recent.indexOf(defenderPlayer.getUniqueId(), attackerId, now); // 1-based order of THIS attacker
            // %posthit.health% reads the hit as the SERVER prices it — getFinalDamage() is the post-armor /
            // protection / resistance value, and nothing has folded onto the event yet, so this is the
            // vanilla-final figure BEFORE any SE contribution. Only this side supplies it; every other context
            // leaves it NaN, so the fact stays 0 where there is no pending hit.
            ActivationContext defenseCtx = new ActivationContext(defenderPlayer, attacker, attacker, at,
                    incomingDamage, null, 0, causeName, false, defenderRecent, attackerIndex,
                    event.getFinalDamage(), impactHeight, projectileKind);
            runner.run(abilities, snapshot.generation(), worldId, defenseTriggerId, false, defenderPlayer, defenseCtx,
                    sink, snapshot.stableKeys());
            // HURT: the all-cause defender walk, on the SAME context and sink as DEFENSE, so both fold once and
            // read identical facts. applyHeroic=false — DEFENSE just contributed the worn heroic reduction, and a
            // second contribution would double it. Every gate this branch already passed (pvp/pve, friendly-fire,
            // the ADR-0054 engine-damage frame, the §3.7 re-hit skip) covers HURT by construction.
            if (hurtTriggerId >= 0) {
                runner.run(abilities, snapshot.generation(), worldId, hurtTriggerId, false, defenderPlayer,
                        defenseCtx, sink, snapshot.stableKeys(), false);
            }
            // §7 one-shot SUPPRESS consume (Neutralize): burn the victim's armed one-shots after their defense walk.
            suppression.consumeEventScoped(defenderPlayer.getUniqueId());
        }

        // VULNERABILITY: the victim's own non-stacking fragility mark, folded once, OUTSIDE the defence-walk
        // gate above — the mark's contract is every source, so a mob's hit and a PvE server both honour it.
        // It rides the reduction bucket NEGATED rather than the outgoing one: the outgoing bucket is scaled by
        // combat.attack-scale, so a +100% mark would land as +500% on this pack, while a −1.0 reduction is
        // exactly x2.0 and additive with whatever armour the victim actually has.
        VulnerabilityStore.Mark vuln = victimEntity instanceof Player marked
                ? vulnerability.active(marked.getUniqueId(), now) : null;
        if (vuln != null) {
            sink.fold().addReduction(-vuln.percent() / 100.0);
        }
        // Fold every damage contribution onto the event ONCE (§6.1). §5 cap first, then §3 hex-reflect off the
        // committed value; both deal retaliation via VICTIM-attributed sink.damage (ADR-0054: downstream
        // plugins see who dealt it); the EngineDamage frame keeps it from re-entering this handler.
        double folded = sink.fold().apply(event.getDamage());
        double committed = folded;
        if (victimEntity instanceof Player capped) {
            if (armedCap != null && folded > armedCap.value()) { // claimed above the walks, so it predates this hit
                committed = armedCap.value();
                if (armedCap.reflectOverflow() && attacker != null) {
                    sink.damage(attacker, folded - committed, capped); // §5 Vengeful Diminish: the excess back to the attacker
                }
            }
            // R-QC19: price whatever this event's walks armed against the value THIS hit committed (post-cap).
            // The readback belongs here and nowhere else — DAMAGE_CAP runs mid-walk, where the number does not
            // exist yet, and pricing at the arm was the one-hit lag that made "half the hit that armed it" name
            // an earlier, unrelated swing. The arming hit itself is untouched: `armedCap` was claimed above the
            // walks, so this window opens for the NEXT hit.
            DamageCapStore.Priced capArmed = damageCap.price(capped.getUniqueId(), committed, now);
            if (capArmed != null && !capArmed.feedback().isEmpty()) {
                // Announced at the commit, at the ceiling the cap actually carries — the same rule VULNERABILITY's
                // and REFLECT's lines follow, and the only point where {damage} can be filled with a true figure.
                sink.message(capped, HitFeedback.fill(capArmed.feedback(), capArmed.value()));
            }
        }
        event.setDamage(committed);
        if (vuln != null && !vuln.hitMessage().isEmpty() && victimEntity instanceof Player marked) {
            // Reported at the hit the mark actually priced, and with the COMMITTED figure — the same rule
            // REFLECT's line follows. Telling a marked player what the mark just cost them is the only way a
            // hit that lands harder reads as the mark's doing rather than the attacker's gear.
            sink.message(marked, HitFeedback.fill(vuln.hitMessage(), committed));
        }
        // /se damagedebug: report the fold's actual buckets to toggled parties (both belong to this event's region).
        // getFinalDamage() reads the post-setDamage modifier chain — the health the victim actually loses — so the
        // readout exposes the server's whole post-fold pipeline (armor plugins included), not just our commit.
        damageDebug.report(damager instanceof Player ap ? ap : null,
                victimEntity instanceof Player vp ? vp : null,
                incomingDamage, sink.fold(), attackScale.getAsDouble(), folded, committed,
                event.getFinalDamage(), sink.cancelled());
        // PROC_REBOUND: the marginal damage the swapped run added, priced through the rebound fold's own
        // arithmetic (caps + attack-scale) so a rebounded +50% is felt exactly as the incoming hit would have
        // felt it. Committed as a bounded SECOND application through sink.damage — which routes via
        // EngineDamage, so the frame keeps it out of onDamage. Owner-accepted cost: this is a separate hit
        // with its own immunity frame and death credit, not literally the attacker's swing turned around.
        if (rebound != null && rebound.claimedAny() && attacker != null) {
            double back = sink.reboundContribution(incomingDamage);
            if (back > 0) {
                sink.damage(attacker, back, victim);
            }
        }
        // §3 REFLECT (Hex): a marked attacker takes a fraction of the committed damage back onto themselves.
        if (damager instanceof Player reflectedAttacker && attacker != null) {
            ReflectMarksStore.Mark hex = reflectMarks.active(reflectedAttacker.getUniqueId(), now);
            if (hex != null) {
                double back = committed * hex.fractionPercent() / 100.0;
                if (hex.cap() > 0) {
                    back = Math.min(back, hex.cap()); // flat per-hit ceiling: a 100% reflect cannot scale without limit
                }
                if (back > 0) {
                    sink.damage(attacker, back, victim);
                    if (!hex.feedback().isEmpty()) {
                        // Emitted HERE, at the hit the reflect actually lands on — not when the window was armed —
                        // so the line reports the health this swing cost its owner (post-cap).
                        sink.message(reflectedAttacker, HitFeedback.fill(hex.feedback(), back));
                    }
                }
            }
        }
        if (sink.armorIgnored()) {
            // IGNORE_ARMOR: zero armor + enchant-protection AFTER setDamage recomputes modifiers from base.
            // isApplicable is the cross-version probe, so no version gate is needed (§ combat-flags).
            zeroModifier(event, EntityDamageEvent.DamageModifier.ARMOR);
            zeroModifier(event, EntityDamageEvent.DamageModifier.MAGIC);
        }
        if (sink.cancelled()) {
            event.setCancelled(true);
            // A cancelled hit never landed: undo this event's combo advance so rage/%combo% cannot build
            // off dodged or inverted swings (the walk above still saw the advanced streak, by design).
            if (comboActor != null) {
                combo.rollback(comboActor, comboMark);
            }
        } else if (victim != null) {
            // This landed hit is the victim's window opener/continuer for the re-hit skip above. Never stamped
            // on a cancel: a dodged hit arms no vanilla window, so its stamp could only mis-skip a later real
            // hit inside a window some OTHER source (fire, a DoT) opened.
            reHits.stamp(victimEntity.getUniqueId(), attackerId, now);
        }
        sink.flush();
    }

    /** Whether StarEnchants combat effects apply in this context — {@code pvp} ⇒ the PvP gate, else PvE (§L). */
    private boolean contextEnabled(boolean pvp) {
        return pvp ? pvpEnabled.getAsBoolean() : pveEnabled.getAsBoolean();
    }

    /** Zero one of the event's damage modifiers if this version/cause carries it (the IGNORE_ARMOR primitive). */
    @SuppressWarnings("deprecation") // DamageModifier is @Deprecated-not-removed across the whole 1.17.1→26.1.x range (javap-verified).
    private static void zeroModifier(EntityDamageEvent event, EntityDamageEvent.DamageModifier modifier) {
        if (event.isApplicable(modifier)) {
            event.setDamage(modifier, 0.0);
        }
    }

    /**
     * The attacker-side trigger for a hit, by RAW damager type; {@code -1} ids fall back to {@code attackId}.
     * A trident is also arrow-like, so it is tested first (the {@link Projectiles} seam encapsulates the
     * 1.13/1.14 trident/abstract-arrow types, absent on 1.8).
     */
    static int attackTrigger(Projectiles projectiles, Entity rawDamager, int attackId, int bowId, int tridentId) {
        if (projectiles.isTrident(rawDamager) && tridentId >= 0) {
            return tridentId;
        }
        if (projectiles.isArrowLike(rawDamager) && bowId >= 0) {
            return bowId;
        }
        return attackId;
    }
}

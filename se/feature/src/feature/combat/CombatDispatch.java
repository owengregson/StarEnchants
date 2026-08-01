package feature.combat;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.Snapshot;
import compile.model.SourceKind;
import engine.effect.kind.ActiveSets;
import engine.effect.kind.EnchantLevels;
import engine.effect.kind.HeldEnchantLevels;
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
import engine.stores.RecentAttackersStore;
import engine.stores.ReflectMarksStore;
import engine.stores.SuppressionStore;
import feature.soul.SoulBinding;
import feature.trigger.TriggerRunner;
import item.worn.WornState;
import item.worn.WornStateStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
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
    private final WornStateStore worn;
    private final SinkFactory sinkFactory;
    private final ContentHolder content;
    // The per-boot sink wiring, threaded to every per-event sink so a write and its separate-event reader share stores.
    private final SinkEnv env;
    // Combat-local consecutive-hit streak (the %combo% fact); cached from the shared aggregate. Only combat
    // writes it, but the composition root registers the aggregate with EngineStoreListener as the single
    // quit-cleanup authority (§5.4).
    private final ComboStore combo;
    // ADR-0049 per-player combat windows, consulted here and written by the sink intents (shared via the env).
    private final RecentAttackersStore recent;    // §2 general/Aegis gank window
    private final RecentAttackersStore antiGankRecent; // Anti Gank records only while its enchanted axe is held
    private final ReflectMarksStore reflectMarks;  // §3 Hex reflect
    private final OutgoingDebuffStore outgoingDebuff; // §4 Weaken/Destruction
    private final DamageCapStore damageCap;         // §5 Diminish
    private final SuppressionStore suppression;      // §7 one-shot Neutralize consume
    // ADR-0071 reforge armed-window stores: consulted at the hit site here, armed by the sink intents (shared via the env).
    private final HitTempoStore hitTempo;           // Quickening tempo window + per-victim stolen stamps
    private final BatteryStore battery;             // Supernova core (banked, discharged on the next hit)
    private final DisarmWindowStore disarmWindows;  // the Unhanding one-shot window
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
        this.sinkFactory = Objects.requireNonNull(sinkFactory, "sinkFactory");
        this.content = Objects.requireNonNull(content, "content");
        this.env = Objects.requireNonNull(env, "env");
        this.worn = Objects.requireNonNull(worn, "worn");
        this.projectiles = Objects.requireNonNull(projectiles, "projectiles");
        Objects.requireNonNull(caps, "caps");
        this.combo = env.stores().combo();
        this.recent = env.stores().recentAttackers();
        this.antiGankRecent = env.stores().antiGankAttackers();
        this.reflectMarks = env.stores().reflectMarks();
        this.outgoingDebuff = env.stores().outgoingDebuff();
        this.damageCap = env.stores().damageCap();
        this.suppression = env.stores().suppression();
        this.hitTempo = env.stores().hitTempo();
        this.battery = env.stores().battery();
        this.disarmWindows = env.stores().disarmWindows();
        this.nowTicks = env.nowTicks();
        this.maxBonusDamage = caps.maxBonusDamage();
        this.maxBonusReduction = caps.maxBonusReduction();
        this.attackScale = caps.attackScale();
        this.pvpEnabled = caps.pvp();
        this.pveEnabled = caps.pve();
        // Shared VarStore: a condition's %name% reads what an earlier SET_VAR wrote (write side: the per-event sink).
        // Shared RageStackStore too, so %ragestacks% reads the stacks the rage service maintains (§3).
        this.runner = new TriggerRunner(executor, worn, soulBinder, env.nowTicks(),
                FactPopulator.builtin(env.stores().vars(), env.stores().rageStacks(), probe));
        this.attackTriggerId = attackTriggerId;
        this.defenseTriggerId = defenseTriggerId;
        this.bowTriggerId = bowTriggerId;
        this.tridentTriggerId = tridentTriggerId;
        this.dotPark = env.dotPark();
        this.dotRelease = new ComboDotRelease(env.dotPark(), env.nowTicks());
    }

    /** The combo-DoT paced release (ADR-0069) — read by the ControlsModule install; internal composition. */
    public ComboDotRelease dotRelease() {
        return dotRelease;
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
        int projectileMark = rawDamager instanceof Projectile
                ? env.projectileMarks().consume(rawDamager.getUniqueId(), now) : 0;
        double projectileHeight = rawDamager instanceof Projectile
                ? rawDamager.getLocation().getY() - victimEntity.getLocation().getY() : 0.0;
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
            if (HeldEnchantLevels.held(recorded, "enchants/anti-gank") > 0) {
                antiGankRecent.record(recorded.getUniqueId(), attackerId, now);
            }
            // Bat cloud (ADR-0068): capture the attacker ENTITY at-hit on the victim's thread; the
            // owner-side publisher reads its pose later under a Regions guard (the ADR-0043 shape).
            SwarmClouds.noteHit(recorded.getUniqueId(), damager, now);
        }

        // PvP/PvE context (config.yml combat.pvp/pve) is decided by the VICTIM's player-ness.
        boolean victimIsPlayer = victimEntity instanceof Player;
        // §N friendly-fire: skip ALL SE combat effects between two friendly players.
        boolean friendly = damager instanceof Player a && victimEntity instanceof Player v && friendlyFire.test(a, v);
        boolean weakCosmicBow = rawDamager instanceof Projectile
                && CosmicProjectilePower.weak(rawDamager.getUniqueId());

        // Attack side: self = attacker, target = victim.
        UUID comboActor = null;      // set iff combo.hit ran — a cancelled event rolls the streak back below
        Object comboMark = null;
        if (damager instanceof Player attackerPlayer && contextEnabled(victimIsPlayer) && !friendly
                && !weakCosmicBow) {
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
            // §4 WEAKEN (Destruction): the attacker's active non-stacking outgoing-damage debuff, folded once.
            double weaken = outgoingDebuff.active(attackerPlayer.getUniqueId(), now);
            if (weaken != 0.0) {
                sink.fold().addOutgoing(-weaken / 100.0);
            }
            // ADR-0071 reforge armed-window consults. Melee-only (the reforge lives on a held melee weapon):
            // rawDamager must be the player, not a projectile. Contributions are optimistic; consumption commits
            // at MONITOR via ReforgeStrikeRelay iff the event survives (ReforgeStrikeListener), so a Dodge/negate
            // never eats a window or a charge. These consults inherit this branch's pvp/pve + friendly gate by
            // construction — reforge windows are SE combat economy and vanish wherever all other SE combat does.
            boolean meleeHit = rawDamager == damager;
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
            int attackerRecent = recent.distinctCount(attackerPlayer.getUniqueId(), now); // how many are ganking the attacker (general)
            int antiGankAttackers = antiGankRecent.distinctCount(attackerPlayer.getUniqueId(), now, 120);
            ActivationContext attackCtx = new ActivationContext(attackerPlayer, victim, null, at, incomingDamage,
                    event.getFinalDamage(), null, streak, causeName, false, attackerRecent, 0, projectileMark,
                    projectileHeight, antiGankAttackers, 0);
            ReflectedCandidates reflected = splitReflected(snapshot, attackerPlayer,
                    victimEntity instanceof Player player ? player : null, attackId);
            runner.runCandidates(abilities, snapshot.generation(), worldId, attackId, true, attackerPlayer,
                    attackCtx, sink, snapshot.stableKeys(), reflected.normal());
            // Cosmic rolls reflection once per enchant, before that enchant's own proc chance. Reflected
            // candidates execute as the defender against the attacker in a separate fold: direct target effects
            // naturally reverse, while event-damage bonuses become attributed retaliation rather than increasing
            // the blow still landing on the defender.
            runReflected(snapshot, worldId, attackId, reflected.reflected(),
                    victimEntity instanceof Player player ? player : null, attackerPlayer, at,
                    incomingDamage, event.getDamage(), event.getFinalDamage(), causeName, projectileMark,
                    projectileHeight);
            // §8 ECHO_STRIKE (Double Strike): re-run the attacker walk EXACTLY once over the same event/sink/fold.
            // Checked once → run once, so a second ECHO_STRIKE proc in the echo pass cannot request a third pass.
            if (sink.echoRequested()) {
                runner.runCandidates(abilities, snapshot.generation(), worldId, attackId, true, attackerPlayer,
                        attackCtx, sink, snapshot.stableKeys(), reflected.normal());
            }
        }
        // Defense side: self = victim, target = attacker.
        if (victimEntity instanceof Player defenderPlayer && contextEnabled(damager instanceof Player) && !friendly) {
            int defenderRecent = recent.distinctCount(defenderPlayer.getUniqueId(), now);   // distinct attackers on me (Aegis)
            int attackerIndex = recent.indexOf(defenderPlayer.getUniqueId(), attackerId, now); // 1-based order of THIS attacker
            int aegisAttackerIndex = recent.indexOf(defenderPlayer.getUniqueId(), attackerId, now, 100);
            ActivationContext defenseCtx = new ActivationContext(defenderPlayer, attacker, attacker, at,
                    incomingDamage, event.getFinalDamage(), null, 0, causeName, false, defenderRecent, attackerIndex,
                    projectileMark, projectileHeight, 0, aegisAttackerIndex);
            runner.run(abilities, snapshot.generation(), worldId, defenseTriggerId, false, defenderPlayer, defenseCtx,
                    sink, snapshot.stableKeys());
            // §7 one-shot SUPPRESS consume (Neutralize): burn the victim's armed one-shots after their defense walk.
            suppression.consumeEventScoped(defenderPlayer.getUniqueId());
        }

        // Fold every damage contribution onto the event ONCE (§6.1). §5 cap first, then §3 hex-reflect off the
        // committed value; both deal retaliation via VICTIM-attributed sink.damage (ADR-0054: downstream
        // plugins see who dealt it); the EngineDamage frame keeps it from re-entering this handler.
        double folded = sink.fold().apply(event.getDamage());
        double committed = folded;
        if (victimEntity instanceof Player capped) {
            DamageCapStore.Cap cap = damageCap.consumeArmed(capped.getUniqueId(), now); // one-shot: consumed even if unused
            if (cap != null && folded > cap.value()) {
                committed = cap.value();
                if (cap.reflectOverflow() && attacker != null) {
                    sink.damage(attacker, folded - committed, capped); // §5 Vengeful Diminish: the excess back to the attacker
                }
            }
            damageCap.recordLastTaken(capped.getUniqueId(), committed); // ALWAYS record the committed value (post-cap)
        }
        event.setDamage(committed);
        // /se damagedebug: report the fold's actual buckets to toggled parties (both belong to this event's region).
        // getFinalDamage() reads the post-setDamage modifier chain — the health the victim actually loses — so the
        // readout exposes the server's whole post-fold pipeline (armor plugins included), not just our commit.
        damageDebug.report(damager instanceof Player ap ? ap : null,
                victimEntity instanceof Player vp ? vp : null,
                incomingDamage, sink.fold(), attackScale.getAsDouble(), folded, committed,
                event.getFinalDamage(), sink.cancelled());
        // §3 REFLECT (Hex): a marked attacker takes a fraction of the committed damage back onto themselves.
        if (damager instanceof Player reflectedAttacker && attacker != null) {
            double reflectPercent = reflectMarks.active(reflectedAttacker.getUniqueId(), now);
            if (reflectPercent > 0.0) {
                sink.damage(attacker, committed * reflectPercent / 100.0, victim);
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
        if (sink.removeProjectileRequested() && rawDamager instanceof Projectile) {
            rawDamager.remove();
        }
    }

    /** Cosmic Enchant Reflect candidate partition, preserving original worn order within both routes. */
    private ReflectedCandidates splitReflected(Snapshot snapshot, Player attacker, Player defender, int triggerId) {
        WornState state = worn.get(attacker.getUniqueId());
        if (state == null || state.gen() != snapshot.generation()) {
            return ReflectedCandidates.EMPTY;
        }
        int[] candidates = state.byTrigger(triggerId);
        if (defender == null || candidates.length == 0) {
            return new ReflectedCandidates(candidates, new int[0]);
        }
        java.util.Map<Integer, Integer> heldEnchantCounts = new java.util.HashMap<>();
        for (int id : state.heldEnchantByTrigger(triggerId)) {
            heldEnchantCounts.merge(id, 1, Integer::sum);
        }
        int normalLevel = EnchantLevels.worn(defender, "enchants/enchant-reflect");
        int heroicLevel = CosmicTierGate.tierSixPlusEnabled(defender)
                ? EnchantLevels.worn(defender, "enchants/heroic-enchant-reflect") : 0;
        int masteryLevel = ActiveSets.has(defender, "sets/dragon-slayer") ? 10 : 0;
        if (normalLevel == 0 && heroicLevel == 0 && masteryLevel == 0) {
            return new ReflectedCandidates(candidates, new int[0]);
        }

        Map<String, Boolean> rolls = new LinkedHashMap<>();
        ArrayList<Integer> normal = new ArrayList<>(candidates.length);
        ArrayList<Integer> reflected = new ArrayList<>();
        for (int id : candidates) {
            if (id < 0 || id >= snapshot.abilities().length) {
                continue;
            }
            Ability ability = snapshot.abilities()[id];
            int heldCopies = heldEnchantCounts.getOrDefault(id, 0);
            if (ability.sourceKind() != SourceKind.ENCHANT || heldCopies <= 0) {
                normal.add(id);
                continue;
            }
            if (heldCopies == 1) {
                heldEnchantCounts.remove(id);
            } else {
                heldEnchantCounts.put(id, heldCopies - 1);
            }
            String stableKey = snapshot.stableKeys().keyOf(id);
            String enchantKey = baseContentKey(stableKey, id);
            boolean isReflected = rolls.computeIfAbsent(enchantKey,
                    ignored -> reflectRoll(snapshot, ability, normalLevel, heroicLevel, masteryLevel));
            (isReflected ? reflected : normal).add(id);
        }
        return new ReflectedCandidates(toInts(normal), toInts(reflected));
    }

    /** enchants/lifesteal/5/a1 -> enchants/lifesteal; each enchant gets one independent Reflect roll. */
    static String baseContentKey(String stableKey, int fallbackId) {
        if (stableKey == null) {
            return "#" + fallbackId;
        }
        int first = stableKey.indexOf('/');
        int second = first < 0 ? -1 : stableKey.indexOf('/', first + 1);
        return second < 0 ? stableKey : stableKey.substring(0, second);
    }

    /** Exact source precedence and deliberate integer-division chance curve. */
    private static boolean reflectRoll(Snapshot snapshot, Ability ability, int normal, int heroic, int mastery) {
        int groupId = ability.cdScopeGroup();
        String group = groupId < 0 || groupId >= snapshot.interners().cooldownScopes().size()
                ? "" : snapshot.interners().cooldownScopes().nameOf(groupId);
        int tier = switch (group.toLowerCase(java.util.Locale.ROOT)) {
            case "simple" -> 1;
            case "unique" -> 2;
            case "elite" -> 3;
            case "ultimate" -> 4;
            case "legendary" -> 5;
            case "soul" -> 6;
            case "heroic" -> 7;
            case "mastery" -> 8;
            default -> 0;
        };
        int level = ability.level();
        int reflectLevel = mastery > 0 && tier == 8 && mastery >= level ? mastery
                : heroic > 0 && tier <= 7 && heroic >= level ? heroic
                : normal > 0 && tier <= 5 && normal >= level ? normal : 0;
        return reflectLevel > 0
                && Math.random() <= 0.02 + 0.01 * (reflectLevel / 3);
    }

    private void runReflected(Snapshot snapshot, int worldId, int triggerId, int[] candidates,
                              Player reflector, Player attacker, Location location, double incomingDamage,
                              double baseDamage, double finalDamage, String causeName, int projectileMark,
                              double projectileHeight) {
        if (reflector == null || candidates.length == 0) {
            return;
        }
        SinkReadback reflectedSink = sinkFactory.create(env);
        reflectedSink.fold().caps(maxBonusDamage.getAsDouble(), maxBonusReduction.getAsDouble());
        reflectedSink.fold().attackScale(attackScale.getAsDouble());
        ActivationContext reflectedContext = new ActivationContext(reflector, attacker, attacker, location,
                incomingDamage, finalDamage, null, 0, causeName, false, 0, 0, projectileMark, projectileHeight);
        runner.runBorrowedCandidates(snapshot.abilities(), snapshot.generation(), worldId, triggerId, reflector,
                reflectedContext, reflectedSink, snapshot.stableKeys(), candidates);
        if (reflectedSink.echoRequested()) {
            runner.runBorrowedCandidates(snapshot.abilities(), snapshot.generation(), worldId, triggerId, reflector,
                    reflectedContext, reflectedSink, snapshot.stableKeys(), candidates);
        }
        double bonus = reflectedSink.fold().apply(baseDamage) - baseDamage;
        if (bonus > 0.0) {
            reflectedSink.damage(attacker, bonus, reflector);
        }
        reflectedSink.flush();
    }

    private static int[] toInts(List<Integer> ids) {
        int[] out = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            out[i] = ids.get(i);
        }
        return out;
    }

    private record ReflectedCandidates(int[] normal, int[] reflected) {
        private static final ReflectedCandidates EMPTY = new ReflectedCandidates(new int[0], new int[0]);
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

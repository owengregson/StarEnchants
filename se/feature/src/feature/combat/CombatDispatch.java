package feature.combat;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.Snapshot;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.ActorProbe;
import engine.run.FactPopulator;
import engine.sink.CombatTag;
import engine.sink.DamageMarks;
import engine.sink.SinkEnv;
import engine.sink.SinkReadback;
import engine.stores.ComboStore;
import feature.soul.SoulBinding;
import feature.trigger.TriggerRunner;
import item.worn.WornStateStore;
import java.util.Objects;
import java.util.Optional;
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
    private final SinkFactory sinkFactory;
    private final ContentHolder content;
    // The per-boot sink wiring, threaded to every per-event sink so a write and its separate-event reader share stores.
    private final SinkEnv env;
    // Combat-local consecutive-hit streak (the %combo% fact); cached from the shared aggregate. Only combat
    // writes it, but the composition root registers the aggregate with EngineStoreListener as the single
    // quit-cleanup authority (§5.4).
    private final ComboStore combo;
    private final LongSupplier nowTicks;
    private final java.util.function.DoubleSupplier maxBonusDamage;    // §L config.yml combat.max-bonus-damage (<0 = uncapped)
    private final java.util.function.DoubleSupplier maxBonusReduction; // §L config.yml combat.max-bonus-reduction (<0 = uncapped)
    private final java.util.function.BooleanSupplier pvpEnabled;       // §L config.yml combat.pvp
    private final java.util.function.BooleanSupplier pveEnabled;       // §L config.yml combat.pve
    private final int attackTriggerId;
    private final int defenseTriggerId;
    private final int bowTriggerId;     // −1 ⇒ no distinct bow trigger; arrow hits fall back to ATTACK
    private final int tridentTriggerId; // −1 ⇒ no distinct trident trigger; trident hits fall back to ATTACK
    private final Projectiles projectiles; // trident/arrow-like typing for the attacker trigger (§4 era seam)

    /** §N friendly-fire gate (ADR-0027): two friendly players get NO SE combat effects. No-op by default. */
    private static volatile java.util.function.BiPredicate<Player, Player> friendlyFire = (attacker, victim) -> false;

    /** Install the friendly-fire gate (boot-time). A {@code null} predicate resets to "never friendly". */
    public static void friendlyFire(java.util.function.BiPredicate<Player, Player> predicate) {
        friendlyFire = predicate == null ? (attacker, victim) -> false : predicate;
    }

    /** §L combat.* live caps/gates (max-bonus-damage / max-bonus-reduction, {@code <0} = uncapped; pvp/pve keyed on the victim's player-ness). */
    public record Caps(java.util.function.DoubleSupplier maxBonusDamage, java.util.function.DoubleSupplier maxBonusReduction,
                       java.util.function.BooleanSupplier pvp, java.util.function.BooleanSupplier pve) {
        /** Uncapped + PvP/PvE on — the uncapped wiring the tester suites use (the config defaults a finite outgoing cap). */
        public static Caps unlimited() {
            return new Caps(() -> -1.0, () -> -1.0, () -> true, () -> true);
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
        this.projectiles = Objects.requireNonNull(projectiles, "projectiles");
        Objects.requireNonNull(caps, "caps");
        this.combo = env.stores().combo();
        this.nowTicks = env.nowTicks();
        this.maxBonusDamage = caps.maxBonusDamage();
        this.maxBonusReduction = caps.maxBonusReduction();
        this.pvpEnabled = caps.pvp();
        this.pveEnabled = caps.pve();
        // Shared VarStore: a condition's %name% reads what an earlier SET_VAR wrote (write side: the per-event sink).
        this.runner = new TriggerRunner(executor, worn, soulBinder, env.nowTicks(),
                FactPopulator.builtin(env.stores().vars(), probe));
        this.attackTriggerId = attackTriggerId;
        this.defenseTriggerId = defenseTriggerId;
        this.bowTriggerId = bowTriggerId;
        this.tridentTriggerId = tridentTriggerId;
    }

    /** Dispatch one entity-on-entity hit: run attacker + defender abilities and fold the result. */
    @SuppressWarnings("deprecation") // EntityDamageEvent.DamageModifier.ARMOR/MAGIC: deprecated-not-removed across the whole range (the IGNORE_ARMOR primitive).
    public void onDamage(EntityDamageByEntityEvent event) {
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
        Location at = victimEntity.getLocation();
        // Capture BEFORE the fold mutates it, so the %damage% fact reads the hit's value at activation time.
        double incomingDamage = event.getDamage();
        int worldId = TriggerRunner.worldId(snapshot, victimEntity.getWorld());

        SinkReadback sink = sinkFactory.create(env);
        sink.fold().caps(maxBonusDamage.getAsDouble(), maxBonusReduction.getAsDouble()); // §L combat caps, live

        // Combat tag (supreme's out-of-combat fly): both parties count as fighting on any hit between them.
        if (damager instanceof Player ap) {
            CombatTag.tag(ap.getUniqueId());
        }
        if (victimEntity instanceof Player vp) {
            CombatTag.tag(vp.getUniqueId());
        }

        // PvP/PvE context (config.yml combat.pvp/pve) is decided by the VICTIM's player-ness.
        boolean victimIsPlayer = victimEntity instanceof Player;
        // §N friendly-fire: skip ALL SE combat effects between two friendly players.
        boolean friendly = damager instanceof Player a && victimEntity instanceof Player v && friendlyFire.test(a, v);

        // Attack side: self = attacker, target = victim.
        if (damager instanceof Player attackerPlayer && contextEnabled(victimIsPlayer) && !friendly) {
            int attackId = attackTrigger(projectiles, rawDamager, attackTriggerId, bowTriggerId, tridentTriggerId);
            int streak = combo.hit(attackerPlayer.getUniqueId(), victimEntity.getUniqueId(), nowTicks.getAsLong()); // %combo% fact, §3.4 — same-target only
            // reaper's Mark of the Reaper: +N% from THIS attacker while the victim is marked by them. Consulted
            // BEFORE the attack abilities run, so a mark this hit sets (the 5% proc) applies only to LATER hits.
            if (victim != null) {
                double markBonus = DamageMarks.bonus(victim.getUniqueId(), attackerPlayer.getUniqueId());
                if (markBonus != 0.0) {
                    sink.fold().addOutgoing(markBonus);
                }
            }
            runner.run(abilities, snapshot.generation(), worldId, attackId, true,
                    attackerPlayer,
                    new ActivationContext(attackerPlayer, victim, null, at, incomingDamage, null, streak), sink,
                    snapshot.stableKeys());
        }
        // Defense side: self = victim, target = attacker.
        if (victimEntity instanceof Player defenderPlayer && contextEnabled(damager instanceof Player) && !friendly) {
            runner.run(abilities, snapshot.generation(), worldId, defenseTriggerId, false,
                    defenderPlayer, new ActivationContext(defenderPlayer, attacker, attacker, at, incomingDamage, null),
                    sink, snapshot.stableKeys());
        }

        // Fold every damage contribution onto the event ONCE (§6.1); honour a cancel; flush deferred work.
        event.setDamage(sink.fold().apply(event.getDamage()));
        if (sink.armorIgnored()) {
            // IGNORE_ARMOR: zero armor + enchant-protection AFTER setDamage recomputes modifiers from base.
            // isApplicable is the cross-version probe, so no version gate is needed (§ combat-flags).
            zeroModifier(event, EntityDamageEvent.DamageModifier.ARMOR);
            zeroModifier(event, EntityDamageEvent.DamageModifier.MAGIC);
        }
        if (sink.cancelled()) {
            event.setCancelled(true);
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

package feature.combat;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.Snapshot;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.FactPopulator;
import engine.sink.CombatTag;
import engine.sink.DamageMarks;
import engine.sink.SinkReadback;
import engine.sink.SoulDebit;
import engine.stores.ComboStore;
import engine.stores.ImmuneStore;
import engine.stores.KeepOnDeathStore;
import engine.stores.KnockbackControlStore;
import engine.stores.SuppressionStore;
import engine.stores.TeleblockStore;
import engine.stores.VarStore;
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
import platform.economy.EconomyService;
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
    private final EconomyService economy;
    private final SoulDebit souls;
    private final VarStore vars;
    private final SuppressionStore suppression;
    private final KnockbackControlStore knockback;
    private final KeepOnDeathStore keepOnDeath;
    private final TeleblockStore teleblock;
    private final ImmuneStore immune;
    // Combat-local consecutive-hit streak (the %combo% fact); only combat writes it, but it is injected so the
    // composition root can register it with EngineStoreListener as the single quit-cleanup authority (§5.4).
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

    /** §N friendly-fire gate (ADR-0027): two friendly players get NO SE combat effects. No-op by default. */
    private static volatile java.util.function.BiPredicate<Player, Player> friendlyFire = (attacker, victim) -> false;

    /** Install the friendly-fire gate (boot-time). A {@code null} predicate resets to "never friendly". */
    public static void friendlyFire(java.util.function.BiPredicate<Player, Player> predicate) {
        friendlyFire = predicate == null ? (attacker, victim) -> false : predicate;
    }

    /** Combat dispatch with NO soul system (the soul gate is never armed) and no economy. */
    public CombatDispatch(AbilityExecutor executor, SinkFactory sinkFactory, ContentHolder content,
                          WornStateStore worn, int attackTriggerId, int defenseTriggerId,
                          LongSupplier nowTicks) {
        this(executor, sinkFactory, content, worn, attackTriggerId, defenseTriggerId, -1, -1, nowTicks,
                actor -> Optional.empty(), EconomyService.NONE, SoulDebit.NONE, new VarStore(),
                new SuppressionStore(), new KnockbackControlStore(), new KeepOnDeathStore());
    }

    /** Combat dispatch with a soul binder (no economy): an actor in soul mode arms gate 10 from their gem. */
    public CombatDispatch(AbilityExecutor executor, SinkFactory sinkFactory, ContentHolder content,
                          WornStateStore worn, int attackTriggerId, int defenseTriggerId,
                          LongSupplier nowTicks, Function<Player, Optional<SoulBinding>> soulBinder) {
        this(executor, sinkFactory, content, worn, attackTriggerId, defenseTriggerId, -1, -1, nowTicks, soulBinder,
                EconomyService.NONE, SoulDebit.NONE, new VarStore(), new SuppressionStore(),
                new KnockbackControlStore(), new KeepOnDeathStore());
    }

    /** Combat dispatch with a soul binder + economy but no distinct BOW/TRIDENT triggers (arrow hits fire ATTACK). */
    public CombatDispatch(AbilityExecutor executor, SinkFactory sinkFactory, ContentHolder content,
                          WornStateStore worn, int attackTriggerId, int defenseTriggerId,
                          LongSupplier nowTicks, Function<Player, Optional<SoulBinding>> soulBinder,
                          EconomyService economy) {
        this(executor, sinkFactory, content, worn, attackTriggerId, defenseTriggerId, -1, -1, nowTicks, soulBinder,
                economy, SoulDebit.NONE, new VarStore(), new SuppressionStore(),
                new KnockbackControlStore(), new KeepOnDeathStore());
    }

    /** Full dispatch: distinct BOW/TRIDENT triggers + soul binder + economy; either trigger id {@code -1} falls those hits back to the Cosmic Enchants-style melee-only ATTACK. */
    public CombatDispatch(AbilityExecutor executor, SinkFactory sinkFactory, ContentHolder content,
                          WornStateStore worn, int attackTriggerId, int defenseTriggerId,
                          int bowTriggerId, int tridentTriggerId,
                          LongSupplier nowTicks, Function<Player, Optional<SoulBinding>> soulBinder,
                          EconomyService economy, SoulDebit souls, VarStore vars, SuppressionStore suppression,
                          KnockbackControlStore knockback, KeepOnDeathStore keepOnDeath) {
        this(executor, sinkFactory, content, worn, attackTriggerId, defenseTriggerId, bowTriggerId, tridentTriggerId,
                nowTicks, soulBinder, economy, souls, vars, suppression, knockback, keepOnDeath,
                new TeleblockStore(), new ImmuneStore(), new ComboStore(),
                () -> -1.0, () -> -1.0, () -> true, () -> true); // combat caps uncapped + PvP/PvE on by default
    }

    /**
     * As above, plus the live combat caps (config.yml {@code combat.*}, §L): additive bonus ceilings
     * ({@code < 0} ⇒ uncapped) and the {@code pvp}/{@code pve} gates keyed on the victim's player-ness.
     */
    public CombatDispatch(AbilityExecutor executor, SinkFactory sinkFactory, ContentHolder content,
                          WornStateStore worn, int attackTriggerId, int defenseTriggerId,
                          int bowTriggerId, int tridentTriggerId,
                          LongSupplier nowTicks, Function<Player, Optional<SoulBinding>> soulBinder,
                          EconomyService economy, SoulDebit souls, VarStore vars, SuppressionStore suppression,
                          KnockbackControlStore knockback, KeepOnDeathStore keepOnDeath,
                          TeleblockStore teleblock, ImmuneStore immune, ComboStore combo,
                          java.util.function.DoubleSupplier maxBonusDamage,
                          java.util.function.DoubleSupplier maxBonusReduction,
                          java.util.function.BooleanSupplier pvpEnabled,
                          java.util.function.BooleanSupplier pveEnabled) {
        this.sinkFactory = Objects.requireNonNull(sinkFactory, "sinkFactory");
        this.content = Objects.requireNonNull(content, "content");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.souls = Objects.requireNonNull(souls, "souls");
        this.vars = Objects.requireNonNull(vars, "vars");
        this.suppression = Objects.requireNonNull(suppression, "suppression");
        this.knockback = Objects.requireNonNull(knockback, "knockback");
        this.keepOnDeath = Objects.requireNonNull(keepOnDeath, "keepOnDeath");
        this.teleblock = Objects.requireNonNull(teleblock, "teleblock");
        this.immune = Objects.requireNonNull(immune, "immune");
        this.combo = Objects.requireNonNull(combo, "combo");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.maxBonusDamage = Objects.requireNonNull(maxBonusDamage, "maxBonusDamage");
        this.maxBonusReduction = Objects.requireNonNull(maxBonusReduction, "maxBonusReduction");
        this.pvpEnabled = Objects.requireNonNull(pvpEnabled, "pvpEnabled");
        this.pveEnabled = Objects.requireNonNull(pveEnabled, "pveEnabled");
        // Shared VarStore: a condition's %name% reads what an earlier SET_VAR wrote (write side: the per-event sink).
        this.runner = new TriggerRunner(executor, worn, soulBinder, nowTicks, FactPopulator.builtin(vars));
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

        SinkReadback sink = sinkFactory.create(economy, souls, vars, suppression, knockback, keepOnDeath,
                teleblock, immune, nowTicks);
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
            int attackId = attackTrigger(rawDamager, attackTriggerId, bowTriggerId, tridentTriggerId);
            int streak = combo.hit(attackerPlayer.getUniqueId(), nowTicks.getAsLong()); // %combo% fact, §3.4
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
    static int attackTrigger(Entity rawDamager, int attackId, int bowId, int tridentId) {
        if (Projectiles.isTrident(rawDamager) && tridentId >= 0) {
            return tridentId;
        }
        if (Projectiles.isArrowLike(rawDamager) && bowId >= 0) {
            return bowId;
        }
        return attackId;
    }
}

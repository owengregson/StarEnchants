package feature.combat;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.Snapshot;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.FactPopulator;
import engine.sink.DispatchSink;
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
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import platform.economy.EconomyService;
import platform.resolve.RuntimeHandles;

/**
 * Turns a Bukkit combat event into ability activations (docs/architecture.md §3.3, §3.6) — the
 * convergence of the whole runtime spine. On an {@link EntityDamageByEntityEvent} it gathers the
 * attacker's {@code ATTACK} abilities and the defender's {@code DEFENSE} abilities from their
 * PRE-RESOLVED {@link WornState}s (read-only — never re-resolving a cross-region entity, §3.4), runs
 * each through the {@link AbilityExecutor} into one per-event {@link DispatchSink}, folds the
 * accumulated damage deltas onto the event a single time (§6.1), honours a {@code cancelEvent}, and
 * flushes the deferred world mutations batched per owning thread.
 *
 * <p>The event handler runs on the firing region thread (the victim's region on Folia); damage
 * folding and cancellation are synchronous read-backs there, while world-mutating effects are routed
 * to their owning threads by the Sink. The actor's {@code RuntimeHandles} are paired with the
 * snapshot's compile resolver so handle-using effects (potions, spawns) resolve correctly.
 */
public final class CombatDispatch {

    private final TriggerRunner runner;
    private final RuntimeHandles handles;
    private final ContentHolder content;
    private final EconomyService economy;
    private final SoulDebit souls;
    private final VarStore vars;
    private final SuppressionStore suppression;
    private final KnockbackControlStore knockback;
    private final KeepOnDeathStore keepOnDeath;
    private final TeleblockStore teleblock;
    private final ImmuneStore immune;
    // Combat-local consecutive-hit streak (the %combo% fact); owned here since only combat writes it.
    private final ComboStore combo = new ComboStore();
    private final LongSupplier nowTicks;
    private final java.util.function.DoubleSupplier maxHeroicOutgoing; // §F config.yml heroic.max-outgoing-factor
    private final java.util.function.DoubleSupplier maxBonusDamage;    // §L config.yml combat.max-bonus-damage (<0 = uncapped)
    private final java.util.function.DoubleSupplier maxBonusReduction; // §L config.yml combat.max-bonus-reduction (<0 = uncapped)
    private final java.util.function.BooleanSupplier pvpEnabled;       // §L config.yml combat.pvp
    private final java.util.function.BooleanSupplier pveEnabled;       // §L config.yml combat.pve
    private final int attackTriggerId;
    private final int defenseTriggerId;
    private final int bowTriggerId;     // −1 ⇒ no distinct bow trigger; arrow hits fall back to ATTACK
    private final int tridentTriggerId; // −1 ⇒ no distinct trident trigger; trident hits fall back to ATTACK

    /** Combat dispatch with NO soul system (the soul gate is never armed) and no economy. */
    public CombatDispatch(AbilityExecutor executor, RuntimeHandles handles, ContentHolder content,
                          WornStateStore worn, int attackTriggerId, int defenseTriggerId,
                          LongSupplier nowTicks) {
        this(executor, handles, content, worn, attackTriggerId, defenseTriggerId, -1, -1, nowTicks,
                actor -> Optional.empty(), EconomyService.NONE, SoulDebit.NONE, new VarStore(),
                new SuppressionStore(), new KnockbackControlStore(), new KeepOnDeathStore());
    }

    /** Combat dispatch with a soul binder (no economy): an actor in soul mode arms gate 10 from their gem. */
    public CombatDispatch(AbilityExecutor executor, RuntimeHandles handles, ContentHolder content,
                          WornStateStore worn, int attackTriggerId, int defenseTriggerId,
                          LongSupplier nowTicks, Function<Player, Optional<SoulBinding>> soulBinder) {
        this(executor, handles, content, worn, attackTriggerId, defenseTriggerId, -1, -1, nowTicks, soulBinder,
                EconomyService.NONE, SoulDebit.NONE, new VarStore(), new SuppressionStore(),
                new KnockbackControlStore(), new KeepOnDeathStore());
    }

    /** Combat dispatch with a soul binder + economy but no distinct BOW/TRIDENT triggers (arrow hits fire ATTACK). */
    public CombatDispatch(AbilityExecutor executor, RuntimeHandles handles, ContentHolder content,
                          WornStateStore worn, int attackTriggerId, int defenseTriggerId,
                          LongSupplier nowTicks, Function<Player, Optional<SoulBinding>> soulBinder,
                          EconomyService economy) {
        this(executor, handles, content, worn, attackTriggerId, defenseTriggerId, -1, -1, nowTicks, soulBinder,
                economy, SoulDebit.NONE, new VarStore(), new SuppressionStore(),
                new KnockbackControlStore(), new KeepOnDeathStore());
    }

    /**
     * Full combat dispatch: distinct BOW/TRIDENT attacker triggers + soul binder + economy. A bow-arrow
     * hit fires {@code bowTriggerId} and a thrown-trident hit fires {@code tridentTriggerId} (the EE model
     * where ATTACK is melee-only); either id at {@code -1} falls those hits back to {@code attackTriggerId}.
     */
    public CombatDispatch(AbilityExecutor executor, RuntimeHandles handles, ContentHolder content,
                          WornStateStore worn, int attackTriggerId, int defenseTriggerId,
                          int bowTriggerId, int tridentTriggerId,
                          LongSupplier nowTicks, Function<Player, Optional<SoulBinding>> soulBinder,
                          EconomyService economy, SoulDebit souls, VarStore vars, SuppressionStore suppression,
                          KnockbackControlStore knockback, KeepOnDeathStore keepOnDeath) {
        this(executor, handles, content, worn, attackTriggerId, defenseTriggerId, bowTriggerId, tridentTriggerId,
                nowTicks, soulBinder, economy, souls, vars, suppression, knockback, keepOnDeath,
                () -> engine.interact.DamageFold.DEFAULT_MAX_HEROIC_OUTGOING_FACTOR);
    }

    /**
     * As the full ctor, plus the live heroic outgoing-damage ceiling (config.yml {@code heroic.max-outgoing-factor},
     * §F) threaded into each per-event {@link DispatchSink}. The composition root passes
     * {@code () -> master.config().heroic().maxOutgoingFactor()}; the ctor above defaults it.
     */
    public CombatDispatch(AbilityExecutor executor, RuntimeHandles handles, ContentHolder content,
                          WornStateStore worn, int attackTriggerId, int defenseTriggerId,
                          int bowTriggerId, int tridentTriggerId,
                          LongSupplier nowTicks, Function<Player, Optional<SoulBinding>> soulBinder,
                          EconomyService economy, SoulDebit souls, VarStore vars, SuppressionStore suppression,
                          KnockbackControlStore knockback, KeepOnDeathStore keepOnDeath,
                          java.util.function.DoubleSupplier maxHeroicOutgoing) {
        this(executor, handles, content, worn, attackTriggerId, defenseTriggerId, bowTriggerId, tridentTriggerId,
                nowTicks, soulBinder, economy, souls, vars, suppression, knockback, keepOnDeath,
                new TeleblockStore(), new ImmuneStore(), maxHeroicOutgoing,
                () -> -1.0, () -> -1.0, () -> true, () -> true); // combat caps uncapped + PvP/PvE on by default
    }

    /**
     * As above, plus the live cross-cutting combat caps (config.yml {@code combat.*}, §L): the additive
     * bonus ceilings ({@code maxBonusDamage}/{@code maxBonusReduction}, {@code < 0} ⇒ uncapped) threaded onto
     * each event's fold, and the {@code pvp}/{@code pve} gates that decide whether a side's effects apply by
     * the victim's player-ness. The composition root passes live config suppliers; the ctor above defaults
     * them (uncapped, both contexts on).
     */
    public CombatDispatch(AbilityExecutor executor, RuntimeHandles handles, ContentHolder content,
                          WornStateStore worn, int attackTriggerId, int defenseTriggerId,
                          int bowTriggerId, int tridentTriggerId,
                          LongSupplier nowTicks, Function<Player, Optional<SoulBinding>> soulBinder,
                          EconomyService economy, SoulDebit souls, VarStore vars, SuppressionStore suppression,
                          KnockbackControlStore knockback, KeepOnDeathStore keepOnDeath,
                          TeleblockStore teleblock, ImmuneStore immune,
                          java.util.function.DoubleSupplier maxHeroicOutgoing,
                          java.util.function.DoubleSupplier maxBonusDamage,
                          java.util.function.DoubleSupplier maxBonusReduction,
                          java.util.function.BooleanSupplier pvpEnabled,
                          java.util.function.BooleanSupplier pveEnabled) {
        this.handles = Objects.requireNonNull(handles, "handles");
        this.content = Objects.requireNonNull(content, "content");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.souls = Objects.requireNonNull(souls, "souls");
        this.vars = Objects.requireNonNull(vars, "vars");
        this.suppression = Objects.requireNonNull(suppression, "suppression");
        this.knockback = Objects.requireNonNull(knockback, "knockback");
        this.keepOnDeath = Objects.requireNonNull(keepOnDeath, "keepOnDeath");
        this.teleblock = Objects.requireNonNull(teleblock, "teleblock");
        this.immune = Objects.requireNonNull(immune, "immune");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
        this.maxHeroicOutgoing = Objects.requireNonNull(maxHeroicOutgoing, "maxHeroicOutgoing");
        this.maxBonusDamage = Objects.requireNonNull(maxBonusDamage, "maxBonusDamage");
        this.maxBonusReduction = Objects.requireNonNull(maxBonusReduction, "maxBonusReduction");
        this.pvpEnabled = Objects.requireNonNull(pvpEnabled, "pvpEnabled");
        this.pveEnabled = Objects.requireNonNull(pveEnabled, "pveEnabled");
        // The runner reads conditions through a populator backed by the shared VarStore, so a condition's
        // %name% can read a value an earlier SET_VAR wrote (the write side is the per-event DispatchSink below).
        this.runner = new TriggerRunner(executor, worn, soulBinder, nowTicks, FactPopulator.builtin(vars));
        this.attackTriggerId = attackTriggerId;
        this.defenseTriggerId = defenseTriggerId;
        this.bowTriggerId = bowTriggerId;
        this.tridentTriggerId = tridentTriggerId;
    }

    /** Dispatch one entity-on-entity hit: run attacker + defender abilities and fold the result. */
    public void onDamage(EntityDamageByEntityEvent event) {
        Snapshot snapshot = content.snapshot();
        Ability[] abilities = snapshot.abilities();
        Entity rawDamager = event.getDamager();
        // A projectile (bow/trident/snowball) attributes the hit to its shooter for ability purposes;
        // reading the projectile's shooter reference is safe (the projectile is in the firing region).
        // The RAW damager type still decides which attacker trigger fires (melee=ATTACK, arrow=BOW, …).
        Entity damager = rawDamager;
        if (rawDamager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        Entity victimEntity = event.getEntity();
        LivingEntity victim = victimEntity instanceof LivingEntity living ? living : null;
        LivingEntity attacker = damager instanceof LivingEntity living ? living : null;
        Location at = victimEntity.getLocation();
        // Capture the incoming damage BEFORE the additive fold mutates it (line ~end), so the %damage% fact
        // reads the hit's value at activation time, not the post-effect total.
        double incomingDamage = event.getDamage();
        int worldId = TriggerRunner.worldId(snapshot, victimEntity.getWorld());

        DispatchSink sink = new DispatchSink(handles, economy, souls, vars, suppression, knockback, keepOnDeath,
                teleblock, immune, nowTicks, maxHeroicOutgoing);
        sink.fold().caps(maxBonusDamage.getAsDouble(), maxBonusReduction.getAsDouble()); // §L combat caps, live

        // PvP/PvE gates (config.yml combat.pvp/pve): a side whose context is disabled contributes nothing to
        // the fold. The context is decided by the VICTIM's player-ness — a player victim is PvP, else PvE.
        boolean victimIsPlayer = victimEntity instanceof Player;

        // Attack side: the player damager's abilities act on the victim (self = the attacker). The trigger
        // is melee ATTACK, or the distinct BOW/TRIDENT trigger when the hit came via that projectile.
        if (damager instanceof Player attackerPlayer && contextEnabled(victimIsPlayer)) {
            int attackId = attackTrigger(rawDamager, attackTriggerId, bowTriggerId, tridentTriggerId);
            // Extend the attacker's consecutive-hit streak for the %combo% fact (RAGE-style scaling, §3.4).
            int streak = combo.hit(attackerPlayer.getUniqueId(), nowTicks.getAsLong());
            runner.run(abilities, snapshot.generation(), worldId, attackId, true,
                    attackerPlayer,
                    new ActivationContext(attackerPlayer, victim, null, at, incomingDamage, null, streak), sink,
                    snapshot.stableKeys());
        }
        // Defense side: the player victim's DEFENSE abilities retaliate against the attacker. A player victim
        // always implies the PvP gate when the attacker is a player, else the PvE gate.
        if (victimEntity instanceof Player defenderPlayer && contextEnabled(damager instanceof Player)) {
            runner.run(abilities, snapshot.generation(), worldId, defenseTriggerId, false,
                    defenderPlayer, new ActivationContext(defenderPlayer, attacker, attacker, at, incomingDamage, null),
                    sink, snapshot.stableKeys());
        }

        // Fold every damage contribution onto the event ONCE (§6.1); honour a cancel; flush deferred work.
        event.setDamage(sink.fold().apply(event.getDamage()));
        if (sink.armorIgnored()) {
            // IGNORE_ARMOR: zero the server's armor + enchant-protection reductions AFTER setDamage (which
            // recomputes all modifiers from base). isApplicable is the cross-version capability probe — a
            // version/cause without a given modifier is a no-op, so no version gate is needed (§ combat-flags).
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
     * The attacker-side trigger for a hit, by the RAW damager type: a thrown trident fires
     * {@code tridentId}, a bow/crossbow arrow (any {@link AbstractArrow} that is not a trident) fires
     * {@code bowId}, and everything else (melee, or any projectile with no distinct trigger) fires
     * {@code attackId}. A {@code bowId}/{@code tridentId} of {@code -1} means "no distinct trigger" and
     * also falls back to {@code attackId}. {@link Trident} extends {@link AbstractArrow}, so it is tested
     * first.
     */
    static int attackTrigger(Entity rawDamager, int attackId, int bowId, int tridentId) {
        if (rawDamager instanceof Trident && tridentId >= 0) {
            return tridentId;
        }
        if (rawDamager instanceof AbstractArrow && bowId >= 0) {
            return bowId;
        }
        return attackId;
    }
}

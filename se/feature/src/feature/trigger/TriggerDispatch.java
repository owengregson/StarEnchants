package feature.trigger;

import compile.load.ContentHolder;
import compile.model.Ability;
import compile.model.Snapshot;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.ActorProbe;
import engine.run.FactPopulator;
import engine.run.UseAttempt;
import engine.sink.SinkEnv;
import engine.stores.DotAmplifyStore;
import engine.sink.SinkReadback;
import engine.trigger.TriggerRegistry;
import feature.soul.SoulBinding;
import item.worn.WornStateStore;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntPredicate;
import feature.combat.MineDrops;
import feature.combat.ProjectileHoming;
import feature.compat.DropControl;
import feature.compat.Hands;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import engine.sink.SinkFactory;

/**
 * Dispatches the NON-combat triggers (§3.3) — MINE, KILL, FALL, FIRE, INTERACT* — that {@code CombatDispatch}
 * (ATTACK/DEFENSE on {@code EntityDamageByEntityEvent}) does not cover. Routes the actor's worn abilities
 * through the shared {@link TriggerRunner} into a per-event {@link SinkReadback}, then applies its read-backs:
 * a neutral event ({@link #fire}) honours only a {@code cancelEvent}; a damage event ({@link #fireDamage})
 * also folds the accumulated deltas onto it. Trigger ids resolve once at construction; an absent trigger is
 * {@code -1} and its {@code fire} is a no-op.
 */
public final class TriggerDispatch {

    private final TriggerRunner runner;
    private final AbilityExecutor executor; // §B lifecycle runs effects gatelessly, outside the runner's gate path
    private final SinkFactory sinkFactory;
    private final ContentHolder content;
    // The per-boot sink wiring, threaded to every per-event sink so a write and its separate-event reader share stores.
    private final SinkEnv env;
    private final IntPredicate attackTrigger;
    private final Hands hands;             // held-tool read for MINE effective drops (§4 era seam)
    private final DropControl dropControl; // vanilla-drop suppression for SMELT/TELEPORT_DROPS (§4 era seam)

    public final int mine;
    public final int kill;
    public final int fall;
    public final int fire;
    public final int interact;
    public final int interactLeft;
    public final int interactRight;
    public final int death;
    public final int bowFire;
    public final int fishing;
    public final int eat;
    public final int itemDamage;
    public final int breakItem;
    public final int repeating;
    public final int held;    // §B HELD lifecycle — fired by the LifecycleDriver, not a Bukkit listener
    public final int passive; // §B PASSIVE lifecycle — fired by the LifecycleDriver, not a Bukkit listener
    public final int command; // §B COMMAND — fired by the configured CommandTriggerCommand
    public final int impact;  // fired by a landing FALLING_BLOCK via FallingBlockListener (victim = what it hit)
    public final int expGain; // fired by TriggerListeners.onExpChange; scales the PlayerExpChangeEvent's XP in place
    public final int use;     // §3.6 USE — fired only by the use-item right-click flow (UseItemService)
    public final int guardianHurt; // fired by GuardianHurtListener when a summoned guardian is hurt (victim = the guardian)
    public final int hurt;    // every damage-taken event, any cause — rides the same two damage paths as FALL/FIRE and DEFENSE
    public final int equipChange; // fired by EquipChangeDriver on the worn-ability diff (%equipchange% = EQUIP|UNEQUIP)
    public final int projectileLand; // fired by ProjectileLandListener at a player projectile's landing point
    public final int proximityEvent; // fired on nearby WEARERS by someone else's event (a player death)

    /**
     * Trigger dispatch over the shared per-boot {@link SinkEnv}. GIVE_MONEY/TAKE_MONEY on MINE/KILL/… and the
     * TELEBLOCK/IMMUNE flags all ride the env's economy/stores, shared with their separate-event listeners.
     */
    public TriggerDispatch(AbilityExecutor executor, SinkFactory sinkFactory, ActorProbe probe, ContentHolder content,
                           WornStateStore worn, TriggerRegistry triggers,
                           Function<Player, Optional<SoulBinding>> soulBinder, SinkEnv env,
                           Hands hands, DropControl dropControl) {
        this.sinkFactory = Objects.requireNonNull(sinkFactory, "sinkFactory");
        this.content = Objects.requireNonNull(content, "content");
        this.env = Objects.requireNonNull(env, "env");
        this.hands = Objects.requireNonNull(hands, "hands");
        this.dropControl = Objects.requireNonNull(dropControl, "dropControl");
        // Conditions read through a VarStore-backed populator so a %name% can read an earlier SET_VAR write; the
        // shared RageStackStore sources %ragestacks% (§3) for any non-combat trigger that reads it.
        this.runner = new TriggerRunner(executor, worn, soulBinder, env.nowTicks(),
                FactPopulator.builtin(env.stores(), probe));
        this.executor = Objects.requireNonNull(executor, "executor");
        this.attackTrigger = triggers.attackTriggers();
        this.mine = triggers.idOf("MINE").orElse(-1);
        this.kill = triggers.idOf("KILL").orElse(-1);
        this.fall = triggers.idOf("FALL").orElse(-1);
        this.fire = triggers.idOf("FIRE").orElse(-1);
        this.interact = triggers.idOf("INTERACT").orElse(-1);
        this.interactLeft = triggers.idOf("INTERACT_LEFT").orElse(-1);
        this.interactRight = triggers.idOf("INTERACT_RIGHT").orElse(-1);
        this.death = triggers.idOf("DEATH").orElse(-1);
        this.bowFire = triggers.idOf("BOW_FIRE").orElse(-1);
        this.fishing = triggers.idOf("FISHING").orElse(-1);
        this.eat = triggers.idOf("EAT").orElse(-1);
        this.itemDamage = triggers.idOf("ITEM_DAMAGE").orElse(-1);
        this.breakItem = triggers.idOf("BREAK").orElse(-1);
        this.repeating = triggers.idOf("REPEATING").orElse(-1);
        this.held = triggers.idOf("HELD").orElse(-1);
        this.passive = triggers.idOf("PASSIVE").orElse(-1);
        this.command = triggers.idOf("COMMAND").orElse(-1);
        this.impact = triggers.idOf("IMPACT").orElse(-1);
        this.expGain = triggers.idOf("EXP_GAIN").orElse(-1);
        this.use = triggers.idOf("USE").orElse(-1);
        this.guardianHurt = triggers.idOf("GUARDIAN_HURT").orElse(-1);
        this.hurt = triggers.idOf("HURT").orElse(-1);
        this.equipChange = triggers.idOf("EQUIP_CHANGE").orElse(-1);
        this.projectileLand = triggers.idOf("PROJECTILE_LAND").orElse(-1);
        this.proximityEvent = triggers.idOf("PROXIMITY_EVENT").orElse(-1);
    }

    /**
     * Fire a neutral (non-damage) trigger. {@code cancellable} (the firing event, or {@code null}) is cancelled
     * iff an effect asked for it. The heroic fold is inert here (no damage event reads it).
     */
    public void fire(Player actor, int triggerId, ActivationContext context, Cancellable cancellable) {
        if (triggerId < 0) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        SinkReadback sink = newSink();
        runner.run(snapshot.abilities(), snapshot.generation(), worldId(snapshot, context), triggerId,
                attackTrigger.test(triggerId), actor, context, sink, snapshot.stableKeys());
        if (cancellable != null && sink.cancelled()) {
            cancellable.setCancelled(true);
        }
        sink.flush();
    }

    /**
     * Fire MINE for a block break, then apply the drop read-backs to {@code event}: {@code cancelEvent}, plus
     * {@code SMELT} / {@code TELEPORT_DROPS} (Cosmic Enchants-style parity). Runs on the block's region thread.
     */
    public void fireMine(Player actor, ActivationContext context, BlockBreakEvent event) {
        if (mine < 0) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        SinkReadback sink = newSink();
        runner.run(snapshot.abilities(), snapshot.generation(), worldId(snapshot, context), mine,
                attackTrigger.test(mine), actor, context, sink, snapshot.stableKeys());
        if (sink.cancelled()) {
            event.setCancelled(true);
        } else {
            MineDrops.apply(event, sink.smeltRequested(), sink.teleportDropsRequested(), hands, dropControl);
        }
        sink.flush();
    }

    /**
     * Fire BOW_FIRE for a bow shot, then home the projectile onto the nearest line-of-sight target if a
     * {@code SEEK} proc asked for it (steering runs on the projectile's entity scheduler — Folia-correct).
     * Honours a {@code cancelEvent}.
     */
    public void fireBow(Player shooter, ActivationContext context, EntityShootBowEvent event) {
        if (bowFire < 0) {
            fire(shooter, bowFire, context, event);
            return;
        }
        Snapshot snapshot = content.snapshot();
        SinkReadback sink = newSink();
        runner.run(snapshot.abilities(), snapshot.generation(), worldId(snapshot, context), bowFire,
                attackTrigger.test(bowFire), shooter, context, sink, snapshot.stableKeys());
        if (sink.cancelled()) {
            event.setCancelled(true);
        } else {
            if (sink.seekRequested() && event.getProjectile() instanceof Projectile projectile) {
                ProjectileHoming.start(shooter, projectile);
            }
            if (sink.projectileDressing() != null && event.getProjectile() != null) {
                // The projectile exists only here, so the sink's half of PROJECTILE_DRESSING is called with it
                // in hand — before flush, so the spawn rides the plan like every other world intent.
                sink.attachProjectileRider(event.getProjectile(), sink.projectileDressing());
            }
        }
        sink.flush();
    }

    /**
     * Fire a damage-direction trigger (FALL/FIRE — defender side) and fold the deltas onto {@code event}, so a
     * defensive/heroic reduction actually softens the damage.
     */
    public void fireDamage(Player actor, int triggerId, ActivationContext context,
                           org.bukkit.event.entity.EntityDamageEvent event, boolean applyHeroic) {
        if (triggerId < 0) {
            return;
        }
        fireDamage(actor, triggerId, -1, context, event, applyHeroic);
    }

    /**
     * Fire a cause-specific damage trigger AND the all-cause {@code HURT} into ONE sink, so both walks share a
     * single fold and a single {@code setDamage} — an ability authors one or the other, but a wearer may carry
     * both. The worn heroic reduction is contributed by the FIRST present walk only: adding it per walk would
     * double it. Either id may be {@code -1}; with both absent this degrades to the heroic-only fold.
     */
    public void fireDamage(Player actor, int triggerId, int alsoTriggerId, ActivationContext context,
                           org.bukkit.event.entity.EntityDamageEvent event, boolean applyHeroic) {
        if (triggerId < 0 && alsoTriggerId < 0) {
            if (applyHeroic) {
                fireEnvironmentalHeroic(actor, event);
            }
            return;
        }
        Snapshot snapshot = content.snapshot();
        SinkReadback sink = newSink();
        int worldId = worldId(snapshot, context);
        boolean heroic = applyHeroic;
        if (triggerId >= 0) {
            runner.run(snapshot.abilities(), snapshot.generation(), worldId, triggerId,
                    attackTrigger.test(triggerId), actor, context, sink, snapshot.stableKeys(), heroic);
            heroic = false;
        }
        if (alsoTriggerId >= 0) {
            runner.run(snapshot.abilities(), snapshot.generation(), worldId, alsoTriggerId,
                    attackTrigger.test(alsoTriggerId), actor, context, sink, snapshot.stableKeys(), heroic);
        }
        amplifyDot(actor, event, sink);
        event.setDamage(sink.fold().apply(event.getDamage()));
        if (sink.cancelled()) {
            event.setCancelled(true);
        }
        sink.flush();
    }

    /**
     * Fold ONLY the worn heroic reduction onto {@code event} — environmental damage with no trigger, softened
     * by heroic under {@code reduction-scope: ALL} (§F). Runs no trigger abilities.
     */
    public void fireEnvironmentalHeroic(Player actor, org.bukkit.event.entity.EntityDamageEvent event) {
        Snapshot snapshot = content.snapshot();
        SinkReadback sink = newSink();
        runner.contributeHeroicReduction(snapshot.generation(), actor, sink);
        amplifyDot(actor, event, sink);
        event.setDamage(sink.fold().apply(event.getDamage()));
        sink.flush();
    }

    /**
     * Fold {@code actor}'s DOT_AMPLIFY_MARK multiplier onto a wither/poison tick. It rides the same fold and
     * the same single {@code setDamage} as the trigger walks, so an amplified tick is still ONE priced hit —
     * and it applies whether or not the bearer carries any ability for this trigger, because the mark is the
     * attacker's, not theirs. Expressed as an outgoing bonus because this path sets no attack-scale, so the
     * additive term is exactly the authored multiplier (§L attack-scale is CombatDispatch's alone).
     */
    private void amplifyDot(Player actor, org.bukkit.event.entity.EntityDamageEvent event, SinkReadback sink) {
        int causeBit = dotCauseBit(event.getCause());
        if (causeBit == 0) {
            return;
        }
        double factor = env.stores().dotAmplify().factor(actor.getUniqueId(), env.nowTicks().getAsLong(), causeBit);
        if (factor > 1.0) {
            sink.fold().addOutgoing(factor - 1.0);
        }
    }

    /** The {@link DotAmplifyStore} bit for a damage cause, or {@code 0} when the cause is not amplifiable. */
    private static int dotCauseBit(org.bukkit.event.entity.EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case WITHER -> DotAmplifyStore.CAUSE_WITHER;
            case POISON -> DotAmplifyStore.CAUSE_POISON;
            default -> 0;
        };
    }

    /**
     * Fire ONE repeating ability — the §B {@link feature.trigger.RepeatingDriver}'s timer body, on the player's
     * entity thread. Runs just that ability through the full gate sequence; chance/cooldown/condition gates
     * still apply each period. No firing event, so nothing to cancel.
     */
    public void fireRepeating(Player actor, int abilityId) {
        if (repeating < 0 || abilityId < 0) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        ActivationContext context = new ActivationContext(actor, null, null, actor.getLocation());
        SinkReadback sink = newSink();
        runner.runCandidates(snapshot.abilities(), snapshot.generation(), worldId(snapshot, context),
                repeating, false, actor, context, sink, snapshot.stableKeys(), new int[]{abilityId});
        sink.flush();
    }

    /**
     * Fire the HELD/PASSIVE lifecycle transition (§B) on the player's entity thread, into ONE sink. NOT gated:
     * a maintained buff is deterministic. Ordering: STOP before START (level-swap removes the old buff before
     * re-applying); STOP is unconditional (a buff can never leak); START honours only the world-blacklist.
     */
    public void fireLifecycle(Player actor, List<Ability> stops, List<Ability> starts) {
        if (held < 0 && passive < 0) {
            return;
        }
        if (stops.isEmpty() && starts.isEmpty()) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        ActivationContext context = new ActivationContext(actor, null, null, actor.getLocation());
        int worldId = worldId(snapshot, context);
        SinkReadback sink = newSink();
        for (Ability ability : stops) {
            executor.runLifecycle(ability, context, sink, true); // teardown=true: unconditional, never world-gated
        }
        for (Ability ability : starts) {
            if (!ability.blockedInWorld(worldId)) { // gate-1 only: a world-disabled passive does not turn on
                executor.runLifecycle(ability, context, sink, false);
            }
        }
        sink.flush();
    }

    /**
     * Apply/renew the maintained PASSIVE/HELD potion buffs (§B) into ONE sink: each {@code desired} entry
     * (potion handle id → amplifier) is (re)applied at a permanent duration, each {@code remove} handle is
     * cleared. The diff is owned by {@link PassiveEffectDriver}; this only executes it. Runs on the player's
     * own thread.
     */
    public void applyPassivePotions(Player player, Map<Integer, Integer> desired, Set<Integer> remove) {
        if (desired.isEmpty() && remove.isEmpty()) {
            return;
        }
        SinkReadback sink = newSink();
        for (Map.Entry<Integer, Integer> entry : desired.entrySet()) {
            sink.potion(player, entry.getKey(), entry.getValue(), PassiveEffectDriver.PERMANENT_TICKS);
        }
        for (int handle : remove) {
            sink.removePotion(player, handle);
        }
        sink.flush();
    }

    /**
     * Run ONE cleanse on {@code player} — the same {@code CURE category: HARMFUL} sweep clarity's Bless fires on
     * a timer and the Cow Pet fires on right-click, through the identical sink path so the three can never
     * diverge (ADR-0072). {@code /bless} is exactly this, once. Runs on the player's own thread.
     */
    public void cleanse(Player player) {
        SinkReadback sink = newSink();
        sink.cureByCategory(player, engine.sink.PotionCategories.HARMFUL);
        sink.flush();
    }

    /**
     * Reconcile the plugin-owned worn max-health modifier to {@code total} (the {@code HEALTH}-on-PASSIVE/HELD
     * channel). The sum is owned by {@link MaxHealthDriver}; this only executes it — SET-not-add, so repeated
     * refreshes are idempotent. Runs on the player's own thread.
     */
    public void applyWornMaxHealth(Player player, double total) {
        SinkReadback sink = newSink();
        sink.applyWornMaxHealth(player, total);
        sink.flush();
    }

    /**
     * Reconcile the plugin-owned worn water-movement modifier to {@code total} (the {@code WATER_SPEED}
     * channel, ADR-0060). The sum is owned by {@link WaterSpeedDriver}; this only executes it — SET-not-add,
     * so repeated refreshes are idempotent. Runs on the player's own thread.
     */
    public void applyWornWaterSpeed(Player player, double total) {
        SinkReadback sink = newSink();
        sink.applyWornWaterSpeed(player, total);
        sink.flush();
    }

    /**
     * Fire the §B COMMAND trigger — the body of the configured {@code CommandTriggerCommand}. A normal triggered
     * activation: runs the full gate sequence (chance/cooldown/condition/souls) like any other trigger, only the
     * entry point differs. Neutral, so the heroic fold is inert.
     */
    public void fireCommand(Player actor) {
        fire(actor, command, new ActivationContext(actor, null, null, actor.getLocation()), null);
    }

    /**
     * Fire the IMPACT trigger — a landing {@code FALLING_BLOCK} hit {@code victim}. Runs {@code owner}'s
     * IMPACT abilities (gated normally; a short cooldown on the ability dedups a grid's many landings) with the
     * carried hit damage exposed as {@code %damage%}, so the impact effects are fully author-defined. Runs on
     * the landing block's region; {@code owner}'s worn abilities resolve from the immutable WornState
     * (Folia-safe cross-region read), and effects route to {@code victim} via the sink.
     */
    public void fireImpact(Player owner, LivingEntity victim, double carriedDamage) {
        if (impact < 0 || owner == null || victim == null) {
            return;
        }
        ActivationContext context =
                new ActivationContext(owner, victim, null, victim.getLocation(), carriedDamage, null);
        fire(owner, impact, context, null);
    }

    /**
     * Fire the GUARDIAN_HURT trigger — a summoned guardian owned by {@code owner} took {@code damage} (ADR-0049
     * Blood Link). Runs {@code owner}'s GUARDIAN_HURT abilities (gated normally) with the hit damage exposed as
     * {@code %damage%}; the guardian rides the context as the victim so facts (health, distance) can read it.
     * Modeled on {@link #fireImpact}: runs on the guardian's region, {@code owner}'s worn state resolves from the
     * immutable WornState, and any owner mutation (Blood Link's heal) routes through the sink.
     */
    public void fireGuardianHurt(Player owner, LivingEntity guardian, double damage) {
        if (guardianHurt < 0 || owner == null || guardian == null) {
            return;
        }
        ActivationContext context =
                new ActivationContext(owner, guardian, null, guardian.getLocation(), damage, null);
        fire(owner, guardianHurt, context, null);
    }

    /** Fire EXP_GAIN, then scale the gained XP by the accumulated EXP_MULTIPLY factor (recursion-safe: no new XP granted). */
    public void fireExp(Player actor, ActivationContext context, org.bukkit.event.player.PlayerExpChangeEvent event) {
        if (expGain < 0) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        SinkReadback sink = newSink();
        runner.run(snapshot.abilities(), snapshot.generation(), worldId(snapshot, context), expGain,
                attackTrigger.test(expGain), actor, context, sink, snapshot.stableKeys());
        double m = sink.expMultiplier();
        if (m != 1.0) {
            event.setAmount(Math.max(0, (int) Math.round(event.getAmount() * m)));
        }
        sink.flush();
    }

    /**
     * Fire the §3.6 USE trigger for a right-clicked use-item: resolve {@code stableKeys} (the def's ability keys,
     * in order) to dense ids against the CURRENT snapshot — so the run and its read-back share one snapshot even
     * across a concurrent reload — run them through the shared cold-path machinery, flush, and return the compact
     * {@link UseAttempt} the {@link feature.useitem.UseItemService} renders feedback from. A {@code -1} key stays
     * {@code -1} in the candidate array so the returned condition index still aligns with the def's ability order.
     */
    public UseAttempt fireUse(Player actor, java.util.List<String> stableKeys) {
        if (use < 0) {
            return UseAttempt.none();
        }
        Snapshot snapshot = content.snapshot();
        int[] candidates = new int[stableKeys.size()];
        for (int i = 0; i < candidates.length; i++) {
            candidates[i] = snapshot.stableKeys().idOf(stableKeys.get(i));
        }
        ActivationContext context = new ActivationContext(actor, null, null, actor.getLocation());
        SinkReadback sink = newSink();
        UseAttempt attempt = runner.runUse(snapshot.abilities(), snapshot.generation(),
                worldId(snapshot, context), use, actor, context, sink, snapshot.stableKeys(), candidates);
        sink.flush();
        return attempt;
    }

    /**
     * The hard ceiling on the PROXIMITY_EVENT observer walk, in blocks. It bounds the scan, it is not the
     * gameplay range: an ability picks that itself with {@code %distance%}, which on this activation is exactly
     * the observer-to-subject distance. Deaths are rare, so the walk is cold — but it still has to be bounded.
     *
     * <p>Sized to clear the widest range content actually authors (Avenging Angel reaches 98 blocks at L4) —
     * a ceiling below an authored radius does not shorten that ability, it makes it silently never fire. One
     * {@code getNearbyEntities} per death is affordable at this size precisely because deaths are rare; this
     * is not a per-hit scan.
     */
    public static final double PROXIMITY_RADIUS = 100.0;

    /**
     * Fire PROXIMITY_EVENT on {@code observer} because {@code subject} died nearby. The subject rides the
     * activation as the VICTIM, so {@code %victim.relation%} (the one installed alliance predicate),
     * {@code %distance%} and every other victim fact price against them — which is how an ability authors its
     * range and its ally/enemy filter, with no second parameterisation surface. Anchored at the death location
     * like {@link #fireImpact}, so an {@code @Aoe} centres on the body. Runs on the subject's region thread,
     * where every observer the walk found is co-region.
     */
    public void fireProximity(Player observer, Player subject) {
        if (proximityEvent < 0 || observer == null || subject == null
                || observer.getUniqueId().equals(subject.getUniqueId())) {
            return;
        }
        fire(observer, proximityEvent, new ActivationContext(observer, subject, null, subject.getLocation()), null);
    }

    /**
     * Fire PROJECTILE_LAND where {@code shooter}'s projectile came down. The activation ANCHORS at {@code at}
     * rather than the shooter, so an {@code @Aoe} selector centres on the impact — the whole point of the
     * trigger. No victim: an entity hit is BOW's, and this only ever runs for a world landing. Runs on the
     * landing region's thread; the shooter's abilities resolve from the immutable WornState (a safe cross-region
     * read) and every mutation routes through the sink, the {@link #fireImpact} pattern.
     */
    public void fireProjectileLand(Player shooter, Location at) {
        if (projectileLand < 0 || shooter == null || at == null) {
            return;
        }
        fire(shooter, projectileLand, new ActivationContext(shooter, null, null, at), null);
    }

    /**
     * Fire EQUIP_CHANGE for one direction's worth of abilities — the {@link EquipChangeDriver}'s diff, on the
     * player's own thread, into ONE sink. Candidates are explicit dense ids because an UNEQUIP walk's ability is
     * no longer in the worn state; they still run the full gate sequence (chance/cooldown/condition/souls), only
     * the candidate source differs. No firing event, so nothing to cancel.
     */
    public void fireEquipChange(Player actor, int[] candidates, String direction) {
        if (equipChange < 0 || candidates.length == 0) {
            return;
        }
        Snapshot snapshot = content.snapshot();
        ActivationContext context = ActivationContext.equipChange(actor, direction);
        SinkReadback sink = newSink();
        runner.runDetached(snapshot.abilities(), worldId(snapshot, context), equipChange,
                actor, context, sink, snapshot.stableKeys(), candidates);
        sink.flush();
    }

    /**
     * Teleport {@code player} to {@code to} through the shared sink (ADR-0061, the Mole recall): the
     * {@code Sink.teleport} intent clones the destination, exempts the hop from a bundled anti-cheat and runs
     * the era leaf on the player's OWN entity scheduler (async on Folia/modern, synchronous on 1.8) — the
     * {@link #applyWornMaxHealth} passthrough idiom over a fresh per-call sink, so this is safe from any cold
     * path (never from inside an already-flushed plan — the cage rule).
     */
    public void teleport(Player player, Location to) {
        SinkReadback sink = newSink();
        sink.teleport(player, to);
        sink.flush();
    }

    /**
     * Emit a batch of coloured-dust motes through the shared sink (ADR-0061 amendment, the Mole home-window
     * visuals): each point rides the sink's dust intent, so it is region-routed on Folia and colour-degrades
     * on 1.8.9 exactly like PARTICLE_LINE. Cold-path only (a 2 Hz cosmetic pulse), never from a gate walk.
     */
    public void dust(List<Location> points, int particleId, int r, int g, int b, float size) {
        if (particleId < 0 || points.isEmpty()) {
            return;
        }
        SinkReadback sink = newSink();
        for (Location point : points) {
            sink.dust(point, particleId, r, g, b, size, 1);
        }
        sink.flush();
    }

    /**
     * GRAPPLE reel/zip through a fresh sink (ADR-0071, Leviathan's Reach): GrappleService has already resolved
     * the mode and geometry on the actor's thread, so this just mints + flushes the {@code grapple} intent —
     * safe from the cold activation path (never from inside an already-flushed plan, the teleport/dust idiom).
     */
    public void grapple(Player actor, Location eye, LivingEntity victim, Location reelTo, Location hookPoint,
                        int flightTicks, int slowPotionId, int slowAmplifier, int slowTicks,
                        org.bukkit.util.Vector zip, int particleId, int r, int g, int b, float size,
                        double density) {
        SinkReadback sink = newSink();
        sink.grapple(actor, eye, victim, reelTo, hookPoint, flightTicks, slowPotionId, slowAmplifier,
                slowTicks, zip, particleId, r, g, b, size, density);
        sink.flush();
    }

    /**
     * Entity-typed teleport through a fresh sink (ADR-0071): Castling swaps a possibly-mob victim and the
     * Javelin camera-lock re-asserts the victim's position every tick — both need the {@link engine.sink.Sink#teleport(Entity, Location)}
     * intent (era leaf on the target's own scheduler, {@code teleportAsync} on Folia), which the {@link #teleport(Player, Location)}
     * passthrough could not reach because its parameter was a {@link Player}. Same fresh-per-call idiom, so it is
     * safe from any cold path (never from inside an already-flushed plan — the cage rule).
     */
    public void teleportEntity(Entity target, Location to) {
        SinkReadback sink = newSink();
        sink.teleport(target, to);
        sink.flush();
    }

    /**
     * Apply one potion effect through a fresh sink (ADR-0071, the Javelin post-lock Nausea): the sink's
     * {@code potion} intent resolves the interned effect id through the era leaf, so this is the same
     * era-resolution path as an authored POTION op. A {@code < 0} id (absent on this version) skips silently —
     * the cue-table {@code orElse(-1)} convention. Cold-path only, never from inside a flushed plan.
     */
    public void potion(LivingEntity target, int potionEffectId, int amplifier, int durationTicks) {
        if (potionEffectId < 0) {
            return;
        }
        SinkReadback sink = newSink();
        sink.potion(target, potionEffectId, amplifier, durationTicks);
        sink.flush();
    }

    /** Play a world-audible sound at {@code at} through the shared sink (region-routed, era-resolved). */
    public void sound(Location at, int soundId, float volume, float pitch) {
        if (soundId < 0) {
            return;
        }
        SinkReadback sink = newSink();
        sink.sound(at, soundId, volume, pitch);
        sink.flush();
    }

    private SinkReadback newSink() {
        return sinkFactory.create(env);
    }

    private static int worldId(Snapshot snapshot, ActivationContext context) {
        Location at = context.location();
        return TriggerRunner.worldId(snapshot, at == null ? null : at.getWorld());
    }
}

package engine.run;

import compile.cond.VarBinding;
import compile.cond.VarKind;
import compile.model.FactMask;
import engine.condition.BuiltinVars;
import engine.condition.FactBuffer;
import engine.condition.VarVocabulary;
import engine.stores.RageStackStore;
import engine.stores.VarStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import platform.caps.Regions;

/**
 * Fills a condition {@link FactBuffer} from one activation's live context (docs/architecture.md §3.4).
 * Built once at boot from the same vocabulary the compiler lowered {@code %scope.name%} against, so a
 * compiled condition's slot and the buffer agree by construction.
 *
 * <p>One buffer per worker thread, cleared and refilled in place (the SAME instance is returned), keeping
 * the per-hit pipeline allocation-free — safe only because the buffer never escapes the synchronous pass.
 *
 * <p>Folia: every read runs on the firing thread; an entity owned by another region (e.g. a cross-region
 * projectile shooter) fails hard on read, so each entity side is wrapped to leave its facts defaulted
 * rather than abort the activation.
 */
public final class FactPopulator {

    @FunctionalInterface
    private interface ActorD { double read(Player actor); }

    @FunctionalInterface
    private interface ActorB { boolean read(Player actor); }

    @FunctionalInterface
    private interface ActorS { String read(Player actor); }

    @FunctionalInterface
    private interface VictimD { double read(LivingEntity victim); }

    @FunctionalInterface
    private interface VictimB { boolean read(LivingEntity victim); }

    @FunctionalInterface
    private interface VictimS { String read(LivingEntity victim); }

    private record ActorNum(int slot, ActorD src) {}

    private record ActorFlag(int slot, ActorB src) {}

    private record ActorStr(int slot, ActorS src) {}

    private record VictimNum(int slot, VictimD src) {}

    private record VictimFlag(int slot, VictimB src) {}

    private record VictimStr(int slot, VictimS src) {}

    private final ThreadLocal<FactBuffer> buffer;
    private final VarStore vars;
    private final RageStackStore rageStacks; // §3 %ragestacks% source — an actor-scoped read (mask-gated)
    private final UnaryOperator<String> papiDelegate;
    private final ActorProbe probe; // §3.3 era-specific entity/material reads (swim/glide/isAir/main-hand)

    /** {@code %victim.mobtype%} soft hook (ADR-0027): boot-installed so the engine never references the MythicMobs API. */
    private static volatile java.util.function.Function<org.bukkit.entity.Entity, String> entityTypeResolver =
            entity -> "";

    /** A {@code null} resets to empty. */
    public static void entityTypeResolver(java.util.function.Function<org.bukkit.entity.Entity, String> resolver) {
        entityTypeResolver = resolver == null ? entity -> "" : resolver;
    }
    private final List<ActorNum> actorNum = new ArrayList<>();
    private final List<ActorFlag> actorFlag = new ArrayList<>();
    private final List<ActorStr> actorStr = new ArrayList<>();
    private final List<VictimNum> victimNum = new ArrayList<>();
    private final List<VictimFlag> victimFlag = new ArrayList<>();
    private final List<VictimStr> victimStr = new ArrayList<>();
    // Context facts come from the event payload, not an actor/victim entity (slot −1 if absent).
    private final int damageSlot;
    private final int blockTypeSlot;
    private final int isBlockSlot;
    private final int worldRainingSlot;
    private final int worldThunderingSlot;
    private final int worldTimeSlot;
    private final int comboSlot;
    private final int distanceSlot;       // actor↔victim distance in blocks (derived, Folia-guarded)
    private final int nearbyEnemiesSlot;  // living entities within NEARBY_RADIUS (derived, Folia-guarded)
    private final int victimInZoneSlot;   // victim inside an actor-owned MARK_ZONE (derived, Folia-guarded)
    // ADR-0049: gank/cause/item-damage context facts (carried on the ActivationContext) + one derived (behindvictim).
    private final int recentAttackersSlot;   // distinct recent attackers (from the context; sourced in CombatDispatch)
    private final int attackerIndexSlot;     // 1-based first-seen order of THIS attacker (from the context)
    private final int damageCauseSlot;       // DamageCause name of the triggering event (from the context)
    private final int itemDamageArmorSlot;   // ITEM_DAMAGE: worn-armor vs held (from the context)
    private final int actorBehindVictimSlot; // actor behind the victim's facing (derived, Folia-guarded)
    private final int actorBelowVictimSlot;  // actor's feet below the victim's (derived, Folia-guarded, ADR-0052)
    private final int rageStacksSlot;        // %ragestacks% (actor-scoped store read, mask-gated)

    /** Search radius for {@code %nearbyenemies%}, in blocks. */
    private static final double NEARBY_RADIUS = 8.0;

    /** No dynamic-var store and no PAPI: unknown tokens resolve to null. */
    public FactPopulator(VarVocabulary vocabulary, ActorProbe probe) {
        this(vocabulary, new VarStore(), t -> null, probe, new RageStackStore());
    }

    /** Backed by a shared {@link VarStore} but no rage-stack source ({@code %ragestacks%} reads 0) — the pre-§3 form. */
    public FactPopulator(VarVocabulary vocabulary, VarStore vars, UnaryOperator<String> papiDelegate, ActorProbe probe) {
        this(vocabulary, vars, papiDelegate, probe, new RageStackStore());
    }

    /**
     * Backed by a shared {@link VarStore} ({@code SET_VAR}/{@code INVERT_VAR} write target), an optional PAPI
     * delegate, and the {@link RageStackStore} that sources {@code %ragestacks%} (§3). Unknown {@code %name%}
     * resolution order: built-in slot → player dynamic var → {@code papiDelegate} → null. {@code probe} is the
     * era-specific entity/material read seam (§3.3).
     */
    public FactPopulator(VarVocabulary vocabulary, VarStore vars, UnaryOperator<String> papiDelegate, ActorProbe probe,
                         RageStackStore rageStacks) {
        Objects.requireNonNull(vocabulary, "vocabulary");
        this.vars = Objects.requireNonNull(vars, "vars");
        this.rageStacks = Objects.requireNonNull(rageStacks, "rageStacks");
        this.papiDelegate = papiDelegate == null ? t -> null : papiDelegate;
        this.probe = Objects.requireNonNull(probe, "probe");
        this.buffer = ThreadLocal.withInitial(vocabulary::newFactBuffer);

        addActorNum(vocabulary, "actor.health", Player::getHealth);
        addActorNum(vocabulary, "actor.maxhealth", FactPopulator::maxHealth);
        addActorNum(vocabulary, "actor.food", actor -> actor.getFoodLevel());
        addActorNum(vocabulary, "actor.level", actor -> actor.getLevel());
        addActorNum(vocabulary, "actor.totalexp", actor -> actor.getTotalExperience());
        addActorFlag(vocabulary, "sneaking", Player::isSneaking);
        addActorFlag(vocabulary, "blocking", Player::isBlocking);
        addActorFlag(vocabulary, "flying", Player::isFlying);
        addActorFlag(vocabulary, "sprinting", Player::isSprinting);
        addActorFlag(vocabulary, "swimming", probe::isSwimming);
        addActorFlag(vocabulary, "gliding", probe::isGliding);
        addActorNum(vocabulary, "actor.healthpercent", actor -> healthPercent(actor));
        addActorFlag(vocabulary, "onfire", actor -> actor.getFireTicks() > 0);
        addActorFlag(vocabulary, "onground", FactPopulator::onGround);
        addActorStr(vocabulary, "actor.world", actor -> actor.getWorld().getName());
        addActorStr(vocabulary, "actor.gamemode", actor -> actor.getGameMode().name());
        addActorStr(vocabulary, "actor.helditem", probe::mainHandTypeName);
        addActorStr(vocabulary, "actor.type", actor -> actor.getType().name());
        // The block the actor is standing on (one below its feet), for on-terrain conditions like Frost's "on ice"
        // (ADR-0035). Same-region as the actor, so Folia-safe; guarded with the other actor reads if it ever isn't.
        addActorStr(vocabulary, "actor.groundblock",
                actor -> actor.getLocation().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getType().name());

        addVictimNum(vocabulary, "victim.health", LivingEntity::getHealth);
        addVictimNum(vocabulary, "victim.maxhealth", FactPopulator::maxHealth);
        addVictimNum(vocabulary, "victim.healthpercent", FactPopulator::healthPercent);
        addVictimNum(vocabulary, "victim.food", v -> v instanceof Player p ? p.getFoodLevel() : 0);
        addVictimFlag(vocabulary, "victim.sneaking", v -> v instanceof Player p && p.isSneaking());
        addVictimFlag(vocabulary, "victim.blocking", v -> v instanceof Player p && p.isBlocking());
        addVictimFlag(vocabulary, "victim.flying", v -> v instanceof Player p && p.isFlying());
        addVictimFlag(vocabulary, "victim.sprinting", v -> v instanceof Player p && p.isSprinting());
        addVictimFlag(vocabulary, "victim.swimming", v -> v instanceof Player p && probe.isSwimming(p));
        addVictimFlag(vocabulary, "victim.gliding", v -> v instanceof Player p && probe.isGliding(p));
        addVictimStr(vocabulary, "victim.type", v -> v.getType().name());
        addVictimStr(vocabulary, "victim.helditem", probe::mainHandTypeName);
        // §N MythicMob internal name via the soft hook; empty when not a MythicMob / integration absent.
        addVictimStr(vocabulary, "victim.mobtype", v -> entityTypeResolver.apply(v));

        this.damageSlot = slot(vocabulary, "damage", VarKind.NUM);
        this.blockTypeSlot = slot(vocabulary, "block.type", VarKind.STR);
        this.isBlockSlot = slot(vocabulary, "isblock", VarKind.BOOL);
        this.worldRainingSlot = slot(vocabulary, "world.raining", VarKind.BOOL);
        this.worldThunderingSlot = slot(vocabulary, "world.thundering", VarKind.BOOL);
        this.worldTimeSlot = slot(vocabulary, "world.time", VarKind.NUM);
        this.comboSlot = slot(vocabulary, "combo", VarKind.NUM);
        this.distanceSlot = slot(vocabulary, "distance", VarKind.NUM);
        this.nearbyEnemiesSlot = slot(vocabulary, "nearbyenemies", VarKind.NUM);
        this.victimInZoneSlot = slot(vocabulary, "victim.inzone", VarKind.BOOL);
        this.recentAttackersSlot = slot(vocabulary, "recentattackers", VarKind.NUM);
        this.attackerIndexSlot = slot(vocabulary, "attackerindex", VarKind.NUM);
        this.damageCauseSlot = slot(vocabulary, "damagecause", VarKind.STR);
        this.itemDamageArmorSlot = slot(vocabulary, "itemdamage.armor", VarKind.BOOL);
        this.actorBehindVictimSlot = slot(vocabulary, "actor.behindvictim", VarKind.BOOL);
        this.actorBelowVictimSlot = slot(vocabulary, "actor.belowvictim", VarKind.NUM);
        this.rageStacksSlot = slot(vocabulary, "ragestacks", VarKind.NUM);
    }

    /** A populator over the built-in vocabulary — the production default, paired with the compiler's resolver. */
    public static FactPopulator builtin(ActorProbe probe) {
        return new FactPopulator(BuiltinVars.vocabulary(), probe);
    }

    /** A built-in populator backed by a shared {@link VarStore} so conditions can read {@code SET_VAR} dynamic vars. */
    public static FactPopulator builtin(VarStore vars, ActorProbe probe) {
        return new FactPopulator(BuiltinVars.vocabulary(), vars, t -> null, probe);
    }

    /** As {@link #builtin(VarStore, ActorProbe)} but also sourcing {@code %ragestacks%} from {@code rageStacks} (§3). */
    public static FactPopulator builtin(VarStore vars, RageStackStore rageStacks, ActorProbe probe) {
        return new FactPopulator(BuiltinVars.vocabulary(), vars, t -> null, probe, rageStacks);
    }

    public FactBuffer populate(ActivationContext context) {
        return populate(context, 0L, FactMask.ALL);
    }

    /** Populate every fact (unmasked) — the compatibility entry for callers with no per-wearer mask. */
    public FactBuffer populate(ActivationContext context, long nowTicks) {
        return populate(context, nowTicks, FactMask.ALL);
    }

    /**
     * The thread-local buffer, cleared and repopulated from {@code context}, computing ONLY the slots
     * {@code mask} marks referenced (ADR-0039) — an unreferenced derived fact (the {@code %nearbyenemies%}
     * entity scan, {@code %distance%}, {@code %victim.inzone%}, {@code %actor.groundblock%}) is skipped
     * entirely. {@link FactBuffer#clear()} still zeroes every slot, so an unreferenced slot reads its
     * default, never a stale value. Returns the shared instance, valid until this method is next called on
     * this thread. The unknown-token resolver reads the activator's dynamic var (at {@code nowTicks}) before
     * PAPI — the read side of {@code SET_VAR}.
     */
    public FactBuffer populate(ActivationContext context, long nowTicks, FactMask mask) {
        FactBuffer facts = buffer.get();
        facts.clear();
        if (context != null) {
            populateActor(facts, context.actor(), mask);
            populateVictim(facts, context.victim(), mask);
            populateContext(facts, context, mask);
            populateDerived(facts, context, mask);
            Player actor = context.actor();
            if (actor != null) {
                UUID id = actor.getUniqueId();
                // %ragestacks%: an actor-scoped store read, mask-gated (no entity access, so no Folia guard needed).
                if (id != null && rageStacksSlot >= 0 && mask.readsNum(rageStacksSlot)) {
                    facts.setNumber(rageStacksSlot, rageStacks.current(id));
                }
                facts.papiResolver(token -> {
                    String value = vars.get(id, token, nowTicks);
                    return value != null ? value : papiDelegate.apply(token);
                });
            } else {
                facts.papiResolver(papiDelegate);
            }
        }
        return facts;
    }

    private void populateActor(FactBuffer facts, Player actor, FactMask mask) {
        if (actor == null) {
            return;
        }
        try {
            for (ActorNum f : actorNum) {
                if (mask.readsNum(f.slot())) {
                    facts.setNumber(f.slot(), f.src().read(actor));
                }
            }
            for (ActorFlag f : actorFlag) {
                if (mask.readsFlag(f.slot())) {
                    facts.setFlag(f.slot(), f.src().read(actor));
                }
            }
            for (ActorStr f : actorStr) {
                if (mask.readsStr(f.slot())) {
                    facts.setString(f.slot(), f.src().read(actor)); // e.g. the %actor.groundblock% block lookup
                }
            }
        } catch (RuntimeException unreadable) {
            // Folia: actor owned by another region (cross-region shooter on ATTACK) — default, never abort.
            Regions.swallowed("FactPopulator.populateActor", unreadable);
        }
    }

    private void populateVictim(FactBuffer facts, LivingEntity victim, FactMask mask) {
        if (victim == null) {
            return;
        }
        try {
            for (VictimNum f : victimNum) {
                if (mask.readsNum(f.slot())) {
                    facts.setNumber(f.slot(), f.src().read(victim));
                }
            }
            for (VictimFlag f : victimFlag) {
                if (mask.readsFlag(f.slot())) {
                    facts.setFlag(f.slot(), f.src().read(victim));
                }
            }
            for (VictimStr f : victimStr) {
                if (mask.readsStr(f.slot())) {
                    facts.setString(f.slot(), f.src().read(victim));
                }
            }
        } catch (RuntimeException unreadable) {
            // Cross-region victim (e.g. the attacker exposed on the DEFENSE pass) or a read failure.
            Regions.swallowed("FactPopulator.populateVictim", unreadable);
        }
    }

    // World weather/time are global-region-owned on Folia, so wrapped: a wrong-thread read defaults only those facts.
    private void populateContext(FactBuffer facts, ActivationContext context, FactMask mask) {
        if (damageSlot >= 0 && mask.readsNum(damageSlot)) {
            facts.setNumber(damageSlot, context.damage());
        }
        if (comboSlot >= 0 && mask.readsNum(comboSlot)) {
            facts.setNumber(comboSlot, context.combo());
        }
        // ADR-0049 context facts: the gank counts + cause + item-damage flag are computed by the dispatcher on the
        // firing thread and carried on the context (never a live entity read here), so they need no Folia guard.
        if (recentAttackersSlot >= 0 && mask.readsNum(recentAttackersSlot)) {
            facts.setNumber(recentAttackersSlot, context.recentAttackers());
        }
        if (attackerIndexSlot >= 0 && mask.readsNum(attackerIndexSlot)) {
            facts.setNumber(attackerIndexSlot, context.attackerIndex());
        }
        if (damageCauseSlot >= 0 && mask.readsStr(damageCauseSlot)) {
            facts.setString(damageCauseSlot, context.damageCauseName());
        }
        if (itemDamageArmorSlot >= 0 && mask.readsFlag(itemDamageArmorSlot)) {
            facts.setFlag(itemDamageArmorSlot, context.itemDamageArmor());
        }
        boolean wantsBlockType = blockTypeSlot >= 0 && mask.readsStr(blockTypeSlot);
        boolean wantsIsBlock = isBlockSlot >= 0 && mask.readsFlag(isBlockSlot);
        org.bukkit.block.Block block = context.block();
        if (block != null && (wantsBlockType || wantsIsBlock)) {
            try {
                org.bukkit.Material type = block.getType();
                if (wantsBlockType) {
                    facts.setString(blockTypeSlot, type.name());
                }
                if (wantsIsBlock) {
                    facts.setFlag(isBlockSlot, !probe.isAir(type));
                }
            } catch (RuntimeException unreadable) {
                // A block owned by another region — leave the block facts defaulted.
                Regions.swallowed("FactPopulator.populateContext.block", unreadable);
            }
        }
        boolean wantsRaining = worldRainingSlot >= 0 && mask.readsFlag(worldRainingSlot);
        boolean wantsThundering = worldThunderingSlot >= 0 && mask.readsFlag(worldThunderingSlot);
        boolean wantsTime = worldTimeSlot >= 0 && mask.readsNum(worldTimeSlot);
        if (wantsRaining || wantsThundering || wantsTime) {
            try {
                org.bukkit.World world = context.actor() != null ? context.actor().getWorld()
                        : context.location() != null ? context.location().getWorld() : null;
                if (world != null) {
                    if (wantsRaining) {
                        facts.setFlag(worldRainingSlot, world.hasStorm());
                    }
                    if (wantsThundering) {
                        facts.setFlag(worldThunderingSlot, world.isThundering());
                    }
                    if (wantsTime) {
                        facts.setNumber(worldTimeSlot, world.getTime());
                    }
                }
            } catch (RuntimeException unreadable) {
                // Folia: weather/time are global-region-owned; a wrong-thread read defaults only these facts.
                Regions.swallowed("FactPopulator.populateContext.world", unreadable);
            }
        }
    }

    // Derived combat geometry (distance, nearbyenemies, victim.inzone); Folia-wrapped like the entity facts
    // (default cross-region). ADR-0039: each slot is mask-gated, so the expensive %nearbyenemies% entity scan
    // runs ONLY when a worn condition/arg actually reads it.
    private void populateDerived(FactBuffer facts, ActivationContext context, FactMask mask) {
        boolean wantsDistance = distanceSlot >= 0 && mask.readsNum(distanceSlot);
        boolean wantsInZone = victimInZoneSlot >= 0 && mask.readsFlag(victimInZoneSlot);
        boolean wantsNearby = nearbyEnemiesSlot >= 0 && mask.readsNum(nearbyEnemiesSlot);
        boolean wantsBehind = actorBehindVictimSlot >= 0 && mask.readsFlag(actorBehindVictimSlot);
        boolean wantsBelow = actorBelowVictimSlot >= 0 && mask.readsNum(actorBelowVictimSlot);
        if (!wantsDistance && !wantsInZone && !wantsNearby && !wantsBehind && !wantsBelow) {
            return;
        }
        org.bukkit.entity.Player actor = context.actor();
        if (actor == null) {
            return;
        }
        try {
            if (wantsDistance) {
                LivingEntity victim = context.victim();
                if (victim != null && victim.getWorld() == actor.getWorld()) {
                    facts.setNumber(distanceSlot, actor.getLocation().distance(victim.getLocation()));
                }
            }
            if (wantsInZone) {
                LivingEntity victim = context.victim();
                if (victim != null) {
                    // The zone is owned by the activating wearer; true when the victim stands in one of theirs.
                    facts.setFlag(victimInZoneSlot,
                            engine.sink.OwnerZones.contains(actor.getUniqueId(), victim.getLocation()));
                }
            }
            if (wantsNearby) {
                int count = 0;
                for (org.bukkit.entity.Entity e : actor.getNearbyEntities(NEARBY_RADIUS, NEARBY_RADIUS, NEARBY_RADIUS)) {
                    if (e instanceof LivingEntity && !e.equals(actor)) {
                        count++;
                    }
                }
                facts.setNumber(nearbyEnemiesSlot, count);
            }
            if (wantsBehind) {
                LivingEntity victim = context.victim();
                if (victim != null && victim.getWorld() == actor.getWorld()) {
                    facts.setFlag(actorBehindVictimSlot, behindVictim(actor, victim));
                }
            }
            if (wantsBelow) {
                LivingEntity victim = context.victim();
                if (victim != null && victim.getWorld() == actor.getWorld()) {
                    // How far the actor's feet sit BELOW the victim's (ADR-0052 Eagle) — the threshold is
                    // authored on the condition (e.g. %actor.belowvictim% > 1.5), never hardcoded here.
                    facts.setNumber(actorBelowVictimSlot,
                            victim.getLocation().getY() - actor.getLocation().getY());
                }
            }
        } catch (RuntimeException unreadable) {
            // Cross-region actor (Folia) or a read failure — leave the derived facts defaulted.
            Regions.swallowed("FactPopulator.populateDerived", unreadable);
        }
    }

    /**
     * Whether {@code actor} stands behind {@code victim}'s body facing (ADR-0049 Rogue): the horizontal angle
     * between the victim's facing (yaw → direction, y flattened) and the victim→actor vector exceeds 90°, i.e.
     * their dot is negative. Only the SIGN matters, so neither vector is normalised (a zero vector — same column,
     * or a straight-up look — yields dot 0 = not behind).
     */
    private static boolean behindVictim(Player actor, LivingEntity victim) {
        org.bukkit.util.Vector facing = victim.getLocation().getDirection().setY(0);
        org.bukkit.util.Vector toActor = actor.getLocation().toVector()
                .subtract(victim.getLocation().toVector()).setY(0);
        return facing.dot(toActor) < 0;
    }

    /** Cross-version-stable {@code getMaxHealth()} (the Attribute API flipped at 1.21.3). */
    @SuppressWarnings("deprecation")
    private static double maxHealth(LivingEntity entity) {
        return entity.getMaxHealth();
    }

    /** Health as a percentage of max (0–100); 0 when max health is non-positive. */
    private static double healthPercent(LivingEntity entity) {
        double max = maxHealth(entity);
        return max > 0 ? 100.0 * entity.getHealth() / max : 0.0;
    }

    /** Cross-version-stable {@code isOnGround()}; deprecated-not-removed (client-reported) across the range. */
    @SuppressWarnings("deprecation")
    private static boolean onGround(Player player) {
        return player.isOnGround();
    }

    private void addActorNum(VarVocabulary v, String key, ActorD src) {
        int slot = slot(v, key, VarKind.NUM);
        if (slot >= 0) {
            actorNum.add(new ActorNum(slot, src));
        }
    }

    private void addActorFlag(VarVocabulary v, String key, ActorB src) {
        int slot = slot(v, key, VarKind.BOOL);
        if (slot >= 0) {
            actorFlag.add(new ActorFlag(slot, src));
        }
    }

    private void addActorStr(VarVocabulary v, String key, ActorS src) {
        int slot = slot(v, key, VarKind.STR);
        if (slot >= 0) {
            actorStr.add(new ActorStr(slot, src));
        }
    }

    private void addVictimNum(VarVocabulary v, String key, VictimD src) {
        int slot = slot(v, key, VarKind.NUM);
        if (slot >= 0) {
            victimNum.add(new VictimNum(slot, src));
        }
    }

    private void addVictimFlag(VarVocabulary v, String key, VictimB src) {
        int slot = slot(v, key, VarKind.BOOL);
        if (slot >= 0) {
            victimFlag.add(new VictimFlag(slot, src));
        }
    }

    private void addVictimStr(VarVocabulary v, String key, VictimS src) {
        int slot = slot(v, key, VarKind.STR);
        if (slot >= 0) {
            victimStr.add(new VictimStr(slot, src));
        }
    }

    /** Resolve a {@code scope.name} (or bare {@code name}) key to its slot for {@code kind}, or −1 if absent. */
    private static int slot(VarVocabulary v, String key, VarKind kind) {
        int dot = key.indexOf('.');
        String scope = dot < 0 ? null : key.substring(0, dot);
        String name = dot < 0 ? key : key.substring(dot + 1);
        return v.lookup(scope, name).filter(b -> b.kind() == kind).map(VarBinding::slot).orElse(-1);
    }
}

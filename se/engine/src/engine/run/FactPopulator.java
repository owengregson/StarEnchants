package engine.run;

import compile.cond.VarBinding;
import compile.cond.VarKind;
import engine.condition.BuiltinVars;
import engine.condition.FactBuffer;
import engine.condition.VarVocabulary;
import engine.stores.VarStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

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
    private final UnaryOperator<String> papiDelegate;

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

    /** Search radius for {@code %nearbyenemies%}, in blocks. */
    private static final double NEARBY_RADIUS = 8.0;

    /** No dynamic-var store and no PAPI: unknown tokens resolve to null. */
    public FactPopulator(VarVocabulary vocabulary) {
        this(vocabulary, new VarStore(), t -> null);
    }

    /**
     * Backed by a shared {@link VarStore} ({@code SET_VAR}/{@code INVERT_VAR} write target) and an optional
     * PAPI delegate. Unknown {@code %name%} resolution order: built-in slot → player dynamic var →
     * {@code papiDelegate} → null.
     */
    public FactPopulator(VarVocabulary vocabulary, VarStore vars, UnaryOperator<String> papiDelegate) {
        Objects.requireNonNull(vocabulary, "vocabulary");
        this.vars = Objects.requireNonNull(vars, "vars");
        this.papiDelegate = papiDelegate == null ? t -> null : papiDelegate;
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
        addActorFlag(vocabulary, "swimming", EntityCompat::isSwimming);
        addActorFlag(vocabulary, "gliding", EntityCompat::isGliding);
        addActorNum(vocabulary, "actor.healthpercent", actor -> healthPercent(actor));
        addActorFlag(vocabulary, "onfire", actor -> actor.getFireTicks() > 0);
        addActorFlag(vocabulary, "onground", FactPopulator::onGround);
        addActorStr(vocabulary, "actor.world", actor -> actor.getWorld().getName());
        addActorStr(vocabulary, "actor.gamemode", actor -> actor.getGameMode().name());
        addActorStr(vocabulary, "actor.helditem", HeldItem::mainHandTypeName);
        addActorStr(vocabulary, "actor.type", actor -> actor.getType().name());

        addVictimNum(vocabulary, "victim.health", LivingEntity::getHealth);
        addVictimNum(vocabulary, "victim.maxhealth", FactPopulator::maxHealth);
        addVictimNum(vocabulary, "victim.healthpercent", FactPopulator::healthPercent);
        addVictimNum(vocabulary, "victim.food", v -> v instanceof Player p ? p.getFoodLevel() : 0);
        addVictimFlag(vocabulary, "victim.sneaking", v -> v instanceof Player p && p.isSneaking());
        addVictimFlag(vocabulary, "victim.blocking", v -> v instanceof Player p && p.isBlocking());
        addVictimFlag(vocabulary, "victim.flying", v -> v instanceof Player p && p.isFlying());
        addVictimFlag(vocabulary, "victim.sprinting", v -> v instanceof Player p && p.isSprinting());
        addVictimFlag(vocabulary, "victim.swimming", v -> v instanceof Player p && EntityCompat.isSwimming(p));
        addVictimFlag(vocabulary, "victim.gliding", v -> v instanceof Player p && EntityCompat.isGliding(p));
        addVictimStr(vocabulary, "victim.type", v -> v.getType().name());
        addVictimStr(vocabulary, "victim.helditem", FactPopulator::heldItemName);
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
    }

    /** A populator over the built-in vocabulary — the production default, paired with the compiler's resolver. */
    public static FactPopulator builtin() {
        return new FactPopulator(BuiltinVars.vocabulary());
    }

    /** A built-in populator backed by a shared {@link VarStore} so conditions can read {@code SET_VAR} dynamic vars. */
    public static FactPopulator builtin(VarStore vars) {
        return new FactPopulator(BuiltinVars.vocabulary(), vars, t -> null);
    }

    public FactBuffer populate(ActivationContext context) {
        return populate(context, 0L);
    }

    /**
     * The thread-local buffer, cleared and repopulated from {@code context}. Returns the shared instance,
     * valid until this method is next called on this thread. The unknown-token resolver reads the
     * activator's dynamic var (at {@code nowTicks}) before PAPI — the read side of {@code SET_VAR}.
     */
    public FactBuffer populate(ActivationContext context, long nowTicks) {
        FactBuffer facts = buffer.get();
        facts.clear();
        if (context != null) {
            populateActor(facts, context.actor());
            populateVictim(facts, context.victim());
            populateContext(facts, context);
            populateDerived(facts, context);
            Player actor = context.actor();
            if (actor != null) {
                UUID id = actor.getUniqueId();
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

    private void populateActor(FactBuffer facts, Player actor) {
        if (actor == null) {
            return;
        }
        try {
            for (ActorNum f : actorNum) {
                facts.setNumber(f.slot(), f.src().read(actor));
            }
            for (ActorFlag f : actorFlag) {
                facts.setFlag(f.slot(), f.src().read(actor));
            }
            for (ActorStr f : actorStr) {
                facts.setString(f.slot(), f.src().read(actor));
            }
        } catch (RuntimeException unreadable) {
            // Folia: actor owned by another region (cross-region shooter on ATTACK) — default, never abort.
        }
    }

    private void populateVictim(FactBuffer facts, LivingEntity victim) {
        if (victim == null) {
            return;
        }
        try {
            for (VictimNum f : victimNum) {
                facts.setNumber(f.slot(), f.src().read(victim));
            }
            for (VictimFlag f : victimFlag) {
                facts.setFlag(f.slot(), f.src().read(victim));
            }
            for (VictimStr f : victimStr) {
                facts.setString(f.slot(), f.src().read(victim));
            }
        } catch (RuntimeException unreadable) {
            // Cross-region victim (e.g. the attacker exposed on the DEFENSE pass) or a read failure.
        }
    }

    // World weather/time are global-region-owned on Folia, so wrapped: a wrong-thread read defaults only those facts.
    private void populateContext(FactBuffer facts, ActivationContext context) {
        if (damageSlot >= 0) {
            facts.setNumber(damageSlot, context.damage());
        }
        if (comboSlot >= 0) {
            facts.setNumber(comboSlot, context.combo());
        }
        org.bukkit.block.Block block = context.block();
        if (block != null && (blockTypeSlot >= 0 || isBlockSlot >= 0)) {
            try {
                org.bukkit.Material type = block.getType();
                if (blockTypeSlot >= 0) {
                    facts.setString(blockTypeSlot, type.name());
                }
                if (isBlockSlot >= 0) {
                    facts.setFlag(isBlockSlot, !EntityCompat.isAir(type));
                }
            } catch (RuntimeException unreadable) {
                // A block owned by another region — leave the block facts defaulted.
            }
        }
        if (worldRainingSlot >= 0 || worldThunderingSlot >= 0 || worldTimeSlot >= 0) {
            try {
                org.bukkit.World world = context.actor() != null ? context.actor().getWorld()
                        : context.location() != null ? context.location().getWorld() : null;
                if (world != null) {
                    if (worldRainingSlot >= 0) {
                        facts.setFlag(worldRainingSlot, world.hasStorm());
                    }
                    if (worldThunderingSlot >= 0) {
                        facts.setFlag(worldThunderingSlot, world.isThundering());
                    }
                    if (worldTimeSlot >= 0) {
                        facts.setNumber(worldTimeSlot, world.getTime());
                    }
                }
            } catch (RuntimeException unreadable) {
                // Folia: weather/time are global-region-owned; a wrong-thread read defaults only these facts.
            }
        }
    }

    // Derived combat geometry (distance, nearbyenemies); Folia-wrapped like the entity facts (default to 0 cross-region).
    private void populateDerived(FactBuffer facts, ActivationContext context) {
        if (distanceSlot < 0 && nearbyEnemiesSlot < 0) {
            return;
        }
        org.bukkit.entity.Player actor = context.actor();
        if (actor == null) {
            return;
        }
        try {
            if (distanceSlot >= 0) {
                LivingEntity victim = context.victim();
                if (victim != null && victim.getWorld() == actor.getWorld()) {
                    facts.setNumber(distanceSlot, actor.getLocation().distance(victim.getLocation()));
                }
            }
            if (nearbyEnemiesSlot >= 0) {
                int count = 0;
                for (org.bukkit.entity.Entity e : actor.getNearbyEntities(NEARBY_RADIUS, NEARBY_RADIUS, NEARBY_RADIUS)) {
                    if (e instanceof LivingEntity && !e.equals(actor)) {
                        count++;
                    }
                }
                facts.setNumber(nearbyEnemiesSlot, count);
            }
        } catch (RuntimeException unreadable) {
            // Cross-region actor (Folia) or a read failure — leave the derived facts defaulted.
        }
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

    /** The victim's main-hand material name, or {@code null} if it has no equipment (via the {@link HeldItem} seam). */
    private static String heldItemName(LivingEntity victim) {
        return HeldItem.mainHandTypeName(victim);
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

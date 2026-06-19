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
 * Populates a condition {@link FactBuffer} from one activation's live context (docs/architecture.md
 * §3.4; v3.1 §A) — the runtime half of the condition variable system. The compiler lowers
 * {@code %scope.name%} references to dense {@link FactBuffer} slot indices via a {@link VarVocabulary};
 * this fills those exact slots from the firing player and the combat victim so gate 7 reads real values.
 * Built once at boot from the SAME vocabulary the compiler lowered against (so a compiled condition's
 * slot and the populated buffer agree by construction).
 *
 * <p><strong>Table-driven.</strong> Each built-in fact is one entry pairing its resolved slot with a
 * pure extractor over the actor ({@link Player}) or victim ({@link LivingEntity}); event-payload facts
 * ({@code damage}, {@code block.type}/{@code isblock}, world weather/time) are filled from the
 * {@link ActivationContext} in {@code populateContext}. Adding a fact is one line here plus its declaration
 * in {@link BuiltinVars}; a fact whose name is absent from the vocabulary is simply skipped. {@code combo}
 * has no entry (no combat-streak tracker exists) so it reads its default (0).
 *
 * <p><strong>Thread-local pooling (§3.4).</strong> The buffer is held per worker thread and reused —
 * {@link #populate} clears it and refills, returning the SAME instance — so the per-hit pipeline stays
 * allocation-free. Safe because the buffer never escapes the synchronous pass.
 *
 * <p><strong>Folia.</strong> Every read runs on the firing thread. The firing player's own state and the
 * event's own victim are region-owned there and read safely; an entity owned by ANOTHER region (e.g. a
 * cross-region projectile shooter) cannot be read, and Folia makes that fail hard. Each entity side's
 * reads are wrapped so such a failure leaves that side's facts defaulted rather than aborting the
 * activation. Reads never mutate, so they need no scheduler hop. Caveat (unchanged from the buffer's
 * introduction): a defaulted cross-region fact is a value, not "unknown" — but no shipped content reads
 * actor facts on the ATTACK projectile path or victim facts on the DEFENSE path.
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
    private final List<ActorNum> actorNum = new ArrayList<>();
    private final List<ActorFlag> actorFlag = new ArrayList<>();
    private final List<ActorStr> actorStr = new ArrayList<>();
    private final List<VictimNum> victimNum = new ArrayList<>();
    private final List<VictimFlag> victimFlag = new ArrayList<>();
    private final List<VictimStr> victimStr = new ArrayList<>();
    // Context facts come from the event payload, not an actor/victim entity — resolved slots (−1 if absent).
    private final int damageSlot;
    private final int blockTypeSlot;
    private final int isBlockSlot;
    private final int worldRainingSlot;
    private final int worldThunderingSlot;
    private final int worldTimeSlot;

    /** A populator with no dynamic-var store and no PAPI (unknown tokens resolve to null) — the lower-level default. */
    public FactPopulator(VarVocabulary vocabulary) {
        this(vocabulary, new VarStore(), t -> null);
    }

    /**
     * A populator backed by a shared {@link VarStore} (the {@code SET_VAR}/{@code INVERT_VAR} write target)
     * and an optional real-PAPI delegate. An unknown {@code %name%} token resolves: built-in slot (handled
     * by the compiler/IR) → this player's dynamic var → {@code papiDelegate} → null.
     */
    public FactPopulator(VarVocabulary vocabulary, VarStore vars, UnaryOperator<String> papiDelegate) {
        Objects.requireNonNull(vocabulary, "vocabulary");
        this.vars = Objects.requireNonNull(vars, "vars");
        this.papiDelegate = papiDelegate == null ? t -> null : papiDelegate;
        this.buffer = ThreadLocal.withInitial(vocabulary::newFactBuffer);

        // ── Actor (the firing player) ──
        addActorNum(vocabulary, "actor.health", Player::getHealth);
        addActorNum(vocabulary, "actor.maxhealth", FactPopulator::maxHealth);
        addActorNum(vocabulary, "actor.food", actor -> actor.getFoodLevel());
        addActorNum(vocabulary, "actor.level", actor -> actor.getLevel());
        addActorNum(vocabulary, "actor.totalexp", actor -> actor.getTotalExperience());
        addActorFlag(vocabulary, "sneaking", Player::isSneaking);
        addActorFlag(vocabulary, "blocking", Player::isBlocking);
        addActorFlag(vocabulary, "flying", Player::isFlying);
        addActorFlag(vocabulary, "sprinting", Player::isSprinting);
        addActorFlag(vocabulary, "swimming", Player::isSwimming);
        addActorFlag(vocabulary, "gliding", Player::isGliding);
        addActorNum(vocabulary, "actor.healthpercent", actor -> healthPercent(actor));
        addActorFlag(vocabulary, "onfire", actor -> actor.getFireTicks() > 0);
        addActorFlag(vocabulary, "onground", FactPopulator::onGround);
        addActorStr(vocabulary, "actor.world", actor -> actor.getWorld().getName());
        addActorStr(vocabulary, "actor.gamemode", actor -> actor.getGameMode().name());
        addActorStr(vocabulary, "actor.helditem",
                actor -> actor.getInventory().getItemInMainHand().getType().name());
        addActorStr(vocabulary, "actor.type", actor -> actor.getType().name());

        // ── Victim (the combat target) ──
        addVictimNum(vocabulary, "victim.health", LivingEntity::getHealth);
        addVictimNum(vocabulary, "victim.maxhealth", FactPopulator::maxHealth);
        addVictimNum(vocabulary, "victim.healthpercent", FactPopulator::healthPercent);
        addVictimNum(vocabulary, "victim.food", v -> v instanceof Player p ? p.getFoodLevel() : 0);
        addVictimFlag(vocabulary, "victim.sneaking", v -> v instanceof Player p && p.isSneaking());
        addVictimFlag(vocabulary, "victim.blocking", v -> v instanceof Player p && p.isBlocking());
        addVictimFlag(vocabulary, "victim.flying", v -> v instanceof Player p && p.isFlying());
        addVictimFlag(vocabulary, "victim.sprinting", v -> v instanceof Player p && p.isSprinting());
        addVictimFlag(vocabulary, "victim.swimming", v -> v instanceof Player p && p.isSwimming());
        addVictimFlag(vocabulary, "victim.gliding", v -> v instanceof Player p && p.isGliding());
        addVictimStr(vocabulary, "victim.type", v -> v.getType().name());
        addVictimStr(vocabulary, "victim.helditem", FactPopulator::heldItemName);

        // ── Context (event payload: combat damage, the broken block, world weather/time) ──
        this.damageSlot = slot(vocabulary, "damage", VarKind.NUM);
        this.blockTypeSlot = slot(vocabulary, "block.type", VarKind.STR);
        this.isBlockSlot = slot(vocabulary, "isblock", VarKind.BOOL);
        this.worldRainingSlot = slot(vocabulary, "world.raining", VarKind.BOOL);
        this.worldThunderingSlot = slot(vocabulary, "world.thundering", VarKind.BOOL);
        this.worldTimeSlot = slot(vocabulary, "world.time", VarKind.NUM);
    }

    /** A populator over the built-in vocabulary — the production default, paired with the compiler's resolver. */
    public static FactPopulator builtin() {
        return new FactPopulator(BuiltinVars.vocabulary());
    }

    /** A built-in populator backed by a shared {@link VarStore} so conditions can read {@code SET_VAR} dynamic vars. */
    public static FactPopulator builtin(VarStore vars) {
        return new FactPopulator(BuiltinVars.vocabulary(), vars, t -> null);
    }

    /** The thread-local buffer for {@code context}, with timed dynamic vars read at tick {@code 0}. */
    public FactBuffer populate(ActivationContext context) {
        return populate(context, 0L);
    }

    /**
     * The thread-local buffer, cleared and repopulated from {@code context} (or just cleared if
     * {@code context} is {@code null}). One per trigger pass — installed on the {@code Activation} and
     * read by every candidate ability's condition gate before this method is next called on this thread.
     *
     * <p>After the built-in slots are filled, the buffer's unknown-token resolver is installed so a
     * condition's {@code %name%} resolves the activator's dynamic var (from the {@link VarStore} at
     * {@code nowTicks}) before falling through to real PAPI — the read side of {@code SET_VAR}.
     */
    public FactBuffer populate(ActivationContext context, long nowTicks) {
        FactBuffer facts = buffer.get();
        facts.clear();
        if (context != null) {
            populateActor(facts, context.actor());
            populateVictim(facts, context.victim());
            populateContext(facts, context);
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
            // Folia: the actor is owned by another region (e.g. a cross-region projectile shooter on the
            // ATTACK pass), or some read failed — leave the actor facts defaulted, never abort the hit.
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

    /**
     * Fill the event-payload facts (combat {@code damage}, the broken {@code block}, world weather/time) from
     * the {@link ActivationContext}. These are not actor/victim entity reads, so they have their own guards:
     * {@code damage} is a plain value; the block snapshot is region-owned on the firing thread (MINE); the
     * world getters are global-region-owned on Folia and wrapped so a wrong-thread read defaults only them.
     */
    private void populateContext(FactBuffer facts, ActivationContext context) {
        if (damageSlot >= 0) {
            facts.setNumber(damageSlot, context.damage());
        }
        org.bukkit.block.Block block = context.block();
        if (block != null && (blockTypeSlot >= 0 || isBlockSlot >= 0)) {
            try {
                org.bukkit.Material type = block.getType();
                if (blockTypeSlot >= 0) {
                    facts.setString(blockTypeSlot, type.name());
                }
                if (isBlockSlot >= 0) {
                    facts.setFlag(isBlockSlot, !type.isAir());
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

    /** Max health via the cross-version-stable {@code getMaxHealth()} (the Attribute API flipped at 1.21.3). */
    @SuppressWarnings("deprecation")
    private static double maxHealth(LivingEntity entity) {
        return entity.getMaxHealth();
    }

    /** Health as a percentage of max (0–100), or 0 when max health is non-positive. */
    private static double healthPercent(LivingEntity entity) {
        double max = maxHealth(entity);
        return max > 0 ? 100.0 * entity.getHealth() / max : 0.0;
    }

    /** On-ground via the cross-version-stable getter; deprecated-not-removed (client-reported) across the range. */
    @SuppressWarnings("deprecation")
    private static boolean onGround(Player player) {
        return player.isOnGround();
    }

    /** The main-hand item's material name for a living victim, or {@code null} if it has no equipment. */
    private static String heldItemName(LivingEntity victim) {
        return victim.getEquipment() == null ? null
                : victim.getEquipment().getItemInMainHand().getType().name();
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

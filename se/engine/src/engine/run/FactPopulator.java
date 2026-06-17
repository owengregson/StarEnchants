package engine.run;

import compile.cond.VarBinding;
import compile.cond.VarKind;
import engine.condition.BuiltinVars;
import engine.condition.FactBuffer;
import engine.condition.VarVocabulary;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
 * pure extractor over the actor ({@link Player}) or victim ({@link LivingEntity}). Adding a fact is one
 * line here plus its declaration in {@link BuiltinVars}; a fact whose name is absent from the vocabulary
 * is simply skipped. Facts whose extraction is not yet wired (e.g. {@code damage}, {@code combo}) are
 * declared in the vocabulary but have no entry here, so they read their default (0).
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
    private final List<ActorNum> actorNum = new ArrayList<>();
    private final List<ActorFlag> actorFlag = new ArrayList<>();
    private final List<ActorStr> actorStr = new ArrayList<>();
    private final List<VictimNum> victimNum = new ArrayList<>();
    private final List<VictimFlag> victimFlag = new ArrayList<>();
    private final List<VictimStr> victimStr = new ArrayList<>();

    public FactPopulator(VarVocabulary vocabulary) {
        Objects.requireNonNull(vocabulary, "vocabulary");
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
        addActorStr(vocabulary, "actor.world", actor -> actor.getWorld().getName());
        addActorStr(vocabulary, "actor.gamemode", actor -> actor.getGameMode().name());
        addActorStr(vocabulary, "actor.helditem",
                actor -> actor.getInventory().getItemInMainHand().getType().name());

        // ── Victim (the combat target) ──
        addVictimNum(vocabulary, "victim.health", LivingEntity::getHealth);
        addVictimNum(vocabulary, "victim.maxhealth", FactPopulator::maxHealth);
        addVictimFlag(vocabulary, "victim.sneaking", v -> v instanceof Player p && p.isSneaking());
        addVictimFlag(vocabulary, "victim.blocking", v -> v instanceof Player p && p.isBlocking());
        addVictimFlag(vocabulary, "victim.flying", v -> v instanceof Player p && p.isFlying());
        addVictimStr(vocabulary, "victim.type", v -> v.getType().name());
    }

    /** A populator over the built-in vocabulary — the production default, paired with the compiler's resolver. */
    public static FactPopulator builtin() {
        return new FactPopulator(BuiltinVars.vocabulary());
    }

    /**
     * The thread-local buffer, cleared and repopulated from {@code context} (or just cleared if
     * {@code context} is {@code null}). One per trigger pass — installed on the {@code Activation} and
     * read by every candidate ability's condition gate before this method is next called on this thread.
     */
    public FactBuffer populate(ActivationContext context) {
        FactBuffer facts = buffer.get();
        facts.clear();
        if (context != null) {
            populateActor(facts, context.actor());
            populateVictim(facts, context.victim());
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

    /** Max health via the cross-version-stable {@code getMaxHealth()} (the Attribute API flipped at 1.21.3). */
    @SuppressWarnings("deprecation")
    private static double maxHealth(LivingEntity entity) {
        return entity.getMaxHealth();
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

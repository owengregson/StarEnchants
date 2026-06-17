package engine.run;

import compile.cond.VarBinding;
import compile.cond.VarKind;
import engine.condition.BuiltinVars;
import engine.condition.FactBuffer;
import engine.condition.VarVocabulary;
import java.util.Objects;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Populates a condition {@link FactBuffer} from one activation's live context (docs/architecture.md
 * §3.4) — the runtime half of the condition variable system. The compiler lowers {@code %scope.name%}
 * references to dense {@link FactBuffer} slot indices via a {@link VarVocabulary}; this fills those
 * exact slots from the firing player and the combat victim so gate 7 reads real values instead of an
 * empty buffer. Built once at boot from the SAME vocabulary the compiler lowered against (so a
 * compiled condition's slot and the populated buffer agree by construction).
 *
 * <p><strong>Thread-local pooling (§3.4).</strong> The buffer is held per worker thread and reused —
 * {@link #populate} clears it and refills the sourced slots, returning the SAME instance — so the
 * per-hit pipeline stays allocation-free (the {@link FactBuffer} javadoc describes exactly this). This
 * is safe because the buffer never escapes the synchronous pass: {@code TriggerRunner.run} installs it
 * on the {@code Activation}, the executor reads it during the gate walk, and the method returns before
 * any next {@code populate} on that thread; effects read their own {@code EffectCtx}, never the buffer.
 *
 * <p><strong>Folia.</strong> Every read runs on the firing thread (where {@code TriggerRunner} invokes
 * this). The firing player's own state and the event's own victim are owned by that region and read
 * safely; an entity owned by ANOTHER region cannot be read there, and Folia makes that access fail
 * hard. Such a failure is caught per side so the activation is never aborted. <em>Caveat:</em> the
 * unreadable fact then keeps its default (0 / {@code false}), which can be a <em>wrong</em> value, not
 * merely "unknown" — e.g. {@code %actor.health%} would read 0 for a cross-region projectile shooter on
 * the ATTACK pass, or {@code %victim.health%} for the attacker exposed on the DEFENSE pass. No shipped
 * content hits this (the {@code actor.health} enchants are DEFENSE — actor = the region-owned defender;
 * {@code executioner}'s {@code victim.health} is ATTACK — victim = the region-owned target). A proper
 * fix (capturing these primitives on each entity's owning thread at event entry) lands with the full
 * variable vocabulary in a later v3.1 increment; until then this is a documented Folia limitation.
 *
 * <p>Not every declared variable is sourced yet: {@code damage} and {@code combo} have no runtime
 * source in the current context and read their default (0) until a later increment wires them; no
 * shipped content depends on them.
 */
public final class FactPopulator {

    private final ThreadLocal<FactBuffer> buffer;
    // Resolved slots for the sourced built-in facts (−1 = absent from the vocabulary → skipped).
    private final int actorHealthSlot;
    private final int victimHealthSlot;
    private final int sneakingSlot;
    private final int blockingSlot;
    private final int flyingSlot;

    public FactPopulator(VarVocabulary vocabulary) {
        Objects.requireNonNull(vocabulary, "vocabulary");
        this.buffer = ThreadLocal.withInitial(vocabulary::newFactBuffer);
        this.actorHealthSlot = numberSlot(vocabulary, "actor", "health");
        this.victimHealthSlot = numberSlot(vocabulary, "victim", "health");
        this.sneakingSlot = flagSlot(vocabulary, "sneaking");
        this.blockingSlot = flagSlot(vocabulary, "blocking");
        this.flyingSlot = flagSlot(vocabulary, "flying");
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

    /** The firing player's own facts — health + pose flags. Guarded so a read failure defaults, never aborts. */
    private void populateActor(FactBuffer facts, Player actor) {
        if (actor == null) {
            return;
        }
        try {
            if (actorHealthSlot >= 0) {
                facts.setNumber(actorHealthSlot, actor.getHealth());
            }
            if (sneakingSlot >= 0) {
                facts.setFlag(sneakingSlot, actor.isSneaking());
            }
            if (blockingSlot >= 0) {
                facts.setFlag(blockingSlot, actor.isBlocking());
            }
            if (flyingSlot >= 0) {
                facts.setFlag(flyingSlot, actor.isFlying());
            }
        } catch (RuntimeException unreadable) {
            // Folia throws IllegalStateException for a cross-region actor (e.g. a projectile shooter on
            // the ATTACK pass); any other read failure is treated the same — leave the actor facts at
            // their default rather than aborting the activation (matches the engine's warn-and-skip).
        }
    }

    /** The combat victim's health. Guarded the same way (e.g. the cross-region attacker on the DEFENSE pass). */
    private void populateVictim(FactBuffer facts, LivingEntity victim) {
        if (victim == null || victimHealthSlot < 0) {
            return;
        }
        try {
            facts.setNumber(victimHealthSlot, victim.getHealth());
        } catch (RuntimeException unreadable) {
            // Cross-region victim or any read failure — leave victim.health at its default.
        }
    }

    private static int numberSlot(VarVocabulary vocabulary, String scope, String name) {
        return slotOf(vocabulary, scope, name, VarKind.NUM);
    }

    private static int flagSlot(VarVocabulary vocabulary, String name) {
        return slotOf(vocabulary, null, name, VarKind.BOOL);
    }

    private static int slotOf(VarVocabulary vocabulary, String scope, String name, VarKind kind) {
        return vocabulary.lookup(scope, name)
                .filter(binding -> binding.kind() == kind)
                .map(VarBinding::slot)
                .orElse(-1);
    }
}

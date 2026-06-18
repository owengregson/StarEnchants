package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import java.util.UUID;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code REMOVE_SOULS} — actor-only soul debit (docs/v3-directives.md §D): charge {@code amount} souls from
 * the activator's active soul gem. Souls bind to the activator, so there is NO target selector — the holder
 * is the constant end (cf. {@link MoneyEffect}'s actor-fixed transfer). A no-op when the activator is not in
 * soul mode (no active gem) or the gem cannot afford it; the spend is atomic in the {@code SoulLedger} and
 * written through to the gem's PDC on the holder's own thread by the {@code Sink}.
 * {@link Affinity#CONTEXT_LOCAL}: no world mutation is routed here (the sink owns the thread hop).
 */
public final class RemoveSoulsEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("REMOVE_SOULS")
            .param("amount", D.INT.min(1))
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Debit souls from the activator's active soul gem (a no-op when they are not in soul mode).")
            .example("REMOVE_SOULS:5")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        UUID gemId = ctx.activeGem();
        Player holder = ctx.actor();
        int amount = ctx.integer("amount");
        if (gemId == null || holder == null || amount <= 0) {
            return; // not in soul mode (or nothing to spend) → nothing to debit
        }
        sink.removeSouls(holder, gemId, amount);
    }
}

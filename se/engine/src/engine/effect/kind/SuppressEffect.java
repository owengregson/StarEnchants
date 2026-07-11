package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code SUPPRESS} — temporarily disable a target player's enchant / group / type / effect kind (§C,
 * ADR-0053). The suppression keys the SAME interned id gate 5 reads (the bridge invariant). {@code key} is
 * lowered at compile (the {@code EraseStage}): scope ENCHANT/GROUP/TYPE interns it into the cooldown-scope
 * namespace; scope KIND resolves it to the dense effect kindId (an unknown head is a diagnostic and the op
 * is dropped). {@code scope}/{@code mode} erase to their ints, so {@code run} does zero string work.
 */
public final class SuppressEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SUPPRESS")
            .param("scope", D.enumOf("ENCHANT", "GROUP", "TYPE", "KIND"))
            .param("key", D.STRING)
            .param("duration", D.TICKS.def(200))
            .param("mode", D.enumOf("timed", "next-hit").def("timed"))
            .param("charges", D.INT.min(1).def(1))
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Disable a target's enchant/group/type (the key) for a duration in ticks "
                    + "(DISABLE_ENCHANT/GROUP/TYPE), or with scope KIND every ability carrying the keyed effect "
                    + "head (e.g. MODIFY_FOOD). mode: timed (the duration window) or next-hit (a one-shot that "
                    + "clears after the target's next `charges` incoming hits, Neutralize). Default target the combat victim.")
            .example("{ SUPPRESS: { scope: GROUP, key: lifesteal, duration: 200, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int scopeKind = ctx.integer("scope"); // erased to ScopeKinds.ENCHANT/GROUP/TYPE/KIND (0/1/2/3)
        int keyId = ctx.integer("key");       // erased to the cooldown-scope interner id (KIND: the effect kindId)
        int duration = ctx.integer("duration");
        boolean nextHit = ctx.integer("mode") == 1; // enum erased to ordinal: 0=timed, 1=next-hit
        int charges = ctx.integer("charges");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.suppress(p, scopeKind, keyId, duration, ctx.sourceDefId(), nextHit, charges);
            }
        }
    }
}

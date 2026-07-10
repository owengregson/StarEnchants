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
 * {@code SUPPRESS} — temporarily disable a target player's enchant / group / type (§C). The suppression keys
 * the SAME interned scope id gate 5 reads (the bridge invariant). {@code key} is interned into the
 * cooldown-scope namespace at compile (the {@code EraseStage}) and {@code scope} to its kind int, so
 * {@code run} reads both as ints.
 */
public final class SuppressEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SUPPRESS")
            .param("scope", D.enumOf("ENCHANT", "GROUP", "TYPE"))
            .param("key", D.STRING)
            .param("duration", D.TICKS.def(200))
            .param("mode", D.enumOf("timed", "next-hit").def("timed"))
            .param("charges", D.INT.min(1).def(1))
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Disable a target's enchant/group/type (the key) for a duration in ticks "
                    + "(DISABLE_ENCHANT/GROUP/TYPE). mode: timed (the duration window) or next-hit (a one-shot that "
                    + "clears after the target's next `charges` incoming hits, Neutralize). Default target the combat victim.")
            .example("{ SUPPRESS: { scope: GROUP, key: lifesteal, duration: 200, who: \"@Victim\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int scopeKind = ctx.integer("scope"); // erased to ScopeKinds.ENCHANT/GROUP/TYPE (0/1/2)
        int keyId = ctx.integer("key");       // erased to the cooldown-scope interner id
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

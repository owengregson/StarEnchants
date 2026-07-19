package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;
import org.bukkit.entity.Player;

/**
 * {@code TRAP_BREAK} — Turnkey (ADR-0071): early-restore every registered confining structure currently
 * holding the wearer (Fantasy web, Spider box, Cage cell), intact, through the temp-block ledger's
 * reclaim path, and thaw a live freeze window (the owner's "any active confinement on self"). Floor
 * paint, trails and pillars are not confinement and are never touched.
 */
public final class TrapBreakEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("TRAP_BREAK")
            .target("who", T.SELF)
            .param("whiff-sound", D.sound().def("BLOCK_ANVIL_LAND"),
                    "played (low-pitched) when nothing confining was found — a silent no-op is "
                            + "indistinguishable from a broken feature")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Break every confining trap currently on the wearer — encasing webs, web boxes, "
                    + "cage cells — restoring the trapped blocks to their true originals "
                    + "immediately. Area floors and trails are unaffected. Works through ability "
                    + "silence (it is a block restore, not an ability negation).")
            .example("{ TRAP_BREAK: { } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity who : ctx.targets("who")) {
            if (who instanceof Player p) {
                sink.breakTraps(p, ctx.integer("whiff-sound"));
            }
        }
    }
}

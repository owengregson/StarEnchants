package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import schema.spec.D;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * {@code PROC_REBOUND} — Enchant Reflect: a maintained PASSIVE marker that steals an incoming enchant's
 * activation and re-runs it with the roles swapped, so the attacker eats their own proc. Armed on equip
 * ({@link #run}) and lifted on unequip ({@link #stop}) by the HELD/PASSIVE lifecycle (ADR-0022) — the enchant
 * has no effects of its own; the whole behaviour lives in the combat dispatch.
 *
 * <p>The rebound LEVEL is this ability's own level, not a param: the matrix's gate is "rebound level &ge; the
 * incoming enchant's level", which is a comparison against the worn piece, not an authored number.
 *
 * <p>{@code tier-min} exists so the matrix's EXCLUSIVE grade chain composes without any engine notion of
 * grade: author mastery as {@code 8..8}, heroic as {@code 6..7}, normal as {@code 0..5} and the dispatch
 * picks the armed grade with the greatest {@code tier-min} whose band contains the incoming tier — which is
 * exactly "mastery, else heroic, else normal; first match wins". Overlapping bands are legal and resolve the
 * same way, so a wearer carrying several grades always uses exactly one branch.
 *
 * <p>The rebounded damage lands as a bounded SECOND application against the attacker (ADR-0054's stand-down
 * stays: no re-entry into the combat dispatch), so it carries its own immunity frame and death credit rather
 * than being literally the attacker's own swing turned around.
 */
public final class ProcReboundEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("PROC_REBOUND")
            .param("chance", D.DOUBLE.min(0).max(100))
            .param("tier-max", D.INT.min(0))
            .param("tier-min", D.INT.min(0).def(0))
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("While worn, give incoming enchant activations a chance to be taken off you and re-run with "
                    + "the roles swapped — the attacker eats their own proc, and it is NOT applied to you for "
                    + "that hit. Gated by the attacking enchant's rarity-tier weight (tier-min..tier-max) and "
                    + "by level: this enchant's level must be at least the incoming one's. Several worn grades "
                    + "compose — the one whose band reaches the incoming tier with the highest tier-min wins. "
                    + "A maintained PASSIVE marker, armed on equip and lifted on unequip. Player-only.")
            .example("{ PROC_REBOUND: { chance: 4, tier-min: 6, tier-max: 7, who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player holder) {
                sink.armRebound(holder, ctx.sourceDefId(), ctx.level(), ctx.dbl("chance"),
                        ctx.integer("tier-min"), ctx.integer("tier-max"));
            }
        }
    }

    @Override
    public void stop(EffectCtx ctx, Sink sink) {
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player holder) {
                sink.disarmRebound(holder, ctx.sourceDefId());
            }
        }
    }
}

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
 * incoming enchant's level", which is a comparison against the worn piece, not an authored number. A LEVELLESS
 * source — a set bonus, a crystal, a mask, a pet — has no enchant level to state and arms at {@code 0}, which
 * the gate reads as "no level bound" rather than as the lowest grade: such a rule answers every level its band
 * reaches. Without that reading the level gate alone would make the whole authored band inert, because every
 * enchant level starts at 1.
 *
 * <p>The band is compared against the incoming enchant's tier WEIGHT — the number its rung carries in
 * {@code tiers.yml}, not a 0-based rung index. On the shipped ascending-by-ten ladder (common 10 … heroic 70,
 * mastery 80) the matrix's exclusive grade chain is authored as mastery {@code 80..80}, heroic {@code 10..70},
 * normal {@code 0..50}; a pack that re-weights its ladder re-bands with it. The grades OVERLAP deliberately —
 * a heroic reflector answers everything up to heroic, and only loses to mastery — which is what makes the
 * chain exclusive under the tier-min rule below. A source carrying no tier at all (pets, reforges, masks)
 * weighs {@code -1}, which no band containing 0 reaches, so it is never rebounded.
 *
 * <p>{@code tier-min} exists so that chain composes without any engine notion of grade: the dispatch picks the
 * armed grade with the greatest {@code tier-min} whose band contains the incoming weight — which is exactly
 * "mastery, else heroic, else normal; first match wins". Overlapping bands are legal and resolve the same way,
 * so a wearer carrying several grades always uses exactly one branch.
 *
 * <p>The rebounded damage lands as a bounded SECOND application against the attacker (ADR-0054's stand-down
 * stays: no re-entry into the combat dispatch), so it carries its own immunity frame and death credit rather
 * than being literally the attacker's own swing turned around.
 */
public final class ProcReboundEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("PROC_REBOUND")
            // All three read INSIDE the target loop, so each armed holder prices their own window (ADR-0076);
            // declared, because PerTargetParamTest counts the reads and a hoist would otherwise pass silently.
            .param("chance", D.DOUBLE.min(0).max(100).perTarget())
            .param("tier-max", D.INT.min(0).perTarget())
            .param("tier-min", D.INT.min(0).def(0).perTarget())
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("While worn, give incoming enchant activations a chance to be taken off you and re-run with "
                    + "the roles swapped — the attacker eats their own proc, and it is NOT applied to you for "
                    + "that hit. Gated by the attacking enchant's tier WEIGHT — the number its rung carries in "
                    + "tiers.yml, not a rung index — which must fall in tier-min..tier-max, and by level: this "
                    + "enchant's level must be at least the incoming one's (a levelless source — set, crystal, "
                    + "mask, pet — has no level to compare and answers its whole band). Several worn grades compose — the "
                    + "one whose band reaches the incoming weight with the highest tier-min wins. "
                    + "A maintained PASSIVE marker, armed on equip and lifted on unequip. Player-only.")
            .example("{ PROC_REBOUND: { chance: 4, tier-min: 10, tier-max: 70, who: \"@Self\" } }")
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

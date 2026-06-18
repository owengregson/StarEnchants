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
 * {@code SUPPRESS} — temporarily disable a target player's enchant / group / type for a span of ticks
 * (docs/v3-directives.md §C, covering AE's DISABLE_ENCHANT / DISABLE_GROUP / DISABLE_TYPE). {@code scope}
 * selects which identifier {@code key} names, and {@code key} is the enchant base key, group, or type the
 * suppressed abilities lower their cooldown-scope to — so the suppression keys the SAME interned scope id
 * gate 5 reads (the bridge invariant). Default target is the {@link T#VICTIM combat victim} (an attacker
 * silencing the defender); {@code @Self} silences the activator.
 *
 * <p>The {@code key} is interned into the cooldown-scope namespace at compile time (the {@code EraseStage}),
 * and {@code scope} to its kind int — so {@code run} reads both as ints. {@link Affinity#CONTEXT_LOCAL}:
 * per-player in-memory state, no world mutation (the sink writes the concurrent suppression store).
 */
public final class SuppressEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("SUPPRESS")
            .param("scope", D.enumOf("ENCHANT", "GROUP", "TYPE"))
            .param("key", D.STRING)
            .param("duration", D.TICKS.def(200))
            .target("who", T.VICTIM)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Disable a target's enchant/group/type (the key) for a duration in ticks "
                    + "(DISABLE_ENCHANT/GROUP/TYPE). Default target the combat victim.")
            .example("SUPPRESS:GROUP:lifesteal:200:@Victim")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int scopeKind = ctx.integer("scope"); // erased to 0/1/2 (enchant/group/type)
        int keyId = ctx.integer("key");       // erased to the cooldown-scope interner id
        int duration = ctx.integer("duration");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player p) {
                sink.suppress(p, scopeKind, keyId, duration);
            }
        }
    }
}

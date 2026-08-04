package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code INVENTORY_CONVERT} — turn up to {@code limit} of one material into another across the whole inventory.
 *
 * <p>{@code REMOVE_ITEM} + {@code GIVE_ITEM} is the near-miss and it cannot express any of the three things
 * that matter here: "up to N" (remove takes a fixed count and silently under-removes), the count the rest of
 * the activation prices itself on, or the zero-converted branch.
 *
 * <p>A stack that STRADDLES the remaining limit converts up to the limit and hands the rest back as
 * {@code from}. The measured original inverted this — it converted the overflow and returned the part that
 * fitted — which turned a nearly-full budget into a nearly-empty conversion.
 *
 * <p>The converted count is written to {@code count-var}, readable as {@code %<count-var>%} by any later
 * ability in the same activation walk: the count is settled inline, before any inventory write is scheduled,
 * precisely so the failure branch and a count-scaled XP grant can gate on it.
 */
public final class InventoryConvertEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("INVENTORY_CONVERT")
            .param("from", D.material(), "the material consumed")
            .param("to", D.material(), "the material handed back in its place")
            .param("limit", D.INT.min(1), "the most items one activation may convert")
            .param("plain", D.BOOL.def(true),
                    "true = skip any stack carrying meta (a named/enchanted/plugin item is never raw material)")
            .param("protect-seconds", D.INT.min(0).def(0),
                    "how long items that no longer fit stay owner-locked on the ground (0 = unprotected)")
            .param("count-var", D.STRING.def("converted"),
                    "per-player variable the converted count is written to, read back as %name%")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Replace up to `limit` of the activator's `from` items with `to`, walking the whole "
                    + "inventory. With `plain` only meta-less stacks are touched. A stack straddling the "
                    + "remaining limit converts up to the limit and returns the overflow as `from`; anything "
                    + "that no longer fits is dropped at their feet, pickable only by them for "
                    + "`protect-seconds`. The number converted lands in `count-var`, so the zero-converted "
                    + "failure branch and any count-scaled follow-up read it as %converted%.")
            .example("{ INVENTORY_CONVERT: { from: BUCKET, to: LAVA_BUCKET, limit: 1152, plain: true, "
                    + "protect-seconds: 60, count-var: converted } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        // Actor-scoped by construction — the whole point is the ACTIVATOR's own inventory — so this kind
        // declares no who-slot rather than offering one it would have to ignore.
        sink.convertInventory(ctx.actor(), ctx.integer("from"), ctx.integer("to"), ctx.integer("limit"),
                ctx.bool("plain"), ctx.integer("protect-seconds") * 20, ctx.str("count-var"));
    }
}

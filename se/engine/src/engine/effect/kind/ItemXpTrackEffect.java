package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import schema.spec.D;

/**
 * {@code ITEM_XP_TRACK} — credit progression XP to the ITEM that fired this activation.
 *
 * <p>{@code MODIFY_EXP} is the near-miss and it is a different economy entirely: vanilla player XP, spent at
 * an anvil, lost on death. This one advances a level counter living on the item, which survives death, trades
 * and reloads because it is item state.
 *
 * <p><strong>Owner ruling (2026-08-01):</strong> this path grants under the COSMIC semantics — <em>at most one
 * level per grant, remainder banked; bank unbounded at the cap</em> — WITHOUT altering the shipped roll behind
 * kill / vanilla-XP / food / passive credit, which rolls as many levels as the grant pays for and parks exp at
 * the cap. The two semantics COEXIST, keyed by WHICH PATH GRANTS.
 *
 * <p>{@code window} is a per-ITEM gate, not a per-player one: the timestamp rides the item, so a pet traded
 * mid-window carries its cooldown to its new owner and a freshly minted one — carrying no stamp at all —
 * earns immediately.
 */
public final class ItemXpTrackEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("ITEM_XP_TRACK")
            .param("amount", D.INT.min(1), "experience credited to the held item")
            .param("window", D.INT.min(0).def(0),
                    "MINUTES between grants for this item (0 = ungated); 1440 = once a day")
            .param("message", D.STRING.def(""),
                    "line sent on a grant ({xp}, {exp}, {needed}); empty = silent")
            .param("level-up-message", D.STRING.def(""),
                    "line sent when the grant levels the item ({item} = its name BEFORE the level-up, {level})")
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Credit `amount` experience to the item the activator is holding — the item whose ability "
                    + "fired. At most ONE level per grant: the remainder is banked toward the next, and at the "
                    + "item's cap the bank simply keeps growing. `window` gates the grant to once per that "
                    + "many minutes using a stamp carried BY the item, so the gate follows it through a trade "
                    + "and a freshly minted item earns straight away; a grant inside the window is skipped "
                    + "whole, never scaled. Per-level thresholds and the level cap come from the item's own "
                    + "definition (a pet's `exp-curve` / `max-level`), falling back to the universal "
                    + "`pets.exp-per-level` and `pets.max-level`.")
            .example("{ ITEM_XP_TRACK: { amount: 500, window: 1440, "
                    + "message: \"&a&l+ &a{xp} Pet EXP &a&l[&7{exp}/{needed}&a&l]\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        // Holder-scoped by construction — the item that fired is the activator's held one — so this kind
        // declares no who-slot rather than offering one it would have to ignore.
        sink.itemXpTrack(ctx.actor(), ctx.integer("amount"), ctx.integer("window"),
                ctx.str("message"), ctx.str("level-up-message"));
    }
}

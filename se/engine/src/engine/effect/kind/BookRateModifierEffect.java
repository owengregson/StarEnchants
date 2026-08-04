package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import engine.stores.BookRateStore;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.D;

/**
 * {@code BOOK_RATE_MODIFIER} — arm a one-shot bonus on the holder's next enchant-book roll.
 *
 * <p>The book economy's success rolls are engine-internal, so no {@code SET_VAR}/condition combination could
 * reach inside one; this is the seam that lets content bid on the next roll. The charge is spent by that roll
 * REGARDLESS of outcome — a failed apply burns it, exactly as the measured original does — so the item's value
 * is the attempt, not the result.
 *
 * <p>The two sites are separate charges: a {@code generate} charge (a black scroll minting a book) and an
 * {@code apply} charge (a book going onto gear) coexist, and neither spends the other. Whether a second arm is
 * refused is authored, not engine policy — read the paired {@code %bookrate.generate%} / {@code %bookrate.apply%}
 * fact in a condition and stop.
 */
public final class BookRateModifierEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("BOOK_RATE_MODIFIER")
            .param("site", D.enumOf(BookRateStore.names()),
                    "which roll the charge waits for: generate (a scroll minting a book) or apply")
            .param("percent", D.INT.min(1).max(100), "percentage points added to that roll's success chance")
            .target("who", T.SELF)
            .affinity(Affinity.CONTEXT_LOCAL)
            .doc("Arm a one-shot `percent`-point bonus on each target's next enchant-book roll at `site`: "
                    + "`generate` raises the success rate of the book a black scroll mints, `apply` raises the "
                    + "chance a book applies to gear. The charge is consumed by the next roll at that site "
                    + "whatever it returns — a failed apply spends it — and it survives a relog, since the "
                    + "roll it is waiting for may be days away. Both sites cap at the server's global "
                    + "books.max-success ceiling. Guard a second arm with %bookrate.generate% / %bookrate.apply%.")
            .example("{ BOOK_RATE_MODIFIER: { site: generate, percent: 5, who: \"@Self\" } }")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int site = wireCode(ctx.str("site"));
        int percent = ctx.integer("percent");
        for (LivingEntity target : ctx.targets("who")) {
            if (target instanceof Player player) {
                sink.armBookRate(player, site, percent);
            }
        }
    }

    /** The authored token as the store's site ordinal — resolved ONCE, above the fan-out loop. */
    private static int wireCode(String site) {
        return "apply".equalsIgnoreCase(site) ? BookRateStore.APPLY : BookRateStore.GENERATE;
    }
}

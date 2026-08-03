package testfx;

import engine.spec.EffectSpec;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.spec.Param;
import schema.spec.ParamType;

/**
 * Fills a {@link FakeEffectCtx} with one type-correct stand-in value for every parameter an
 * {@link EffectSpec} declares — and only those. A conformance test runs a kind against this ctx, so a kind
 * that reads a param its spec does NOT declare (a typo, a renamed param, a copy-paste) trips
 * {@link FakeEffectCtx}'s throw-on-unset. That turns the "the production param name and the test's literal
 * string must stay in sync" coupling into a structural check: the {@link EffectSpec} is the single source of
 * the names, never a hand-typed string duplicated into a test.
 *
 * <p>Scalars only, so this stays Mockito-free (testfx ships no test framework): the caller supplies the
 * actor / targets / location, which need Bukkit mocks the consuming test module provides.
 */
public final class SpecDrivenCtx {

    private SpecDrivenCtx() {
    }

    /**
     * A ctx pre-filled with a stand-in for each scalar param the spec declares; the caller then adds the
     * actor, target slots, and location before running the kind. Values are typed to the param's
     * {@link ParamType.Kind} so each {@code ctx.dbl/integer/bool/str} read resolves without a cast error:
     * numeric and HANDLE params are a number, BOOL a boolean, ENUM its first canonical value (so a kind that
     * switches on the value still enters a real branch), STRING a placeholder.
     */
    public static FakeEffectCtx scalars(EffectSpec spec) {
        FakeEffectCtx ctx = FakeEffectCtx.create();
        for (Param p : spec.paramSpec().params()) {
            ParamType type = p.type();
            switch (type.kind()) {
                case DOUBLE -> ctx.with(p.name(), 1.0);
                case INT, TICKS, HANDLE -> ctx.with(p.name(), 1);
                case BOOL -> ctx.with(p.name(), true);
                case ENUM -> ctx.with(p.name(), type.allowed().isEmpty() ? "x" : type.allowed().get(0));
                case STRING -> ctx.with(p.name(), "x");
            }
        }
        return ctx;
    }

    /**
     * A ctx pre-filled with each param's DECLARED DEFAULT — the args an ability that never mentions the param
     * compiles to. The back-compat instrument for a kind that gains a param: start here, override only what the
     * row exercises, and a default that silently shifts fails the behaviour assertion instead of hiding behind a
     * re-typed literal. Values go through the param's OWN {@link ParamType#parse}, so this cannot drift from the
     * compiler. A param with no default (or a HANDLE, whose default is a token only the resolver can intern)
     * keeps the {@link #scalars} stand-in.
     */
    public static FakeEffectCtx defaults(EffectSpec spec) {
        FakeEffectCtx ctx = scalars(spec);
        for (Param p : spec.paramSpec().params()) {
            ParamType type = p.type();
            if (type.kind() == ParamType.Kind.HANDLE) {
                continue;
            }
            type.defaultRaw()
                    .flatMap(raw -> type.parse(raw, Source.UNKNOWN, new Diagnostics()))
                    .ifPresent(value -> ctx.with(p.name(), value));
        }
        return ctx;
    }
}

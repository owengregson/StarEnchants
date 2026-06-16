package compile.stage;

import compile.LineCompiler;
import compile.MapSpecRegistry;
import compile.SelectorCompiler;
import compile.SpecRegistry;
import compile.cond.ConditionCompiler;
import compile.cond.VarResolver;
import compile.def.AbilityDef;
import compile.model.Affinity;
import compile.model.CompiledCondition;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.cond.Cond;
import schema.diag.Diagnostics;
import schema.grammar.EffectLine;
import schema.grammar.expr.Expr;
import schema.grammar.expr.ExprParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The default {@link LowerStage}: turns one authored {@link AbilityDef} into a
 * {@link LoweredAbility} (docs/architecture.md §3.2 "compile, never interpret",
 * §3.4, §3.6, §7).
 *
 * <p>Each effect line is validated against its {@code ParamSpec} into a flyweight
 * {@link CompiledEffect} via a reused {@link LineCompiler}; {@code WAIT:n} lines are
 * <em>timing</em>, not effects, so they emit no {@code CompiledEffect} and instead
 * accumulate into the cumulative tick delay stamped on every following effect — the
 * fix for EE's WAIT-overwrite bug (§3.6). The condition string is parsed once into a
 * {@link CompiledCondition}; a blank/absent condition lowers to {@code null}
 * ("always true"). The ability-level {@link Affinity} is the MAX fold over the
 * emitted effects' declared affinities, so the {@code Sink} can route the whole
 * activation without per-effect inspection (§3.6).
 *
 * <p>Never throws: every fault — a malformed {@code WAIT}, an unknown effect head, a
 * condition parse error — is reported into the supplied {@link Diagnostics} and
 * lowering continues, so one bad line yields one precise finding rather than aborting
 * the load (§7, §10). Selectors are deferred to a later increment; every effect
 * lowers with {@link CompiledSelector#SELF}.
 */
public final class DefaultLowerStage implements LowerStage {

    private final LineCompiler lineCompiler;
    private final Function<String, Affinity> affinityOf;
    private final SelectorCompiler selectorCompiler;
    private final Function<String, String> defaultSelectorOf;
    private final ConditionCompiler conditionCompiler;

    /**
     * @param registry          the spec registry resolving effect heads to {@code ParamSpec}s
     * @param affinityOf        maps an effect head to its declared {@link Affinity};
     *                          a {@code null} result is treated as {@link Affinity#CONTEXT_LOCAL}
     * @param selectors         the spec registry resolving selector heads to {@code ParamSpec}s
     * @param defaultSelectorOf maps an effect head to the selector head it targets by
     *                          default; a {@code null} result means {@code SELF}
     * @param vars              the condition variable vocabulary (name &rarr; slot+type);
     *                          unknown variables become PlaceholderAPI tokens
     */
    public DefaultLowerStage(SpecRegistry registry, Function<String, Affinity> affinityOf,
                             SpecRegistry selectors, Function<String, String> defaultSelectorOf,
                             VarResolver vars) {
        Objects.requireNonNull(registry, "registry");
        this.affinityOf = Objects.requireNonNull(affinityOf, "affinityOf");
        this.selectorCompiler = new SelectorCompiler(Objects.requireNonNull(selectors, "selectors"));
        this.defaultSelectorOf = Objects.requireNonNull(defaultSelectorOf, "defaultSelectorOf");
        this.conditionCompiler = new ConditionCompiler(Objects.requireNonNull(vars, "vars"));
        this.lineCompiler = new LineCompiler(registry);
    }

    /** Convenience: selector support, but the empty variable vocabulary. */
    public DefaultLowerStage(SpecRegistry registry, Function<String, Affinity> affinityOf,
                             SpecRegistry selectors, Function<String, String> defaultSelectorOf) {
        this(registry, affinityOf, selectors, defaultSelectorOf, VarResolver.none());
    }

    /**
     * Convenience: the given affinity lookup, but no selectors are resolvable — every
     * effect targets {@code SELF}. Used by tests and the bare {@code Compiler.of}.
     */
    public DefaultLowerStage(SpecRegistry registry, Function<String, Affinity> affinityOf) {
        this(registry, affinityOf, MapSpecRegistry.of(), head -> null, VarResolver.none());
    }

    /** Convenience: every effect's affinity defaults to {@link Affinity#CONTEXT_LOCAL}. */
    public DefaultLowerStage(SpecRegistry registry) {
        this(registry, head -> Affinity.CONTEXT_LOCAL);
    }

    @Override
    public LoweredAbility lower(AbilityDef def, Diagnostics diags) {
        Objects.requireNonNull(def, "def");
        Objects.requireNonNull(diags, "diags");

        List<CompiledEffect> out = new ArrayList<>();
        int waitAccum = 0;

        for (EffectLine line : def.effects()) {
            if ("WAIT".equalsIgnoreCase(line.head())) {
                Integer ticks = parseWait(line, diags);
                if (ticks != null) {
                    waitAccum += ticks;
                }
                continue; // WAIT is timing — never an emitted effect
            }
            Optional<compile.CompiledLine> compiled = lineCompiler.compile(line, diags);
            if (compiled.isEmpty()) {
                continue; // unknown head — LineCompiler already diagnosed it
            }
            compile.CompiledLine cl = compiled.get();
            CompiledSelector selector = resolveSelector(line, cl.head(), diags);
            out.add(new CompiledEffect(
                    cl.head(), cl.args(), selector, waitAccum, affinityOf(cl.head())));
        }

        CompiledCondition condition = lowerCondition(def, diags);

        Affinity ability = Affinity.CONTEXT_LOCAL;
        for (CompiledEffect effect : out) {
            ability = ability.max(effect.affinity());
        }

        return new LoweredAbility(
                def.sourceKind(),
                def.stableKey(),
                def.defId(),
                def.level(),
                def.baseChance(),
                def.cooldownTicks(),
                def.soulCost(),
                def.triggers(),
                def.worldBlacklist(),
                condition,
                out,
                def.suppressKey(),
                def.cdScopeEnchant(),
                def.cdScopeGroup(),
                def.cdScopeType(),
                def.repeatTicks(),
                ability,
                def.source(),
                def.setPieces());
    }

    /**
     * Validate a {@code WAIT} line into its non-negative tick count, or return
     * {@code null} (recording an {@code E_WAIT_ARG} diagnostic) for any fault: a
     * wrong argument count, a non-integer, or a negative value.
     */
    private static Integer parseWait(EffectLine line, Diagnostics diags) {
        List<String> argTexts = line.argTexts();
        if (argTexts.size() != 1) {
            diags.error("E_WAIT_ARG",
                    "WAIT takes exactly one argument but got " + argTexts.size(),
                    line.source(), "usage: WAIT:<ticks>  (e.g. WAIT:20)");
            return null;
        }
        String raw = argTexts.get(0);
        int ticks;
        try {
            ticks = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            diags.error("E_WAIT_ARG",
                    "WAIT expects a whole number of ticks but got '" + raw + "'",
                    line.source(), "use a non-negative whole number, e.g. WAIT:20");
            return null;
        }
        if (ticks < 0) {
            diags.error("E_WAIT_ARG",
                    "WAIT ticks must be non-negative but got " + ticks,
                    line.source(), "use 0 or more, e.g. WAIT:20");
            return null;
        }
        return ticks;
    }

    /**
     * Parse and lower the raw condition into a typed {@link CompiledCondition}, or
     * {@code null} when the condition is blank/absent ("always true") or when parsing
     * or lowering failed (the diagnostic was already recorded). Two passes: the
     * {@link ExprParser} produces the untyped {@link Expr}; the {@link ConditionCompiler}
     * resolves variables to {@code FactBuffer} slots and type-checks it (§3.4).
     */
    private CompiledCondition lowerCondition(AbilityDef def, Diagnostics diags) {
        String expr = def.conditionExpr();
        if (expr == null || expr.isBlank()) {
            return null;
        }
        Optional<Expr> parsed = ExprParser.parse(expr, def.source(), diags);
        if (parsed.isEmpty()) {
            return null; // ExprParser already diagnosed the syntax error
        }
        Optional<Cond> lowered = conditionCompiler.compile(parsed.get(), diags);
        return lowered.map(root -> new CompiledCondition(root, def.source())).orElse(null);
    }

    /**
     * The target selector for an effect line: the author's inline {@code @Head{...}}
     * selector if present, otherwise the effect kind's declared default target
     * (falling back to {@code SELF}). Faults fall back to {@code SELF} after a
     * diagnostic (§3.5, §7).
     */
    private CompiledSelector resolveSelector(EffectLine line, String effectHead, Diagnostics diags) {
        Optional<String> inline = line.selectorToken();
        if (inline.isPresent()) {
            return selectorCompiler.compileInline(inline.get(), line.selectorSource(), diags);
        }
        return selectorCompiler.defaultFor(defaultSelectorOf.apply(effectHead), line.source(), diags);
    }

    /** The declared affinity for {@code head}, defaulting to {@link Affinity#CONTEXT_LOCAL}. */
    private Affinity affinityOf(String head) {
        Affinity a = affinityOf.apply(head);
        return a != null ? a : Affinity.CONTEXT_LOCAL;
    }
}

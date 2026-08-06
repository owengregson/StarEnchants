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
import compile.model.cond.NumExpr;
import compile.model.cond.NumExprMap;
import compile.resolve.PlatformResolvers;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.grammar.EffectLine;
import schema.grammar.expr.Cmp;
import schema.grammar.expr.Expr;
import schema.grammar.expr.ExprParser;
import schema.grammar.expr.FlowKind;
import schema.spec.Args;
import schema.spec.ExprMap;
import schema.spec.HandleCategory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * The default {@link LowerStage}: one authored {@link AbilityDef} &rarr; a {@link LoweredAbility}
 * (docs/architecture.md §3.2, §3.6). {@code WAIT:n} lines are <em>timing</em>, not effects: they
 * accumulate into the tick delay stamped on every following effect — the fix for a Cosmic Enchants-style
 * WAIT-overwrite bug. The ability-level {@link Affinity} is the MAX fold over its effects' affinities, so
 * the {@code Sink} routes the whole activation without per-effect inspection. Never throws: one bad line
 * is one diagnostic, not an aborted load.
 */
public final class DefaultLowerStage implements LowerStage {

    private final LineCompiler lineCompiler;
    private final Function<String, Affinity> affinityOf;
    private final SelectorCompiler selectorCompiler;
    private final Function<String, String> defaultSelectorOf;
    private final ConditionCompiler conditionCompiler;
    // The EFFECT-SIDE twin, the only one that admits the %target.*% subject cursor (ADR-0076). Two instances
    // rather than a mode flag threaded through the walk: the legality of a scope is a property of WHERE an
    // expression sits, and here that is decided once, by which compiler the caller reaches for.
    private final ConditionCompiler effectCompiler;
    private final ToIntFunction<String> effectIdOf;
    private final PlatformResolvers resolvers;

    /** The per-target knobs {@code EffectSpec} declares on every entity-targeting kind (ADR-0076). */
    private static final String EACH_IF = "each-if";
    private static final String EACH_CHANCE = "each-chance";
    private static final String EACH_COOLDOWN = "each-cooldown";

    /** Convenience: no dense-id stamping — every effect/selector {@code kindId} is {@code -1} (the head-fallback path). */
    public DefaultLowerStage(SpecRegistry registry, Function<String, Affinity> affinityOf,
                             SpecRegistry selectors, Function<String, String> defaultSelectorOf,
                             VarResolver vars) {
        this(registry, affinityOf, selectors, defaultSelectorOf, vars, head -> -1, head -> -1);
    }

    /** Convenience: dense-id stamping but no handle resolution — a keyed potion condition cannot resolve. */
    public DefaultLowerStage(SpecRegistry registry, Function<String, Affinity> affinityOf,
                             SpecRegistry selectors, Function<String, String> defaultSelectorOf,
                             VarResolver vars, ToIntFunction<String> effectIdOf,
                             ToIntFunction<String> selectorIdOf) {
        this(registry, affinityOf, selectors, defaultSelectorOf, vars, effectIdOf, selectorIdOf,
                PlatformResolvers.none());
    }

    /**
     * @param affinityOf        effect head &rarr; declared {@link Affinity}; {@code null} &rarr; {@link Affinity#CONTEXT_LOCAL}
     * @param defaultSelectorOf effect head &rarr; default selector head; {@code null} &rarr; {@code SELF}
     * @param vars              condition variable vocabulary; unknown variables become PlaceholderAPI tokens
     * @param effectIdOf        effect head &rarr; dense kind id stamped on each {@link CompiledEffect} (ADR-0039)
     * @param selectorIdOf      selector head &rarr; dense kind id stamped on each {@link CompiledSelector} (ADR-0039)
     * @param resolvers         the §9 handle facade conditions resolve their keyed potion tokens through — the
     *                          SAME one the resolve stage uses, so an effect and a condition agree on what a
     *                          potion name means on this version
     */
    public DefaultLowerStage(SpecRegistry registry, Function<String, Affinity> affinityOf,
                             SpecRegistry selectors, Function<String, String> defaultSelectorOf,
                             VarResolver vars, ToIntFunction<String> effectIdOf,
                             ToIntFunction<String> selectorIdOf, PlatformResolvers resolvers) {
        Objects.requireNonNull(registry, "registry");
        this.affinityOf = Objects.requireNonNull(affinityOf, "affinityOf");
        this.selectorCompiler = new SelectorCompiler(Objects.requireNonNull(selectors, "selectors"),
                Objects.requireNonNull(selectorIdOf, "selectorIdOf"));
        this.defaultSelectorOf = Objects.requireNonNull(defaultSelectorOf, "defaultSelectorOf");
        this.resolvers = Objects.requireNonNull(resolvers, "resolvers");
        this.conditionCompiler = new ConditionCompiler(Objects.requireNonNull(vars, "vars"), resolvers);
        this.effectCompiler = new ConditionCompiler(vars, resolvers, true);
        this.lineCompiler = new LineCompiler(registry);
        this.effectIdOf = Objects.requireNonNull(effectIdOf, "effectIdOf");
    }

    /** Convenience: selector support, but the empty variable vocabulary. */
    public DefaultLowerStage(SpecRegistry registry, Function<String, Affinity> affinityOf,
                             SpecRegistry selectors, Function<String, String> defaultSelectorOf) {
        this(registry, affinityOf, selectors, defaultSelectorOf, VarResolver.none());
    }

    /** Convenience: the given affinity lookup, but no selectors — every effect targets {@code SELF}. */
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
            Args args = cl.args();
            // The per-target knobs are HOISTED out of the arg bag into CompiledEffect fields before the generic
            // expression walk runs — otherwise each-if (a condition) would be lowered as a number, and the
            // executor would pay a map lookup per hit for a knob almost no effect declares.
            Cond eachCondition = lowerEachCondition(args, diags);
            NumExpr eachCooldown = lowerEachCooldown(args, def, diags);
            out.add(new CompiledEffect(cl.head(),
                    lowerExprArgs(args.without(EACH_IF, EACH_CHANCE, EACH_COOLDOWN), diags), selector,
                    waitAccum, affinityOf(cl.head()), effectIdOf.applyAsInt(cl.head()),
                    eachCondition, eachCooldown));
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
                def.setPieces(),
                def.suppressImmune(),
                lowerChance(def, diags),
                def.noSoulsMessage(),
                def.soulCostCarried(),
                envelopeHandle(def.noSoulsSound(), HandleCategory.SOUND, "no-souls-sound", def, diags),
                envelopeHandle(def.noSoulsParticle(), HandleCategory.PARTICLE, "no-souls-particle", def, diags),
                def.soulCostGrowth(),
                def.soulCostCap(),
                def.soulCostDecayPeriod(),
                def.cooldownPerVictim(),
                def.repeatDelayTicks(),
                def.sourceGroup(),
                def.stacks());
    }

    /**
     * Intern an ENVELOPE handle token to its id, {@code -1} for none. Resolved here rather than in the resolve
     * stage because the envelope is not a {@code ParamSpec} surface, and resolve's walk is driven entirely by
     * declared params. An unknown token is the same blocking {@code E_UNKNOWN_HANDLE} an effect arg raises, so
     * a typo cannot ship as a silently missing cue.
     */
    private int envelopeHandle(String token, HandleCategory category, String key, AbilityDef def,
                               Diagnostics diags) {
        if (token == null || token.isBlank()) {
            return -1;
        }
        String trimmed = token.trim();
        OptionalInt id = category == HandleCategory.SOUND
                ? resolvers.sound(trimmed)
                : resolvers.particle(trimmed);
        if (id.isEmpty()) {
            diags.error(DiagCode.E_UNKNOWN_HANDLE, "unknown " + category.label() + " '" + trimmed
                    + "' for '" + key + "'", def.source(), "use a name valid on the target version, or remove it");
            return -1;
        }
        return id.getAsInt();
    }

    /**
     * The chance expression lowered to {@link NumExpr}, or {@code null} when {@code chance:} was a constant
     * (the fast path) or the expression was rejected.
     *
     * <p>Lowered into a scratch {@link Diagnostics} so a FAULTY expression is dropped whole: the parser's
     * recovery node is a numeric {@code 0}, which as a chance would silently mean "never fires". Falling back
     * to the authored constant instead keeps a typo from disabling the ability outright — the diagnostic still
     * rides out and blocks the load.
     */
    private NumExpr lowerChance(AbilityDef def, Diagnostics diags) {
        String raw = def.chanceExpr();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Diagnostics scratch = new Diagnostics();
        NumExpr lowered = ExprParser.parse(raw, def.source(), scratch)
                .flatMap(expr -> conditionCompiler.numeric(expr, scratch))
                .orElse(null);
        diags.merge(scratch);
        return scratch.hasErrors() ? null : lowered;
    }

    /**
     * The effect's per-target filter (ADR-0076), or {@code null} when it declares neither knob.
     *
     * <p>{@code each-chance: X} is DEFINED as sugar for {@code each-if: "%target.roll% < X"} — the same single
     * uniform draw the cursor takes when it binds a body. That is what lets a filter and its complement
     * partition: two rows reading one draw, instead of the two independent ability-level rolls the shipped
     * corpus has to use, which can both land or both miss. Declaring both ANDs them, in that order.
     */
    private Cond lowerEachCondition(Args args, Diagnostics diags) {
        Cond gate = args.opt(EACH_IF)
                .filter(Expr.class::isInstance)
                .flatMap(expr -> effectCompiler.compile((Expr) expr, diags))
                .orElse(null);
        Object chance = args.opt(EACH_CHANCE).orElse(null);
        if (chance == null) {
            return gate;
        }
        NumExpr rate = chance instanceof Expr expr
                ? effectCompiler.numeric(expr, diags).orElse(null)
                : new NumExpr.Lit(((Number) chance).doubleValue());
        if (rate == null) {
            return gate; // the expression was rejected; its diagnostic already rode out
        }
        Cond roll = new Cond.NumCmp(new NumExpr.SubjectNum(NumExpr.SubjectFact.ROLL), Cmp.LT, rate);
        return gate == null ? roll : new Cond.And(gate, roll);
    }

    /**
     * The per-target cooldown window in ticks, {@code null} for none. It stamps the SELECTOR TARGET in the
     * cooldown store's existing per-victim dimension under this ability's ENCHANT scope, so an ability with no
     * such scope has nothing to key on — a blocking diagnostic rather than a knob that silently does nothing.
     */
    private NumExpr lowerEachCooldown(Args args, AbilityDef def, Diagnostics diags) {
        Object authored = args.opt(EACH_COOLDOWN).orElse(null);
        if (authored == null) {
            return null;
        }
        if (def.cdScopeEnchant() == null || def.cdScopeEnchant().isBlank()) {
            diags.error(DiagCode.E_EFFECT, "each-cooldown needs a cooldown scope to key its per-target window on",
                    def.source(), "give the ability a cooldown-scope:, or drop each-cooldown:");
            return null;
        }
        return authored instanceof Expr expr
                ? effectCompiler.numeric(expr, diags).orElse(null)
                : new NumExpr.Lit(((Number) authored).doubleValue());
    }

    /**
     * Rewrites expression-valued numeric args to the slot-resolved {@link NumExpr} IR via the same
     * variable&rarr;slot resolution conditions use (§3.4); constant numeric args pass through.
     */
    private Args lowerExprArgs(Args args, Diagnostics diags) {
        Args out = args;
        for (Map.Entry<String, Object> entry : args.asMap().entrySet()) {
            if (entry.getValue() instanceof Expr expr) {
                // The EFFECT-side compiler: an argument is read inside gate 12, where a subject can exist.
                Optional<NumExpr> lowered = effectCompiler.numeric(expr, diags);
                if (lowered.isPresent()) {
                    out = out.with(entry.getKey(), lowered.get());
                }
            } else if (entry.getValue() instanceof ExprMap map) {
                out = out.with(entry.getKey(), lowerExprMap(map, diags));
            }
        }
        return out;
    }

    /**
     * Lower every binding of an {@code EXPR_MAP} arg. A binding that fails to lower is DROPPED (with its own
     * diagnostic already recorded), not defaulted to zero: the placeholder it fills then stays literal, which
     * reads as "unbound" rather than quietly reporting a wrong number.
     */
    private NumExprMap lowerExprMap(ExprMap map, Diagnostics diags) {
        if (map.isEmpty()) {
            return NumExprMap.empty();
        }
        Map<String, NumExpr> lowered = new LinkedHashMap<>();
        for (Map.Entry<String, Expr> binding : map.entries().entrySet()) {
            effectCompiler.numeric(binding.getValue(), diags)
                    .ifPresent(expr -> lowered.put(binding.getKey(), expr));
        }
        return new NumExprMap(lowered);
    }

    /** A {@code WAIT} line's non-negative tick count, or {@code null} (with an {@code E_WAIT_ARG}) on any fault. */
    private static Integer parseWait(EffectLine line, Diagnostics diags) {
        List<String> argTexts = line.argTexts();
        if (argTexts.size() != 1) {
            diags.error(DiagCode.E_WAIT_ARG,
                    "WAIT takes exactly one argument but got " + argTexts.size(),
                    line.source(), "usage: WAIT:<ticks>  (e.g. WAIT:20)");
            return null;
        }
        String raw = argTexts.get(0);
        int ticks;
        try {
            ticks = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            diags.error(DiagCode.E_WAIT_ARG,
                    "WAIT expects a whole number of ticks but got '" + raw + "'",
                    line.source(), "use a non-negative whole number, e.g. WAIT:20");
            return null;
        }
        if (ticks < 0) {
            diags.error(DiagCode.E_WAIT_ARG,
                    "WAIT ticks must be non-negative but got " + ticks,
                    line.source(), "use 0 or more, e.g. WAIT:20");
            return null;
        }
        return ticks;
    }

    /**
     * The typed {@link CompiledCondition}, or {@code null} when blank/absent ("always true") or
     * parsing/lowering failed (diagnostic already recorded).
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
        // A clause "<test> : <outcome>" wraps a boolean test with an authored flow + chance delta;
        // a bare expression is a plain gate (pass → CONTINUE, fail → STOP).
        Expr root = parsed.get();
        if (root instanceof Expr.Clause clause) {
            Optional<Cond> test = conditionCompiler.compile(clause.test(), diags);
            return test.map(c -> new CompiledCondition(
                    c, clause.flow(), FlowKind.CONTINUE, clause.chanceDelta(), def.source())).orElse(null);
        }
        Optional<Cond> lowered = conditionCompiler.compile(root, diags);
        return lowered.map(c -> CompiledCondition.gate(c, def.source())).orElse(null);
    }

    /** Inline {@code @Head{...}} selector if present, else the effect kind's declared default (§3.5). */
    private CompiledSelector resolveSelector(EffectLine line, String effectHead, Diagnostics diags) {
        Optional<String> inline = line.selectorToken();
        if (inline.isPresent()) {
            return selectorCompiler.compileInline(inline.get(), line.selectorSource(), diags);
        }
        return selectorCompiler.defaultFor(defaultSelectorOf.apply(effectHead), line.source(), diags);
    }

    private Affinity affinityOf(String head) {
        Affinity a = affinityOf.apply(head);
        return a != null ? a : Affinity.CONTEXT_LOCAL;
    }
}

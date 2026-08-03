package engine.doc;

import compile.cond.VarBinding;
import engine.condition.BuiltinVars;
import engine.effect.EffectKind;
import engine.effect.kind.BuiltinEffects;
import engine.selector.SelectorKind;
import engine.selector.kind.BuiltinSelectors;
import engine.spec.EffectSpec;
import engine.spec.SelectorSpec;
import engine.spec.TargetSpec;
import engine.trigger.BuiltinTriggers;
import engine.trigger.TriggerKind;
import engine.trigger.TriggerRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import schema.grammar.expr.Cmp;
import schema.grammar.expr.ExprFn;
import schema.grammar.expr.FlowKind;
import schema.grammar.expr.StrOp;
import schema.spec.Param;
import schema.spec.ParamSpec;

/**
 * Generates the StarEnchants DSL reference as Markdown from the five runtime vocabularies — the same
 * registries the in-game {@code ReferenceCatalog} reads, so a newly-registered effect/selector/trigger/
 * operator/variable appears here automatically. Pure and server-free, so it runs in a unit test; the
 * committed {@code docs/reference/dsl-reference.md} is drift-guarded by {@code ReferenceDocDriftTest}.
 *
 * <p>Output is deterministic so the committed file does not drift on re-render: heads sorted because
 * {@code kinds()} order is not stable across JVMs, variables by key, the rest in declared order.
 */
public final class ReferenceDoc {

    private ReferenceDoc() {
    }

    /** The full Markdown reference. */
    public static String render() {
        StringBuilder out = new StringBuilder();
        out.append("# StarEnchants DSL reference\n\n");
        out.append("_Generated from the engine's effect / selector / trigger / condition / variable "
                + "vocabularies. Do not edit by hand — run_ `./gradlew :engine:test --tests \"*ReferenceDocDriftTest\""
                + " -Dse.doc.regen=true` _to regenerate; the build fails if this file drifts from the code._\n\n");
        effects(out);
        selectors(out);
        triggers(out);
        conditions(out);
        variables(out);
        return out.toString().stripTrailing() + "\n"; // exactly one trailing newline (markdownlint MD012)
    }

    private static void effects(StringBuilder out) {
        out.append("## Effects\n\n");
        out.append("The actions an ability runs. Each is a block map `{ HEAD: { param: value, who:, wait: } }` "
                + "in an enchant/set/crystal's `effects:` list.\n\n");
        // Sort by head: the registry's kinds() iteration order is not stable across JVMs, but the doc must be.
        List<EffectKind> kinds = new ArrayList<>(BuiltinEffects.registry().kinds());
        kinds.sort(Comparator.comparing(k -> k.spec().head()));
        for (EffectKind kind : kinds) {
            EffectSpec spec = kind.spec();
            out.append("### ").append(spec.head()).append("\n\n"); // blank below heading (MD022)
            if (!spec.doc().isBlank()) {
                out.append(spec.doc()).append("\n\n"); // blank before the bullet list (MD032)
            }
            out.append("- _affinity_: `").append(spec.affinity()).append("`\n");
            out.append("- _usage_: `").append(spec.paramSpec().usage()).append("`\n");
            appendParams(out, spec.paramSpec());
            for (TargetSpec target : spec.targets()) {
                out.append("- _target_ `").append(target.name()).append("`: selector `")
                        .append(target.selectorType()).append("`\n");
            }
            if (!spec.example().isBlank()) {
                out.append("- _example_: `").append(spec.example()).append("`\n");
            }
            out.append('\n');
        }
    }

    private static void selectors(StringBuilder out) {
        out.append("## Selectors\n\n");
        out.append("Choose WHO an effect targets (`@Self`, `@Victim`, `@Aoe`, …). Routing is the effect's; a "
                + "selector carries no affinity.\n\n");
        List<SelectorKind> kinds = new ArrayList<>(BuiltinSelectors.registry().kinds());
        kinds.sort(Comparator.comparing(k -> k.spec().head()));
        for (SelectorKind kind : kinds) {
            SelectorSpec spec = kind.spec();
            out.append("### ").append(spec.head()).append("\n\n");
            if (!spec.doc().isBlank()) {
                out.append(spec.doc()).append("\n\n");
            }
            out.append("- _usage_: `").append(spec.paramSpec().usage()).append("`\n");
            appendParams(out, spec.paramSpec());
            if (!spec.example().isBlank()) {
                out.append("- _example_: `").append(spec.example()).append("`\n");
            }
            out.append('\n');
        }
    }

    private static void triggers(StringBuilder out) {
        out.append("## Triggers\n\n");
        out.append("The event that fires an ability (an enchant/set/crystal's `trigger:`). Triggers take no "
                + "arguments.\n\n");
        out.append("| Trigger | Direction | Uses held | Scans equipment | Needs target |\n");
        out.append("| --- | --- | --- | --- | --- |\n");
        TriggerRegistry registry = BuiltinTriggers.registry();
        for (int id = 0; id < registry.count(); id++) {
            TriggerKind t = registry.byId(id);
            out.append("| `").append(t.name()).append("` | ").append(t.direction()).append(" | ")
                    .append(t.usesHeld()).append(" | ").append(t.scansEquipment()).append(" | ")
                    .append(t.needsTarget()).append(" |\n");
        }
        out.append('\n');
    }

    private static void conditions(StringBuilder out) {
        out.append("## Conditions\n\n");
        out.append("Boolean expressions over `%scope.name%` variables, combined with `&& || ! ( )` and the "
                + "operators below (an ability's `condition:`).\n\n");
        out.append("### Relational operators\n\n");
        out.append("| Operator | Name |\n| --- | --- |\n");
        for (Cmp cmp : Cmp.values()) {
            out.append("| `").append(cmp.symbol()).append("` | ").append(cmp.name().toLowerCase()).append(" |\n");
        }
        out.append("\n### String operators\n\n");
        out.append("| Operator | Name |\n| --- | --- |\n");
        for (StrOp op : StrOp.values()) {
            out.append("| `").append(op.symbol()).append("` | ").append(op.name().toLowerCase()).append(" |\n");
        }
        out.append("\n### Numeric functions\n\n");
        out.append("Callable anywhere a number is legal — inside a `condition:` and as an expression-valued "
                + "numeric parameter (`{ DAMAGE: { amount: \"min(%combo% * 2, 12)\" } }`). Arguments are "
                + "themselves expressions, so calls nest.\n\n");
        out.append("| Function | Result |\n| --- | --- |\n");
        for (ExprFn fn : ExprFn.values()) {
            out.append("| `").append(fn.token()).append('(').append(fnArgs(fn)).append(")` | ")
                    .append(fnDoc(fn)).append(" |\n");
        }
        out.append("\nA parameter that declares a range clamps an expression to it at evaluation, so a "
                + "`double[0..100]` parameter written as `\"%combo% * 40\"` can never exceed 100 however "
                + "large the variable grows. A constant outside the range is still a load error.\n");
        out.append("\n### Flow / chance clauses\n\n");
        out.append("A condition may end in a clause `<test> : <outcome>` whose outcome is applied when the test "
                + "is true (a bare condition with no clause is a gate that stops the activation when false).\n\n");
        out.append("| Clause | Effect when the test is true |\n| --- | --- |\n");
        for (FlowKind flow : FlowKind.values()) {
            out.append("| `%").append(flow.name().toLowerCase()).append("%` | ").append(flowDoc(flow)).append(" |\n");
        }
        out.append("| `±N %chance%` | add N percentage points to the chance roll |\n");
        out.append('\n');
    }

    private static String fnArgs(ExprFn fn) {
        return switch (fn) {
            case MIN, MAX -> "a, b";
            case CLAMP -> "x, lo, hi";
            case FLOOR -> "x";
            case RAND -> "lo, hi";
        };
    }

    private static String fnDoc(ExprFn fn) {
        return switch (fn) {
            case MIN -> "the smaller of `a` and `b`";
            case MAX -> "the larger of `a` and `b`";
            case CLAMP -> "`x` confined to `[lo, hi]`";
            case FLOOR -> "`x` rounded down (toward negative infinity)";
            case RAND -> "a uniform random value in `[lo, hi)`, drawn once per evaluation";
        };
    }

    private static String flowDoc(FlowKind flow) {
        return switch (flow) {
            case CONTINUE -> "proceed to the chance roll as normal";
            case STOP -> "block this activation";
            case FORCE -> "force activation, skipping the chance roll";
            case ALLOW -> "allow activation regardless of the chance roll";
        };
    }

    private static void variables(StringBuilder out) {
        out.append("## Variables\n\n");
        out.append("The `%scope.name%` facts a condition (or a `MESSAGE`/`SET_VAR`) can read.\n\n");
        out.append("| Variable | Type |\n| --- | --- |\n");
        // bindings() is unordered; sort by key for a stable, deterministic listing (as ReferenceCatalog does).
        Map<String, VarBinding> sorted = new TreeMap<>(BuiltinVars.vocabulary().bindings());
        for (Map.Entry<String, VarBinding> e : sorted.entrySet()) {
            out.append("| `%").append(e.getKey()).append("%` | ").append(e.getValue().kind()).append(" |\n");
        }
        out.append('\n');
        // The keyed families take an author-chosen suffix, so they have no fixed name to list above — but an
        // author who cannot see them here has no way to discover they exist.
        out.append("Three families take a name rather than being fixed facts, and read as NUM:\n\n");
        out.append("- `%victim.var.<name>%` — a counter `SET_VAR` wrote on the victim; `0` when unset.\n");
        out.append("- `%actor.potion.<effect>%` / `%victim.potion.<effect>%` — the active level of one potion "
                + "effect, as amplifier + 1, so `> 0` means \"active\" and `> 1` means \"at least II\"; `0` when "
                + "absent. `<effect>` is resolved when the pack loads, so a name unknown on this version is a "
                + "load error, not a condition that silently never matches.\n\n");
    }

    private static void appendParams(StringBuilder out, ParamSpec spec) {
        for (Param param : spec.params()) {
            out.append("- _param_ `").append(param.name()).append("` `").append(param.type().label()).append('`');
            if (!param.doc().isBlank()) {
                out.append(" — ").append(param.doc());
            }
            out.append('\n');
        }
    }
}

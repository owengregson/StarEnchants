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
import schema.grammar.expr.StrOp;
import schema.spec.Param;
import schema.spec.ParamSpec;

/**
 * Generates the StarEnchants DSL reference as Markdown, straight from the five runtime vocabularies
 * (docs/v3-directives.md §M) — the SAME registries the in-game {@code feature.menu.ReferenceCatalog} and the
 * {@code /se effects|…} commands read, so a newly-registered effect/selector/trigger/condition-operator/
 * variable appears here automatically with no hand-editing. Pure and server-free (the {@code Builtin*}
 * registries construct without a server), so it runs in a unit test; the committed
 * {@code docs/reference/dsl-reference.md} is drift-guarded against this output by {@code ReferenceDocDriftTest}.
 *
 * <p>Deterministic by construction: effects/selectors sorted by head (the registry's {@code kinds()} order
 * is not stable across JVMs), triggers in id order, operators in {@code values()} order, variables sorted by
 * key. The output ends with a trailing newline so the committed file is POSIX-clean.
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
        out.append("The actions an ability runs. Each is a `HEAD:args` token in an enchant/set/crystal's "
                + "`effects:` list.\n\n");
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
            out.append("### ").append(spec.head()).append("\n\n"); // blank below heading (MD022)
            if (!spec.doc().isBlank()) {
                out.append(spec.doc()).append("\n\n"); // blank before the bullet list (MD032)
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
        out.append('\n');
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
    }

    /** Append one bullet per declared param: {@code name  type — doc}. */
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

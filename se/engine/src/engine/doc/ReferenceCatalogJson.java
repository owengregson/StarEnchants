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
import schema.grammar.expr.FlowKind;
import schema.grammar.expr.StrOp;
import schema.spec.Param;
import schema.spec.ParamSpec;
import schema.spec.ParamType;

/**
 * Renders the five runtime vocabularies to the structured {@code catalog.json} the docs site and the web
 * enchant creator consume (ADR-0028) — the same registries {@link ReferenceDoc} and the in-game
 * {@code ReferenceCatalog} read, so a new kind appears automatically. Unlike the Markdown reference this
 * carries the full per-param schema (kind, range, enum values, handle category, default) the creator's
 * form needs. Pure and deterministic (heads sorted, variables by key); drift-guarded by
 * {@code ReferenceCatalogDriftTest}.
 */
public final class ReferenceCatalogJson {

    private ReferenceCatalogJson() {
    }

    public static String render() {
        Json root = Json.obj();
        root.num("version", 1);
        root.raw("effects", effects());
        root.raw("selectors", selectors());
        root.raw("triggers", triggers());
        root.raw("conditions", conditions());
        root.raw("variables", variables());
        return root.toString() + "\n";
    }

    private static Json effects() {
        List<EffectKind> kinds = new ArrayList<>(BuiltinEffects.registry().kinds());
        kinds.sort(Comparator.comparing(k -> k.spec().head()));
        Json arr = Json.arr();
        for (EffectKind kind : kinds) {
            EffectSpec spec = kind.spec();
            Json e = Json.obj();
            e.str("head", spec.head());
            e.str("doc", spec.doc());
            e.str("affinity", String.valueOf(spec.affinity()));
            e.str("usage", spec.paramSpec().usage());
            e.str("example", spec.example());
            e.raw("params", params(spec.paramSpec()));
            Json targets = Json.arr();
            for (TargetSpec target : spec.targets()) {
                Json t = Json.obj();
                t.str("name", target.name());
                t.str("selector", String.valueOf(target.selectorType()));
                targets.add(t);
            }
            e.raw("targets", targets);
            arr.add(e);
        }
        return arr;
    }

    private static Json selectors() {
        List<SelectorKind> kinds = new ArrayList<>(BuiltinSelectors.registry().kinds());
        kinds.sort(Comparator.comparing(k -> k.spec().head()));
        Json arr = Json.arr();
        for (SelectorKind kind : kinds) {
            SelectorSpec spec = kind.spec();
            Json s = Json.obj();
            s.str("head", spec.head());
            s.str("doc", spec.doc());
            s.str("usage", spec.paramSpec().usage());
            s.str("example", spec.example());
            s.raw("params", params(spec.paramSpec()));
            arr.add(s);
        }
        return arr;
    }

    private static Json params(ParamSpec spec) {
        Json arr = Json.arr();
        for (Param param : spec.params()) {
            ParamType type = param.type();
            Json p = Json.obj();
            p.str("name", param.name());
            p.str("kind", type.kind().name());
            p.str("label", type.label());
            p.bool("required", param.required());
            if (type.defaultRaw().isPresent()) {
                p.str("default", type.defaultRaw().get());
            } else {
                p.nul("default");
            }
            if (type.min().isPresent()) {
                p.num("min", type.min().getAsDouble());
            } else {
                p.nul("min");
            }
            if (type.max().isPresent()) {
                p.num("max", type.max().getAsDouble());
            } else {
                p.nul("max");
            }
            Json allowed = Json.arr();
            for (String v : type.allowed()) {
                allowed.addStr(v);
            }
            p.raw("enum", allowed);
            p.str("handle", type.handleCategory() == null ? null : type.handleCategory().name());
            p.str("doc", param.doc());
            arr.add(p);
        }
        return arr;
    }

    private static Json triggers() {
        TriggerRegistry registry = BuiltinTriggers.registry();
        Json arr = Json.arr();
        for (int id = 0; id < registry.count(); id++) {
            TriggerKind t = registry.byId(id);
            Json j = Json.obj();
            j.str("name", t.name());
            j.str("direction", String.valueOf(t.direction()));
            j.bool("usesHeld", t.usesHeld());
            j.bool("scansEquipment", t.scansEquipment());
            j.bool("needsTarget", t.needsTarget());
            arr.add(j);
        }
        return arr;
    }

    private static Json conditions() {
        Json c = Json.obj();
        Json rel = Json.arr();
        for (Cmp cmp : Cmp.values()) {
            Json o = Json.obj();
            o.str("symbol", cmp.symbol());
            o.str("name", cmp.name().toLowerCase());
            rel.add(o);
        }
        c.raw("relational", rel);
        Json str = Json.arr();
        for (StrOp op : StrOp.values()) {
            Json o = Json.obj();
            o.str("symbol", op.symbol());
            o.str("name", op.name().toLowerCase());
            str.add(o);
        }
        c.raw("string", str);
        Json flow = Json.arr();
        for (FlowKind f : FlowKind.values()) {
            Json o = Json.obj();
            o.str("token", "%" + f.name().toLowerCase() + "%");
            o.str("doc", flowDoc(f));
            flow.add(o);
        }
        Json chance = Json.obj();
        chance.str("token", "±N %chance%");
        chance.str("doc", "add N percentage points to the chance roll");
        flow.add(chance);
        c.raw("flow", flow);
        return c;
    }

    private static String flowDoc(FlowKind flow) {
        return switch (flow) {
            case CONTINUE -> "proceed to the chance roll as normal";
            case STOP -> "block this activation";
            case FORCE -> "force activation, skipping the chance roll";
            case ALLOW -> "allow activation regardless of the chance roll";
        };
    }

    private static Json variables() {
        Map<String, VarBinding> sorted = new TreeMap<>(BuiltinVars.vocabulary().bindings());
        Json arr = Json.arr();
        for (Map.Entry<String, VarBinding> e : sorted.entrySet()) {
            Json v = Json.obj();
            v.str("name", e.getKey());
            v.str("type", String.valueOf(e.getValue().kind()));
            arr.add(v);
        }
        return arr;
    }

    /** A tiny, dependency-free, deterministic JSON writer (2-space indent) so the committed file diffs cleanly. */
    private static final class Json {
        private final boolean array;
        private final List<String> keys = new ArrayList<>();
        private final List<String> values = new ArrayList<>();

        private Json(boolean array) {
            this.array = array;
        }

        static Json obj() {
            return new Json(false);
        }

        static Json arr() {
            return new Json(true);
        }

        void str(String key, String value) {
            put(key, value == null ? "null" : quote(value));
        }

        void num(String key, double value) {
            put(key, number(value));
        }

        void bool(String key, boolean value) {
            put(key, Boolean.toString(value));
        }

        void nul(String key) {
            put(key, "null");
        }

        void raw(String key, Json child) {
            put(key, child.toString());
        }

        void add(Json child) {
            values.add(child.toString());
        }

        void addStr(String value) {
            values.add(quote(value));
        }

        private void put(String key, String rendered) {
            keys.add(key);
            values.add(rendered);
        }

        @Override
        public String toString() {
            if (values.isEmpty()) {
                return array ? "[]" : "{}";
            }
            StringBuilder sb = new StringBuilder(array ? "[" : "{").append('\n');
            for (int i = 0; i < values.size(); i++) {
                if (!array) {
                    sb.append(quote(keys.get(i))).append(": ");
                }
                sb.append(indent(values.get(i)));
                if (i < values.size() - 1) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            return sb.append(array ? "]" : "}").toString();
        }

        /** Re-indent a child's already-rendered lines (all but the first) by two spaces. */
        private static String indent(String rendered) {
            return rendered.replace("\n", "\n  ");
        }

        private static String number(double v) {
            return v == Math.rint(v) && !Double.isInfinite(v) ? Long.toString((long) v) : Double.toString(v);
        }

        private static String quote(String s) {
            StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            return sb.append('"').toString();
        }
    }
}

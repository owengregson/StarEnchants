package feature.menu;

import compile.cond.VarBinding;
import engine.condition.BuiltinVars;
import engine.condition.VarVocabulary;
import engine.effect.EffectKind;
import engine.effect.kind.BuiltinEffects;
import engine.selector.SelectorKind;
import engine.selector.kind.BuiltinSelectors;
import engine.spec.EffectSpec;
import engine.spec.SelectorSpec;
import engine.trigger.TriggerKind;
import engine.trigger.TriggerRegistry;
import engine.trigger.BuiltinTriggers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import schema.grammar.expr.Cmp;
import schema.grammar.expr.StrOp;
import schema.spec.Param;
import schema.spec.ParamSpec;

/**
 * The in-game reference dictionary, read straight from the five runtime vocabularies (docs/v3-directives.md
 * §K "Effects/reference browser", §M). Pure and server-free — the {@code Builtin*} registries construct off
 * a server — so it is built once at boot and unit-tested. It surfaces the same vocabulary the {@code /se
 * effects|selectors|triggers|conditions|variables} commands will (§J); the §M auto-doc Markdown is a
 * separate, drift-guarded artefact (task #33) generated from these same registries.
 *
 * <p>Five categories, each a flat list of {@link Entry} (a title + tooltip lore):
 * <ul>
 *   <li><strong>Effects</strong> — head, doc, affinity, each param ({@code name type}), example.</li>
 *   <li><strong>Selectors</strong> — head, doc, params, example (no affinity/targets — routing is the
 *       effect's).</li>
 *   <li><strong>Triggers</strong> — name + the routing metadata (direction / uses-held / scans-equipment /
 *       needs-target); triggers take no DSL args, so there are no params.</li>
 *   <li><strong>Conditions</strong> — the operator vocabulary (relational {@link Cmp} + string {@link StrOp});
 *       conditions are an expression grammar, not a head registry, so this lists the operators a clause uses.</li>
 *   <li><strong>Variables</strong> — every {@code %scope.name%} fact and its type, from the
 *       {@link VarVocabulary} (enumerated via its bindings view).</li>
 * </ul>
 */
public final class ReferenceCatalog {

    /** One reference entry: a display title and its tooltip detail lines (legacy {@code &} colour codes). */
    public record Entry(String title, List<String> lore) {
        public Entry {
            lore = List.copyOf(lore);
        }
    }

    public static final String EFFECTS = "Effects";
    public static final String SELECTORS = "Selectors";
    public static final String TRIGGERS = "Triggers";
    public static final String CONDITIONS = "Conditions";
    public static final String VARIABLES = "Variables";

    private final Map<String, List<Entry>> categories;

    private ReferenceCatalog(Map<String, List<Entry>> categories) {
        this.categories = categories;
    }

    /** Build the reference from the live built-in registries (deterministic; no server needed). */
    public static ReferenceCatalog build() {
        Map<String, List<Entry>> cats = new LinkedHashMap<>();
        cats.put(EFFECTS, effects());
        cats.put(SELECTORS, selectors());
        cats.put(TRIGGERS, triggers());
        cats.put(CONDITIONS, conditions());
        cats.put(VARIABLES, variables());
        return new ReferenceCatalog(cats);
    }

    /** The category names, in display order. */
    public List<String> categories() {
        return new ArrayList<>(categories.keySet());
    }

    /** The entries of {@code category}, or an empty list if unknown. */
    public List<Entry> entries(String category) {
        return categories.getOrDefault(category, List.of());
    }

    private static List<Entry> effects() {
        List<Entry> out = new ArrayList<>();
        for (EffectKind kind : BuiltinEffects.registry().kinds()) {
            EffectSpec spec = kind.spec();
            List<String> lore = new ArrayList<>();
            if (!spec.doc().isBlank()) {
                lore.add("&7" + spec.doc());
            }
            lore.add("&8affinity: &7" + spec.affinity());
            appendParams(lore, spec.paramSpec());
            if (!spec.example().isBlank()) {
                lore.add("&8e.g. &7" + spec.example());
            }
            out.add(new Entry(spec.head(), lore));
        }
        return out;
    }

    private static List<Entry> selectors() {
        List<Entry> out = new ArrayList<>();
        for (SelectorKind kind : BuiltinSelectors.registry().kinds()) {
            SelectorSpec spec = kind.spec();
            List<String> lore = new ArrayList<>();
            if (!spec.doc().isBlank()) {
                lore.add("&7" + spec.doc());
            }
            appendParams(lore, spec.paramSpec());
            if (!spec.example().isBlank()) {
                lore.add("&8e.g. &7" + spec.example());
            }
            out.add(new Entry(spec.head(), lore));
        }
        return out;
    }

    private static List<Entry> triggers() {
        List<Entry> out = new ArrayList<>();
        TriggerRegistry registry = BuiltinTriggers.registry();
        for (int id = 0; id < registry.count(); id++) {
            TriggerKind trigger = registry.byId(id);
            out.add(new Entry(trigger.name(), List.of(
                    "&8direction: &7" + trigger.direction(),
                    "&8uses held: &7" + trigger.usesHeld(),
                    "&8scans equipment: &7" + trigger.scansEquipment(),
                    "&8needs target: &7" + trigger.needsTarget())));
        }
        return out;
    }

    private static List<Entry> conditions() {
        List<Entry> out = new ArrayList<>();
        out.add(new Entry("&8(grammar)", List.of(
                "&7Boolean expressions over %scope.name% variables,",
                "&7combined with &f&& || ! ( )&7 and the operators below.")));
        for (Cmp cmp : Cmp.values()) {
            out.add(new Entry(cmp.symbol(), List.of("&7relational comparator (" + cmp.name().toLowerCase() + ")")));
        }
        for (StrOp op : StrOp.values()) {
            out.add(new Entry(op.symbol(), List.of("&7string operator")));
        }
        return out;
    }

    private static List<Entry> variables() {
        List<Entry> out = new ArrayList<>();
        // bindings() is unordered (an immutable copy); sort by key for a stable, deterministic listing.
        Map<String, VarBinding> sorted = new TreeMap<>(BuiltinVars.vocabulary().bindings());
        for (Map.Entry<String, VarBinding> e : sorted.entrySet()) {
            out.add(new Entry("%" + e.getKey() + "%", List.of("&8type: &7" + e.getValue().kind())));
        }
        return out;
    }

    /** Append one lore line per declared param: {@code • name  type} (+ doc when present). */
    private static void appendParams(List<String> lore, ParamSpec spec) {
        for (Param param : spec.params()) {
            String line = "&8• &f" + param.name() + " &7" + param.type().label();
            if (!param.doc().isBlank()) {
                line += " &8— &7" + param.doc();
            }
            lore.add(line);
        }
    }
}

package compile.stage;

import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.Interner;
import compile.model.Interners;
import compile.model.SourceMap;
import compile.model.StableKeyIndex;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.spec.Args;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The default {@link EraseStage} (docs/architecture.md §4.1). A kept ability's dense id is its
 * output-array position, so the {@link ErasedContent} invariant {@code abilities[i].id() == i} holds.
 * Never throws — every fault is reported and survivable (a duplicate key dropped, an overflowed bit
 * skipped), keeping a broken snapshot loadable.
 *
 * <p>With a canonical trigger vocabulary the interner is pre-seeded so every {@code triggerMask} bit
 * means the same trigger the runtime routes (§3.7); an out-of-vocabulary name is reported, not silently
 * interned. With none, trigger names are interned ad-hoc (lower-level tests).
 */
public final class DefaultEraseStage implements EraseStage {

    /** The {@code triggerMask} is an {@code int}, so trigger ids must fit in {@code [0,32)}. */
    private static final int TRIGGER_BITS = 32;

    /** The {@code worldBlacklist} is a {@code long}, so world ids must fit in {@code [0,64)}. */
    private static final int WORLD_BITS = 64;

    private final List<String> canonicalTriggers;

    /** Ad-hoc mode: trigger names are interned as encountered, with no vocabulary check. */
    public DefaultEraseStage() {
        this(List.of());
    }

    /** Canonical mode: trigger names match this vocabulary case-insensitively, interned to its id order. */
    public DefaultEraseStage(List<String> canonicalTriggers) {
        this.canonicalTriggers = List.copyOf(canonicalTriggers);
    }

    @Override
    public ErasedContent erase(List<LoweredAbility> lowered, Diagnostics diags) {
        Interner worlds = new Interner();
        Interner triggers = new Interner();
        Interner suppress = new Interner();
        Interner cooldownScopes = new Interner();

        boolean canonicalMode = !canonicalTriggers.isEmpty();
        Set<String> knownTriggers = new HashSet<>();
        if (canonicalMode) {
            for (String trigger : canonicalTriggers) {
                String up = trigger.toUpperCase(Locale.ROOT);
                triggers.intern(up);
                knownTriggers.add(up);
            }
        }

        Set<String> seenKeys = new HashSet<>();
        List<Ability> abilities = new ArrayList<>();
        List<String> keysByDenseId = new ArrayList<>();
        Map<Integer, SourceMap.Entry> sourceEntries = new LinkedHashMap<>();

        for (LoweredAbility la : lowered) {
            if (!seenKeys.add(la.stableKey())) {
                diags.error(DiagCode.E_DUP_KEY,
                        "duplicate stable key '" + la.stableKey() + "' — the second definition is dropped",
                        la.source(),
                        "make every ability's stable key unique across all sources");
                continue;
            }

            int id = abilities.size();

            int triggerMask = 0;
            for (String trigger : la.triggers()) {
                String name = canonicalMode ? trigger.toUpperCase(Locale.ROOT) : trigger;
                if (canonicalMode && !knownTriggers.contains(name)) {
                    diags.error(DiagCode.E_UNKNOWN_TRIGGER,
                            "unknown trigger '" + trigger + "'",
                            la.source(),
                            "run /se triggers to list available triggers");
                    continue;
                }
                int tid = triggers.intern(name);
                if (tid >= TRIGGER_BITS) {
                    diags.error(DiagCode.E_TRIGGER_OVERFLOW,
                            "trigger '" + trigger + "' is the " + (tid + 1) + "th distinct trigger; "
                                    + "only " + TRIGGER_BITS + " fit in the trigger mask — this trigger is skipped",
                            la.source(),
                            "reduce the number of distinct trigger names across all content");
                    continue;
                }
                triggerMask |= (1 << tid);
            }

            long worldBlacklist = 0L;
            for (String world : la.worldBlacklist()) {
                int wid = worlds.intern(world);
                if (wid >= WORLD_BITS) {
                    diags.error(DiagCode.E_WORLD_OVERFLOW,
                            "world '" + world + "' is the " + (wid + 1) + "th distinct blacklisted world; "
                                    + "only " + WORLD_BITS + " fit in the world bitset — this world is skipped",
                            la.source(),
                            "reduce the number of distinct blacklisted world names across all content");
                    continue;
                }
                worldBlacklist |= (1L << wid);
            }

            int suppressKey = la.suppressKey() == null ? -1 : suppress.intern(la.suppressKey());
            int cdScopeEnchant = la.cdScopeEnchant() == null ? -1 : cooldownScopes.intern(la.cdScopeEnchant());
            int cdScopeGroup = la.cdScopeGroup() == null ? -1 : cooldownScopes.intern(la.cdScopeGroup());
            int cdScopeType = la.cdScopeType() == null ? -1 : cooldownScopes.intern(la.cdScopeType());

            Ability ability = new Ability(
                    id,
                    la.defId(),
                    la.sourceKind(),
                    triggerMask,
                    la.level(),
                    la.baseChance(),
                    la.cooldownTicks(),
                    la.soulCost(),
                    worldBlacklist,
                    la.condition(),
                    eraseSuppressArgs(la.effects(), cooldownScopes),
                    la.repeatTicks(),
                    la.affinity(),
                    cdScopeEnchant,
                    cdScopeGroup,
                    cdScopeType,
                    suppressKey,
                    la.setPieces());

            abilities.add(ability);
            keysByDenseId.add(la.stableKey());
            sourceEntries.put(la.defId(), new SourceMap.Entry(la.sourceKind(), la.stableKey(), la.source()));
        }

        StableKeyIndex stableKeyIndex = new StableKeyIndex(keysByDenseId);
        SourceMap sourceMap = new SourceMap(sourceEntries);
        Interners interners = new Interners(worlds, triggers, suppress, cooldownScopes);

        return new ErasedContent(abilities.toArray(new Ability[0]), interners, stableKeyIndex, sourceMap);
    }

    /**
     * Interns each {@code SUPPRESS} effect's {@code key} into the SAME {@code cooldownScopes} interner the
     * abilities' {@code cdScope*} use, so a {@code SUPPRESS} write shares one namespace with gate 5's reads
     * (the bridge invariant). A malformed SUPPRESS is left as-is.
     */
    private static CompiledEffect[] eraseSuppressArgs(List<CompiledEffect> effects, Interner cooldownScopes) {
        CompiledEffect[] out = new CompiledEffect[effects.size()];
        for (int i = 0; i < effects.size(); i++) {
            CompiledEffect effect = effects.get(i);
            Args args = effect.args();
            if ("SUPPRESS".equals(effect.head()) && args.has("scope") && args.has("key")) {
                Args rewritten = args
                        .with("scope", (long) scopeKind(args.str("scope")))
                        .with("key", (long) cooldownScopes.intern(args.str("key")));
                out[i] = new CompiledEffect(effect.head(), rewritten, effect.target(),
                        effect.cumulativeWaitTicks(), effect.affinity());
            } else {
                out[i] = effect;
            }
        }
        return out;
    }

    /** The cooldown-scope kind for a {@code SUPPRESS} scope token (matches ActivationPipeline's SCOPE_* ids). */
    private static int scopeKind(String scope) {
        return switch (scope.toUpperCase(Locale.ROOT)) {
            case "GROUP" -> 1;
            case "TYPE" -> 2;
            default -> 0; // ENCHANT
        };
    }
}

package compile.stage;

import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.FactMask;
import compile.model.FactMasks;
import compile.model.Interner;
import compile.model.Interners;
import compile.model.ScopeKinds;
import compile.model.SourceMap;
import compile.model.StableKeyIndex;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.spec.Args;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

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
    /** Effect head &rarr; dense kindId for {@code SUPPRESS scope: KIND} key resolution (ADR-0053); {@code null}
     *  = no registry wired (lower-level tests) — a KIND key then erases to {@code -1} silently, mirroring the
     *  un-stamped {@code kindId} path. */
    private final ToIntFunction<String> effectIdOf;

    /** Ad-hoc mode: trigger names are interned as encountered, with no vocabulary check. */
    public DefaultEraseStage() {
        this(List.of());
    }

    /** Canonical mode: trigger names match this vocabulary case-insensitively, interned to its id order. */
    public DefaultEraseStage(List<String> canonicalTriggers) {
        this(canonicalTriggers, null);
    }

    /** Canonical mode with {@code SUPPRESS scope: KIND} key resolution against the effect registry (ADR-0053). */
    public DefaultEraseStage(List<String> canonicalTriggers, ToIntFunction<String> effectIdOf) {
        this.canonicalTriggers = List.copyOf(canonicalTriggers);
        this.effectIdOf = effectIdOf;
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

            CompiledEffect[] effects = eraseSuppressArgs(la.effects(), cooldownScopes, la.source(), diags);
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
                    effects,
                    la.repeatTicks(),
                    la.affinity(),
                    cdScopeEnchant,
                    cdScopeGroup,
                    cdScopeType,
                    suppressKey,
                    la.setPieces(),
                    la.suppressImmune(),
                    // ADR-0039: only these slots get populated per hit — the chance expression reads the same
                    // buffer at gate 8, so its facts join the union or it would roll against a stale 0.
                    FactMasks.of(la.condition(), la.chanceExpr(), effects),
                    la.chanceExpr(),
                    la.noSoulsMessage(),
                    la.soulCostCarried(),
                    la.noSoulsSound(),
                    la.noSoulsParticle(),
                    la.soulCostGrowth(),
                    la.soulCostCap(),
                    la.soulCostDecayPeriod(),
                    la.cooldownPerVictim());

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
     * Lowers each {@code SUPPRESS}/{@code SUPPRESS_INCOMING} effect's args to ints so {@code run} does zero
     * string work — both directions share the one keying, so they must share the one lowering: scope
     * ENCHANT/GROUP/TYPE interns {@code key} into the SAME {@code cooldownScopes} interner the abilities'
     * {@code cdScope*} use (the gate-5 bridge invariant); scope KIND (ADR-0053) resolves {@code key} as an
     * effect head to its dense kindId — an unknown head is an {@code E_UNKNOWN_KIND} and the op is dropped
     * (the {@code E_UNKNOWN_HANDLE} warn-and-skip policy). {@code mode} erases to its ordinal
     * (0=timed, 1=next-hit). A malformed SUPPRESS is left as-is.
     */
    private CompiledEffect[] eraseSuppressArgs(List<CompiledEffect> effects, Interner cooldownScopes,
                                               Source source, Diagnostics diags) {
        List<CompiledEffect> out = new ArrayList<>(effects.size());
        for (CompiledEffect effect : effects) {
            Args args = effect.args();
            boolean suppressLike = "SUPPRESS".equals(effect.head())
                    || "SUPPRESS_INCOMING".equals(effect.head()); // the same scope/key bridge, other direction
            if (!suppressLike || !args.has("scope") || !args.has("key")) {
                out.add(effect);
                continue;
            }
            int scopeKind = ScopeKinds.of(args.str("scope"));
            long keyId;
            if (scopeKind == ScopeKinds.KIND) {
                String head = args.str("key");
                keyId = effectIdOf == null ? -1 : effectIdOf.applyAsInt(head.toUpperCase(Locale.ROOT));
                if (effectIdOf != null && keyId < 0) {
                    diags.error(DiagCode.E_UNKNOWN_KIND,
                            "unknown effect '" + head + "' for " + effect.head()
                                    + " scope KIND — this effect is skipped",
                            source,
                            "use a registered effect head, e.g. MODIFY_FOOD (run /se docs to list kinds)");
                    continue; // warn-and-skip this one op (the E_UNKNOWN_HANDLE policy)
                }
            } else {
                keyId = cooldownScopes.intern(args.str("key"));
            }
            Args rewritten = args
                    .with("scope", (long) scopeKind)
                    .with("key", keyId)
                    .with("mode", modeOrdinal(args)); // run() reads ints only (the SuppressEffect contract)
            out.add(effect.withArgs(rewritten)); // keep the stamped kindId (ADR-0039)
        }
        return out.toArray(new CompiledEffect[0]);
    }

    /** {@code mode} lowered to its wire ordinal: 1 = next-hit, 0 = timed (also the absent/unknown default). */
    private static long modeOrdinal(Args args) {
        if (!args.has("mode")) {
            return 0L;
        }
        Object raw = args.opt("mode").orElse(null);
        if (raw instanceof Number n) {
            return n.longValue(); // already lowered (a re-erase or a hand-built test)
        }
        return "next-hit".equalsIgnoreCase(String.valueOf(raw)) ? 1L : 0L;
    }
}

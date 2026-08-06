package bootstrap;

import compile.model.Interner;
import compile.model.Interners;
import compile.model.ScopeKinds;
import compile.model.Snapshot;
import compile.model.SourceMap;
import compile.model.StableKeyIndex;
import engine.pipeline.GateOutcome;
import engine.stores.WhyRing;
import item.worn.WornState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import platform.lang.Messages;

/**
 * Pure, server-free rendering for {@code /se why} (ADR-0045): decoded ring attempts &rarr; chat lines through
 * the {@link Messages} catalogue. All id&rarr;name resolution happens HERE, against the live snapshot; a
 * record whose stamped generation mismatches renders as pre-reload instead of resolving against the wrong
 * tables. Cold path — free to allocate and format.
 */
final class WhyRender {

    private WhyRender() {
    }

    static final int DEFAULT_ROWS = 10;   // unfiltered: one chat page
    static final int FILTERED_ROWS = 20;  // with a key filter: deeper history, still bounded

    /**
     * @param filter      a lower-cased content key or {@code null}
     * @param worn        the player's live {@link WornState} or {@code null}
     * @param quarantined the live snapshot's quarantined stable keys
     */
    static List<String> lines(List<WhyRing.Attempt> attempts, Snapshot snapshot, long nowTicks,
                              String filter, List<String> quarantined, WornState worn,
                              String playerName, Messages messages) {
        List<String> out = new ArrayList<>();
        String filterSuffix = filter == null ? ""
                : messages.fragment("command.why.filter-suffix", "KEY", filter);
        out.add(messages.format("command.why.header", "PLAYER", playerName, "FILTER", filterSuffix));

        int gen = snapshot.generation() & 0xFF;
        int depth = filter == null ? DEFAULT_ROWS : FILTERED_ROWS;
        List<String> rows = new ArrayList<>();
        for (WhyRing.Attempt at : attempts) {
            if (rows.size() >= depth) {
                break;
            }
            if (at.verdict() < 0 || at.verdict() >= GateOutcome.values().length) {
                continue; // future enum growth guard: an old ring against a newer server
            }
            if (filter != null && !matchesAttempt(at, filter, gen, snapshot)) {
                continue;
            }
            rows.add(row(at, snapshot, nowTicks, gen, messages));
        }

        if (rows.isEmpty()) {
            out.add(filter == null
                    ? messages.format("command.why.none", "PLAYER", playerName)
                    : messages.format("command.why.none-matching", "KEY", filter));
        } else {
            out.addAll(rows);
        }

        if (filter != null) {
            appendQuarantineNote(out, filter, quarantined, messages);
            if (rows.isEmpty()) {
                diagnoseFilter(out, filter, snapshot, worn, playerName, messages);
            }
        }
        return out;
    }

    /**
     * Filter match: {@code venom} / {@code enchants/venom} / {@code enchants/venom/3} / {@code yeti} /
     * {@code crystals/frost} all work. A key matches iff key == f, key startsWith f + "/", or the key minus its
     * source prefix does.
     */
    static boolean matches(String stableKey, String filter) {
        if (stableKey.equals(filter) || stableKey.startsWith(filter + "/")) {
            return true;
        }
        int slash = stableKey.indexOf('/');
        if (slash < 0) {
            return false;
        }
        String rest = stableKey.substring(slash + 1);
        return rest.equals(filter) || rest.startsWith(filter + "/");
    }

    private static boolean matchesAttempt(WhyRing.Attempt at, String filter, int gen, Snapshot snapshot) {
        if (at.gen() != gen) {
            return false; // an unresolvable (pre-reload) key can never match a filter
        }
        SourceMap.Entry entry = snapshot.sourceMap().lookup(at.defId());
        return entry != null && matches(entry.stableKey(), filter);
    }

    private static String row(WhyRing.Attempt at, Snapshot snapshot, long nowTicks, int gen, Messages messages) {
        long agoTicks = nowTicks - at.tick();
        long ago = agoTicks < 0 ? 0 : agoTicks / 20; // a racing writer can stamp a tick newer than the read
        SourceMap.Entry entry = at.gen() == gen ? snapshot.sourceMap().lookup(at.defId()) : null;
        if (entry == null) {
            return messages.format("command.why.stale", "AGO", ago, "DEFID", at.defId());
        }
        String key = "[" + entry.sourceKind().name().toLowerCase(Locale.ROOT) + "] " + entry.stableKey();
        return messages.format("command.why.attempt", "AGO", ago, "KEY", key,
                "TRIGGER", triggerName(snapshot.interners(), at.triggerId()), "RESULT", result(at, snapshot, messages));
    }

    /** The per-verdict result fragment; exhaustive over {@link GateOutcome} (out-of-range is guarded before here). */
    private static String result(WhyRing.Attempt at, Snapshot snapshot, Messages messages) {
        Interners in = snapshot.interners();
        return switch (GateOutcome.values()[at.verdict()]) {
            case BLOCKED_WORLD -> messages.fragment("command.why.blocked-world",
                    "WORLD", boundedName(in.worlds(), at.pA(), "#"));
            case BLOCKED_PROTECTION -> messages.fragment("command.why.blocked-protection");
            case WRONG_TRIGGER -> messages.fragment("command.why.wrong-trigger");
            case OUT_OF_LEVEL -> messages.fragment("command.why.out-of-level", "LEVEL", at.pA());
            case SUPPRESSED -> suppressed(at, snapshot, messages);
            case ON_COOLDOWN -> messages.fragment("command.why.on-cooldown", "TICKS", at.pB(),
                    "SCOPE", scopeWord(WhyRing.scopeKind(at.pA())),
                    "KEY", boundedName(in.cooldownScopes(), WhyRing.scopeId(at.pA()), "#"));
            case CONDITION_FAILED -> messages.fragment("command.why.condition");
            case CHANCE_FAILED -> messages.fragment("command.why.chance",
                    "ROLL", percent(at.pA()), "CHANCE", percent(at.pB()));
            // ADR-0076: CHANCE is the UNREBATED rate, so the pair reads "you would have procced at {CHANCE},
            // and the rebate took it" — the one answer a bare CHANCE_FAILED could never give an operator.
            case REBATED -> messages.fragment("command.why.rebated",
                    "ROLL", percent(at.pA()), "CHANCE", percent(at.pB()));
            case CANCELLED -> messages.fragment("command.why.cancelled");
            case NO_SOULS -> messages.fragment(
                    at.pA() == 0 ? "command.why.no-souls-gem" : "command.why.no-souls-pool", "COST", at.pB());
            case ACTIVATED -> at.pA() < 0
                    ? messages.fragment("command.why.fired-forced")
                    : messages.fragment("command.why.fired", "ROLL", percent(at.pA()), "CHANCE", percent(at.pB()));
        };
    }

    private static String suppressed(WhyRing.Attempt at, Snapshot snapshot, Messages messages) {
        Interners in = snapshot.interners();
        if (WhyRing.scopeFlavor(at.pA()) == 0) { // transient per-activation set; KEY via the suppress interner
            return messages.fragment("command.why.suppressed-transient",
                    "KEY", boundedName(in.suppress(), WhyRing.scopeId(at.pA()), "#"));
        }
        int byDefId = at.pB();
        String by = "";
        if (byDefId >= 0) { // gen already matched to reach here, so the suppressor resolves against this snapshot
            SourceMap.Entry src = snapshot.sourceMap().lookup(byDefId);
            if (src != null) {
                by = messages.fragment("command.why.suppressed-by", "SOURCE", src.stableKey());
            }
        }
        return messages.fragment("command.why.suppressed",
                "SCOPE", scopeWord(WhyRing.scopeKind(at.pA())),
                "KEY", boundedName(in.cooldownScopes(), WhyRing.scopeId(at.pA()), "#"), "BY", by);
    }

    private static void appendQuarantineNote(List<String> out, String filter, List<String> quarantined,
                                             Messages messages) {
        for (String key : quarantined) {
            if (matches(key, filter)) {
                out.add(messages.format("command.why.quarantined-note", "KEY", key));
                return;
            }
        }
    }

    private static void diagnoseFilter(List<String> out, String filter, Snapshot snapshot, WornState worn,
                                       String playerName, Messages messages) {
        StableKeyIndex keys = snapshot.stableKeys();
        List<Integer> matchingIds = new ArrayList<>();
        for (int id = 0; id < keys.size(); id++) {
            String key = keys.keyOf(id);
            if (key != null && matches(key, filter)) {
                matchingIds.add(id);
            }
        }
        if (matchingIds.isEmpty()) {
            out.add(messages.format("command.why.unknown-key", "KEY", filter));
            return;
        }
        if (worn == null) {
            out.add(messages.format("command.why.worn-note-missing", "KEY", filter, "PLAYER", playerName));
            return;
        }
        Interner triggers = snapshot.interners().triggers();
        List<String> listensOn = new ArrayList<>();
        for (int t = 0; t < triggers.size(); t++) {
            for (int id : worn.byTrigger(t)) {
                if (matchingIds.contains(id)) {
                    listensOn.add(triggers.nameOf(t));
                    break;
                }
            }
        }
        if (listensOn.isEmpty()) {
            out.add(messages.format("command.why.worn-note-missing", "KEY", filter, "PLAYER", playerName));
        } else {
            out.add(messages.format("command.why.worn-note-listening", "KEY", filter, "PLAYER", playerName,
                    "TRIGGERS", String.join(", ", listensOn)));
        }
    }

    private static String triggerName(Interners in, int triggerId) {
        Interner triggers = in.triggers();
        return triggerId >= 0 && triggerId < triggers.size() ? triggers.nameOf(triggerId) : "trigger#" + triggerId;
    }

    /** The DISABLE_* scope word for a {@link ScopeKinds} kind int (the effect-head vocabulary). */
    private static String scopeWord(int scopeKind) {
        return switch (scopeKind) {
            case ScopeKinds.GROUP -> "GROUP";
            case ScopeKinds.TYPE -> "TYPE";
            default -> "ENCHANT";
        };
    }

    private static String boundedName(Interner interner, int id, String miss) {
        return id >= 0 && id < interner.size() ? interner.nameOf(id) : miss + id;
    }

    /** Basis points (round(pct * 100)) back to a 2-dp percent string. */
    private static String percent(int basisPoints) {
        return String.format(Locale.ROOT, "%.2f", basisPoints / 100.0);
    }
}

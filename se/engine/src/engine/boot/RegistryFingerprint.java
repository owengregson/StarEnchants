package engine.boot;

import compile.cond.VarBinding;
import engine.condition.BuiltinVars;
import engine.condition.VarVocabulary;
import engine.effect.EffectKind;
import engine.effect.EffectRegistry;
import engine.selector.SelectorKind;
import engine.selector.SelectorRegistry;
import engine.selector.kind.BuiltinSelectors;
import engine.spec.EffectSpec;
import engine.spec.SelectorSpec;
import engine.trigger.BuiltinTriggers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import schema.grammar.expr.Cmp;
import schema.grammar.expr.FlowKind;
import schema.grammar.expr.StrOp;
import schema.spec.Param;
import schema.spec.ParamSpec;
import schema.spec.ParamType;

/**
 * A stable SHA-256 over a canonical serialization of the live content-authoring surface (ADR-0046): effect
 * heads + per-kind {@link ParamSpec}, selector heads + {@link ParamSpec}, trigger names, condition operator
 * vocabulary, and the {@code %var%} vocabulary. A pack stamps this at export and {@code /se pack apply}
 * compares it against the live one — the fingerprint EXPLAINS a mismatch fast; the dry-run compile DECIDES
 * (ADR-0046). Pure, server-free, era-neutral (shared by both overlay trees; JDK-8-API-safe — no
 * {@code HexFormat}, a manual hex loop instead).
 *
 * <p>Sorting (heads, enum values, triggers, operators, vars by key) makes the hash independent of
 * registration/declaration order, so addon registration order can never perturb it; declared param ORDER is
 * significant (positional parsing makes it part of the ABI). Computed from the LIVE {@link EffectRegistry}
 * ({@code builtins + registered addon kinds}); selectors/triggers/vars stay builtin-only, mirroring
 * {@code ContentCompiler.production}'s sourcing — a pack exported with addon X bakes X's heads in, and applied
 * where X is absent reads MISMATCH (the dry-run then reports {@code E_UNKNOWN_KIND} per line).
 *
 * <p>Excluded (surface-irrelevant): {@code doc}/{@code example} prose, {@code Affinity}/target slots/
 * {@code needsActorOrigin} (runtime routing — changes behaviour, never authorability), {@link
 * schema.spec.CrossRule}s (unserializable lambdas — a cross-rule change is caught by the dry-run), and dense
 * kind ids / trigger mask bits / var slots (per-run accelerators).
 */
public final class RegistryFingerprint {

    /** Bumped only when the canonical serialization format itself changes (then every older fingerprint compares unequal). */
    public static final String VERSION = "1";

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private RegistryFingerprint() {
    }

    /** Canonical serialization of the live authoring surface (§2 of ADR-0046). */
    public static String canonical(EffectRegistry effects) {
        return canonical(effects, BuiltinSelectors.registry(),
                BuiltinTriggers.registry().names(), BuiltinVars.vocabulary());
    }

    // Package-private seam so the sensitivity tests can vary each vocabulary independently.
    static String canonical(EffectRegistry effects, SelectorRegistry selectors,
                            List<String> triggerNames, VarVocabulary vars) {
        StringBuilder sb = new StringBuilder();
        sb.append("starenchants authoring surface v").append(VERSION).append('\n');

        sb.append("[effects]\n");
        List<EffectKind> effectKinds = new ArrayList<>(effects.kinds());
        effectKinds.sort(Comparator.comparing(k -> k.spec().head()));
        for (EffectKind kind : effectKinds) {
            EffectSpec spec = kind.spec();
            sb.append("effect ").append(spec.head()).append('\n');
            appendParams(sb, spec.paramSpec());
        }

        sb.append("[selectors]\n");
        List<SelectorKind> selectorKinds = new ArrayList<>(selectors.kinds());
        selectorKinds.sort(Comparator.comparing(k -> k.spec().head()));
        for (SelectorKind kind : selectorKinds) {
            SelectorSpec spec = kind.spec();
            sb.append("selector ").append(spec.head()).append('\n');
            appendParams(sb, spec.paramSpec());
        }

        sb.append("[triggers]\n");
        List<String> triggers = new ArrayList<>(triggerNames);
        triggers.sort(Comparator.naturalOrder());
        for (String trigger : triggers) {
            sb.append("trigger ").append(trigger).append('\n');
        }

        sb.append("[operators]\n");
        for (String symbol : sortedSymbols(cmpSymbols())) {
            sb.append("cmp ").append(symbol).append('\n');
        }
        for (String symbol : sortedSymbols(strOpSymbols())) {
            sb.append("strop ").append(symbol).append('\n');
        }
        for (String name : sortedSymbols(flowNames())) {
            sb.append("flow ").append(name).append('\n');
        }
        // The ±N %chance% clause form — a constant today, emitted verbatim so the clause grammar is represented.
        sb.append("flowmod chance\n");

        sb.append("[vars]\n");
        // bindings() keys are already canonical lower-case scope.name; TreeMap sorts them (slot indices excluded).
        for (Map.Entry<String, VarBinding> e : new TreeMap<>(vars.bindings()).entrySet()) {
            sb.append("var ").append(e.getKey()).append(' ').append(e.getValue().kind().name()).append('\n');
        }

        return sb.toString();
    }

    /** {@code "1:" + sha256-hex(canonical)} — stable across JVMs by construction (the only JVM-dependent input, iteration order, is sorted away). */
    public static String hash(EffectRegistry effects) {
        return VERSION + ":" + sha256Hex(canonical(effects));
    }

    /** Human-readable manifest summary, e.g. {@code "effects 81 · selectors 12 · triggers 30 · variables 60"}. */
    public static String summary(EffectRegistry effects) {
        return "effects " + effects.kinds().size()
                + " · selectors " + BuiltinSelectors.registry().kinds().size()
                + " · triggers " + BuiltinTriggers.registry().names().size()
                + " · variables " + BuiltinVars.vocabulary().bindings().size();
    }

    /** {@code "1:ab12cd34ef56"} — the chat-length form of a full fingerprint (never hashed against). */
    public static String shortForm(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) {
            return fingerprint; // manifests are operator-editable text — never throw on a malformed value
        }
        int colon = fingerprint.indexOf(':');
        if (colon < 0) {
            return fingerprint;
        }
        String hex = fingerprint.substring(colon + 1);
        if (hex.length() <= 12) {
            return fingerprint;
        }
        return fingerprint.substring(0, colon + 1) + hex.substring(0, 12);
    }

    private static void appendParams(StringBuilder sb, ParamSpec spec) {
        for (Param param : spec.params()) {
            ParamType type = param.type();
            // default= is last on purpose: defaultRaw() may contain spaces (verbatim to end of line).
            sb.append("  param ")
                    .append(param.name()).append(' ')
                    .append(type.kind().name()).append(' ')
                    .append(param.required() ? "required" : "optional")
                    .append(" min=").append(type.min().isPresent() ? num(type.min().getAsDouble()) : "-")
                    .append(" max=").append(type.max().isPresent() ? num(type.max().getAsDouble()) : "-")
                    .append(" enum=").append(enumField(type))
                    .append(" handle=").append(type.handleCategory() != null ? type.handleCategory().name() : "-")
                    .append(" default=").append(type.defaultRaw().orElse("-"))
                    .append('\n');
        }
    }

    /** Enum allowed-values sorted (String.compareTo) + comma-joined, so reordering the list never moves the hash; empty → {@code -}. */
    private static String enumField(ParamType type) {
        List<String> allowed = type.allowed();
        if (allowed.isEmpty()) {
            return "-";
        }
        List<String> sorted = new ArrayList<>(allowed);
        sorted.sort(Comparator.naturalOrder());
        return String.join(",", sorted);
    }

    private static List<String> cmpSymbols() {
        List<String> out = new ArrayList<>();
        for (Cmp cmp : Cmp.values()) {
            out.add(cmp.symbol());
        }
        return out;
    }

    private static List<String> strOpSymbols() {
        List<String> out = new ArrayList<>();
        for (StrOp op : StrOp.values()) {
            out.add(op.symbol());
        }
        return out;
    }

    private static List<String> flowNames() {
        List<String> out = new ArrayList<>();
        for (FlowKind flow : FlowKind.values()) {
            out.add(flow.name().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /** Sorted by natural order, so a constant reorder without a vocabulary change never moves the hash. */
    private static List<String> sortedSymbols(List<String> symbols) {
        symbols.sort(Comparator.naturalOrder());
        return symbols;
    }

    /** Canonical number form: an integral value renders without a decimal (same trim as {@code ParamType.label()}). */
    private static String num(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }

    private static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                int value = b & 0xFF;
                sb.append(HEX[value >>> 4]).append(HEX[value & 0x0F]);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is guaranteed present on every JVM", impossible);
        }
    }
}

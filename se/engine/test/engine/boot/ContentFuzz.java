package engine.boot;

import compile.cond.VarBinding;
import compile.def.AbilityDef;
import compile.model.SourceKind;
import engine.condition.BuiltinVars;
import engine.condition.VarVocabulary;
import engine.effect.EffectKind;
import engine.effect.EffectRegistry;
import engine.effect.kind.BuiltinEffects;
import engine.selector.SelectorKind;
import engine.selector.SelectorRegistry;
import engine.selector.kind.BuiltinSelectors;
import engine.trigger.BuiltinTriggers;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import schema.diag.DiagCode;
import schema.diag.Source;
import schema.grammar.EffectLine;
import schema.spec.Param;
import schema.spec.ParamSpec;
import schema.spec.ParamType;
import testfx.Defs;

/**
 * Seeded content generator whose grammar IS each kind's {@link ParamSpec}: draws valid and adversarial
 * near-valid {@link AbilityDef}s (and whole enchant YAML files) for {@link CompilerFuzzTest}/{@link
 * LoaderFuzzTest}. All seeds are fixed and every draw is from a deterministically ordered list, so the same
 * seed yields the same sample on every JVM (no assertions here — the two test classes own those).
 */
final class ContentFuzz {

    static final long BASE_SEED = 0x5EEDC0DEL;

    /** The accepted boolean spellings (a generator input mirroring {@code ParamType.parseBool}'s vocabulary). */
    private static final List<String> BOOL_VOCAB =
            List.of("true", "yes", "on", "1", "false", "no", "off", "0");

    /** Test-owned world names (well under the 64-bit blacklist cap), for {@code disabled-worlds} draws. */
    private static final List<String> WORLDS =
            List.of("world", "world_nether", "world_the_end", "arena", "spawn", "the_void");

    private static final String STRING_ALPHA =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_ .-";
    private static final String SELECTOR_ALPHA =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_.-";

    private ContentFuzz() {
    }

    /** Deterministic per-(head,case) seed — adding a kind never shifts another kind's stream. */
    static long seedFor(String head, int caseIndex) {
        return BASE_SEED * 1_000_003L + head.hashCode() * 31L + caseIndex;
    }

    enum ValueKind { NUM_LITERAL, NUM_EXPR, BOOL, ENUM, STRING, HANDLE }

    /** One generated named-arg value plus how it should read back (drives {@code CompilerFuzzTest} round-trip checks). */
    record Authored(String raw, ValueKind kind) {
    }

    /** One generated effect line; a {@code WAIT} line is head {@code "WAIT"} with a single {@code ticks} value. */
    record GeneratedLine(String head, Map<String, Authored> named, String selectorToken) {
    }

    /** One generated def + a human label, the {@link DiagCode} a mutation deterministically produces (null = totality only), and the per-line generation record. */
    record Case(AbilityDef def, String label, DiagCode expected, List<GeneratedLine> lines) {
    }

    /** One syntactically valid enchant file plus its declared level count. */
    record YamlSample(String fileName, String yaml, int levelCount) {
    }

    /** Deterministically ordered vocabulary snapshot built once from the live registries. */
    record Vocab(EffectRegistry effects,
                 SelectorRegistry selectors,
                 List<String> triggerNames,
                 List<EffectKind> effectKinds,
                 List<SelectorKind> selectorKinds,
                 List<String> numVars,
                 List<String> flagVars,
                 List<String> strVars) {

        static Vocab live() {
            EffectRegistry effects = BuiltinEffects.registry();
            SelectorRegistry selectors = BuiltinSelectors.registry();
            List<String> num = new ArrayList<>();
            List<String> flag = new ArrayList<>();
            List<String> str = new ArrayList<>();
            VarVocabulary vv = BuiltinVars.vocabulary();
            // bindings() is a Map.copyOf whose iteration order is salted per JVM run — sort by key first.
            for (Map.Entry<String, VarBinding> e : new TreeMap<>(vv.bindings()).entrySet()) {
                switch (e.getValue().kind()) {
                    case NUM -> num.add(e.getKey());
                    case BOOL -> flag.add(e.getKey());
                    case STR -> str.add(e.getKey());
                }
            }
            return new Vocab(effects, selectors, BuiltinTriggers.registry().names(),
                    List.copyOf(effects.kinds()), List.copyOf(selectors.kinds()),
                    List.copyOf(num), List.copyOf(flag), List.copyOf(str));
        }
    }

    // ────────────────────────────────────────────────────────────── Family A: def-level ──

    static Case valid(EffectKind kind, int caseIndex, Vocab vocab) {
        Plan p = buildValidPlan(kind, caseIndex, vocab);
        return new Case(assemble(p), "valid #" + caseIndex, null, List.copyOf(p.lines));
    }

    static Case adversarial(EffectKind kind, int caseIndex, Vocab vocab) {
        Plan p = buildValidPlan(kind, caseIndex, vocab);
        Random rnd = new Random(seedFor(kind.head(), caseIndex));
        Mutation mutation = Mutation.values()[caseIndex];
        DiagCode expected = applyMutation(mutation, kind, p, rnd, vocab, seedFor(kind.head(), caseIndex));
        return new Case(assemble(p), "mutation=" + p.label, expected, List.copyOf(p.lines));
    }

    // The 12-entry mutation catalogue (index = case index). WRONG_TYPE/BOUNDARY/HUGE_NUMERIC/DROP_REQUIRED/
    // UNICODE_GARBAGE substitute UNKNOWN_PARAM when a kind lacks the param they target.
    private enum Mutation {
        WRONG_TYPE, BOUNDARY, HUGE_NUMERIC, UNKNOWN_HEAD, UNKNOWN_PARAM, DROP_REQUIRED, UNICODE_GARBAGE,
        MALFORMED_SELECTOR, MALFORMED_CONDITION, WAIT_ABUSE, EMPTY_EVERYTHING, UNKNOWN_TRIGGER
    }

    private static DiagCode applyMutation(Mutation mutation, EffectKind kind, Plan p, Random rnd, Vocab vocab,
                                          long seed) {
        p.label = mutation.name();
        List<Param> params = kind.spec().paramSpec().params();
        return switch (mutation) {
            case WRONG_TYPE -> wrongType(p, params);
            case BOUNDARY -> boundary(p, params);
            case HUGE_NUMERIC -> huge(p, params, rnd);
            case UNKNOWN_HEAD -> {
                GeneratedLine head = p.lines.get(0);
                p.lines.set(0, new GeneratedLine(kind.head() + "_ZZ", head.named(), head.selectorToken()));
                yield DiagCode.E_UNKNOWN_KIND;
            }
            case UNKNOWN_PARAM -> unknownParam(p);
            case DROP_REQUIRED -> dropRequired(p, params);
            case UNICODE_GARBAGE -> unicodeGarbage(p, params);
            case MALFORMED_SELECTOR -> {
                String[] rot = {"@", "@Nope", "@Aoe{r=}", "@Aoe{r=4", "@Aoe{=4}"};
                GeneratedLine head = p.lines.get(0);
                p.lines.set(0, new GeneratedLine(head.head(), head.named(), rot[rnd.nextInt(rot.length)]));
                yield null;
            }
            case MALFORMED_CONDITION -> {
                String[] rot = {"%% && ((", "%actor.health% <", "(%sneaking%",
                        "%damage% contains \"x\"", "1 : %bogus% : %stop%", "%no.such.var% > 1",
                        "min(1) > 0", "clamp(1, 2) > 0", "unknownfn(1) > 0", "min(1, 2 > 0",
                        "floor() > 0", "max(%damage%) > 0"};
                p.condition = rot[rnd.nextInt(rot.length)];
                yield null;
            }
            case WAIT_ABUSE -> {
                String ticks = (seed & 1L) == 1L ? "ten" : "-5";
                p.lines.add(0, waitLine(ticks));
                yield DiagCode.E_WAIT_ARG;
            }
            case EMPTY_EVERYTHING -> {
                p.triggers.clear();
                p.lines.clear();
                p.condition = "";
                yield null;
            }
            case UNKNOWN_TRIGGER -> {
                p.triggers = new ArrayList<>(List.of("NOT_A_TRIGGER", vocab.triggerNames().get(0)));
                yield DiagCode.E_UNKNOWN_TRIGGER;
            }
        };
    }

    private static DiagCode wrongType(Plan p, List<Param> params) {
        Param numeric = firstOf(params, ParamType.Kind.DOUBLE, ParamType.Kind.INT, ParamType.Kind.TICKS);
        if (numeric != null) {
            setNamed(p, numeric.name(), new Authored("abc", ValueKind.NUM_LITERAL));
            return DiagCode.E_TYPE;
        }
        Param bool = firstOf(params, ParamType.Kind.BOOL);
        if (bool != null) {
            setNamed(p, bool.name(), new Authored("12.5", ValueKind.BOOL));
            return DiagCode.E_TYPE;
        }
        Param en = firstOf(params, ParamType.Kind.ENUM);
        if (en != null) {
            setNamed(p, en.name(), new Authored("NOT_A_VALUE", ValueKind.ENUM));
            return DiagCode.E_ENUM;
        }
        return substituteUnknownParam(p);
    }

    private static DiagCode boundary(Plan p, List<Param> params) {
        for (Param param : params) {
            ParamType t = param.type();
            if (!isNumeric(t.kind()) || (t.min().isEmpty() && t.max().isEmpty())) {
                continue;
            }
            double value = t.min().isPresent() ? t.min().getAsDouble() - 1 : t.max().getAsDouble() + 1;
            String token = t.kind() == ParamType.Kind.DOUBLE ? fmt3(value) : Long.toString((long) value);
            setNamed(p, param.name(), new Authored(token, ValueKind.NUM_LITERAL));
            return DiagCode.E_RANGE;
        }
        return substituteUnknownParam(p);
    }

    private static DiagCode huge(Plan p, List<Param> params, Random rnd) {
        Param numeric = firstOf(params, ParamType.Kind.DOUBLE, ParamType.Kind.INT, ParamType.Kind.TICKS);
        if (numeric == null) {
            return substituteUnknownParam(p);
        }
        String[] rot = {"9223372036854775808", "1e309", "NaN", "Infinity", "-0.0", "999999999999999999999.9"};
        setNamed(p, numeric.name(), new Authored(rot[rnd.nextInt(rot.length)], ValueKind.NUM_LITERAL));
        return null;
    }

    private static DiagCode unknownParam(Plan p) {
        setNamed(p, "bogus_arg", new Authored("1", ValueKind.STRING));
        return DiagCode.E_UNKNOWN_EFFECT_PARAM;
    }

    private static DiagCode dropRequired(Plan p, List<Param> params) {
        for (Param param : params) {
            if (param.required()) {
                GeneratedLine line = p.lines.get(0);
                Map<String, Authored> named = new LinkedHashMap<>(line.named());
                named.remove(param.name());
                p.lines.set(0, new GeneratedLine(line.head(), named, line.selectorToken()));
                return DiagCode.E_MISSING_ARG;
            }
        }
        return substituteUnknownParam(p);
    }

    private static DiagCode unicodeGarbage(Plan p, List<Param> params) {
        Param str = firstOf(params, ParamType.Kind.STRING);
        if (str != null) {
            setNamed(p, str.name(), new Authored("‮💀 §ḱ", ValueKind.STRING));
            return null;
        }
        Param handle = firstOf(params, ParamType.Kind.HANDLE);
        if (handle != null) {
            setNamed(p, handle.name(), new Authored("‮💀 §ḱ", ValueKind.HANDLE));
            return null;
        }
        return substituteUnknownParam(p);
    }

    private static DiagCode substituteUnknownParam(Plan p) {
        p.label = p.label + "→UNKNOWN_PARAM";
        return unknownParam(p);
    }

    /** Set (or add) a named arg on line 0 — the walked kind's line. */
    private static void setNamed(Plan p, String name, Authored value) {
        GeneratedLine line = p.lines.get(0);
        Map<String, Authored> named = new LinkedHashMap<>(line.named());
        named.put(name, value);
        p.lines.set(0, new GeneratedLine(line.head(), named, line.selectorToken()));
    }

    // ─────────────────────────────────────────────────────── selector-walk support ──

    static Case selectorCase(SelectorKind selector, int caseIndex, boolean validArgs, Vocab vocab) {
        Random rnd = new Random(seedFor(selector.head(), caseIndex));
        EffectKind host = vocab.effectKinds().get(0);
        GeneratedLine line = new GeneratedLine(host.head(),
                validNamed(host.spec().paramSpec(), rnd, caseIndex, vocab),
                validArgs ? "@" + selector.head() + selectorArgs(selector, rnd) : null);
        DiagCode expected = null;
        if (!validArgs) {
            String[] junk = {"@", "@Nope", "@Aoe{r=}", "@Aoe{r=4", "@Aoe{=4}"};
            int pick = rnd.nextInt(3);
            if (pick == 0) {
                line = new GeneratedLine(host.head(), line.named(),
                        "@" + selector.head() + "{bogus_arg=1}"); // W_SELECTOR_UNKNOWN_ARG (non-blocking)
            } else if (pick == 1) {
                Param bounded = firstBounded(selector.spec().paramSpec().params());
                if (bounded != null) {
                    double v = bounded.type().min().isPresent()
                            ? bounded.type().min().getAsDouble() - 1 : bounded.type().max().getAsDouble() + 1;
                    String token = bounded.type().kind() == ParamType.Kind.DOUBLE
                            ? fmt3(v) : Long.toString((long) v);
                    line = new GeneratedLine(host.head(), line.named(),
                            "@" + selector.head() + "{" + bounded.name() + "=" + token + "}");
                    expected = DiagCode.E_RANGE;
                } else {
                    line = new GeneratedLine(host.head(), line.named(), junk[rnd.nextInt(junk.length)]);
                }
            } else {
                line = new GeneratedLine(host.head(), line.named(), junk[rnd.nextInt(junk.length)]);
            }
        }
        Plan p = new Plan();
        p.stableKey = "fuzz/selector/" + selector.head() + "/" + caseIndex;
        p.defId = caseIndex;
        p.triggers = new ArrayList<>(List.of(vocab.triggerNames().get(0)));
        p.source = Source.of("fuzz.yml", caseIndex + 1, 1);
        p.lines.add(line);
        return new Case(assemble(p), (validArgs ? "sel-valid #" : "sel-adv #") + caseIndex, expected,
                List.copyOf(p.lines));
    }

    private static String selectorArgs(SelectorKind selector, Random rnd) {
        StringBuilder sb = new StringBuilder();
        for (Param param : selector.spec().paramSpec().params()) {
            if (!(param.required() || rnd.nextBoolean())) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(param.name()).append('=').append(selectorValue(param.type(), rnd));
        }
        return sb.length() == 0 ? "" : "{" + sb + "}";
    }

    // ─────────────────────────────────────────────────────────── valid plan build ──

    private static Plan buildValidPlan(EffectKind kind, int i, Vocab vocab) {
        Random rnd = new Random(seedFor(kind.head(), i));
        Plan p = new Plan();
        p.source = Source.of("fuzz.yml", i + 1, 1);
        p.sourceKind = SourceKind.values()[rnd.nextInt(SourceKind.values().length)];
        p.stableKey = "fuzz/" + kind.head() + "/" + i;
        p.defId = i;
        p.level = rnd.nextInt(6);
        p.chance = rnd.nextDouble() * 100.0; // [0,100) — the loader-owned precondition
        p.cooldown = rnd.nextInt(12001);
        p.soulCost = rnd.nextInt(11);
        p.repeatTicks = rnd.nextBoolean() ? 0 : 20 + rnd.nextInt(181);
        p.triggers = distinctTriggers(rnd, vocab, 1 + rnd.nextInt(3));
        p.worlds = distinctWorlds(rnd, rnd.nextInt(4));
        if (rnd.nextBoolean()) {
            p.suppressKey = "fuzz/scope" + rnd.nextInt(1000);
        }
        p.cdEnchant = rnd.nextBoolean() ? "fuzz/scope" + rnd.nextInt(1000) : null;
        p.cdGroup = rnd.nextBoolean() ? "fuzz/scope" + rnd.nextInt(1000) : null;
        p.cdType = rnd.nextBoolean() ? "fuzz/scope" + rnd.nextInt(1000) : null;
        p.condition = buildCondition(rnd, vocab, i);

        int lineCount = 1 + rnd.nextInt(3);
        for (int li = 0; li < lineCount; li++) {
            EffectKind lk = li == 0 ? kind : vocab.effectKinds().get(rnd.nextInt(vocab.effectKinds().size()));
            if (li > 0 && rnd.nextBoolean()) {
                p.lines.add(waitLine(String.valueOf(1 + rnd.nextInt(40)))); // exercises cumulative-WAIT folding
            }
            p.lines.add(new GeneratedLine(lk.head(),
                    validNamed(lk.spec().paramSpec(), rnd, i, vocab), selectorToken(rnd, i, vocab)));
        }
        return p;
    }

    /** For every required param emit a token; each optional param — with or without default — is included with probability ½, so an absent optional round-trips for both shapes. */
    private static Map<String, Authored> validNamed(ParamSpec spec, Random rnd, int i, Vocab vocab) {
        Map<String, Authored> named = new LinkedHashMap<>();
        boolean firstNumericSeen = false;
        boolean unicode = i % 5 == 0;
        for (Param param : spec.params()) {
            ParamType type = param.type();
            boolean include = param.required() || rnd.nextBoolean();
            if (!include) {
                continue;
            }
            boolean numeric = isNumeric(type.kind());
            if (numeric && !firstNumericSeen) {
                firstNumericSeen = true;
                if (i % 4 == 3) {
                    String var = vocab.numVars().get(rnd.nextInt(vocab.numVars().size()));
                    named.put(param.name(), new Authored("%" + var + "% * 2", ValueKind.NUM_EXPR));
                    continue;
                }
            }
            named.put(param.name(), authored(type, rnd, unicode));
        }
        teachKindKey(named, rnd, vocab);
        return named;
    }

    /**
     * The ADR-0053 cross-rule (spec-shape-driven, no head literal): a spec pairing a {@code scope} ENUM that
     * admits KIND with a {@code key} must — when KIND is drawn — key a REGISTERED effect head, because the
     * erase stage drops anything else with {@code E_UNKNOWN_KIND} (which would fail the valid-content case).
     */
    private static void teachKindKey(Map<String, Authored> named, Random rnd, Vocab vocab) {
        Authored scope = named.get("scope");
        if (scope != null && scope.kind() == ValueKind.ENUM && "KIND".equalsIgnoreCase(scope.raw())
                && named.containsKey("key")) {
            String head = vocab.effectKinds().get(rnd.nextInt(vocab.effectKinds().size())).spec().head();
            named.put("key", new Authored(head, ValueKind.STRING));
        }
    }

    /** A valid value token for one param, via an exhaustive switch — a new {@link ParamType.Kind} breaks the build. */
    private static Authored authored(ParamType type, Random rnd, boolean unicode) {
        return switch (type.kind()) {
            case DOUBLE -> new Authored(fmt3(inDoubleRange(type, rnd)), ValueKind.NUM_LITERAL);
            case INT, TICKS -> new Authored(Long.toString(inLongRange(type, rnd)), ValueKind.NUM_LITERAL);
            case BOOL -> new Authored(scramble(BOOL_VOCAB.get(rnd.nextInt(BOOL_VOCAB.size())), rnd), ValueKind.BOOL);
            case ENUM -> new Authored(scramble(type.allowed().get(rnd.nextInt(type.allowed().size())), rnd),
                    ValueKind.ENUM);
            case STRING -> new Authored(randomString(rnd) + (unicode ? "é中" : ""), ValueKind.STRING);
            case HANDLE -> new Authored("FUZZ_TOKEN_" + rnd.nextInt(1_000_000), ValueKind.HANDLE);
        };
    }

    /** A selector-safe value token (alphabet excludes {@code , } =} and spaces), via the same exhaustive switch. */
    private static String selectorValue(ParamType type, Random rnd) {
        return switch (type.kind()) {
            case DOUBLE -> fmt3(inDoubleRange(type, rnd));
            case INT, TICKS -> Long.toString(inLongRange(type, rnd));
            case BOOL -> scramble(BOOL_VOCAB.get(rnd.nextInt(BOOL_VOCAB.size())), rnd);
            case ENUM -> scramble(type.allowed().get(rnd.nextInt(type.allowed().size())), rnd);
            case STRING -> selectorString(rnd);
            case HANDLE -> "FUZZ_TOKEN_" + rnd.nextInt(1_000_000);
        };
    }

    private static String selectorToken(Random rnd, int i, Vocab vocab) {
        if (i % 3 == 0) {
            return null; // exercises every kind's declared default-selector path
        }
        SelectorKind sk = vocab.selectorKinds().get(rnd.nextInt(vocab.selectorKinds().size()));
        return "@" + sk.head() + selectorArgs(sk, rnd);
    }

    private static String buildCondition(Random rnd, Vocab vocab, int i) {
        if (i % 3 == 0) {
            return null; // "always true"
        }
        int atomCount = 1 + rnd.nextInt(3);
        String expr = conditionAtom(rnd, vocab);
        for (int a = 1; a < atomCount; a++) {
            expr += (rnd.nextBoolean() ? " && " : " || ") + conditionAtom(rnd, vocab);
        }
        if (rnd.nextBoolean()) {
            expr = "(" + expr + ")";
        }
        if (i % 4 == 1) {
            expr += rnd.nextBoolean() ? " : +25 %chance%" : " : %stop%";
        }
        return expr;
    }

    private static String conditionAtom(Random rnd, Vocab vocab) {
        return switch (rnd.nextInt(4)) {
            case 3 -> {
                String var = vocab.numVars().get(rnd.nextInt(vocab.numVars().size()));
                String[] calls = {"min(%" + var + "%, 5)", "max(%" + var + "%, 5)",
                        "clamp(%" + var + "%, 0, 10)", "floor(%" + var + "% / 2)", "rand(0, 10)"};
                yield calls[rnd.nextInt(calls.length)] + " > " + rnd.nextInt(20);
            }
            case 0 -> {
                String var = vocab.numVars().get(rnd.nextInt(vocab.numVars().size()));
                String[] ops = {"==", "!=", "<", "<=", ">", ">="};
                yield "%" + var + "% " + ops[rnd.nextInt(ops.length)] + " " + rnd.nextInt(20);
            }
            case 1 -> {
                String var = vocab.flagVars().get(rnd.nextInt(vocab.flagVars().size()));
                yield (rnd.nextBoolean() ? "" : "!") + "%" + var + "%";
            }
            default -> {
                String var = vocab.strVars().get(rnd.nextInt(vocab.strVars().size()));
                yield rnd.nextBoolean() ? "%" + var + "% contains \"abc\"" : "%" + var + "% == \"x\"";
            }
        };
    }

    // ───────────────────────────────────────────────────────── Family B: YAML files ──

    static YamlSample yamlValid(int fileIndex, Vocab vocab) {
        Random rnd = new Random(seedFor("yaml", fileIndex));
        int levels = 1 + rnd.nextInt(3);
        StringBuilder sb = new StringBuilder();
        sb.append("display: ").append(q("Fuzz " + fileIndex)).append('\n');
        sb.append("description: ").append(q("generated fuzz enchant")).append('\n');
        // tier: omitted — TierRegistry.BUILTIN's accepted names are not this gate's contract (trap 14).
        int triggerCount = 1 + rnd.nextInt(2);
        if (triggerCount == 1) {
            sb.append("trigger: ").append(q(pickTrigger(rnd, vocab))).append('\n');
        } else {
            sb.append("trigger: [").append(q(pickTrigger(rnd, vocab))).append(", ")
                    .append(q(pickTrigger(rnd, vocab))).append("]\n");
        }
        if (rnd.nextBoolean()) {
            sb.append("group: ").append(q("fuzzgroup")).append('\n');
        }
        sb.append("levels:\n");
        for (int lv = 1; lv <= levels; lv++) {
            sb.append("  ").append(lv).append(":\n");
            sb.append("    chance: ").append(q(fmt3(rnd.nextDouble() * 100.0))).append('\n');
            sb.append("    cooldown: ").append(q(String.valueOf(rnd.nextInt(2000)))).append('\n');
            if (rnd.nextBoolean()) {
                sb.append("    condition: ").append(q(yamlCondition(rnd, vocab))).append('\n');
            }
            sb.append("    effects:\n");
            int effectCount = 1 + rnd.nextInt(3);
            for (int e = 0; e < effectCount; e++) {
                sb.append(yamlEffectItem(vocab.effectKinds().get(rnd.nextInt(vocab.effectKinds().size())), rnd));
            }
        }
        return new YamlSample("fuzz-" + fileIndex + ".yml", sb.toString(), levels);
    }

    private static String yamlEffectItem(EffectKind kind, Random rnd) {
        Map<String, String> values = new LinkedHashMap<>();
        boolean unicode = rnd.nextInt(5) == 0;
        for (Param param : kind.spec().paramSpec().params()) {
            if (!(param.required() || rnd.nextBoolean())) {
                continue;
            }
            values.put(param.name(), authored(param.type(), rnd, unicode).raw());
        }
        // The ADR-0053 cross-rule, as in teachKindKey: a drawn scope KIND must key a registered effect
        // head or the loader's valid family trips the erase's E_UNKNOWN_KIND. The host's own head suffices.
        if ("KIND".equalsIgnoreCase(values.getOrDefault("scope", "")) && values.containsKey("key")) {
            values.put("key", kind.head());
        }
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (body.length() > 0) {
                body.append(", ");
            }
            body.append(entry.getKey()).append(": ").append(q(entry.getValue()));
        }
        if (rnd.nextBoolean()) {
            if (body.length() > 0) {
                body.append(", ");
            }
            body.append("who: ").append(q("@Self"));
        }
        if (rnd.nextBoolean()) {
            if (body.length() > 0) {
                body.append(", ");
            }
            body.append("wait: ").append(q(String.valueOf(1 + rnd.nextInt(20))));
        }
        return "      - { " + kind.head() + ": { " + body + " } }\n";
    }

    private static String yamlCondition(Random rnd, Vocab vocab) {
        String expr = conditionAtom(rnd, vocab);
        if (rnd.nextBoolean()) {
            expr += (rnd.nextBoolean() ? " && " : " || ") + conditionAtom(rnd, vocab);
        }
        return expr;
    }

    /** The FIXED pathological YAML corpus (name &rarr; raw text), in a deterministic order. */
    static List<Map.Entry<String, String>> yamlAdversarial() {
        List<Map.Entry<String, String>> out = new ArrayList<>();
        StringBuilder deep = new StringBuilder();
        for (int d = 0; d < 300; d++) {
            deep.append("  ".repeat(d)).append("a:\n");
        }
        deep.append("  ".repeat(300)).append("leaf: \"x\"\n");
        out.add(Map.entry("deep-nesting.yml", deep.toString()));

        StringBuilder aliases = new StringBuilder("base: &a [\"x\", \"x\"]\nlist:\n");
        for (int a = 0; a < 100; a++) {
            aliases.append("  - *a\n");
        }
        out.add(Map.entry("alias-bomb.yml", aliases.toString()));

        out.add(Map.entry("dup-keys.yml", """
                display: "first"
                display: "second"
                trigger: "ATTACK"
                levels:
                  1:
                    chance: "50"
                    effects:
                      - { CANCEL: {} }
                  1:
                    chance: "60"
                    effects:
                      - { CANCEL: {} }
                """));
        out.add(Map.entry("terse.yml", """
                display: "terse"
                trigger: "ATTACK"
                levels:
                  1:
                    chance: "50"
                    effects:
                      - "DAMAGE:5"
                """));
        out.add(Map.entry("scalar-root.yml", "just a string\n"));
        out.add(Map.entry("bad-level.yml", """
                display: "badlevel"
                trigger: "ATTACK"
                levels:
                  zero:
                    chance: "50"
                    effects:
                      - { CANCEL: {} }
                  -1:
                    chance: "50"
                    effects:
                      - { CANCEL: {} }
                """));
        out.add(Map.entry("no-trigger.yml", """
                display: "notrigger"
                levels:
                  1:
                    chance: "50"
                    effects:
                      - { CANCEL: {} }
                """));
        out.add(Map.entry("bad-chance.yml", """
                display: "badchance"
                trigger: "ATTACK"
                chance: "250"
                levels:
                  1:
                    chance: "NaN"
                    effects:
                      - { CANCEL: {} }
                  2:
                    effects:
                      - { CANCEL: {} }
                """));
        out.add(Map.entry("unicode-keys.yml", """
                display: "unicode"
                trigger: "ATTACK"
                "§💀": "x"
                levels:
                  1:
                    chance: "50"
                    effects:
                      - { CANCEL: {} }
                """));
        out.add(Map.entry("empty.yml", ""));
        out.add(Map.entry("empty-lists.yml", """
                display: "emptylists"
                trigger: []
                levels:
                  1:
                    chance: "50"
                    effects: []
                """));
        out.add(Map.entry("multi-ability.yml", """
                display: "multiability"
                trigger: "ATTACK"
                levels:
                  1:
                    abilities:
                      - { chance: 50, effects: [{ CANCEL: {} }] }
                      - { trigger: "DEFENSE", cooldown: 20, effects: [{ CANCEL: {} }] }
                """));
        out.add(Map.entry("multi-ability-ambiguous.yml", """
                display: "ambiguous"
                trigger: "ATTACK"
                levels:
                  1:
                    effects: [{ CANCEL: {} }]
                    abilities:
                      - { effects: [{ CANCEL: {} }] }
                """));
        out.add(Map.entry("multi-ability-empty.yml", """
                display: "emptyabilities"
                trigger: "ATTACK"
                levels:
                  1: { abilities: [] }
                """));
        out.add(Map.entry("multi-ability-scalar-entry.yml", """
                display: "scalarentry"
                trigger: "ATTACK"
                levels:
                  1:
                    abilities:
                      - "not a mapping"
                      - 42
                """));
        out.add(Map.entry("fn-arity.yml", """
                display: "fnarity"
                trigger: "ATTACK"
                levels:
                  1:
                    condition: "min(1) > 0"
                    effects:
                      - { CANCEL: {} }
                """));
        out.add(Map.entry("fn-unknown.yml", """
                display: "fnunknown"
                trigger: "ATTACK"
                levels:
                  1:
                    condition: "unknownfn(1) > 0"
                    effects:
                      - { CANCEL: {} }
                """));
        out.add(Map.entry("fn-unterminated.yml", """
                display: "fnunterminated"
                trigger: "ATTACK"
                levels:
                  1:
                    condition: "min(1, 2 > 0"
                    effects:
                      - { CANCEL: {} }
                """));
        out.add(Map.entry("fn-valid.yml", """
                display: "fnvalid"
                trigger: "ATTACK"
                levels:
                  1:
                    condition: "clamp(%damage% * 2, 0, 10) > min(1, 2)"
                    effects:
                      - { DAMAGE: { amount: "max(%damage%, 3)" } }
                """));
        out.add(Map.entry("tabs-and-garbage.yml", "\tdisplay: \"x\"\n{{{[[\n"));
        out.add(Map.entry("wrong-shapes.yml", """
                display: "wrongshapes"
                trigger: "ATTACK"
                levels: 3
                """));
        return out;
    }

    // ─────────────────────────────────────────────────────────────────── shared ──

    /** Failure rendering: stable key, triggers, condition, worlds, and each line's head + named map + selector. */
    static String describe(AbilityDef def) {
        StringBuilder sb = new StringBuilder();
        sb.append("stableKey=").append(def.stableKey()).append('\n');
        sb.append("triggers=").append(def.triggers()).append('\n');
        sb.append("condition=").append(def.conditionExpr()).append('\n');
        sb.append("worlds=").append(def.worldBlacklist()).append('\n');
        for (EffectLine line : def.effects()) {
            sb.append("  ").append(line.head());
            sb.append(line.isVerbose() ? " " + line.named() : " args=" + line.argTexts());
            line.selectorToken().ifPresent(s -> sb.append(" @=").append(s));
            sb.append('\n');
        }
        return sb.toString();
    }

    private static AbilityDef assemble(Plan p) {
        List<EffectLine> effects = new ArrayList<>();
        for (GeneratedLine line : p.lines) {
            if (line.head().equals("WAIT")) {
                effects.add(EffectLine.waitLine(line.named().get("ticks").raw(), p.source));
                continue;
            }
            Map<String, String> named = new LinkedHashMap<>();
            line.named().forEach((k, v) -> named.put(k, v.raw()));
            effects.add(EffectLine.verbose(line.head(), 1, named, line.selectorToken(), p.source));
        }
        return Defs.ability()
                .sourceKind(p.sourceKind).stableKey(p.stableKey).defId(p.defId).level(p.level)
                .chance(p.chance).cooldown(p.cooldown).soulCost(p.soulCost).repeatTicks(p.repeatTicks)
                .setPieces(p.setPieces).triggers(p.triggers).worldBlacklist(p.worlds)
                .condition(p.condition).suppressKey(p.suppressKey)
                .cooldownScope(p.cdEnchant, p.cdGroup, p.cdType)
                .effects(effects).source(p.source).build();
    }

    private static GeneratedLine waitLine(String ticks) {
        return new GeneratedLine("WAIT", Map.of("ticks", new Authored(ticks, ValueKind.NUM_LITERAL)), null);
    }

    private static ArrayList<String> distinctTriggers(Random rnd, Vocab vocab, int count) {
        List<String> names = vocab.triggerNames();
        LinkedHashSet<Integer> idx = new LinkedHashSet<>();
        int guard = 0;
        while (idx.size() < Math.min(count, names.size()) && guard++ < 64) {
            idx.add(rnd.nextInt(names.size()));
        }
        ArrayList<String> out = new ArrayList<>();
        for (int k : idx) {
            String t = names.get(k);
            out.add(rnd.nextBoolean() ? t.toLowerCase(Locale.ROOT) : t); // erase upper-cases in canonical mode
        }
        return out;
    }

    private static ArrayList<String> distinctWorlds(Random rnd, int count) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        int guard = 0;
        while (out.size() < Math.min(count, WORLDS.size()) && guard++ < 32) {
            out.add(WORLDS.get(rnd.nextInt(WORLDS.size())));
        }
        return new ArrayList<>(out);
    }

    private static String pickTrigger(Random rnd, Vocab vocab) {
        return vocab.triggerNames().get(rnd.nextInt(vocab.triggerNames().size()));
    }

    private static Param firstOf(List<Param> params, ParamType.Kind... kinds) {
        Set<ParamType.Kind> wanted = Set.of(kinds);
        for (Param p : params) {
            if (wanted.contains(p.type().kind())) {
                return p;
            }
        }
        return null;
    }

    private static Param firstBounded(List<Param> params) {
        for (Param p : params) {
            if (isNumeric(p.type().kind()) && (p.type().min().isPresent() || p.type().max().isPresent())) {
                return p;
            }
        }
        return null;
    }

    private static boolean isNumeric(ParamType.Kind kind) {
        return kind == ParamType.Kind.DOUBLE || kind == ParamType.Kind.INT || kind == ParamType.Kind.TICKS;
    }

    private static double inDoubleRange(ParamType type, Random rnd) {
        double lo = type.min().orElse(-1000);
        double hi = type.max().orElse(lo + 1000);
        double v = lo + rnd.nextDouble() * (hi - lo);
        return Math.max(lo, Math.min(hi, v));
    }

    private static long inLongRange(ParamType type, Random rnd) {
        long lo = (long) Math.ceil(type.min().orElse(type.kind() == ParamType.Kind.TICKS ? 0 : -1000));
        long hi = (long) Math.floor(type.max().orElse(lo + 1000));
        if (hi < lo) {
            hi = lo;
        }
        long v = lo + (long) (rnd.nextDouble() * (hi - lo + 1));
        return Math.min(hi, v);
    }

    /** Three-decimal plain-string rendering (no scientific notation), so a literal always re-parses cleanly. */
    private static String fmt3(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).toPlainString();
    }

    private static String scramble(String s, Random rnd) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(rnd.nextBoolean() ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static String randomString(Random rnd) {
        int len = 1 + rnd.nextInt(12);
        StringBuilder sb = new StringBuilder(len);
        for (int k = 0; k < len; k++) {
            sb.append(STRING_ALPHA.charAt(rnd.nextInt(STRING_ALPHA.length())));
        }
        return sb.toString();
    }

    private static String selectorString(Random rnd) {
        int len = 1 + rnd.nextInt(8);
        StringBuilder sb = new StringBuilder(len);
        for (int k = 0; k < len; k++) {
            sb.append(SELECTOR_ALPHA.charAt(rnd.nextInt(SELECTOR_ALPHA.length())));
        }
        return sb.toString();
    }

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Mutable working shape assembled into an {@link AbilityDef}; a mutation edits it in place. */
    private static final class Plan {
        SourceKind sourceKind = SourceKind.ENCHANT;
        String stableKey = "fuzz/x";
        int defId;
        int level;
        double chance;
        int cooldown;
        int soulCost;
        int repeatTicks;
        int setPieces;
        List<String> triggers = new ArrayList<>();
        List<String> worlds = new ArrayList<>();
        String condition;
        String suppressKey;
        String cdEnchant;
        String cdGroup;
        String cdType;
        List<GeneratedLine> lines = new ArrayList<>();
        Source source = Source.UNKNOWN;
        String label = "";
    }
}

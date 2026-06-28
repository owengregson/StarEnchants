package schema.diag;

/**
 * The closed set of stable diagnostic codes (docs/architecture.md §10).
 *
 * <p>One constant per code; its {@link #name()} <em>is</em> the wire string that
 * {@link Diagnostic#code()} carries. So a producer writes {@code diags.error(DiagCode.E_RANGE, …)}
 * and a test asserts {@code d.is(DiagCode.E_RANGE)} — the code lives in ONE place instead of being
 * re-typed as a bare literal at every producer site and again in every test (the single most
 * pervasive coupling the test-suite audit found). {@code code()} is asserted; message wording is
 * never the contract.
 *
 * <p>Codes are grouped by the stage/loader that emits them. Two duplicate-key codes are
 * intentionally distinct: {@link #E_DUP_KEY} is one compilation claiming a content key twice
 * (erase stage), {@link #E_DUPLICATE_KEY} is two files resolving to the same key (library loader).
 */
public enum DiagCode {

    // Grammar — the expression/condition parser and the selector AST. The condition parser/lexer split
    // E_PARSE into specific faults so error tests assert a CODE, not an English message substring. Every
    // sub-code starts with "E_PARSE", so the parse family is recognisable by prefix.
    E_PARSE,
    E_PARSE_TRAILING,        // a complete expression followed by leftover tokens
    E_PARSE_CHAINED_CMP,     // a < b < c — comparators / string-ops are non-associative
    E_PARSE_UNCLOSED_GROUP,  // '(' with no matching ')'
    E_PARSE_EXPECTED_VALUE,  // a value was required (an empty group, a leading operator, …)
    E_PARSE_CLAUSE,          // a malformed ':' outcome clause (bad sentinel / ±N %chance% / a second clause)
    E_PARSE_BAD_CHAR,        // lexer: a character outside the condition alphabet
    E_PARSE_HALF_OP,         // lexer: a single '&' / '|' / '=' where '&&' / '||' / '==' was meant
    E_PARSE_UNTERMINATED,    // lexer: an unterminated %variable% or "string"
    E_PARSE_EMPTY_VAR,       // lexer: '%%' with no variable name
    E_SELECTOR_SYNTAX,
    E_TERSE_EFFECT,

    // ParamSpec / ParamType — typed argument validation.
    E_TYPE,
    E_ENUM,
    E_RANGE,
    E_MISSING_ARG,
    E_UNKNOWN_EFFECT_PARAM,
    W_EXTRA_ARGS,

    // Condition compiler.
    E_COND_TYPE,

    // Effect / selector / handle / trigger resolution and lowering.
    E_EFFECT,
    E_UNKNOWN_KIND,
    E_UNKNOWN_SELECTOR,
    E_UNKNOWN_HANDLE,
    E_UNKNOWN_TRIGGER,
    E_REL_UNKNOWN,
    E_WAIT_ARG,

    // Erase/lower stage — bit-mask overflow guards and per-compilation duplicate key.
    E_TRIGGER_OVERFLOW,
    E_WORLD_OVERFLOW,
    E_DUP_KEY,

    // Library / content loading.
    E_DUPLICATE_KEY,
    E_CONFIG_IO,
    E_CONFIG_SHAPE,
    W_CONFIG_NUM,
    W_UNKNOWN_KEY,
    W_TIER_FOLDER_MISMATCH,

    // Set custom-enchant refs (§6.6) — a set piece's enchants: block.
    E_SET_ENCHANT_UNKNOWN, // names a custom enchant (enchants/<id>) the library doesn't define
    E_SET_ENCHANT_LEVEL,   // references an existing custom enchant at an out-of-range level
    W_SET_ENCHANT,         // a non-numeric enchant level — warned and skipped

    // Items loader (per-item config + soul-gem maps).
    E_ITEM_IO,
    E_ITEM_SHAPE,
    E_ITEM_TYPE,
    W_ITEM_DUP,
    W_ITEM_NUM,
    W_ITEM_TYPE,
    W_SOUL_MOB,
    W_SOUL_TIER,

    // Lang loader.
    E_LANG_IO,
    E_LANG_SHAPE,

    // Menus loader.
    E_MENU_IO,
    E_MENU_SHAPE,
    W_MENU_DUP,
    W_MENU_NUM,

    // Selector argument hygiene.
    W_SELECTOR_DUP_ARG,
    W_SELECTOR_UNKNOWN_ARG;

    /** The wire string carried by {@link Diagnostic#code()} — identical to {@link #name()}. */
    public String code() {
        return name();
    }
}

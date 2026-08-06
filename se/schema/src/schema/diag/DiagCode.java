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
 *
 * <p>These names also supersede the last dotted string codes ({@code reload.busy}, {@code reload.failed},
 * {@code migrate.*}): the String overloads on {@link Diagnostics}/{@link Diagnostic} are gone, so the set is
 * compiler-enforced — an off-catalogue code cannot be produced (ADR-0042).
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
    E_PARSE_FN_ARITY,        // a known function called with the wrong number of arguments
    E_PARSE_UNKNOWN_FN,      // an identifier called like a function but naming none
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
    E_VAR_SCOPE,           // a %scope.name% read from a place that scope cannot answer, or a fact that scope has no
                           // entry for — today the %target.*% subject scope (ADR-0076), which is effect-side only

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
    E_PACK_ERA,            // a config pack declares a server floor this server is below (R-QC11)
    W_CONFIG_NUM,
    W_CONFIG_BOOL,         // a config.yml boolean outside the canonical vocabulary — warned, fallback kept
    W_UNKNOWN_KEY,
    W_TIER_FOLDER_MISMATCH,

    // Set custom-enchant refs (§6.6) — a set piece's enchants: block.
    E_SET_ENCHANT_UNKNOWN, // names a custom enchant (enchants/<id>) the library doesn't define
    E_SET_ENCHANT_LEVEL,   // references an existing custom enchant at an out-of-range level
    W_SET_ENCHANT,         // a non-numeric enchant level — warned and skipped

    // Content-file loaders (ADR-0014) — one diagnostic per malformed content kind. The wire string is the
    // constant name; these names supersede the older load.* dotted codes, which carried no severity prefix.
    E_LOAD_YAML,           // a content file is not parseable YAML
    E_LOAD_IO,             // a content file could not be read
    E_LOAD_KEY,            // a content file has no usable name stem
    E_LOAD_CHANCE,         // a chance: value is outside [0,100] or non-numeric
    E_LOAD_INT,            // a value that must be an integer is not
    E_LOAD_DOUBLE,         // a value that must be a number is not
    W_LOAD_BOOL,           // a value that must be a boolean is outside the true/yes/on/1 | false/no/off/0 vocabulary — warned, fallback kept
    E_LOAD_ENCHANT,        // an enchant file is not a YAML mapping
    E_LOAD_ENCHANT_TRIGGER,// an enchant declares no trigger
    E_LOAD_ENCHANT_LEVELS, // an enchant declares no levels: map
    E_LOAD_ENCHANT_LEVEL,  // a single level entry of an enchant is malformed
    W_LOAD_ENCHANT_RELATIONSHIPS, // a requires/blacklist relationship is suspect (non-blocking)
    E_LOAD_CRYSTAL,        // a crystal file is not a YAML mapping
    E_LOAD_CRYSTAL_TRIGGER,// a crystal declares no trigger
    E_LOAD_USE_ITEM,       // a use-item file is not a YAML mapping, is missing its name/material, or has an empty abilities list
    W_LOAD_USE_TRIGGER,    // a use-item declares an explicit trigger — the USE trigger is implicit, so it is forced and the authored value ignored
    E_LOAD_PET,            // a pet file is not a YAML mapping, is missing its display/type, or has an empty/malformed levels map
    W_LOAD_PET_TRIGGER,    // a pet ability's trigger is inconsistent with its type (ACTIVE default USE; a PASSIVE pet may not author USE) — forced/ignored
    E_LOAD_MASK,           // a mask file is not a YAML mapping, is missing its display, or declares no ability (crystal dual form; no trigger)
    E_LOAD_REFORGE,        // a reforge file is not a YAML mapping, is missing its display, or declares no ability
    W_LOAD_REFORGE_TRIGGER,// the reforge ACTIVE (a0) declares a trigger — USE is implicit on it, forced; support abilities (a1+) may author PASSIVE etc.
    E_LOAD_SET,            // a set file is not a YAML mapping
    E_LOAD_SET_ARMOR,      // a set is missing its armor: block or declares no pieces
    E_LOAD_SET_MEMBER,     // an armour piece declares no material
    E_LOAD_SET_COMPLETE,   // a set's completion count is not positive
    E_LOAD_SET_WEAPON,     // a set weapon declares no material
    E_LOAD_SET_TRIGGER,    // a set bonus declares no trigger
    W_LOAD_SET_WEAPON_UNREACHABLE, // an on:weapon bonus with no weapon: item to hold — it can never fire (split from W_LOAD_EFFECTS)
    W_LOAD_EFFECTS,        // an ability declares no effects — warned, kept
    E_LOAD_REBATE,         // a chance-rebate envelope declares both terms, feedback with no term, or an unknown recipient (ADR-0076)

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

    // Transactional reload (platform.content.ReloadResult) — result-level faults; Source is UNKNOWN.
    E_RELOAD_BUSY,         // single-flight rejection: another reload is already in progress
    E_RELOAD_FAILED,       // the off-thread build threw; the Throwable rides ReloadResult.failure()

    // Migrator (se/migrate) — import-review findings; the migrator never blocks, it flags gaps.
    W_MIGRATE_ID,          // an unsafe legacy id was skipped (path-escape guard)
    W_MIGRATE_TRIGGER,     // a legacy type has no StarEnchants trigger — set one manually
    W_MIGRATE_APPLIES,     // a legacy applies list was not recognised — set applies-to manually
    W_MIGRATE_EFFECT,      // a legacy effect token was not translated — see the inline note

    // Selector argument hygiene.
    W_SELECTOR_DUP_ARG,
    W_SELECTOR_UNKNOWN_ARG;

    /** The wire string carried by {@link Diagnostic#code()} — identical to {@link #name()}. */
    public String code() {
        return name();
    }
}

package compile.cond;

/**
 * The value type of a condition variable, deciding which {@code FactBuffer} slot space
 * it binds to and which comparisons it admits (docs/architecture.md §3.4).
 */
public enum VarKind {
    /** A numeric fact (e.g. {@code %victim.health%}); admits all six comparators. */
    NUM,
    /** A boolean flag (e.g. {@code %sneaking%}); usable directly or with {@code ==}/{@code !=}. */
    BOOL,
    /** A string fact; admits only {@code ==}/{@code !=}. */
    STR
}

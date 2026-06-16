/**
 * The condition-expression sublanguage: tokenizer, recursive-descent parser, and
 * the untyped AST it produces (docs/architecture.md §2, §3.2, §3.4).
 *
 * <p>StarEnchants conditions are written in a tiny boolean/relational expression
 * language — {@code && || ! ( )}, the six comparators, {@code %scope.name%}
 * variables (PlaceholderAPI passthrough included), and number/boolean/string
 * literals. This package turns that text into <em>shape</em> only:
 * <ul>
 *   <li>{@link com.starenchants.schema.grammar.expr.ExprLexer} tokenizes the text
 *       into {@link com.starenchants.schema.grammar.expr.ExprTok}s with 1-based
 *       columns;
 *   <li>{@link com.starenchants.schema.grammar.expr.ExprParser} parses those tokens
 *       with correct precedence
 *       ({@code ||} &lt; {@code &&} &lt; comparators &lt; unary {@code !} &lt;
 *       primary) into an immutable
 *       {@link com.starenchants.schema.grammar.expr.Expr} tree of flyweight nodes
 *       ({@code Or}/{@code And}/{@code Not}/{@code Compare}/{@code VarRef}/literals).
 * </ul>
 *
 * <p>The AST is <strong>untyped, data only</strong>. No evaluation, no type
 * checks, no name resolution, and no rejection of unknown or PlaceholderAPI
 * variables happen here — every {@code %…%} token becomes a
 * {@link com.starenchants.schema.grammar.expr.Expr.VarRef} to be resolved later.
 * Typing against the variable vocabulary and lowering to the runtime's pre-built
 * condition AST are <em>se-compile's</em> job (docs/architecture.md §2,
 * "produces untyped AST (data only)"; §3.4).
 *
 * <p>Like the rest of se-schema, parsing never throws: malformed input becomes a
 * precise {@code E_PARSE}
 * {@link com.starenchants.schema.diag.Diagnostic} and the parser recovers with a
 * best-effort node, so one fault yields one finding rather than a crash.
 */
package com.starenchants.schema.grammar.expr;

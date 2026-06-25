/**
 * The condition-expression sublanguage: tokenizer, recursive-descent parser, and the
 * untyped data-only AST it produces (docs/architecture.md §2, §3.2, §3.4). Typing and
 * lowering are se-compile's job. Parsing never throws — malformed input becomes an
 * {@code E_PARSE} diagnostic and the parser recovers with a best-effort node.
 */
package schema.grammar.expr;

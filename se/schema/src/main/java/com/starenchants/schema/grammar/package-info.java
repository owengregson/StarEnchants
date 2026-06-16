/**
 * Grammar: tokenizing authored content into untyped structure.
 *
 * <p>{@link com.starenchants.schema.grammar.Lexer} splits a line on a delimiter
 * at the top level only — respecting nested {@code () [] {} <>} and quotes — so
 * colons inside inline tags, selector bodies, and quoted strings survive. An
 * {@link com.starenchants.schema.grammar.EffectLine} is the resulting head +
 * column-tagged argument {@link com.starenchants.schema.grammar.Tok}s. No
 * validation happens here; that is the compiler's job against a ParamSpec
 * (docs/architecture.md §2).
 */
package com.starenchants.schema.grammar;

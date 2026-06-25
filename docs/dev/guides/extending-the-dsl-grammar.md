# Extending the DSL grammar

Effects, conditions, selectors, and triggers all add to *vocabularies* — they
don't touch the language. This guide is the rarer, deeper change: adding to the
shared **expression grammar** that conditions and expression-valued arguments are
written in — a new operator, a new token, a new arithmetic form. It is rarer
because the language is small and stable, so do this only when no combination of
existing operators expresses what you need.

The grammar lives in `se/schema/src/schema/grammar/expr/`. It is **pure** — zero
Bukkit, zero engine — so the whole sublanguage is unit-testable in isolation.

## What the grammar already gives you

Before adding anything, confirm the existing operators don't already cover it.
The expression sublanguage supports, in precedence order (lowest to highest):

```text
||                            left-assoc, lowest
&&                            left-assoc
== != < <= > >=              relational, NON-associative
contains  matchesregex        string operators
+  -                          arithmetic, left-assoc
*  /                          arithmetic, left-assoc
!  -                          unary prefix
primary:  ( expr ) | number | "string" | true/false | %variable%
```

So `%victim.health% < %actor.maxhealth% / 2 && !%victim.blocking%` already
parses, arithmetic over `%vars%` and all — no new grammar needed. The full list
is enumerated in `Cmp.java` (relational), `StrOp.java` (string), and
`ArithOp.java` (arithmetic).

## The pipeline a new token threads through

Adding to the grammar means threading one concept through three layers without
ever parsing on the hot path:

| Stage | Module / file | What you touch |
| --- | --- | --- |
| 1. tokenize | `se/schema/.../expr/ExprTok.java`, `ExprLexer.java` | a new token `Kind` + the lexer rule that emits it |
| 2. parse | `se/schema/.../expr/Expr.java`, `ExprParser.java` | an untyped AST node + the parse rule at the right precedence |
| 3. lower (compile, slot) | `se/compile/.../cond/ConditionCompiler.java`, `compile/model/cond/` | a typed, slot-resolved IR node |
| 4. evaluate (runtime) | `se/engine/.../condition/ConditionEvaluator.java` or `NumExprEval.java` | reading that IR node over the `FactBuffer` |

The split is the whole point: **parsing happens once, at compile**; the runtime
walks a pre-built typed tree and never re-lexes a string. A `%variable%` resolves
to a dense `FactBuffer` slot at stage 3, so at stage 4 it is an array index, not a
lookup.

## Worked example — adding a string operator

`contains` is a real string operator; trace it through all four stages as the
template for adding (say) a `startswith`.

**Stage 1 — token.** `ExprTok.Kind` gains the kind, and the lexer recognises the
reserved word. `contains`/`matchesregex` are tokenized as operators, not
identifiers, in `ExprLexer.ident`:

```java
private ExprTok ident(int startCol) {
    ...
    String text = src.substring(start, pos);
    // contains/matchesregex are case-insensitive reserved words, tokenized as operators not identifiers.
    if (text.equalsIgnoreCase("contains")) {
        return new ExprTok(ExprTok.Kind.CONTAINS, text, startCol);
    }
    if (text.equalsIgnoreCase("matchesregex")) {
        return new ExprTok(ExprTok.Kind.MATCHES_REGEX, text, startCol);
    }
    return new ExprTok(ExprTok.Kind.IDENT, text, startCol);
}
```

The lexer **never throws**: a lexical fault becomes an `E_PARSE` diagnostic and
recovers best-effort, so the parser always gets a usable, EOF-terminated stream.
A symbolic operator (like a new `=~`) goes in the `switch (c)` over single chars
instead, following `twoCharOp` / `relational` for multi-char forms.

**Stage 2 — AST + precedence.** `StrOp` enumerates the string operators as pure
syntax, and `ExprParser` parses the operator at its precedence level into an
`Expr.StringMatch` node. Place a new operator at the correct rung of the
recursive-descent ladder (`parseOr → parseAnd → parseComparison → parseAdditive →
…`); putting it at the wrong rung changes how it binds. The parser, like the
lexer, never throws — faults become `E_PARSE` diagnostics with recovery nodes,
carrying a 1-based column for the diagnostic.

**Stage 3 — lower to slot-resolved IR.** `ConditionCompiler` turns the untyped
`Expr` into the typed `Cond` IR (`compile/model/cond/`): variables resolve to
dense slots via the injected `VarResolver`, literals are pre-parsed, and operands
are type-checked. The type rules are enforced here, as diagnostics, never
exceptions:

- numeric operands admit all six comparators;
- string operands admit only `==`/`!=` (and the string operators);
- a PlaceholderAPI token coerces to the other operand's type;
- the whole condition must be boolean — a bare number is an error ("compare it").

A type error records a `file:line:col` diagnostic and returns empty, so the
ability lowers with **no** condition rather than aborting the load. A new operator
needs its own type rule and its own `Cond`/`NumExpr`/`StrExpr` IR node.

**Stage 4 — evaluate (no hot parse).** The runtime walks the compiled IR. String
and boolean nodes are evaluated in `ConditionEvaluator`; numeric nodes (and
expression-valued effect arguments) in `NumExprEval`. This is the real `contains`
evaluation — note it reads slots, not strings-from-source:

```java
if (node instanceof Cond.StrContains c) {
    return containsAny(str(c.left(), f), str(c.right(), f));   // f is the FactBuffer
}
```

`NumExprEval` shows the same for arithmetic, plus the fail-safe rules that keep a
bad value from poisoning combat — an unresolved placeholder reads `NaN` (so
numeric comparisons fail closed) and division by zero yields `0` rather than
`NaN` or an exception:

```java
return switch (b.op()) {
    case ADD -> l + r;
    case SUBTRACT -> l - r;
    case MULTIPLY -> l * r;
    case DIVIDE -> r == 0.0 ? 0.0 : l / r;
};
```

A new operator implements its evaluation here, reading only the `FactBuffer`.

## The flow / chance clause tail

A condition may end in a `<test> : <outcome>` clause, parsed by
`ExprParser.parseClauseTail`. The outcomes — `%continue%`, `%stop%`, `%force%`,
`%allow%`, and `±N %chance%` — are the `FlowKind` enum
(`schema/grammar/expr/FlowKind.java`), mapped to the engine's `Flow` at
evaluation (schema sits below engine in the module graph, so it cannot name the
engine enum). Extending the clause vocabulary is the same four-stage thread,
scoped to the clause-tail parse and the `Flow` mapping.

## The drift guard

The committed DSL reference (`docs/reference/dsl-reference.md`) is generated from
these grammar enums by `ReferenceDoc` — its Conditions section iterates
`Cmp.values()`, `StrOp.values()`, and `FlowKind.values()`. So a new operator
appears in the reference automatically once it is in the enum, and the drift test
(`ReferenceDocDriftTest`) **fails the build** if you add an operator without
regenerating:

```bash
./gradlew regenDocs
```

This is the guard that keeps the language and its documentation from drifting —
you cannot ship a grammar change with stale docs.

## Verify

```bash
./gradlew build
```

The grammar is pure (no Bukkit, no server), so the unit gate is the whole story
for parse/lower/evaluate: `build` runs the lexer/parser/compiler/evaluator tests
**and** the drift test that forces the doc regen above. Run the live Paper +
Folia matrix only if your grammar change reaches into a fact that reads
world/entity state across regions — the language itself never touches a thread,
so a pure operator addition does not need the matrix.

See also:

- [Effect engine internals](../internals/effect-engine.md) — the `FactBuffer`
  and how the compiled condition gates an activation.
- [Compiler and config internals](../internals/compiler-and-config.md) — the
  resolve → typecheck → lower → erase phases the grammar feeds.
- [Decision records](../../decisions/) — ADR-0004 (the modern DSL), ADR-0011
  (engine architecture).

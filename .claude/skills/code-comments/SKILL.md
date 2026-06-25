---
name: code-comments
description: Use when writing or reviewing comments or Javadoc in any source file — deciding what to comment, trimming verbose/redundant comments, or removing development-history narration. Applies to all code in the repo.
---

# Code comments

Comment the **why**, never the **what**. The code already says what it does; a
comment earns its place only by saying something the code cannot — a reason, a
constraint, a consequence that isn't visible at the call site. Default to fewer,
shorter comments. Stupid-simple, but exact.

## The one rule

- Restates the code? **Delete it.**
- Explains a decision, trade-off, footgun, invariant, or non-obvious *why*?
  **Keep it — as one tight line.**

> Violating the spirit (a comment that adds no information a reader lacks) is as
> bad as a literal "what" comment. When unsure, cut — but never cut something a
> reader would need minutes to re-derive.

## Keep (high-value "why")

- The reason behind a non-obvious choice — *"interned so gate 5 is an int compare"*.
- Contracts & invariants callers rely on — public-API Javadoc; *"must run before X"*.
- Footguns: ordering, concurrency, cross-version and Folia traps.
- Units, ranges, edge cases the type doesn't show — *"ticks, not ms"*, *"may be null on Folia"*.
- A pointer to the authority — `ADR-00NN`, `docs/architecture.md §N`. Keep these, terse.

## Cut (noise)

- Restating the line under it — `// increment i`, `// the display-name lookup`.
- **Development history** — *"originally we…, then switched to…"*, *"used to be…"*,
  *"this folded out as…"*. Git holds that, not the source.
- Play-by-play narration of obvious control flow.
- Javadoc that only echoes the method name and parameters.
- Decorative banners, dividers, section ASCII, and filler preambles (*"Note that…"*).
- Commented-out code and stale TODOs.

## Style

- One line where possible; lead with the point.
- Senior-dev tone: precise, plain, confident. No hedging, no storytelling.
- Comment the surprising, not the routine.
- A clause beats a sentence; a sentence beats a paragraph.

## Before / after

```java
// BEFORE — narrates the what, tells the story, three lines for one idea
// The display-name lookup is injected as a base key -> display function, so the
// line-building (lines) is pure and unit-testable with no server or Library; the
// wiring passes Library::displayNameOf (which covers enchants AND crystals).
// AFTER — the why, in one line
// Display lookup injected (not Library) so lines() stays pure & server-free.
```

```java
// BEFORE — dev history + restated code
// We used to clone-and-Gson-parse the item like the old design; we now hash the
// PDC bytes instead. Compute the content hash of the stack.
int hash = contentHash(stack);
// AFTER — delete the history; keep only the non-obvious why (if any)
int hash = contentHash(stack); // cache key: PDC content, not ItemMeta identity (copy-on-write)
```

```java
// BEFORE                                  // AFTER (deleted — the name says it)
/** Returns the player's UUID. */          (nothing)
public UUID uuid() { return uuid; }        public UUID uuid() { return uuid; }
```

## Red flags — stop and cut

- The comment paraphrases the line beneath it.
- Past-tense storytelling about how the code evolved.
- A paragraph where a clause would do.
- Javadoc on a private one-liner whose name already says it.
- "Note that", "Basically", "Essentially", "As you can see".

## Applying as a cleanup pass

- **Comments only** — never change code, identifiers, string literals, or
  annotation/`.doc()` text. A comment edit must not alter behavior.
- Don't reintroduce or alter parent-plugin name references (those are handled
  deliberately elsewhere — see [[starenchants-conventions]]).
- Leave generated files alone (e.g. `docs/reference/dsl-reference.md`) and
  vendored third-party sources verbatim.

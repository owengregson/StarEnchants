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

## Match the comment to the file (read this twice)

Comment depth must match code depth. **Most classes need no class Javadoc, or
one line.** A multi-paragraph Javadoc block is a red flag — reserve it for a
genuinely subtle *public* contract, and even then link the authority instead of
re-explaining it.

- A data holder, utility, config-surface, DTO, or internal helper gets **zero or
  one line**. Writing a second sentence about a simple class? Stop and delete.
- Architecture, rationale, and the "how it all fits" essay live in **ADRs and
  skills**, not re-narrated on every class that touches them. Cite the authority
  in a clause — *"the surface defined in ADR-0023"* — never reproduce the essay
  on the class. One `ADR-00NN` pointer replaces a paragraph.
- The test is not "is this comment true / accurate?" It is **"would a competent
  reader be lost without it?"** If not, delete it. **Default to delete.**
- Be aggressive. A pruning pass that removes too little is a failed pass. If a
  block restates the class name, the field name, or what the next two lines
  plainly do, it goes — length is not value.

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
- **Multi-line class/field Javadoc that re-explains an ADR or the plain purpose
  of a simple class.** Replace with one clause + an `ADR-00NN` cite, or nothing.
- A comment on a self-describing declaration — `/** Top-level dirs. */` above
  `DIRS = List.of("content", "items", "menus")`. The name and value already say it.

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

```java
// BEFORE — a six-line essay on a simple config-surface class; re-narrates ADR-0023
/**
 * Defines and manipulates the config surface a pack captures (ADR-0023): the top-level
 * config.yml + lang.yml files and the content/, items/, menus/ trees — exactly what
 * StarEnchantsPlugin.saveDefaults() extracts on first boot. Everything else in the data
 * folder (the packs/ dir itself, migrated/, staging, dotfiles) is outside the surface and
 * is never collected or overwritten. ...
 */
// AFTER — one clause, the authority cited not reproduced; the genuine footgun kept
/** The files/dirs a pack captures (ADR-0023). {@link #writeAll} rejects escapes (.., absolute, foreign top-level). */
```

```java
// BEFORE                                                 // AFTER (deleted)
/** Top-level recursive directories in the surface. */    (nothing — DIRS + its value say it)
static final List<String> DIRS = List.of("content", "items", "menus");
```

## Red flags — stop and cut

- The comment paraphrases the line beneath it.
- Past-tense storytelling about how the code evolved.
- A paragraph where a clause would do.
- Javadoc on a private one-liner whose name already says it.
- "Note that", "Basically", "Essentially", "As you can see".
- A class Javadoc longer than ~2 lines on anything that isn't a subtle *public* API.
- Any comment you kept only because it was already there and looked harmless.

## Applying as a cleanup pass

- **Comments only** — never change code, identifiers, string literals, or
  annotation/`.doc()` text. A comment edit must not alter behavior.
- Don't reintroduce or alter parent-plugin name references (those are handled
  deliberately elsewhere — see [[starenchants-conventions]]).
- Leave generated files alone (e.g. `docs/reference/dsl-reference.md`) and
  vendored third-party sources verbatim.

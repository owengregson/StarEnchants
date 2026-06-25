# The compiler and config

Content in StarEnchants is **compiled, not interpreted**. Authored YAML and the
embedded effect DSL are turned by [`se/compile`](../../../se/compile) into one
immutable `Snapshot` — a dense `Ability[]` plus the interner tables and indices
the runtime needs — published behind a single `AtomicReference`. The hot path
never sees a string, a regex, or a YAML node; by the time
[the engine](effect-engine.md) runs an ability, every name is an interned int and
every effect line is a typed-arg record. This page walks the compile pipeline,
the one `ParamSpec` that does four jobs, the diagnostics that never throw, and the
transactional reload that keeps a bad edit from ever reaching combat.

Two modules cooperate, both **pure** (zero Bukkit):
[`se/schema`](../../../se/schema) is the DSL as a typed language — the grammar,
`ParamSpec`, and diagnostics; [`se/compile`](../../../se/compile) is the compiler
that turns authored definitions into a `Snapshot`. Bukkit only enters through an
*injected* resolver facade, so the whole pipeline is unit-testable without a
server.

## The pipeline

`Compiler#compile` (`se/compile/src/compile/Compiler.java`) is the whole pipeline
in one loop — lower and resolve run per ability, erase folds them into one array,
snapshot assembles:

```java
public Snapshot compile(List<AbilityDef> defs, int generation, Diagnostics diags) {
    List<LoweredAbility> lowered = new ArrayList<>(defs.size());
    for (AbilityDef def : defs) {
        lowered.add(resolve.resolve(lower.lower(def, diags), diags));
    }
    ErasedContent erased = erase.erase(lowered, diags);
    return snapshot.assemble(erased, diags, generation);
}
```

Each stage is an interface with a `Default*` implementation, so a test can swap
any phase. The phases:

| Phase | Does | Source |
| --- | --- | --- |
| **grammar** | tokenize → untyped AST (effect lines, condition exprs `&& \|\| ()`, selectors, inline tags) | `se/schema/src/schema/grammar/` |
| **lower** | AST → `CompiledEffect`/`CompiledCondition`; `WAIT` → cumulative `waitTicks`; affinity MAX-fold | `compile/stage/DefaultLowerStage.java` |
| **resolve** | version-volatile names → interned handles via the injected `PlatformResolvers` | `compile/stage/DefaultResolveStage.java` |
| **erase** | source erasure: every source → one `Ability[]`; dense ids + stable-key↔id map | `compile/stage/DefaultEraseStage.java` |
| **snapshot** | assemble the immutable `Snapshot`: `Ability[]`, interners, indices, diagnostics | `compile/stage/DefaultSnapshotStage.java` |

The compiler **never throws on bad content.** Every fault collects into a shared
`Diagnostics`; the caller checks `Diagnostics#hasErrors()` before publishing. A
broken edit leaves the previous snapshot live and never reaches the hot path.

### Grammar

The grammar tokenizes an effect line on `:` at the top level only, respecting
nesting and quotes — `Lexer#splitTop` (`se/schema/src/schema/grammar/Lexer.java`)
tracks depth across `()[]{}<>` and quotes so an argument containing a `:` or `@`
survives intact. Condition expressions go through `ExprLexer`/`ExprParser` into an
untyped `Expr` AST; selectors and inline `@Tag{...}` are parsed into their own
ASTs. The output of this phase is *untyped* — names are still names.

### Lower

`DefaultLowerStage#lower` (`se/compile/src/compile/stage/DefaultLowerStage.java`)
turns one authored `AbilityDef` into a `LoweredAbility`. Two behaviors are
load-bearing. First, **`WAIT:n` is timing, not an effect** — it accumulates into a
tick delay stamped on every *following* effect, fixing a Cosmic Enchants-style
WAIT-overwrite bug:

```java
List<CompiledEffect> out = new ArrayList<>();
int waitAccum = 0;
for (EffectLine line : def.effects()) {
    if ("WAIT".equalsIgnoreCase(line.head())) {
        Integer ticks = parseWait(line, diags);
        if (ticks != null) waitAccum += ticks;
        continue; // WAIT is timing — never an emitted effect
    }
    Optional<CompiledLine> compiled = lineCompiler.compile(line, diags);
    if (compiled.isEmpty()) continue; // unknown head — already diagnosed
    CompiledLine cl = compiled.get();
    CompiledSelector selector = resolveSelector(line, cl.head(), diags);
    out.add(new CompiledEffect(
            cl.head(), lowerExprArgs(cl.args(), diags), selector, waitAccum, affinityOf(cl.head())));
}
```

Second, the **ability-level `Affinity` is the MAX fold** over its effects'
affinities, so the dispatcher can route the whole ability by one ordinal — see
[the engine's affinity routing](effect-engine.md#affinity-routing-and-the-dispatch-plan).
Numeric args that are actually expressions (`%victim.health% * 0.1`) are lowered
to a slot-resolved `NumExpr` IR here too, so the runtime evaluates them against
the `FactBuffer` with no parse.

### Resolve

`DefaultResolveStage#resolve`
(`se/compile/src/compile/stage/DefaultResolveStage.java`) is where every
version-volatile token — a material, sound, potion, particle, entity type,
attribute, enchantment — becomes a **stable interned int**. It does so through an
*injected* `PlatformResolvers` facade (`compile/resolve/PlatformResolvers.java`),
which is what keeps `se-compile` Bukkit-free: production wires the real platform
resolver (modern name → legacy alias → registry → warn-and-skip), tests pass a
fake. An unknown token is a diagnostic, and **that one effect is warn-and-skipped
— the rest of the ability survives**:

```java
OptionalInt id = lookup(type.handleCategory(), token);
if (id.isEmpty()) {
    diags.error("E_UNKNOWN_HANDLE",
            "unknown " + type.handleCategory().label() + " '" + token
                    + "' for argument '" + p.name() + "' of '" + effect.head() + "'",
            owner.source(),
            "use a name valid on the target version, or remove the effect");
    return null; // warn-and-skip this one op
}
args = args.with(p.name(), id.getAsInt());
```

Because resolution is a **compile phase**, the runtime can never touch a renamed
constant — a legacy `CONFUSION` or `GENERIC_MAX_HEALTH` in an old config becomes a
resolved handle here. The alias maps and the renames are covered in
[the operator docs](https://owengregson.github.io/StarEnchants/).

### Erase — source erasure into one `Ability[]`

This is the keystone. `DefaultEraseStage#erase`
(`se/compile/src/compile/stage/DefaultEraseStage.java`) folds **every source —
enchant, set, weapon, crystal, heroic — into one dense `Ability[]`**, with names
interned and bit-packed. A kept ability's dense id is its position in the output
array (`abilities[i].id() == i`), and as it goes it builds the stable-key ↔
dense-id table. Trigger and world names are interned into bitsets with hard caps
(32 triggers, 64 worlds), each overflow a diagnostic; a duplicate stable key is
`E_DUP_KEY` and the second definition is dropped. The tail assembles the frozen
interner tables and indices:

```java
StableKeyIndex stableKeyIndex = new StableKeyIndex(keysByDenseId);
SourceMap sourceMap = new SourceMap(sourceEntries);
Interners interners = new Interners(worlds, triggers, suppress, cooldownScopes);
return new ErasedContent(abilities.toArray(new Ability[0]), interners, stableKeyIndex, sourceMap);
```

One subtle bridge: a `SUPPRESS` effect's key is interned into the *same*
`cooldownScopes` interner the abilities' `cdScope*` fields use, so gate 5
suppression matches an authored disable to its target ability as an `O(1)` int
compare. That role distinction is [feature interactions'](feature-interactions.md#suppression)
concern.

### Snapshot

`DefaultSnapshotStage#assemble` is pure assembly — it adds no diagnostics and
just packs the erased content into the immutable record. The result is a
`Snapshot` (`se/compile/src/compile/model/Snapshot.java`):

```java
public record Snapshot(
        int generation,
        Ability[] abilities,
        StableKeyIndex stableKeys,
        Interners interners,
        SourceMap sourceMap,
        List<Diagnostic> diagnostics) { … }
```

`generation` is a monotonic build counter; with a content hash it keys the
[item-view cache](performance-hot-paths.md#read-once-resolve-once) and stamps each
`WornState`, so a pre-reload view is never read as current.

### Stable keys vs dense ids

Items persist **stable string keys** in their PDC (`enchants/venom/1`); the dense
`Ability#id()` is a **per-snapshot accelerator** reassigned freely on reload.
`StableKeyIndex` (`se/compile/src/compile/model/StableKeyIndex.java`) is the
indirection, and an unknown key resolves to `-1` — skipped, never a crash:

```java
public int idOf(String stableKey) {        // -1 if no ability carries it
    Integer id = idByKey.get(stableKey);
    return id == null ? -1 : id;
}
public String keyOf(int id) {              // null if out of range (cross-snapshot safe)
    return id < 0 || id >= keysByDenseId.size() ? null : keysByDenseId.get(id);
}
```

This is why items survive a reorder: the [worn-set resolver](performance-hot-paths.md)
re-resolves keys to ids against the *current* snapshot at equip time. See
[the item model](https://owengregson.github.io/StarEnchants/) for the PDC layout.

## ParamSpec — one declaration, four jobs

A `ParamSpec` (`se/schema/src/schema/spec/ParamSpec.java`) is the signature of one
DSL kind: a head, ordered typed `Param`s, optional cross-rules, a doc string, and
an example. One declaration drives **validation/range**, **tab-completion**,
**`/se docs`**, and **migration** (`toPositional`), so the four can never drift.
The builder uses the `D.*` type vocabulary (`se/schema/src/schema/spec/D.java`):

```java
ParamSpec.of("SMITE")
    .param("chance",   D.DOUBLE.min(0).max(100))
    .param("radius",   D.DOUBLE.min(0))
    .param("cooldown", D.INT.min(0).def(0))   // optional once it has a default
    .doc("Strike lightning at nearby enemies.")
    .example("SMITE:25:4")
    .build();
```

`D.DOUBLE`, `D.INT`, `D.TICKS`, `D.BOOL`, `D.STRING` are shared immutable bases;
`min`/`max`/`range`/`def`/`optional`/`allowing` are *wither* methods on
`ParamType` that each return a new instance, so the bases are reused freely.
`D.INT` rejects decimals (fixing the legacy `getInt` truncation trap); `D.TICKS`
floors at 0; the handle factories (`D.material()`, `D.potionEffect()`, …) declare
a `HANDLE` param whose token survives lowering verbatim and is interned in the
resolve phase.

> **A note for adders.** `Affinity` is **not** a `ParamSpec` method. An effect's
> affinity is declared in its `EffectSpec` (the engine-side spec) and surfaced to
> the compiler as a `Function<String, Affinity>` keyed on the effect head, then
> MAX-folded in the lower stage. `ParamSpec` is purely the argument signature.

`ParamSpec#parse` validates positionally — present args parse against their type,
a missing required arg is `E_MISSING_ARG`, a missing optional uses its default,
extras warn — and cross-rules only run on a clean parse:

```java
Args args = new Args(values);
if (!diags.hasErrors()) {
    for (CrossRule rule : crossRules) {
        rule.check(args, source, diags);
    }
}
return args;
```

The same spec renders `usage()` (e.g.
`SMITE:<chance:double[0..100]>:<radius:double[0..]>[:cooldown:int[0..]=0]`) for
`/se docs`, yields tab-completions, and provides `toPositional` so the
[migrator](https://owengregson.github.io/StarEnchants/) can reorder a legacy
named-arg map into positional order by *meaning*. `LineCompiler`
(`se/compile/src/compile/LineCompiler.java`) is the per-line driver — it looks up
the spec, parses verbose or positional form, and returns a validated
`CompiledLine`. The registry of head → spec is `MapSpecRegistry`, which fails
fast on a duplicate head at construction.

## Diagnostics — file/line/col, never an exception

Validation never throws. Every fault is a `Diagnostic`
(`se/schema/src/schema/diag/Diagnostic.java`):

```java
public record Diagnostic(Severity severity, String code, String message, Source source, String fixHint) { … }
```

The `code` (`E_RANGE`, `E_UNKNOWN_KIND`, `E_DUP_KEY`, …) is stable so tooling can
group and reference findings, and `Source` (`schema/diag/Source.java`) carries the
`file:line:col` threaded from SnakeYAML marks all the way through compile, so a
malformed line is a precise `file:line:col` diagnostic at load — never a
`NumberFormatException` mid-combat. `Diagnostics`
(`schema/diag/Diagnostics.java`) is a mutable, single-thread-confined collector;
`hasErrors()` is true if any diagnostic is `blocking()` (severity `ERROR`).
Warnings and info ship; an error blocks the publish. Diagnostics surface at load,
on `/se reload`, and via `/se problems`.

## Transactional reload

The reload is **all-or-nothing**, built off-thread and swapped by reference only
when clean. `ContentReloader`
(`se/platform/src/platform/content/ContentReloader.java`) is single-flight (an
`AtomicBoolean`), builds the next `Library` on the async scheduler via the pure
`LibraryLoader`, computes `clean = !library.hasErrors()`, and only then hops to
the global thread to swap. The swap itself is one `AtomicReference.set` in
`ContentHolder` (`se/compile/src/compile/load/ContentHolder.java`):

```java
private final AtomicReference<Library> current = new AtomicReference<>();
public Snapshot snapshot() { return current.get().snapshot(); }
/** The transactional reload swap. */
public void publish(Library library) {
    current.set(Objects.requireNonNull(library, "library"));
}
```

A reader always sees a fully-built immutable `Library`, never a torn state. A
broken reload keeps the old content live — **a bad edit never takes the server
down.** `/se reload --dry-run` compiles and reports without swapping.

The same pure load path powers content *validation*. `ContentReloader#validateCandidate`
(ADR [0029](../../decisions/0029-web-creator-and-import-codec.md)) shallow-copies
the live tree into a temp dir, overlays a candidate file, runs it through the real
`LibraryLoader`, and returns a never-published `ReloadResult` — so the `/se import`
command and the test suite can audit content as a reviewed diff, not a live-server
gamble. The bundled library is checked the same way ahead of release: the whole
content set compiles against a fake `PlatformResolvers` and fails on any blocking
diagnostic.

## Where it lives

| Concern | File |
| --- | --- |
| Pipeline driver | `se/compile/src/compile/Compiler.java` |
| Stages | `se/compile/src/compile/stage/Default{Lower,Resolve,Erase,Snapshot}Stage.java` |
| Compiled output | `se/compile/src/compile/model/{Snapshot,Ability,Interner,Interners,StableKeyIndex}.java` |
| Source erasure tag | `se/compile/src/compile/model/SourceKind.java` |
| Resolver facade | `se/compile/src/compile/resolve/PlatformResolvers.java` |
| Per-line compile | `se/compile/src/compile/LineCompiler.java`, `compile/MapSpecRegistry.java` |
| Spec + types | `se/schema/src/schema/spec/{ParamSpec,D,ParamType,Param,Args}.java` |
| Grammar | `se/schema/src/schema/grammar/` |
| Diagnostics | `se/schema/src/schema/diag/{Diagnostic,Diagnostics,Source,Severity}.java` |
| Reload + validate | `se/platform/src/platform/content/ContentReloader.java`, `compile/load/ContentHolder.java` |

## Gotchas and invariants

- **The pipeline never throws.** Faults go to `Diagnostics`; the publish gate is
  purely `hasErrors()`. The `AtomicReference` is only `set` on a clean,
  non-dry-run build.
- **Cross-version resolution is a compile phase**, never runtime. If a token can
  vary across versions, it resolves in the resolve stage to an interned handle.
- **Diagnostics carry `Source(file,line,col)`.** When adding a check, thread the
  source through and pick a stable `code`; never throw.
- **Stable keys are the contract, dense ids are an accelerator.** Items resolve
  by stable key after any reorder; never persist a dense id.
- **One `ParamSpec`, four uses.** Keep validation, completion, docs, and
  migration driven from the single spec — don't add a parallel parser or doc
  string.
- **`WAIT` is timing.** It accumulates into following effects' `waitTicks`; it is
  never an emitted effect.

Adjacent reading: [the effect engine](effect-engine.md) for what runs the
compiled `Ability[]`; [feature interactions](feature-interactions.md) for the
arbiters the lowered effects feed; the ADRs for
[config + migration](../../decisions/0006-config-and-migration.md),
[the v2 content format](../../decisions/0016-content-format-v2.md), and
[the content loader and reload](../../decisions/0014-content-loader-and-reload.md);
and [architecture spec](../../architecture.md) §10 for the rationale.

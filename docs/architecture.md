# StarEnchants — Architecture (approved design spec)

> **Status: APPROVED 2026-06-15** (ADR-0011; user decisions in §13, ADR-0012, ADR-0013). This
> is the single architecture StarEnchants is built to. Derived from a multi-lens design
> workshop (proposals → adversarial critiques → synthesis) — deliberately **not** a
> packet/anticheat-reference or Cosmic Enchants-style mirror. Where a proposal's idea
> survived a critique it is adopted; where a critic could detonate it, it is discarded and the
> replacement is named.
>
> Bracketed tags (`[ax]` author-experience, `[do]` data-oriented, `[hf]` hotpath-folia,
> `[bc]` bounded-context, `[cp]` compiled-program, `[crit:*]` the three critiques) cite the
> design-workshop proposals/critiques, kept locally under `deobf/analysis/design/` (the local
> analysis workspace, gitignored). This document is self-contained for implementation.

---

## 0. The one-paragraph thesis

StarEnchants is a **content compiler + a data-oriented runtime**. At load, a Bukkit-free
compiler turns every enchant level, armor-set bonus, weapon bonus, crystal effect, and heroic
stat — from any of the five **sources** — into **one source-erased `Ability` record type**
(`[do]`) with pre-parsed typed args, a pre-built condition AST, resolved cross-version handles,
and a **declared thread-affinity** folded to the ability level. Every effect/condition/
trigger/selector is a small self-describing class with **one `ParamSpec` declaration used four
ways** (validate / tab-complete / `/se docs` / migrate) (`[ax]`). The compiled world is an
immutable `Snapshot` swapped by a single `AtomicReference`, built off-thread and published
**transactionally** — a bad edit never takes the server down (`[ax]`). The runtime is a handful
of stateless **systems** that, on a trigger, walk a player's **event-driven, pre-flattened,
immutable `WornState`** (all sources already merged and ordered) (`[hf]+[do]`) and execute
abilities through a **`Sink`** — the single mutation boundary that *removes* the scheduler door
from effect authors and routes work by affinity, batched per owning thread (`[do]+[hf]`).
Feature interactions are resolved by shared **contribute-then-resolve arbiters** (one damage
fold, an interned-id suppression set, a cross-gem soul pool) (`[bc]` idea, not its modules). Items store
**stable string-keyed** state in PDC, cached by **raw-blob key + generation** (`[do]`; §5.2); lore is
rendered from state, never parsed. The result is optimal hot-path performance, structural Folia +
cross-version correctness, total compile-time validation, and a codebase where adding any feature
is a small local change.

---

## 1. What we keep from the mirror, and where we DIVERGE

The prior `../ARCHITECTURE.md` (the packet/anticheat-reference mirror: `api / common / core / compat-* / tester`)
is **correct about many mechanisms** and **wrong about the organizing spine**. The user's worry
— "are we building on an inferior borrowed architecture?" — is answered by being explicit.

### 1.1 Kept from the mirror (these are genuinely right, not borrowed-for-borrowing)

- **Floor-API compile target (paper-api 1.17.1), Java 17 class files** for the modern tree. The release
  ships **one Multi-Release jar** (base = legacy v52 classes for 1.8.9/JDK8, `META-INF/versions/17/` =
  the modern v61 tree), built by `scripts/build-mega-jar.sh` with a build-time identical-class-set
  soundness gate — see ADR-0036. The "one universal shaded jar" of the original spec is now that MRJAR.
- **Boot-time resolvers** for every volatile surface (Material/Sound/Particle/Enchantment/
  PotionEffectType/Attribute/EntityType) absorbing the 1.20.5 rename wave + 1.21.3 flip.
- **A `Scheduling` abstraction** probed Folia-vs-Paper at boot; `compat-*` modules behind a
  `Capabilities` probe.
- **PDC item-data** (drop NBT-API), with a **read-only legacy NBT migration contract**.
- **Immutable atomic config snapshot** swapped by reference.
- **First-party `ProtectionProvider` / `EconomyProvider` SPIs.** *(The original "drop the brittle
  bundled bridges / ship each integration as a separate add-on jar" plan — ADR-0017 — is **superseded by
  ADR-0027**: integrations are now bundled in the core jar, soft, and optional, living in `se/integrate`.
  The SPIs stay; the bridges are back in-jar, guarded by lazy classloading.)*
- **A live Paper + Folia integration matrix** as a build gate (the one external practice we adopt).
- **One Paper-native listener set** replacing a Cosmic Enchants-style per-effect-listener + reflective event-registration scheme.

### 1.2 Where this design DIVERGES from the mirror (and why each is better for THIS plugin)

| # | Mirror (`../ARCHITECTURE.md`) | This architecture | Why it is better *here* |
|---|---|---|---|
| D1 | Spine = `common / core` split **by Bukkit-distance (purity)** | Spine = the **content lifecycle**: `schema → compile → engine → content`, plus platform/item leaves | A "layering" split scatters one feature across `common/dsl`+`common/model`+`core/effects`+`core/item`+`core/render`. The dominant activity here is *adding content*; the lifecycle spine gives that a home and makes purity a *consequence* (a compiler must be testable) rather than a decree (`[ax]`, `[crit:maint]`). |
| D2 | "Unified engine" = a monolithic pipeline in `core/engine` that **resolves 4–5 sources per hit** | **Source erasure**: all five sources lower to ONE `Ability` record; unification happens at **compile time + equip time**, so the hit walks one pre-merged ordered array | "It's all one `Ability` array" is provably uniform (one type) and strictly less per-hit work than merging maps of five frameworks each hit (`[do]`, `[hf]`, both critiques rank this #1 idea). |
| D3 | Scheduling = `Scheduling.run(...)` **wrapped at each call site**; effects trusted to call it | **Declared `Affinity` per effect + a `Sink` that removes the scheduler door**; engine routes, batched per owning thread; all-local abilities run **inline (zero hop)** | The door to raw mutation is *removed*, not *discouraged*; the common combat hit does zero dispatches; cross-region work collapses to ~1 hop/thread. Folia-correctness becomes structural, not author discipline (`[do]+[hf]`, `[crit:perf]` top graft). |
| D4 | Item-data = PDC under **many keys**, JSON enchant list, cache "bolted on," keyed by **ItemMeta identity** | **One compact stable-string-keyed record**, cached by **content-hash + generation counter**; lore rendered from state | Meta-identity keys miss constantly and can alias (latent correctness bug per `[crit:perf]` §6). Content-hash + generation is the only cache-soundness story that survives ItemStack copies (`[do]`). |
| D5 | DSL "parsed once at load" but **validation scattered** per effect class | **One `ParamSpec` per effect/condition, used four ways**; total compile-time typecheck with **file/line diagnostics**; **transactional reload + `--dry-run`** | Makes the DSL a *typed language with errors* instead of a string that throws `NumberFormatException` mid-combat; kills parser/completion/docs drift; ops deploy hundreds of edits atomically or not at all (`[ax]`, both critiques' #1 graft). |
| D6 | Interactions handled "by intention" (one damage pass) in a generic `common/interaction` | **Contribute-then-resolve arbiters**: damage-mutating effects are *structurally forbidden* from touching the event; they contribute deltas; one fold commits once | The catalog's worst combat bug (order-dependent multiplicative compounding) becomes impossible *by construction*, and the same pattern generalizes to souls/slots (`[bc]` idea, `[crit:correct]` graft). |
| D7 | `tester` is a peer module, otherwise the gate is implicit | The gate is **two-layer and explicit** with a **`validateContent` CI task** compiling the whole library against a fake resolver, plus golden-item/golden-config fixtures | Auditing hundreds of enchants becomes a reviewed, CI-gated diff, not a live-server gamble (`[ax]`). |

### 1.3 What we explicitly REJECT (critics could detonate these)

- **A full lexer→parser→typed-IR→~150-opcode VM with `HANDLERS[ordinal]` dispatch** (`[cp]`'s
  spine). *Astronaut architecture for a Bukkit plugin*: stack traces point into an interpreter,
  ordinals become a stability contract, and ~90% of its benefit is reachable with compiled records plus
  typed args and a pre-built condition AST (`[crit:maint]` fatal flaw #1). **We steal its
  `OpSignature` idea and its mock-host testability; we leave the VM.**
- **WAIT-as-continuation over a pooled Frame resumed on a (possibly migrated) entity scheduler**
  (`[cp]` §4.3). *The textbook "green on Paper, races on Folia" trap.* **Replaced** by
  compile-WAIT-to-deferred-intent-batches flushed on the region timer (`[do]` model;
  `[crit:perf]` + `[crit:correct]` both flag the continuation as fatal-as-written).
- **An in-process mediator bus as the primary intra-plugin interaction mechanism** (`[bc]` §3.5).
  *Replaces greppable direct calls with publish/subscribe you cannot trace by reading*, and its
  `SetSlotContribution`/`RequestEnchantStamp` examples turn the catalog's hardest **synchronous**
  contracts (omni completion, enchant stamping) into ordering-hazardous async messages
  (`[crit:correct]` fatal #3, `[crit:maint]` fatal #2). **Interactions are synchronous kernel
  query contracts + arbiters instead.**
- **~18 Gradle modules (a module per content domain)** (`[bc]` §2). Taxes the most common activity
  (adding content) with per-domain ceremony. **God-engine prevention is achieved with a handful of
  modules + ArchUnit/lint rules**, not a federation (`[crit:maint]` fatal #3).
- **Pooled mutable `ActivationContext` *object graph* effects receive and could stash** (`[hf]`
  §3.2). State-bleed bugs invisible on Paper, surfacing under Folia (`[crit:maint]` fatal #4). We
  use a **thread-local `FactBuffer` of primitives** (the safe version) and immutable carriers for
  anything deferred.
- **Single `activeSetId` / `boolean fullSet` worn-set state** (`[hf]` §2.4). *Cannot represent
  simultaneous multi-set completion, which omni requires.* **Worn-set state is a SET of active
  sets** (`[crit:correct]` fatal #1 — the single most important correctness fact in the catalog).
- **Per-load interned dense ids written into PDC** (`[hf]` §2.1). *Corrupts live items on any
  config reorder.* **On-item identity is a stable string-derived key** (`[crit:correct]` fatal #2).
- **Annotation-processor codegen as the *primary* registration wiring** (`[cp]`/`[ax]`/`[do]`).
  *Build mystique, IDE confusion, silent breakage.* **An explicit, greppable, checked-in registry
  (or a trivial ServiceLoader/classpath scan) is primary**; codegen, if ever used, is an
  optimization layered on top (`[crit:maint]` fatal #5).

---

## 2. Module + package layout (and the rationale for each boundary)

Gradle multi-module. The spine is the **content lifecycle** (`[ax]`+`[do]`), with version/Folia
edges as **leaves** and one universal shaded jar. Module count is deliberately moderate: enough to
make purity and version-quarantine *compiler-enforced*, few enough that adding content is "add a
file," not "make a module." Boundaries inside a module are enforced by **ArchUnit + a CI lint**,
not by splitting further.

The tree below is the **shipped** layout (flat `se/<module>/src/<module>/…`, single-segment
packages). It has drifted from the original spec in package names and module set — this is the
reconciled map; each module's charter is the footer comment in its `build.gradle.kts`.

```
starenchants/
├── se/schema/         PURE. The DSL as a typed LANGUAGE DEFINITION — grammar, the ParamSpec
│                      type system, and diagnostics. Zero Bukkit; JUnit only. A load-bearing
│                      purity boundary (a compiler must be deterministically testable).
│
├── se/compile/        PURE. The COMPILER: authored YAML+DSL → immutable validated Snapshot.
│                      Stays Bukkit-free by taking a PlatformResolvers facade by INJECTION (tests
│                      pass a fake; production passes se/platform/resolve). Holds resolve/typecheck/
│                      lower/erase (SOURCE ERASURE → ONE Ability[]) + snapshot assembly + the
│                      content loader (compile.load) and lang catalogue.
│
├── se/engine/         The RUNTIME. Bukkit-aware, FLOOR API only. Version-agnostic. Stateless
│   │                  systems walk a pre-flattened WornState and execute abilities through the Sink.
│   ├── boot/          ContentCompiler — wires the production compile pipeline for the bootstrap.
│   ├── trigger/       TriggerKind impls + BuiltinTriggers + registry; ONE Paper-native listener
│   │                  set → Activation, with per-trigger metadata declared.
│   ├── pipeline/      The fixed gate sequence (ActivationPipeline, GateOutcome) consuming
│   │                  abilities; produces a dispatch plan (§3.3, §3.5).
│   ├── effect/        EffectKind + EffectCtx + EffectRegistry, impls under effect/kind. Stateless;
│   │                  declare a spec + Affinity; emit INTENTS into the Sink — never touch entities.
│   ├── condition/     The condition engine: BuiltinVars (the fact vocabulary), VarVocabulary,
│   │                  the compiled ConditionEvaluator + NumExprEval, Flow, and the primitive
│   │                  thread-local FactBuffer (§3.4). Authors extend the FACT vocabulary, not
│   │                  a pluggable condition class.
│   ├── selector/      SelectorKind + SelectorCtx + registry; impls under selector/kind.
│   ├── spec/          The engine-side ParamSpec surface (EffectSpec/SelectorSpec/TargetSpec/T).
│   ├── run/           One activation's execution: AbilityExecutor, ActivationContext/Listener,
│   │                  AreaScan, FactPopulator (fills the FactBuffer from live context), the
│   │                  runtime effect/selector contexts.
│   ├── interact/      The ARBITERS (§6): DamageFold, SuppressionSet, SlotLedger, and the cross-gem
│   │                  soul authority SoulPool + its SoulSpender seam.
│   ├── sink/          THE Sink (the single mutation boundary) + SinkFactory + SinkReadback; the
│   │                  batched, affinity-routed Dispatcher (§3.6). The ONLY code that knows threads.
│   ├── stores/        Named component stores for mutable runtime state (§5.4).
│   └── doc/           ReferenceDoc + ReferenceCatalogJson — the /se docs + doc-site catalog dump.
│
├── se/item/           The item-data service + render. Bukkit-aware, FLOOR API.
│   ├── codec/         ONE compact record codec over PDC (stable string keys; §5.1).
│   ├── view/          ItemView + ItemViewCache: immutable parsed snapshot, raw-blob + generation
│   │                  cache (§5.2).
│   ├── worn/          WornState resolver: event-driven, multi-set, pre-flattened, immutable (§5.5).
│   ├── render/        Lore/name RENDERED from state (DEFAULT/HYPIXEL/transmog). Never parsed back.
│   ├── lang/          The message catalogue accessor items render through (ADR-0033).
│   └── mint/          Minting authored items (books/scrolls/dust/gems/gear) from Snapshot state.
│
├── se/feature/        Thin Bukkit FEATURE shells. One package each; "copy a sibling to add one".
│                      No business logic that isn't a call into engine/item. Shipped packages:
│                      apply/ book/ carrier/ combat/ compat/ crystal/ guard/ heroic/ imports/
│                      menu/ scroll/ slot/ soul/ trak/ trigger/.
│
├── se/platform/       Version + Folia ABSORPTION. Floor API + probed edges. Domain-free leaf.
│   ├── resolve/       Boot-time resolvers (Material/Sound/Particle/Enchantment/PotionEffect/
│   │                  Attribute/EntityType): modern → legacy alias → Registry → warn+skip.
│   │                  Implements the PlatformResolvers facade se/compile injects.
│   ├── sched/         Scheduling abstraction (entity/region/global/async); the Sink dispatches here.
│   ├── caps/          Capabilities probe.
│   ├── protect/       ProtectionProvider SPI (bridges live in se/integrate).
│   ├── economy/       EconomyProvider SPI — atomic withdraw→deposit (Vault bridge in se/integrate).
│   ├── content/       ContentReloader + ReloadResult/ReloadStep — the transactional reload (§10).
│   └── item/          ItemGroups — the applies-to group vocabulary (e.g. [ARMOR] = union of pieces).
│                      There is NO papi/ and NO text/ here: PAPI lives in se/integrate/papi (ADR-0027).
│
├── se/integrate/      Third-party integration bridges — bundled INTO the core jar, active out of
│                      the box, but SOFT (compileOnly APIs, lazy classload; ADR-0027). Holds
│                      protect/ (WorldGuard/Towny/Lands/SuperiorSkyblock/Factions), economy/ (Vault),
│                      papi/ (PlaceholderAPI expansion + passthrough), anticheat/, combat/ (mcMMO),
│                      entity/ (MythicMobs), item/ (ItemsAdder/Oraxen), and the Integrations registrar.
│
├── se/migrate/        EE/EA/AE config importer: parse a legacy plugin's configs, map to the unified
│                      vocabulary, emit COMMENTED reviewable YAML with `# TODO` markers. Pure logic.
│                      (Legacy item-NBT reading was DESCOPED — §4.3.)
│
├── se/pack/           Config packs (ADR-0023): the pure ZIP/filesystem codec + staging/swap store
│                      for a whole config surface (config.yml, lang.yml, content/, items/, menus/,
│                      pack.yml). Knows nothing of Bukkit or the compiler; bootstrap wires apply().
│
├── se/api/            PUBLIC surface ONLY: events, the registration SPI (effect/condition/trigger/
│                      selector/source), read-only item/enchant queries. Add-ons compile here.
│
├── se/bootstrap/      The StarEnchants JavaPlugin — the composition root (ADR-0014): probe caps,
│                      init Scheduling, wire the Compiler, load content/, serve /se reload. Its
│                      test/ tree holds CatalogValidationTest + CosmicPackValidationTest (§10).
│
├── se/compat-folia/   Folia region/entity/global schedulers (probed by se/platform/sched).
│
├── se/tester/         TOOL-ONLY (never shipped). Live Paper + Folia in-server matrix harness.
├── se/imagegen/       TOOL-ONLY. Renders item tooltips/GUIs to committable PNGs by REUSING the
│                      plugin's own server-free render code, so a preview can't drift from render.
└── se/testfx/         TEST-SUPPORT ONLY. Shared unit-test fixtures (FakeEffectCtx, Defs, YamlFixture,
                       RenderGolden, …) so the flat layout doesn't force per-module fixture copies.
```

> Every module also carries a `-Pse.target=legacy` overlay tree (`se/<module>/overlay/{modern,legacy}`)
> for the 1.8.9 build; the modern tree is the default (ADR-0036).

### 2.1 Why these boundaries (not the mirror's, not the federation's)

- **`se-schema` + `se-compile` are pure because a compiler must be deterministically testable**
  (`[ax]`, `[crit:maint]`). This is a *load-bearing* purity boundary (golden YAML → golden Snapshot),
  not the mirror's aspirational "put pure stuff in `common`." `se-compile` stays Bukkit-free by
  taking a **`PlatformResolvers` facade by injection** — tests pass a fake; production passes
  `se-platform/resolve`. (This is the clean seam that lets cross-version resolution be a *compile
  dependency* without coupling the compiler to Bukkit — `[crit:perf]` graft #5.)
- **`se/engine` is one module, not a god-`core` and not 14 contexts.** Inside it, the packages
  (`trigger`/`pipeline`/`effect`/`condition`/`selector`/`run`/`interact`/`sink`/`stores`, plus
  `boot`/`spec`/`doc`) are the *named concerns of a data-oriented engine* (`[do]`) — there is no
  `systems/`; the stateless per-trigger walk lives in `run` (AbilityExecutor) driven by `pipeline`.
  Boundaries are enforced by ArchUnit (`effect/` may not import `Bukkit.getScheduler`; only `sink/`
  touches `se/platform/sched`). We get the federation's
  *god-engine prevention* (the pipeline cannot grow with content because it delegates to registered
  kinds + arbiters) without its *18-module tax* (`[crit:maint]` fatal #3).
- **`se-feature` shells are deliberately thin** (`[ax]`): a scroll/dust/menu is event wiring + calls
  into engine/item/economy. This is what makes "add a feature = copy a sibling" true. The mirror's
  `core/feature/*` mixed real logic in; here the logic lives in engine/item/arbiters.
- **`se-item` is its own module** because item-data + caching + rendering is the second hot path
  (item reads) and has one owner (the conventions invariant "one item-data layer"). It is *one*
  service with a *single-blob* codec — **not** the federation's per-context codecs, which `[crit:perf]`
  showed reintroduce "N keyed PDC reads + N codec dispatches" on the combat-relevant victim miss path.
  (We still get distributed-*ownership* legibility: each feature's state shape is a small typed
  section the codec composes — but it's one read, one decode.)
- **`se-platform` is a domain-free leaf** quarantining every volatile surface and the Folia probe, so
  no `se-engine`/`se-feature` file ever references a renamed constant or names a scheduler (`[do]`,
  `[hf]`, conventions invariant).
- **`compat-folia`** holds the Folia schedulers, behind `Capabilities` — the version-edge module
  pattern, the one part of the mirror that is exactly right. The few newer-than-floor *API* surfaces
  (Brigadier, profile heads, `BlockData` sends) need no dedicated module — they are gated inline via
  `se-platform/caps` + the bootstrap overlay, so an empty modern-edge stub never accrues.

---

## 3. The engine + execution model

### 3.1 No per-effect listeners — a fixed set of stateless systems

Cosmic Enchants-style engines register many effect classes as `Listener`s, or reflect into `HandlerList.allLists`. Both are
deleted (`[do]`). **One Paper-native listener set** (`se-engine/trigger`) translates each Bukkit
event into an `Activation` and hands it to exactly one **System** (Combat/Break/Interact/
Lifecycle/Passive/Repeat). A system's entire job:

```
for (int aid : wornState.byTrigger[trigger]) {     // dense int[] from WornState (§5.5)
    Ability ab = snapshot.abilities[aid];          // contiguous record array
    if (!gates.pass(ab, activation, plan)) continue;
    runEffects(ab.effects, activation, plan);       // emit intents into the dispatch plan
}
dispatcher.flush(plan);                              // §3.6 batched, affinity-routed
```

A system **does not know what `DAMAGE` does** — it walks data. This is the heart of the model:
*systems iterate, effects are kinds, content is rows.* New content cannot make a system bigger.

### 3.2 Compile, never interpret (compiled records, NOT a bytecode VM)

At load, `se-compile/lower` turns each authored line into a `CompiledEffect` — a **flyweight**: a
shared stateless `EffectKind` instance + an immutable typed-args record (`DAMAGE:3:5` → `kind=DAMAGE,
min=3.0, max=5.0`). Conditions compile to a **pre-built expression AST** (flyweight nodes:
`And/Or/Not/Compare/VarRef/Literal`) over int variable slots. **No string survives past load; the
hot path has no parser.** This is the `[cp]` "compile once" benefit *without* the `[cp]` opcode-array +
continuation VM that `[crit:maint]` flagged as over-engineering — a `CompiledEffect[]` walk with a
virtual `run(typed ctx, sink)` per effect is plenty fast (`[crit:perf]`: its per-effect constant is
*smaller* than the IR's per-op affinity-check + `HANDLERS[ordinal]` constant) and a stack trace points
at `DamageEffect.run`, not an interpreter loop.

### 3.3 The activation pipeline (Cosmic Enchants-style gate order preserved, compiled checks)

The Cosmic Enchants-style gate order (`CATALOG §1.5`) is a correctness contract and is preserved **exactly**, identical
for every source (this is what prevents the per-source path drift that broke the originals). Each gate
is a compiled check, not a string op:

```
1. world blacklist        (ability.worldBlacklist & actorWorldBit) == 0          // primitive AND
2. protection / region    ProtectionProvider.allows(actor, target/block)         // uncached by design — every eval asks the providers; see ProtectionService
3. trigger-match + slot    (ability.triggerMask & bit) != 0 && slot-applies       // re-check target!
4. level bounds
5. SUPPRESSION             SuppressionSet.contains(enchantId|groupId|typeId)       // §6.2, O(1)
6. cooldown (3 scopes)     CooldownStore.ready(packedKey)                          // primitive long map
7. condition + chanceΔ     CompiledCondition.eval(FactBuffer) → {flow, chanceΔ}    // AST walk, no alloc
8. chance roll             tlrCurrent() < (baseChance + chanceΔ)   (FORCE ⇒ 100)  // [ax] TLR-per-use fix
9. PreActivate event       cancellable; fired ONLY if a listener is registered    // [hf] alloc fix
10. soul cost              SoulSpender.trySpend(...)   (only if gem active)        // §6.3, cross-gem SoulPool
11. start cooldown         CooldownStore.arm(packedKey, cooldownTicks)
12. run effects            emit intents → dispatch plan, with cumulative WAIT      // §3.6
```

Notes that fix named catalog bugs: gate 3 **re-checks target applicability** (a helmet enchant no
longer fires on ATTACK); chance is `roll [0,100) < chance` read as a `double` (fixes the
`nextDouble(100)+1` quirk and `getInt` truncation).

### 3.4 Conditions + variables: expression AST over a thread-local primitive `FactBuffer`

The expression engine (`se/schema` grammar → AST; `se/compile` validates against the variable
vocabulary and lowers to a **compiled `ConditionEvaluator`** — `engine/condition`) yields a **flow +
chance-delta** result (`STOP / FORCE / CONTINUE / ALLOW / ±chanceΔ`) — one compiled condition both
*gates* and *tunes chance* (the Cosmic Enchants-style chance-delta). Live facts live in a
**thread-local reusable `FactBuffer` of primitives** (`[do]`; the *safe* version of pooling that
`[crit:maint]` endorsed): a flat struct (`actor.health`, `victim.health`, `damage`, `combo`, a `long`
flag bitset for `sneaking/blocking/flying/…`) populated **lazily, once per activation**, read by both
conditions and effect args by compiled slot index. **Zero string parsing, zero boxing on the hot
path.** PAPI/unknown tokens compile to a `PapiVarRef` resolved only when reached and only if present.

**A condition is a compiled expression, not a pluggable class.** There is no `ConditionFn` SPI to
implement per condition; the whole condition language is one AST evaluator over a **fact vocabulary**.
The extension axis for authors is therefore **facts, not condition classes**: a new built-in variable
is declared in `engine/condition/BuiltinVars` (the greppable `VarVocabulary`, slots append-only) and
populated in `engine/run/FactPopulator` — the compiler then lets any `%scope.name%` expression use it.
This is why conditions get richer without the pipeline or a switch growing.

**Folia discipline for victim-derived facts (the hazard every critic named).** `%victim health%`/
`%victim pose%` are *dynamic victim state*. On Folia, `EntityDamageByEntityEvent` fires on the
*attacker's* thread and the victim may be in another region — a raw victim field read there is a
wrong-thread access (`[crit:perf]` fatal #4). Rule: **dynamic victim facts are captured from the
event payload** (the event carries final/base damage; victim health is `victim.getHealth()` which on
the EDBE is read from event context, and we snapshot it when the event fires on the firing region) **or
from the immutable `WornState` snapshot** for set/equipment-derived facts — **never** by reading the
live victim entity from the attacker's thread. `FactBuffer` population for defense-side victim vars is
done at event entry, before any cross-thread work. This is a *designed, matrix-verified* part of the
architecture, not an afterthought.

### 3.5 The dispatch plan (effects produce intents, the engine owns threads)

An `EffectKind.run` **never touches an entity/block/world directly** (`[do]`). It emits **intents**
into a pooled per-event dispatch plan:

```
sink.damageDelta(SourceKind.ENCHANT, +0.25);     // contribute, do NOT setDamage  (§6.1)
sink.potion(target, effectId, level, durTicks);
sink.spawn(loc, entityTypeId, ...);
sink.cancelEvent();
sink.blockChange(loc, blockDataId);
```

The plan accumulates intents grouped by **owning thread** (derived from each `EffectKind`'s declared
`Affinity` and the resolved target). A CI lint bans direct entity mutation and `Bukkit.getScheduler()`
inside `se-engine/effect`. **An effect author cannot write a Folia bug because they never schedule and
never touch an entity.** This is strictly better than handing authors `ctx.scheduling()` (the door is
*removed*, not *discouraged*) — the specific weakness that demoted `[ax]` on the perf/Folia lens.

### 3.6 The Sink + batched affinity Dispatcher (Folia, done right)

`Affinity` is a **declared property of every `EffectKind`**, folded to the **ability level** at compile
time (`[hf]` — ability-level, not `[cp]`'s per-op, to avoid a per-op branch on every effect):

```
enum Affinity { CONTEXT_LOCAL, TARGET_ENTITY, REGION, AOE, GLOBAL, ASYNC }
```

- **`CONTEXT_LOCAL` abilities run entirely INLINE — zero scheduler hop on Paper AND Folia**, because
  the event handler already runs on the firing entity's region thread. Profiling the merged catalog,
  the vast majority of *attacker-side* combat effects (DAMAGE, REDUCTION contribution, POTION:self,
  MESSAGE, SOUND) are `CONTEXT_LOCAL`. This is the single highest-value perf/Folia idea in the field
  (`[hf]`, `[crit:perf]` graft #1).
- After the gate walk, the **`Dispatcher` flushes the plan batched per owning thread** (`[hf]`):
  `TARGET_ENTITY` intents for the same victim → **one** entity-scheduler task carrying all
  victim-directed effects of all activating abilities this hit; `REGION`/`AOE` → grouped by region,
  with AoE doing `getNearbyEntities` **on the center's region** then one batched hop per discovered
  target's thread. N hops collapse to ~1 per distinct thread (`[crit:perf]` graft #2).
- **Deferred intents are snapshotted to immutable carriers** before the plan flushes (the pooling
  hazard `[crit:perf]` flagged): immediate `CONTEXT_LOCAL` intents apply inline with no allocation;
  anything deferred (WAIT, cross-region) captures the primitives it needs into an immutable record, so
  a pooled intent is never aliased after return.
- **WAIT = deferred-intent batches** flushed on the region/entity timer (`[do]`), **not** a continuation
  over a pooled Frame (`[cp]`, rejected by both critiques). Cumulative WAIT (fixing a Cosmic Enchants-style overwrite bug)
  is computed at compile time into `waitTicks`.
- **Honest about the DEFENSE side**: defense effects that mutate the victim (heal-on-hit, dodge-cancel,
  warp-behind-attacker) are `TARGET_ENTITY` and **do** cost one hop on Folia (we do not oversell "zero
  hops"). Damage *modification* itself is applied via the single fold on the firing thread (the event
  belongs to the firing region) — always correct (`[crit:perf]` correction to `[hf]`'s headline).

### 3.7 Triggers: bitmask routing, pluggable kinds

One listener set maps each Bukkit event → a `triggerId`; an ability's `triggerMask` is a bitset, so
"does this ability fire on this trigger" is `(mask & bit) != 0`. New triggers (a Cosmic Enchants-style `RepeatingTrigger`,
shield-block, jump, granular fishing) are a `TriggerKind` class declaring the Bukkit event(s) it binds,
how to populate the `Activation`, and its `uses-held / scans-equipment / needs-target` metadata —
**no router edit, no other trigger touched.** `RepeatingTrigger` is an entity-scheduled task seeded
from `WornState` (this is how the dead `DRAIN_SOULS_CONSTANT` finally gets wired).

---

## 4. The ability + on-item (PDC) data model

### 4.1 `Ability` — the source-erased, compiled unit of behavior (the central data structure)

All five sources lower to **one** immutable record (`[do]`); there is **no `CustomEffect`/`Effect`
class hierarchy**. By the time the hot path runs, "this came from a crystal" is a `sourceKind` tag on
an otherwise-identical struct — *uniform handling is the only thing representable.*

```java
record Ability(
    int          id,            // dense per-snapshot index (NOT persisted; §5.3)
    int          defId,         // back-ref to def for op-visible diagnostics/sourceMap
    short        sourceKind,    // ENCHANT | SET | WEAPON | CRYSTAL | HEROIC  (a tag, not a type)
    int          triggerMask,   // bitset of Trigger ordinals
    byte         level,         // enchants; 0 otherwise
    double       baseChance,    // normalized [0,100)  (fixes nextDouble(100)+1 + getInt truncation)
    int          cooldownTicks,
    int          soulCost,
    long         worldBlacklist,// interned world-id bitset, or 0 = none
    CompiledCondition condition,// pre-built AST; null = always true
    CompiledEffect[]  effects,  // flyweight kind + typed args (NOT opcodes, NOT strings)
    int          repeatTicks,   // RepeatingTrigger; 0 = none
    Affinity     affinity,      // folded MAX over effects → ability-level (§3.6)
    short        cdScopeEnchant,// interned cooldown-scope ids
    short        cdScopeGroup,
    short        cdScopeType,
    int          suppressKey)   // interned (enchant id | group id | type) for DISABLE_* matching
{}
```

Everything on the hot path is an int/short/double or an interned id; comparisons and bitsets replace
string compares. `suppressKey` makes DISABLE matching an int compare (kills the case-sensitivity
divergence — case-folded at compile time) (`[do]`/`[cp]`/`[hf]` converge; `[crit:correct]` graft #3).

### 4.2 On-item (PDC): one compact, **stable-string-keyed** record — state only, never behavior

One `ItemDataService` (`se-item`) owns all item state under versioned `NamespacedKey`s. The item
carries **identity + counters**, never DSL, never lore-derived state. Compiled programs live in the
Snapshot, not on the item — the item names *which* defs it has, by **stable string key**.

| State | PDC representation | Notes |
|---|---|---|
| enchants | list of `(stableEnchantKey, level)` | **stable string key**, reorder-proof (§5.3); the merged channel |
| slots / added / ignore | packed ints | capacity system; computed + persisted (fixes throwaway-copy bug) |
| crystals | **list** of stable crystal keys | a LIST (fixes a Cosmic Enchants-style last-of-type collapse); each = a first-class source |
| set / weapon set | stable set key | was `armor-value`/`weapon-value` |
| omni / heroic | flags + heroic flat stats | omni = wildcard (§6.6); heroic = a source (§6) |
| souls (on gem) | count + soul UUID | tracked by **PDC UUID**, not hotbar slot (fixes reorg bug) |
| economy markers | bitset + side payloads | scroll/dust/orb/nametag/crate identity (separate from combat blob) |

Two design rules the critics made non-negotiable:

1. **Stable string keys, never a volatile dense index, in PDC** (`[crit:correct]` fatal #2): an item
   authored years ago resolves by its stable key even after configs are reordered/reloaded. The dense
   `Ability.id` is a per-run accelerator only (§5.3).
2. **Crystals as a list of keys** (`[do]`/`[ax]`/`[bc]`): the runtime resolves each key → its compiled
   `Ability`s, so crystal-`DISABLE_ENCHANT` works and the "last-of-a-type collapses" bug disappears.

Lore/name are **rendered** from this state (`se/item/render`), deterministically, every time it
changes — never parsed back. Transmog/godly reorder the *render order*, not the stored state (kills the
HYPIXEL index-diff fragility and the `WordUtils` break wholesale).

### 4.3 Migration — config-only (legacy item-NBT reading DESCOPED)

**Config migration shipped; the lazy legacy-item-NBT reader did not.** `se/migrate` translates another
plugin's *configs* (EE/EA/AE) into commented, reviewable StarEnchants YAML (§10). The originally-planned
reflective reader that would decode another plugin's on-item NBT (`customEnchantList` JSON,
`customEnchantSlots`, `armor-value`, `modifiers`, …) into the modern PDC record on first touch was
**never built and is dropped** — see the [Update] note in ADR-0005. An item written by a different
plugin is read as vanilla: StarEnchants only recognises its own `se:*` PDC keys, and an unrecognised
item carries no StarEnchants state. This is deliberate — StarEnchants is a fresh plugin with a config
migrator, not a drop-in binary upgrade for another plugin's live items. `se/item` still owns its own
forward-compatible read path across the version range (stable keys, §5.3); there is no cross-plugin
item importer.

---

## 5. The item-data service, cache, and worn-set resolver

### 5.1 The codec: one read, one decode, stable keys

`se/item/codec` reads **one combat-relevant record** (enchants/slots/crystals/set/heroic) and **a
separate economy/identity record** (so the per-hit decode never touches scroll/dust/crate markers —
identity items are never on the combat hot path) (`[hf]`). Stable string keys keep it reorder-proof
(§4.2); the format is compact but **not** an opaque single-byte-blob with persisted dense ids (`[hf]`'s
mistake) — debuggability is preserved via `/se item dump`.

### 5.2 The cache: raw-blob key + generation (the only sound key)

`ItemViewCache.of(stack)` (`se/item/view`) reads the item's raw **combat-blob string** from PDC; if the
current generation's map holds it, return the cached `ItemView`; else decode once and intern. Two
deliberate choices:

- **The key is the full raw blob string itself, not a computed hash.** The design brief called for a
  "full content hash"; the shipped cache is stricter — it keys on the *entire* blob, so a collision is
  impossible by construction rather than merely improbable. (`[crit:correct]` watch #4 is satisfied a
  fortiori.) An empty/absent blob returns a shared empty view with no map touch or allocation.
- **It is an injected instance cache, not a static/`ItemMeta`-identity cache.** The cache is a plain
  object (holding the `CombatCodec`), constructed and wired at boot — explicitly **NOT** keyed on
  `ItemMeta` identity, which `[crit:perf]` §6 proved both misses constantly (meta is copy-on-write per
  `ItemStack`) and can alias.

A **generation counter** guards reloads: `reload(gen)` swaps in a fresh per-generation map, so every
prior view is dropped atomically — no stale reads survive a content swap. **Growth is bounded** by the
number of *distinct gear configurations* seen within a generation (each distinct blob is one entry),
and a reload resets the map, so the cache cannot grow without bound across a server's lifetime. It is
lock-free across Folia region threads (immutable views, a `volatile` generation holder, a
`ConcurrentHashMap` per generation). In combat the same helmet hit 20×/sec decodes **once**; every later
read is a map lookup — replacing a Cosmic Enchants-style `new NBTItem(item)` clone + Gson parse per slot
per hit, the single biggest CPU win.

### 5.3 The stable-key ↔ dense-id indirection (forward-compatibility)

The Snapshot holds a **persistent stable-key → dense-id map** (`[do]`, `[crit:correct]` graft #2). PDC
stores stable string keys; the dense `Ability.id` is assigned per-snapshot for fast array indexing only.
On reload/restart, ids are reassigned freely; items resolve by stable key. Unknown key → rendered as
"unknown enchant" and skipped (never crash). This is what makes the content library hot-swappable and
items forward-compatible across the 9-year version range.

### 5.4 Component stores (where mutable runtime state lives — enumerable)

The data-oriented discipline: **mutable per-player state is in named, enumerable stores**, not scattered
in effect objects (`[do]`, `[crit:maint]` graft #7). Every store is concurrent, keyed by a stable id
(player/gem/projectile UUID), TTL-evicting where it holds a timed flag, and cleared on quit + `onDisable`
(fixing the Cosmic Enchants-style task/state leaks — neither original tears anything down). The shipped
set (`se/engine/src/engine/stores/`):

| Store | Holds |
|---|---|
| `CooldownStore` | packed scope key → expiry tick (gate 6 `ready` / gate 11 `arm`) |
| `SoulModeStore` | uuid → active soul gem, keyed by the gem's **PDC UUID** (not hotbar slot) |
| `SuppressionStore` | uuid → interned id → expiry tick (timed `DISABLE_*`; the transient per-activation set is a separate arbiter) |
| `ProjectileStore` | projectile UUID → runtime data + expiry (one shared store, not a timer per arrow) |
| `ChargeStore` | uuid → interned ability id → stacking count + sliding-TTL expiry (Rage-style ramps) |
| `RepeatStore` | uuid → (ability id → opaque task handle) for repeating triggers / soul drain |
| `ComboStore` | uuid → combat streak (source of the `%combo%` fact); owned by combat dispatch |
| `VarStore` | uuid → named writable variables (`SET_VAR` / `INVERT_VAR`), optionally time-limited |
| `ImmuneStore` | uuid → timed damage immunity by cause (`IMMUNE`; read back by a separate damage listener) |
| `KeepOnDeathStore` | uuid → "keep items next death" flag w/ TTL (`KEEP_ON_DEATH`) |
| `KnockbackControlStore` | uuid → timed knockback multiplier (`KNOCKBACK_CONTROL`; also the Mental bridge, ADR-0026) |
| `TeleblockStore` | uuid → timed teleport/pearl block (`TELEBLOCK`) |

`WornStateStore` (§5.5) is the twelfth per-player store but lives with its data in `se/item/src/item/worn/`
(uuid → immutable `WornState`), not under `engine/stores/`, because it is owned by the item-data service
that resolves it.

### 5.5 `WornState` — event-driven, **multi-set**, pre-flattened, immutable

Resolved **once per equipment change** (Paper `PlayerArmorChangeEvent`, 1.17.1+, replacing the Arnah
scraping lib + 10-tick hacks) + on held-item change — **never per hit** (`[hf]`/`[do]`):

```java
record WornState(
    int        gen,                       // snapshot generation it was built against
    BitSet     activeSets,                // *** SET of active sets, NOT a single id ***  (§6.6)
    int[]      activeCrystalAbilityIds,   // merged from worn pieces (a LIST source)
    HeroicStat heroic,                    // flat reduction/damage/durability as a source
    int[][]    byTrigger,                 // PRE-FLATTENED: per-trigger dense ability ids from ALL
    int[]      combatAttack,              // sources (enchants+set+weapon+crystals+heroic), ORDERED
    int[]      combatDefense)             // attacker / defender directions pre-merged
{}                                        // IMMUTABLE → safe to read cross-region (§3.6)
```

Two correctness rules the critics made non-negotiable:

1. **`activeSets` is a SET** (a `BitSet` of set ids), and the pre-flattened arrays are the **union over
   all active sets** (`[crit:correct]` fatal #1). A player wearing 3 phantom + 3 yeti + 1 omni has BOTH
   active; a single-`activeSetId` model silently drops a set bonus. This is the single subtlest rule in
   the catalog and the design encodes it in the data shape.
2. **`WornState` is immutable and swapped by reference**, so the attacker's thread can read the victim's
   `combatDefense` cross-region with no lock and no wrong-thread access (`[crit:perf]` graft #3). The
   victim path reads **only** this pre-resolved snapshot — never the live victim `ItemStack`.

**The killer move:** source unification (locked mandate #1) is realized as a **single pre-merged ordered
array per direction**, so the hot path doesn't even know there were five sources. Set/omni/crystal
resolution costs **nothing** per hit. (Rebuilds are debounced within a tick to absorb rapid gear swaps.)

---

## 6. Feature-interaction correctness (first-class, precomputed, arbiter-based)

Every interaction is resolved by a **contribute-then-resolve arbiter** (`[bc]` idea, `[crit:correct]` +
`[crit:maint]` top graft) living in `se-engine/interact`, fed by a plan precomputed in the Snapshot.
Effects *contribute* to an arbiter; the arbiter commits **once**. Crucially — unlike `[bc]` — the arbiter
is **per-event primitive scratch owned by the single firing thread**, not a kernel object threaded across
capabilities (which `[crit:perf]` showed has undefined thread-ownership and races on Folia). All five
sources feed every arbiter uniformly because they are all `Ability`s (source erasure makes this free).

### 6.1 Damage stacking → ONE fold, effects forbidden from touching the event

Damage-mutating effects **never call `event.setDamage`**. They contribute deltas into the plan's damage
accumulator (one additive outgoing bucket; a parallel additive defense/reduction bucket — **no
multiplicative buckets**). After the gate walk over attacker `combatAttack[]` + victim `combatDefense[]`,
**one fold** computes the final damage with the approved **fully-additive** policy (ADR-0012):
`final = max(0, (base × (1 + Σ outgoing%) + Σ flatDamage) × (1 − Σ reduction%) − Σ flatReduction)` — all
same-side percentages summed, **no multiplicative stacking across sources** — and writes the event
**once**. Order-independent by construction; heroic flat stats + set DAMAGE/REDUCTION + crystal + weapon
all feed the same additive accumulator — no special-casing (fixes the catalog's worst combat bug:
registration-order multiplicative compounding). Flat **damage** is added after the outgoing multiplier
(not inflated by the attacker's own buffs) but is still reduced by the defender; flat **reduction** is
subtracted last, absorbing its advertised amount — so flat stats stay predictable (ADR-0012). `DAMAGE_INCREASE` is
equation-capable, so the accumulator stores **compiled expression closures**, not constants — the "one
pass" holds but evaluates per-hit expressions (`[ax]`/`[crit:correct]` caught this; many proposals glossed
it).

### 6.2 DISABLE_ENCHANT / GROUP / TYPE → interned-id suppression set, O(1), role-correct

`DISABLE_*` is an **internal engine call** (not a cross-plugin `EnchantActivationEvent` subscription)
that writes an interned id into the per-activation `SuppressionSet` (and `SuppressionStore` for timed
suppression). At compile time the Snapshot's **suppression plan** precomputes, per ability, the keys that
would cancel it. Gate 5 is a set membership test — **O(1), no string compares**. Semantics preserved
exactly and **role-correctly**: `DISABLE_ENCHANT` keys the **defender** (`equalsIgnoreCase` → interned),
`DISABLE_GROUP` keys the **activator** (`equals` → interned) — the lowering records, per DISABLE op,
whether it reads actor- or target-suppression (`[crit:correct]` non-negotiable (d), the under-spec it
docked `[do]` for). Because crystals are first-class `Ability` sources, **crystal-`DISABLE_ENCHANT`
finally works** (dead in a Cosmic Enchants-style original). A cancellable `PreActivate` API event remains for add-on interception.

### 6.3 Souls → a cross-gem affordability pool (SoulPool / SoulSpender)

Soul spending is a **cross-gem `SoulPool`** (`se/engine/src/engine/interact/`), not a per-gem ledger. A
player in soul mode has an in-memory pool whose invariant is `available == Σ(physical gem souls) −
pending`, so gate 10's affordability check reads the pool, never the holder's inventory. The pipeline
calls the pool through the `SoulSpender` seam (`SoulSpender.trySpend` — implemented by
`feature.soul.SoulService` over the pool), at gate 10 after `PreActivate` (Cosmic Enchants-style order
preserved).

- **`trySpend` is atomic and any-thread**: it lowers `available` and raises `pending` under a per-player
  stripe lock. This matters because combat fires on the **victim's** region thread while the gem-holder
  is the attacker — the check can run on a foreign thread, and the pool must be correct there without an
  inventory read.
- **Least-first drain on the holder thread**: `takePending` hands the holder's own thread the souls
  spent-but-not-yet-drained; the physical gems are then debited least-first and re-rendered. `resync`
  re-establishes the invariant against physical truth after external inventory changes (pickup/drop)
  without manufacturing or destroying souls.
- All-or-nothing (no partial spend); a non-positive cost is always affordable and spends nothing.

`DRAIN_SOULS_CONSTANT` (dead in a Cosmic Enchants-style original) is a repeating-trigger ability via
`RepeatStore`. Soul mutation on *another* player is `TARGET_ENTITY` affinity → routed to that player's
thread.

### 6.4 Slots → single ledger authority, persisted

`SlotLedger` owns `max = base + addedSlots`, `used = enchant count`, `remaining = max − used`, computed
purely from `ItemView` and **persisted in PDC** (fixes a Cosmic Enchants-style throwaway-copy non-persistence; one unified
default removes the 9-vs-10 split). Applying an enchant debits at *apply time* (a cold-path feature
action, not the hot path); transmog/sort use `keepSlots`.

### 6.5 Crystals → authored multi-ability items, runtime N-stack merge (ADR-0034/0035)

Crystals are **authored, multi-ability items** (ADR-0034): a crystal def carries its own abilities
directly — there is **no `combines:[a,b]` compile-time generation** of synergy crystals from two sets
(that story is dropped). A crystal is applied to gear at 100% and stored as a **list** on the item
(§4.2), so multiple crystals coexist; N crystals of one type contribute N `Ability`s into `WornState`,
combined correctly by the damage fold / effect run (no "last key wins").

- **Runtime N-stack merge, capped by `crystals.max-merge`** (ADR-0034): identical crystals on a piece
  merge into one stacked entry up to the configured cap, enforced by the crystal feature shell at apply
  time.
- **Per-crystal `stackable`** (ADR-0035): a crystal declares whether same-type copies may co-apply. A
  non-stackable crystal blocks a same-type merge and is de-duplicated per wearer by the `WornResolver`.
- **"Multi Crystal" rendering** (ADR-0035): a merged/stacked crystal renders under its multi-identity
  name and lore rather than repeating a single crystal's display.
- **Whole-entry extraction** (ADR-0035): gear-extract pops the entire stacked crystal entry intact,
  not one ability of it.

See ADR-0034 (rework) and ADR-0035 (stackability + multi-identity + extraction) for the shipped model.

### 6.6 Omni → wildcard in the ONE multi-set resolver (synchronous, read-time)

Omni is resolved **inside the single `WornState` resolver** (`se-item/worn`), event-driven, computed once
per equip change: omni pieces count as a wildcard toward **every** partially-worn set's tally, so
`activeSets` may contain several sets simultaneously (§5.5). This is a **synchronous read-time
computation** — explicitly **not** the federation's async `SetSlotContribution` message between contexts,
which `[crit:correct]` fatal #3 showed introduces an ordering hazard ("did the contribution arrive before
the resolver ran?") around the catalog's subtlest rule. One resolver, one cache, one truth (fixes a Cosmic Enchants-style
duplicated, off-by-one `isWearingFullSet`/`getFullSets`).

### 6.7 Enchant-on-armor stamping → synchronous, one write path

When `se-feature/armor` builds an item with `custom-enchants:`, it writes via the shared
`ItemDataService` **synchronously, in the build path** (`[crit:correct]` fatal #3 — never a fire-and-forget
`RequestEnchantStamp` message that could hand out an un-enchanted crate item). Both channels (vanilla
`enchants:` + custom) survive through one write path.

### 6.8 The interaction test corpus

Every arbiter has a pure unit suite with **hand-computed expectations** (mixed damage sources;
suppression precedence + defender/activator role; soul double-spend under concurrency; slot edge cases;
**multi-set omni completion**), plus live tests for the Folia cross-region versions (steal souls/money/exp
between two players in different regions). Correctness is *demonstrated*, not asserted.

---

## 7. Extensibility — one self-describing file, registered explicitly

The conventions invariant: **implement one interface, register it in one place; copy a sibling to add
one; no giant switch, no editing five files.** Each kind ships its own `ParamSpec` (validation + docs +
completion + migration) and declares its `Affinity`.

```java
final class SmiteEffect implements EffectKind {

  static final EffectSpec SPEC = EffectSpec.of("SMITE")
      .param("chance",   D.DOUBLE.min(0).max(100))
      .param("radius",   D.DOUBLE.min(0))
      .param("damage",   D.DOUBLE.min(0))
      .param("cooldown", D.INT.min(0).def(0))
      .target("who", T.AOE)
      .affinity(Affinity.AOE)                       // declared → engine routes; author never schedules
      .doc("Lightning + AoE damage to entities near the target.")
      .example("{ SMITE: { chance: 25, radius: 4, damage: 6, cooldown: 40 } }");

  @Override public EffectSpec spec() { return SPEC; }

  @Override public void run(EffectCtx ctx, Sink sink) {     // hot path: typed args, no parsing
    for (LivingEntity e : ctx.targets("who"))               // selector pre-resolved at compile
        sink.lightningAndDamage(e, ctx.dbl("damage"));      // emit INTENT only — no entity touch
  }
}
```

- **Validation is total at load**: the `SPEC` makes a malformed line a **file/line diagnostic**, never a
  runtime `NumberFormatException`/`AIOOBE` mid-combat (`[ax]`, `[do]` `ArgSpec`).
- **Registration is explicit and greppable** — a checked-in registry (or a trivial ServiceLoader/classpath
  scan for `se/api` add-ons), **not** annotation-processor codegen as the primary mechanism
  (`[crit:maint]` fatal #5). A contributor can *see* the wiring.
- **Same pattern** for `TriggerKind`, `SelectorKind`, and the rare `AbilitySource`. **Conditions are the
  exception**: there is no per-condition class — the condition language is one compiled expression AST,
  so a new *fact* (a `%scope.name%` variable) is added by appending to `engine/condition/BuiltinVars` and
  `engine/run/FactPopulator` (§3.4), not by registering a class.
- **A new armor set / crystal / enchant is PURE YAML** — no code — compiled by the existing erasure into
  `Ability`s and validated inside `./gradlew build` (the bootstrap `CatalogValidationTest` /
  `CosmicPackValidationTest` compile the whole shipped library against a fake resolver; §10) before it
  ships. 90% of "adding a feature" is data, and the compiler guarantees it is correct before deploy.
- **Affinity is part of the SPI**, so Folia-correctness is a property a reviewer checks on one line, and
  the `Sink`-only mutation rule means even a mis-declared affinity cannot silently touch an entity off
  the right thread (it would route wrong, caught by the auto-generated per-non-local-effect Folia test —
  `[hf]`). `se/tester` auto-generates a cross-region test for every `TARGET_ENTITY`/`REGION`/`AOE` effect:
  extensibility and coverage grow together.

---

## 8. Performance model

Optimal-by-construction; the hot path is an allocation-light array walk over primitives.

- **Combat hit:** read `WornState.combatAttack/Defense` (`int[]`) → index `Ability[]` (record array) →
  primitive/bitset gates → expression AST over a thread-local primitive `FactBuffer` → emit pooled
  intents. **No string ops, no Gson, no item re-read, no map lookups, no YAML/DSL parse** in the inner
  loop. Damage folded once.
- **Item read:** `ItemView` cache hit = one map lookup; miss = one compact decode (no JSON, no NBT
  clone). Key is the raw combat-blob string + generation (collision-*free*, §5.2).
- **Worn-set resolve:** once per equip event, not per hit; result immutable + pre-flattened.
- **Scheduler hops:** **zero** for all-`CONTEXT_LOCAL` abilities (the common attacker-side hit); ~1 per
  distinct target thread for cross-region/AoE (batched). Defense-side victim mutation costs 1 hop on
  Folia (stated honestly).
- **Interning:** every name (enchant/group/world/material/potion/sound) is a dense int at runtime;
  stable string keys only at the PDC boundary.
- **Cooldowns / suppression / repeaters (honest):** these stores are **boxed `ConcurrentHashMap`s with
  lazy TTL** (expiry checked on read, entries reclaimed opportunistically), not the primitive
  `long`-keyed open-addressed maps the original spec imagined. This costs a boxed key/value and a hash
  per touch — **measured irrelevant** against the combat hot path (the inner combat loop is the `int[]`
  walk; store touches are gate 6/11 and timed-flag reads, not per-effect), so it is **accepted as-is**
  (ratified). They remain bounded (TTL eviction + quit/`onDisable` clear), unlike the unbounded HashMaps
  Cosmic Enchants-style engines leak.
- **`PreActivate`** fired only if a listener is registered (`[hf]`); **`ThreadLocalRandom.current()` per
  use** (`[ax]`, fixes a Cosmic Enchants-style captured-TLR bug, Folia-safe).
- **Enforcement is a gate, not a hope** (`[hf]`/`[do]`): the ArchUnit/CI lint bans `Bukkit.getScheduler()`,
  `new NBTItem`, `ItemStack#clone`, `String#split`, regex compile, and YAML access inside `se/engine`
  hot-path packages; a JMH bench asserts ~0 steady-state allocation on the per-hit pipeline and a
  throughput floor. **The number is the spec.** Cold paths (load/reload/menus/commands/item-apply) are
  unconstrained and parse/allocate freely, off the tick loop, swapped in atomically.

---

## 9. Folia + cross-version integration

- **Cross-version resolution is a COMPILE phase** (`[crit:perf]` graft #5): `se-platform/resolve`
  resolvers run during Snapshot build (invoked via the injected `PlatformResolvers` facade), turning
  every legacy token (`CONFUSION`→nausea, `PROTECTION_ENVIRONMENTAL`→protection, `SULPHUR`→gunpowder,
  `GENERIC_MAX_HEALTH`→max_health, `PIG_ZOMBIE`→zombified_piglin) into a **resolved handle interned into
  the compiled effect**. The runtime is *constitutionally incapable* of touching a renamed constant —
  it only ever sees resolved handles. Unknown token → file/line diagnostic, **warn-and-skip that one op**,
  load never crashes. The migrator reuses the **same alias maps**.
- **Folia is owned entirely by `Affinity` + the `Sink` + `Scheduling`** (§3.5–3.6). Effects emit intents;
  the Sink routes by declared affinity via `se/platform/sched` → `se/compat-folia` on Folia, Bukkit
  scheduler on Paper. Cross-entity intents hop to the target's thread (batched); `teleportAsync` is the
  only teleport API exposed. Stores are concurrent + UUID-keyed. **The cross-region combat read is a
  first-class, designed concern** (§3.4, §5.5): victim abilities read from an immutable `WornState`
  snapshot; dynamic victim facts captured at event entry on the firing region — never a live cross-region
  victim read.
- **Capability probes** (`se/platform/caps`) gate `se/compat-folia` and every newer-than-floor API used
  inline (Brigadier, profile heads, `BlockData` sends, trident/dispenser events). The floor build calls
  `Firework#detonate()` directly (API since 1.19) instead of a Cosmic Enchants-style `InstantFirework`
  NMS; a `SkullCreator`-style NMS path is replaced by capability-gated `PlayerProfile`/`setOwnerProfile`.
  A Cosmic Enchants-style reflective `registerEvent` is **deleted** in favor of the one
  Paper-native listener set.
- **Legacy 1.8.9 is a second tree in the SAME jar** (ADR-0036): the modern floor stays **1.17.1**; a
  `-Pse.target=legacy` srcDir overlay (same-FQN whole-file swaps) compiles the whole plugin against a real
  1.8.8 (`v1_8_R3`) jar, is downgraded 61→52 by JvmDowngrader, and is merged as the **base** classes of
  the Multi-Release release jar (modern tree under `META-INF/versions/17/`). The JVM picks the tree at
  load; testing stays era-specific (the tester is never MRJAR-merged).
- **Verified, never assumed:** `se/tester` boots real Paper AND Folia across the matrix (1.17.1 floor,
  both sides of the 1.20.5 flip at 1.20.6, 1.21.x, the 26.1.x ceiling; Folia from ~1.19.4+). A green Paper
  run proves nothing about Folia.

---

## 10. Config + migration approach

- **One unified config surface:** a global `config.yml` + modular per-feature YAML (enchants, groups,
  targets, armor sets, crystals, crates, crafting, menus, items). Group order = rarity (LinkedHashMap
  insertion order — a good authoring model). Target `parents` resolve **recursively** (fixes a Cosmic Enchants-style
  single-level). YAML is exhaustively commented (conventions style).
- **The DSL is a typed language** with one `ParamSpec` per kind used four ways (validate / complete /
  `/se docs` / migrate) (`[ax]`, the #1 maintainability graft of both critiques). Diagnostics carry
  `Source(file,line,col)` from the SnakeYAML loader through compile, surfaced op-visibly (console at load,
  in-game summary on `/se reload`, queryable via `/se problems`). Runtime failures reference the same
  `sourceMap` and **auto-quarantine the misbehaving ability** (disable it, log it) instead of aborting the
  whole activation (fixes a Cosmic Enchants-style "one bad arg kills the enchant").
- **Transactional, all-or-nothing reload** (`[ax]`, adopt verbatim): `/se reload` builds a new Snapshot
  off-thread; if there are fatal diagnostics, **the old Snapshot stays live and nothing changes** — a
  broken edit never takes the server down. If clean, swap the `AtomicReference` on the global thread.
  `/se reload --dry-run` compiles + reports without swapping. Everything reloads (the originals' partial
  reload is impossible — there is one artifact).
- **The migrator is a first-class authoring tool** (`[ax]`, the best-in-class idea of `[crit:correct]`):
  `/se migrate --from ELITE|AE --in old/ --out new/ --report` reads AE/EE/EA YAML and **emits commented,
  reviewable StarEnchants YAML with inline TODOs** (not opaque transformed configs), reusing the same
  `ParamSpec` to re-order/normalize args and the same alias maps to modernize enum names. It is
  **config-only**; the once-planned legacy item-NBT reader was descoped (§4.3, ADR-0005).
- **Content is a CI-validated data artifact — inside `./gradlew build`, not a separate task.** The
  bundled library and the shipped config packs are compiled against a **fake** `PlatformResolvers` by the
  bootstrap unit tests `CatalogValidationTest` and `CosmicPackValidationTest` (`se/bootstrap/test`),
  failing the build on fatal diagnostics. There is **no `./gradlew validateContent` task** — auditing
  hundreds of enchants is a reviewed diff run by ordinary `build`, not a live-server gamble.

---

## 11. Build + test approach

- **Gradle multi-module** per §2; `se/schema`/`se/compile` have **no Bukkit dependency** (pure;
  JUnit only). `se/engine`/`se/item`/`se/feature`/`se/platform` compile against **paper-api 1.17.1**
  (floor); `se/compat-folia` compiles against the newer API it needs, loaded behind `Capabilities`. **One
  Multi-Release release jar** (modern v61 tree + legacy v52 base tree, `scripts/build-mega-jar.sh`,
  ADR-0036); `api-version: '1.17'`; `folia-supported: true`. **Java 17 class target** for the modern tree
  (per-version toolchains for the matrix: 17 ≤1.20.4, 21+ for 1.20.5+, CI on 25); the legacy tree targets
  Java 8 (v52).
- **Two-layer gate, always in order** (`matrix-gate`):
  1. **`./gradlew build`** — pure-logic unit tests dominate and are cheap + total because the compiler,
     conditions, interaction arbiters, and codecs are Bukkit-free: golden YAML→Snapshot fixtures;
     compiler rejects malformed args with exact diagnostics; condition AST + flow/chance; damage fold +
     suppression role + omni multi-set + soul/slot ledgers over hand-built ability lists; migration
     golden-item + golden-config fixtures; chance math with injected deterministic RNG.
  2. **Integration matrix** — boots a real server per `(platform, version)`, installs StarEnchants +
     the `se-tester` jar, runs in-server suites, writes PASS/FAIL, shuts down. Covers floor, both sides of
     the 1.20.5 flip (1.20.6 post-flip), 1.21.x, the 26.1.x ceiling; Folia where it exists.
- **What MUST be on a real server** (not unit): Folia region/entity scheduling + the batched Dispatcher's
  cross-region hops; **the cross-region combat read** (victim defense abilities + dynamic victim facts);
  PDC round-trips across the mapping flip; armor-equip via `PlayerArmorChangeEvent`; menu drag/shift-click
  safety; protection gating; legacy-item migration on each version; auto-generated per-non-local-effect
  Folia tests.
- **Read results honestly** (`live-server-testing`): each server's `test-results.txt` must be **fresh**
  (mtime within the run) and read PASS; a server that failed to boot leaves a stale/missing result, not a
  red banner. Tick-anchored, not wall-clock-anchored. Fresh actors per test. **A green Paper run says
  nothing about Folia — both must be green.**

---

## 12. The unique wins (what makes THIS design beat a generic mirror)

1. **Content is a compiled program with file/line diagnostics + transactional reload** — bad content
   *cannot* reach the hot path because it never compiles into the live Snapshot; ops deploy hundreds of
   edits atomically or not at all. Categorically beyond the originals (parse-at-fire-time, fail-open) and
   beyond a generic "AtomicReference snapshot."
2. **Source erasure to ONE `Ability` type** — uniform handling of enchants/sets/weapons/crystals/heroic is
   the *only thing representable*, so per-source special-casing (the originals' core mistake) is
   structurally impossible. Crystal-`DISABLE_ENCHANT`, damage stacking, and suppression all "just work."
3. **`WornState`: multi-set, event-driven, pre-flattened, immutable** — source unification happens at
   equip time, so the hit walks one pre-merged ordered array (zero per-hit set/omni/crystal cost), and the
   immutable snapshot is the *safe* cross-region victim read on Folia.
4. **The `Sink` + declared `Affinity` + batched Dispatcher** — effects emit intents and never touch
   entities or schedulers; the common combat hit does zero scheduler hops; cross-region work collapses to
   ~1 hop/thread. Folia-correctness is structural (the door is removed), not author discipline.
5. **Contribute-then-resolve arbiters on single-thread-owned scratch** — damage (one fold), suppression
   (interned-id set), souls/slots (ledgers): the catalog's worst interaction bugs are eliminated *by
   construction* and proven by a pure test corpus — without the federation's undefined-thread shared
   ledger.
6. **One `ParamSpec` per kind, used four ways** — the DSL is a typed language; validation/completion/docs/
   migration can never drift; adding a kind ships its own docs and errors.
7. **Stable-string-key item identity + raw-blob/generation cache** — items survive 9 years of version
   changes and config reorders losslessly; the cache keys on the full blob (collision-*free*, §5.2) — the
   only sound one in the field.

Each of these is *specific to a huge live content library on Paper+Folia*; none would matter for a
packet/anticheat plugin — which is precisely why mirroring that reference plugin's spine was the wrong default.

---

## 13. Resolved decisions (user-approved 2026-06-15)

1. **Damage stacking = FULLY ADDITIVE** (ADR-0012): `final = base × (1 + Σ outgoing%) × (1 − Σ reduction%)`;
   all same-side sources summed; **no multiplicative stacking across sources**. A per-server config knob to
   switch policies may be added later but is not required.
2. **`ParamSpec` discovery = an explicit, greppable, checked-in registry as the primary mechanism**; a
   ServiceLoader/classpath scan is offered for `se-api` add-ons. No annotation-processor codegen as primary.
3. **`se-feature` granularity = one package per feature** inside the single `se-feature` module (no
   module-per-domain federation). Revisit only if a feature (e.g. menus, crates) grows large enough to
   warrant its own module.
4. **Migration = config-only (legacy item-NBT reading DESCOPED, §4.3, ADR-0005).** The original decision
   ("read legacy NBT indefinitely, write modern always") was **not built and is dropped**: `se/migrate`
   translates other plugins' *configs*, but StarEnchants never reads another plugin's on-item NBT — a
   foreign item is read as vanilla. Deliberate: a fresh plugin with a config migrator, not a drop-in
   binary upgrade.
5. **PDC format = compact, stable-string-keyed, inspectable via `/se item dump`** (no separate debug-JSON
   mirror).
6. **Commands = `/se` only** (alias `/star`), merging the full enchant + armor command surface with `effects | conditions |
   triggers | selectors | info | problems | reload | migrate | item dump` subcommands; the legacy per-feature
   command roots are **dropped** (ADR-0013).
7. **Scope:** Cosmic Enchants-style marquee subsystems (GKits, loot/mob-drop tables) and the web panel
   remain **excluded**; they stay possible future add-ons via the `se/api` SPI.
   **[Amendment]** *StatTrak-style traks shipped* — v1.1.5 added `feature/trak` (four traks; ADR/heroic
   batch, see MEMORY). GKits, loot/mob-drop tables, and the web panel are still out of scope.

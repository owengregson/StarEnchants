# ADR 0036: Legacy 1.8.9 via srcDir overlay + a single Multi-Release jar

- **Status:** Accepted
- **Date:** 2026-07-01
- **Deciders:** project owner + agent
- **Relates to:** ADR-0002 (version targets), ADR-0008 (cross-version + Folia),
  `docs/legacy-1.8.9-codeshare-design.md` (the design this ADR ratifies + amends),
  `scripts/build-mega-jar.sh`

## Context

StarEnchants targets Paper 1.17.1 → 26.1.x + Folia AND Minecraft 1.8.9 from one shipped artifact. 1.8.9
predates most of the APIs the modern tree compiles against (PDC, `PlayerArmorChangeEvent`, `teleportAsync`,
Adventure, many enums), so a single class set cannot compile against both. Two things had to be decided and
are now proven in the shipped build (`feat/legacy-1.8.9-fork`, PR #156, live-verified on craftbukkit-1.8.8
under JDK 8): *how the legacy tree shares code with the modern tree*, and *how one download runs on both eras*.

## Decision

**One overlay-selected source tree, one Multi-Release jar.**

- **srcDir overlay, same-FQN whole-file swaps.** Every module carries `overlay/modern` (default) and
  `overlay/legacy` source dirs; the era-specific classes are the *same fully-qualified name* in both, so the
  rest of the codebase links against a stable FQN and never branches on version. `-Pse.target=legacy`
  selects the legacy overlay and swaps the `compileOnly` API to a real Spigot 1.8.8 (`v1_8_R3`) jar. There is
  **no `:compat-legacy` module** — the legacy build is simply *every module built with `-Pse.target=legacy`*,
  so `feature/**` and the `bootstrap` composition root are **shared**, not forked, behind thin same-FQN seams
  (e.g. the item codec store, `EquipSource`, `RegistrySupport`, `DispatchSink`, `SinkFactory`).
- **Dual-compile gate.** CI compiles the whole plugin **both** ways; a modern-only or legacy-only breakage
  fails the build. The gate earned its keep immediately (it caught `CraftMetaItem` package-private,
  `BukkitTask.isCancelled` absent, `PlayerItemDamageEvent`/`getClickedInventory`/`getTargetEntity` absent).
- **JvmDowngrader 61 → 52.** The legacy tree is compiled modern then lowered to Java 8 bytecode (v52) and its
  stdlib API shaded, so it boots under JDK 8.
- **A single Multi-Release release jar.** `scripts/build-mega-jar.sh` merges the legacy v52 tree as the jar
  **base** and the modern v61 tree under `META-INF/versions/17/`, `Multi-Release: true`; the JVM selects the
  tree at load. There is **no** separate `-1.8.9`/`-legacy` release asset. A **build-time identical-class-set
  soundness gate** refuses to merge a module whose modern and legacy trees do not expose the same set of
  class names — the invariant that makes MRJAR selection sound. **Testing stays era-specific**: only modules
  with identical modern/legacy class sets (the plugin, not the tester) are MRJAR-merged; `se/tester` is never
  merged, and legacy is smoke-tested by its own JDK-8 live suite.

The modern floor stays **1.17.1**; 1.8.9 is the base tree of the same jar, not a lowering of the floor.

## Accepted direction (target state)

Recorded here as the ratified target the codebase is being steered toward (not yet fully built):

- **Consolidate era-neutral logic into shared base classes with thin era leaves.** The same-FQN whole-file
  swap currently duplicates a class in both overlays even when most of its body is era-neutral (the
  "twin-file copy tax", most visible in `DispatchSink`). The target is a shared, overlay-free
  `DispatchSinkBase` (and peers) holding the era-neutral logic, with a **thin `overlay/{modern,legacy}` leaf
  subclass** carrying only the genuinely version-specific calls. This shrinks the swapped surface to the
  irreducible edge and removes the copy tax.
- **Extend the dual-compile gate to `:integrate` and `:api`.** These modules are currently outside the
  legacy dual-compile lane; bringing them under it closes the gap where an integration/public-API change
  could break the legacy build unnoticed. This lands in **`feat/legacy-gate-integrity`** this wave.

## Consequences

- One artifact runs on 1.8.8/JDK8, 1.17.1/JDK17, and 26.1.x/JDK25 — no second download, no version skew.
- Whole-tree code share (`feature`, `bootstrap` shared) far exceeds the original blueprint's ~60% estimate;
  the swapped surface is the version edge only.
- The soundness gate makes an unsound MRJAR (mismatched class sets) a **build failure**, not a runtime
  `NoClassDefFoundError`.
- Cost: two overlays per era-specific class until the shared-base consolidation lands; the twin-file tax is
  accepted in the interim, with the target above as the exit.

## Alternatives considered

- **A separate `-1.8.9` jar.** The original blueprint; superseded — one MRJAR removes the second asset and
  its version-skew risk entirely.
- **A forked `:compat-legacy` Bukkit-edge module.** The blueprint's assumption; the active-overlay-as-srcDir
  mechanism is cleaner (no source-set dependency-direction subtleties) and let the overlay absorb the whole
  plugin as shared code.
- **Runtime reflection to bridge eras in one class set.** Rejected: it loses compile-time verification on
  both eras — the dual-compile gate's whole value.

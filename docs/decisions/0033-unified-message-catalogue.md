# ADR 0033: Unified message catalogue — one YAML source, drift-guarded (§L)

- **Status:** Accepted
- **Date:** 2026-06-30
- **Deciders:** project owner + engine work
- **Relates to:** ADR 0023 (config packs — a pack's `lang.yml` is an overlay); docs/v3-directives.md §L

## Context

Player-facing chat messages (§L) had drifted into three hand-maintained copies of the same catalogue with no
guard between them:

- `Lang.defaults()` — a ~160-entry Java map in `se/compile`, used as the runtime fallback and the unit-test
  fixture;
- `se/bootstrap/resources/lang.yml` — the shipped file, written to the data folder on first boot and overlaid
  on the defaults;
- `se/bootstrap/packs-src/cosmic-pack/lang.yml` — a full 158-key fork of the same catalogue.

They had already drifted, one direction being a latent bug: ten `menu.*` keys lived only in the shipped
`lang.yml`, absent from the Java `Lang.defaults()` — so a partial user file (or a unit test, which sees only the
Java map) rendered `&cmenu.mint.given?` markers for the mint/console/sets/crystals menus. The pack fork was
stale in the other direction: it predated `/se import`, so an applied cosmic-pack silently dropped that help
line. Separately, `CarrierService` bypassed the catalogue entirely — ~16 outcomes were raw `§`-coded string
literals (several duplicating existing keys), and `CrystalService` branched on an English substring
(`"crystal slot"`) inside an already-rendered message to pick which key to show.

## Decision

**The bundled YAML is the ONE source of truth.** The catalogue lives at `se/compile/resources/lang.yml`;
`Lang.defaults()` parses that classpath resource once (a lazy holder) — there is no second copy in Java. The
`compile` module already stays Bukkit-free (the arch purity test forbids only `org.bukkit`/NMS/Paper), so a
classpath read is fine, and `LangLoader` shares one parse routine between the bundled defaults and a user's
on-disk overlay. The fat jar merges `compile`'s resources to the jar root, so the same `/lang.yml` serves
`Lang.defaults()`, `saveResource("lang.yml")` on first boot, and every module's tests.

**A pack `lang.yml` is an overlay of only what it re-themes.** `cosmic-pack` drops to the 5 soul-gem likeness
keys it actually changes; every other message is inherited from the defaults, so a newly-shipped default is
picked up automatically instead of being dropped by a stale full copy.

**Every service routes through `Messages`.** `CarrierService`'s literals became `carrier.*` / `white-scroll.*`
keys (reusing existing keys where wording matched); `CrystalService` branches on a typed `ApplyResult.Reason`
instead of sniffing rendered text.

A `LangCatalogueDriftTest` guards it: the bundled resource loads clean and non-empty, every `messages.*("key")`
referenced in production code exists in the catalogue (so a missing key is an offline build failure, not a live
marker), and every pack-overlay key is a real catalogue key.

## Consequences

- One place to author a default message; the drift class that shipped `menu.*` markers is now impossible.
- Menu chrome, item names/lore, DSL `MESSAGE` effect text, and per-set equip/remove messages remain their own
  channels (GUI layout / item-likeness / content), deliberately outside the chat catalogue — the scope here is
  chat messages.
- `compile` gains a bundled resource it must ship; the load-guard test fails the build if a packaging slip drops
  it, rather than blanking every message at runtime.

## Alternatives considered

- **Keep the Java map canonical, generate `lang.yml` from it + a drift test.** Preserves the compiled-in
  fallback and pure fixture, but keeps the authored source in Java (less natural for the owner-facing file) and
  needs generation machinery to preserve the file's comments.
- **Keep both copies, add only a parity test.** The smallest change, but a maintainer still edits two files —
  "provably identical" is not "one source", and the ask was a single, seamless system.

## Update (2026-07-01): send-boundary + colour translation moved to `se/platform`

`Messages` (the Bukkit chat send-boundary) originally lived in `se/item/lang`, and the legacy `&`→`§`
translation was duplicated four ways (`item.render.Colors`, `item.mint.ItemFactory.color`, a private copy in
`CarrierService`, `MenuText`'s `ChatColor` path, and both `DispatchSink` era twins). But `Messages` hosts no
item-data concern, and every *chatting* module (feature listeners/menus/services, bootstrap, `SeCommand`) was
pulling in `:item` only to reach it. So both were relocated to the version-absorption leaf:

- **`platform.text.Colors`** is now the ONE home for `&`→`§` translation. It is Bukkit-free (the original
  `item.render.Colors` implementation, moved verbatim with its tests), so rendered text stays unit-testable,
  and every module can see it (`:item`, `:engine`, `:feature`, `:bootstrap` all depend on `:platform`). The
  four parallel implementations are retired; `DispatchSink` no longer needs `org.bukkit.ChatColor`.
- **`platform.lang.Messages`** is the send-boundary, unchanged in behaviour. After the move it imports nothing
  from `:item` — `:item` no longer sits on the chat path.

Pure relocation + unification: no message wording, key, or catalogue change. The one visible nuance is that
the shared translator does not lowercase an uppercase code char (`&C`→`§C`, not `§c`) — client-invisible, since
the Minecraft legacy formatter is case-insensitive.

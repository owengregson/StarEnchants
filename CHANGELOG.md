# Changelog

All notable changes to StarEnchants are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning: [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- **Cosmic-pack rebalance pass 2 (ADR-0050 Revision 1).** Live god-kit fights landed
  0.5–1 hp per hit — pass 1's anchor had priced in a Rage that was actually inert (see
  Fixed). Attack-percent enchants scale up ~2.5–3× and Rage becomes the centerpiece:
  each stack now folds +20–45% plus a flat rider that lands after vanilla armor's
  multiplier, so a full six-stack combo cracks Prot IV instead of being eaten by it
  (the outgoing cap rises to +600% for headroom; set weapon bonuses deliberately stay
  at 20–30 — the enchants carry the damage). Vanilla Weakness potions aimed at
  combatants (Voodoo, Unfocus, the Reaper blade) become non-stacking `WEAKEN` percents
  inside the fold — modern Weakness I's flat −4 was an unbudgeted ~40% tax on every
  hit — low-health conditions now compare `healthpercent` so finishers and desperation
  procs fire on boosted HP pools, and the Overload family's Health Boost climbs to a
  26-heart pool at Godly Overload III: the HP-pool meta the new scale assumes.

### Fixed

- **Rage never fired.** Content stable keys carry their source segment
  (`enchants/rage/N`), but the worn-rage lookup matched a bare `rage/` prefix, so the
  whole system shipped inert: no stacks, no actionbar, no pitch-ladder cue, and
  `%ragestacks%` read 0 on every hit. The lookup now uses the source-prefixed key
  (pinned by a unit test), and the per-hit particle is a FLAME burst on the struck
  enemy instead of angry villagers on the attacker.

## [1.7.0-beta] — 2026-07-10

### Changed

- **Cosmic-pack PvP rebalance (ADR-0050).** The whole pack is re-budgeted around a
  6–10-hit time-to-kill between maxed god kits. Damage-increasing enchants with
  conditions now apply consistently (chance up, magnitude down: Insanity/Barbarian/
  Execute-family fire on every qualifying hit for single-digit percents instead of
  lottery nukes), Rage tops out at +90% on a full six-stack combo instead of +240%,
  and the invincibility stack is dismantled: heroic armor drops to −3% per piece,
  set reductions land in the 5–15% band, always-on per-piece reductions are budgeted
  ×4, and summed custom reduction is hard-capped at 60% (`combat.max-bonus-reduction`)
  while outgoing caps at +350%. Full negates (Dodge, Inversion) are now true event
  cancels with 4×-aware chances, so the cap can't clamp them and they can't inflate
  the reduction bucket. Hidden always-on power was surfaced and gated: Reaper/Spooky
  no longer apply permanent Wither II/Blindness II on every weapon hit, Stellar's
  every-hit 20-HP absorption becomes a 20%-chance cooldown proc, Ender Walker's
  ungated debuff immunity is chance/cooldown-gated, Godly Overload tops at +12 max HP,
  Sniper's fold-bypassing 8-hp arrows drop to 2–4, and Dominate (which reduced its own
  wielder's damage — an import bug) now `WEAKEN`s the victim's outgoing damage.
- **Quieter repeating and passive cues.** Repeating effects no longer play sounds
  (particles stay) unless they consume souls on activation, and passive/held effects
  that apply once on equip lost their one-shot sound-and-particle pops — the universal
  equip cues already cover that moment.
- **Rage rework.** Rage stacks are now first-class: max stacks = the enchant level, the
  damage expression reads the clamped stack count, every stack plays a blaze-hit whose
  pitch steps down an absolute ladder (1.45 at zero stacks to 0.85 at six), each hit
  flashes a Rage Stacks actionbar, and a broken combo (window lapse or victim switch)
  flashes a BROKEN title with a high-pitched blaze-death.
- **Guard summons fight for you.** Guardians' iron golems and Spirits' blazes now
  auto-target the enemy who triggered them (and keep the Blood Link owner binding).
- **Frost and hell floors replace the ground, not the air.** PermaFrost and the Yeti
  set's Fortified now lay a noisy frost palette (ice / blue ice / packed ice / snow
  block) into the ground under the attacker, like the Devil floor — which itself gained
  a glowstone / netherrack / magma / quartz-ore palette. TEMP_BLOCK now takes up to four
  materials with a palette scale that clusters them into connected patches. PermaFrost's
  cooldown is sextupled.
- **Cues play on the right entity, with real spread.** Particle bursts gained a default
  spatial spread and mid-body anchoring, defense-retaliation cues now burst on the
  attacker and self-buff cues on the wearer (15 re-anchored), and every armor set's
  active ability pops a curated sound + particle (modern-only sounds carry floor
  fallbacks via new "A|B" handle chains). Dodge/Ethereal Dodge, Inversion and Divine
  Immolation got new sounds.

### Fixed

- **Soul gem × holy white scroll.** Merging two gems no longer destroys a holy white
  scroll carried by either input, the gem's soul-count name bracket survives a holy
  apply, and the HOLY PROTECTED lore line survives every soul-count re-render.

## [1.6.1-beta] — 2026-07-10

### Fixed

- **The cosmic pack now loads clean on 1.20.5+ servers.** The activation-cue pass authored
  floor-era particle names (ENCHANTMENT_TABLE, TOTEM, SPELL, VILLAGER_ANGRY, …) that the
  1.20.5 enum flattening renamed — `/se pack apply` refused with 79 unknown-particle errors
  on modern servers. The particle alias map now carries the complete rename wave (derived by
  diffing the real 1.17.1 / 1.21.11 / 26.1.2 enums), so either era's spelling resolves on
  either era. Two gates were hardened so this class of drift fails before shipping: the
  offline build now compiles both shipped libraries against committed per-era sound/particle
  constant lists, and the live matrix's CatalogSuite now compiles the bundled cosmic pack
  (not just the default catalog) with real resolvers on every server version.

## [1.6.0-beta] — 2026-07-09

### Added

- **Soul Drinker** (soul tier): drinks 2 souls a second to hold your hunger at bay
  while active.

- **Unopened-book reveal fanfare.** Opening an unopened enchant book pops an instant
  tier-colored firework at your feet and flashes the revealed book's name as a
  subtitle.

- **Universal apply cues.** A successful enchant-book apply and a failed one each play
  a configurable sound + particles (`apply-cues` in config.yml, live-reloadable).

- **Soul-mode toggle rate limit.** Manual soul-mode toggling is debounced to twice a
  second so toggle-spam can't flicker the maintained state.

- **Use-items `is-food` — eat to trigger.** A use-item with `is-food: true` must be
  EATEN (real eating animation) before its abilities fire, and is forced edible on any
  material via the reflective `EdibleItems` seam (food component on 1.20.5–1.21.1, the
  `consumable` data component on 1.21.2+; a no-op one-click below 1.20.5 and on 1.8).
  Eating is a pure trigger gesture — no hunger — with the vanilla consume cancelled so the
  plugin owns the one-item consume.

- **Use-items — right-click content items (ADR-0048).** A new content family
  `content/use-items/*.yml`: a right-click item (its own material/name/lore/shiny/consumable)
  whose abilities fire on use. They lower to the same source-erased `Ability`s as enchants and
  crystals (implicit `USE` trigger) and run the full gate sequence — cooldown, condition, chance,
  souls, suppression — so they interact with every other feature for free. A `commands:` field
  lowers to `RUN_COMMAND` effects (run as console or the activating player, with
  `{PLAYER}`/`{UUID}`/`{WORLD}` tokens). Lore renders the cooldown as `{TIME_FORMATTED}`; universal
  `use-item.*` feedback covers success/cooldown/fail. Ships the `rage-crystal` template; toggled by
  `features.use-items`. Add one by dropping a YAML file — no code.

- **Pack ABI fingerprints (ADR-0046).** `/se pack export` stamps a fingerprint of the live
  authoring surface (effect/selector heads + parameter signatures, triggers, condition
  operators, variables) into the pack manifest, and `/se pack apply` pre-checks every pack —
  fingerprint compare plus a full dry-run compile through the real loaders — before touching
  a single live file. Incompatible packs abort with the exact `file:line` failures
  (`--force` overrides); old unstamped packs still get the dry-run. The shipped cosmic pack
  is stamped and drift-guarded; the canonical surface is committed at
  `docs/reference/authoring-surface.txt`.

- **`/se why` — the activation flight recorder (ADR-0045).** Every gate-walk attempt is recorded
  into a per-player 64-entry primitive ring (allocation-free, always on); `/se why <player> [key]`
  renders the recent attempts — "stopped at gate 6: cooldown — 32 ticks remaining", "suppressed —
  DISABLE_GROUP(defense) from sets/yeti", "fired (rolled 12.3 < 25%)" — answering the #1 operator
  support question ("why didn't X fire?") without guesswork. Ids resolve at render time; a new JMH
  row (`gateWalkRecorded`) floors the always-on cost.

- **Operator diagnostics & reference commands.** `/se problems` prints the retained
  compile/reload diagnostics (with warning counts) so a bad content file stays inspectable
  after the reload banner scrolls by; `/se item dump` decodes the held item's full engine
  state for support work; `/se docs` is an umbrella routing to the in-game reference views;
  and `/star` ships as a first-class alias. Command help is single-sourced from the command
  table, so the in-game usage, `plugin.yml`, and the website agree by construction.

- **Curated addon API (ADR-0038).** `:api` is now a real, deliberately small SPI — addon
  effect/sink facades plus read-only queries — discovered through the Bukkit
  `ServicesManager`. Addon-registered effect kinds survive `/se reload`.

- **Enforcement gates for the engine invariants.** The rules that used to be prose are now
  builds that fail: ArchUnit boundary tests and a hot-path banned-symbol lint, a JMH
  benchmark module with throughput floors and allocation budgets wired into CI, and a
  runtime quarantine — an ability that keeps throwing is disabled for the snapshot with one
  op-visible diagnostic carrying the authored file:line.

- **Per-affinity Folia live coverage.** The live tester walks the effect registry at matrix
  boot and generates a cross-region check for every non-local effect kind (43 live-checked,
  0 skips, a totality assertion, and a Folia distinct-region staging assertion), so Folia
  coverage now grows automatically with every new kind.

- **Release pipeline hardening.** The legacy dual-compile gate extends to `:api` (with the
  `:integrate` exclusion documented — WorldGuard needs 1.13+ `BlockData`), and the release
  workflow now boots the SHIPPED mega-jar on a real craftbukkit-1.8.8 before publishing.

- **Crystal rework — Cosmic-style Armor Crystals (ADR-0034).** Crystals are now
  content files sharing ONE global likeness (`items/crystal.yml`) rendered through
  `{CRYSTAL}` / `{DESCRIPTION}` / `{KINDS}` tokens; a crystal expands to one or more
  abilities (`/a1`, `/a2`, … walked by the `WornResolver` like an armour set's extra
  bonuses). Crystals **merge** by drag-and-drop up to the global `crystals.max-merge`
  cap (cosmic default 2); applying is now **100%** (the success roll + `consume-on-fail`
  are gone); the extractor pops the whole crystal off gear intact, or the topmost single
  off a multi-crystal item (see ADR-0035). Ships new cosmic Armor Crystals plus a minimal
  engine `EXP_GAIN` trigger + `EXP_MULTIPLY` effect (Light's double-XP) and an optional
  `chance` on `SUPPRESS_IMMUNE`.

- **Crystal stackability, multi-crystal identity & new crystals (ADR-0035).** A crystal may
  declare `stackable: false`: it then cannot merge with another of the same type, and its
  bonus applies **once per wearer** even across several worn pieces (a runtime dedup in the
  worn-state flatten). A **merged** crystal now renders as **`Multi Crystal (…)`** instead of
  `Armor Crystal (…)` (new `name-multi` / `lore-while-on-item-multi` templates). Ships **three
  new cosmic crystals** — **Water** (+2% damage in water), **Ender** (+10% damage to mobs),
  **Dark** (+5% dealt / +5% taken) — and reworks **Chaos** (take 50% less durability damage +
  enemies deal 10% more to you) and **Frost** (+1% to all enemies + 4% while on ice). A new
  `%actor.groundblock%` condition fact (the block beneath the actor) backs "on ice".

- **GUI overhaul — the menus are now the primary way to run StarEnchants (ADR-0030).**
  A themed, framed, highly-configurable menu chrome: a `border` picture-frame default,
  named/colour-coded navigation buttons (`« Previous`, `Next »`, `⤶ Go Back`, `✖ Close`),
  a self-describing info pane on every menu, and gentle glint on actionable tiles — all
  tunable per menu from `menus/<name>.yml` (frame, filler, per-button material/name, info
  pane), with `menus/apply.yml` documenting every field. New `/enchants` player hub (the
  benches + browsers, permission-free) closes the gap where admin-gated `/se` left players
  no entry; `/se menu` now opens an Operator Console that can grant books, **mint any item**,
  drill into **armour sets and mint each piece**, mint crystals, apply enchants, browse the
  DSL reference, and reload — all without leaving the game. The Alchemist and Tinkerer
  benches are redesigned. Both the default and cosmic packs ship a documented, themed
  `menus/*.yml` per GUI. Commands remain as aliases.

- **Closed-world JDK-8 API gate (legacy "Gate 2").** `scripts/jdk8-api-gate.sh` +
  `scripts/tools/Jdk8ApiGate.java` walk the downgraded Java-8 (v52) jar with ASM and fail the
  build if any `java.*`/`javax.*` reference is absent from a real JDK 8 — the static net for
  un-shimmable JDK-9+ stdlib APIs that JvmDowngrader passes through silently (they compile and
  downgrade green, then `NoSuchMethodError` on a real 1.8 server, where the reduced live smoke
  can miss them). Embedded in `build-legacy-jar.sh` after the downgrade, so it gates every
  legacy lane: `legacy-smoke.sh` (PR + push) and `build-mega-jar.sh` (release). Public `java/*`
  is a hard failure; JDK-internal `sun/jdk/com.sun` warns (`--strict-internal` to promote);
  `SE_SKIP_JDK8_GATE=1` is a loud, local-only escape hatch.

- Repository foundation: hygiene config (gitignore/gitattributes/editorconfig),
  project agent skills, contributor + development guides, guarded CI workflow,
  PR/issue templates, CODEOWNERS, and conventional-commit git hooks.
- Project structure: ADR decision log, glossary, root agent guide (CLAUDE.md),
  Code of Conduct, Security policy, docs index, Dependabot, release-notes
  config, and a markdown/workflow lint CI.
- Developer reference cache: `scripts/fetch-reference.sh` (downloads + extracts
  per-version Paper/Folia server jars for javap via the PaperMC Fill v3 API,
  1.17.1 → 26.1.2) and a `reference-cache` skill describing the cache + the
  cached Paper/Folia docs (cache itself is local-only / gitignored).
- Approved architecture: `docs/architecture.md` (content-compiler + data-oriented
  runtime, derived via a multi-lens design workshop) and ADRs 0011 (architecture),
  0012 (fully-additive damage), 0013 (single `/se` command root).
- **ParamSpec-derived content fuzz gate.** `./gradlew build` now fuzzes the content
  compiler and loader from the live registries: for every registered effect and
  selector kind, seeded generators derive valid and adversarial near-valid content
  from the kind's own `ParamSpec` (wrong types, boundary/huge numbers, unknown
  heads/params/triggers, unicode, malformed selectors/conditions, duplicate keys,
  pathological YAML) and assert the compiler never throws — every fault is a
  closed-set `DiagCode` diagnostic — and that accepted content is erase-stage sound
  (dense-id/array agreement, trigger-mask vocabulary, affinity fold, cumulative
  WAIT, SUPPRESS scope/key interning, handle interning). Fully seeded and
  deterministic, bounded to seconds, and a new kind is fuzzed automatically with
  zero per-kind test authoring.

### Changed

- **Cosmic enchant behavior alignment (ADR-0049).** ~40 enchants now do what their
  descriptions claim: Bleed (and its appliers Deep Wounds / Bloody Deep Wounds / Deep
  Bleed) is a 3-iteration damage-over-time that scales off the proc'ing hit; Hardened /
  Reforged / Soul Hardened / Immortal guard durability instead of repairing (Soul
  Hardened and Immortal pay in souls); Hex reflects the target's outgoing damage;
  Diminish caps the next blow at half the last one (Vengeful returns the overflow);
  Corrupt voids the target's next Inversion heal; Neutralize is a weapon enchant that
  disarms defensive enchants for the next hit; Double Strike re-procs your enchants in
  one folded swing; Blood Link heals off your guardians' pain; Rogue only backstabs;
  Aegis / Holy Aegis / Anti Gank read the real gank (distinct recent attackers); Death
  Pact scales both ways with missing health; Hellfire fires explosive fireballs;
  Destruction lashes every nearby enemy and saps their damage (non-stacking); Divine
  Immolation erupts across players near the target with lightning, fire and wither;
  Natures Wrath tears 10% of each victim's max health; PermaFrost shields you while you
  stand on its frost; Slayer never one-shots players; Dodge doubles while sneaking;
  Ethereal Dodge also voids all fall damage; Planetary Deathbringer crits at 2.5x;
  Soul Trap silences the whole soul tier. Engine grew the matching primitives (REFLECT,
  WEAKEN, DAMAGE_CAP, ECHO_STRIKE, one-shot SUPPRESS charges, GUARDIAN_HURT trigger,
  five new condition facts, and block-crack/percent-of-max/AoE-exclude/projectile-yield
  parameter extensions).

- **Every cosmic enchant description regenerated.** Per-level stat rows are gone, the
  body copy is uniform yellow (&e), claims match the shipped behavior, and enchant
  books carry the item-style "&eApplies to:" line. Every enchant also plays a curated
  activation sound + particle cue.

- **Merchant GUIs re-laid in the Cosmic Enchants likeness.** The Alchemist is now a
  three-row exchange window — two book slots up top, a live centre preview of the exact
  book the exchange forges, and a bottom-centre CLICK TO EXCHANGE pane. The Tinkerer is a
  six-row split trade window ("You | Tinkerer"): stage any number of books on the left,
  each mirrors an experience-bottle preview of its honest refund range on the right, and
  the red ACCEPT panes salvage everything at once. The Enchanter is a fixed storefront —
  a tier-coloured tile per rarity (priced live from `tiers.yml`) with White/Black Scroll
  tiles flanking the premium shelf, both priced level with a legendary book. Benches got
  a live input-change hook so previews re-render as items are staged; mechanics are
  unchanged (free combine, `[1, N]` salvage roll, XP-level pricing).

- **Interaction-abuse hardening — player-visible balance changes.** Several fixes below
  change gameplay: Rage's combo damage is now capped (`combat.max-bonus-damage`, default
  +500%) and combos reset on a victim switch; Ender Walker's heal gates on a real hit plus a
  cooldown; Tinkerer salvage now refunds a random 1..(book buy cost) levels (can read "1
  level"); Alchemist combine yields the better of the two inputs' success, not a guaranteed
  book; a keepInventory death now DROPS a staged bench book instead of keeping it; and vanilla
  anvil/grindstone/smithing are blocked on plugin set gear (durability pressure; no free
  Mending or netherite). Heroic weapons forged before this build keep their old modifiers until
  re-forged.

- **Cosmic-pack item likenesses refreshed.** Five economy items were re-themed to their
  Cosmic-Enchants-style names/materials: the unopened book (`{TIER_NAME}`/`{TIER_COLOR}` tokens +
  `(Right Click)` hint), the crystal **Extractor** (bucket), **Magic Dust** (glowstone,
  `{MIN}-{MAX}%`), **Randomizer Dust** (redstone — the rename is cosmetic; failure stays
  `100 − success`), and the **Godly Transmog Scroll** (writable book). `WRITABLE_BOOK`/`RED_DYE`
  gained 1.8-lane material degradations.

- **Feature-module wiring erasure (ADR-0047, internal — no behaviour change).** The 592-line
  hand-wired `onEnable` is replaced by one `FeatureModule` record per feature, an explicit
  ordered `Modules` registry of 19 modules, and a `ModuleFold` that reproduces the exact shipped
  listener registration sequence (golden-pinned; two proven-disjoint listener moves aside).
  `onEnable` is now a ~15-line fold and `onDisable` is `fold.stop()`; the plugin holds the single
  `registerEvents` edge. The vanilla-mechanic guard, the quit-cleanup sweep and the disable list
  are derived from module declarations; the triplicated mint surface collapses to ONE `Mintable`
  declaration per item type behind three derived views (the mint menu, `/se give <type>`, the
  `/se <type>` self-mint rows); and two boot-time static installers become instance wiring (the
  anti-cheat movement exemption rides `SinkEnv`; vanilla-enchant application becomes a
  `VanillaEnchants` instance). A `ModuleTreeGateTest` (structural) and a `RegistryWiringTest`
  (semantic golden orders) make off-registry wiring a build failure. Shipped behaviour is
  unchanged except two accepted micro-deltas (the derived `/se give` tab-suggestion order, and
  boot log-line ordering within `onEnable` — same lines, same tick).
- **`/se modules`** — a new operator command that lists every feature module in registry order
  with its toggle state and depth (boot vs live), wired listeners, dynamic commands, mint types,
  menus, swept player stores and disable stops (ADR-0047).

- **Era erasure for the legacy overlay (ADR-0044, internal — no behaviour change).** The
  same-FQN "whole-file swap" overlay twins are replaced by seam interfaces in `src/` plus
  era-exclusive `Modern*`/`Legacy*` implementations, so cross-era parity is a per-era `javac`
  fact. Exactly two composition-only bindings twins remain (`bootstrap.compat.EraBindings`,
  which absorbs the former Wiring/Bridges/Targets/Commands seams, and
  `platform.resolve.HandleLookups`); the three separate legacy per-tick gear polls unify into
  one `LegacyGearPoll`; and the Multi-Release soundness gate is now derived from the tree +
  module set + constant-pool proof (no hand allowlist), with a fast `EraTreeGateTest` running
  in every build. The shipped 1.8.9 and modern behaviour is unchanged.

- **Heroic damage folds additively (ADR-0037, supersedes ADR-0021).** A heroic piece's
  damage percent now joins the single additive fold like any enchant bonus, instead of a
  separate post-fold multiplicative stage; the `heroic.max-outgoing-factor` clamp is
  retired. Heroic stays "diamond-grade gear with a small damage bonus" — the diamond-grade
  half shipped earlier as ADR-0031 vanilla stats.

- **Effect execution is ~2× faster (ADR-0039).** The engine link-stages dense kind ids at
  snapshot publish (array dispatch replaces per-execution string lookups) and condition
  facts are demand-driven: per-space fact masks mean expensive facts — e.g. the per-hit
  nearby-entity scan — are only computed when some worn ability actually reads them.

- **Lore composes in one pass (ADR-0040).** Item lore renders from state through a single
  sectioned composer; the old distributed prefix-matching protocol survives only as a
  one-time migration shim, and feature services mutate state then recompose.

- **One error policy (ADR-0042).** Auto-reload and boot failures log real diagnostics with
  stack traces, loader IO follows one policy, the diagnostic code set is closed, and
  cross-region entity work goes through a guard helper instead of being silently swallowed
  on Paper.

- **Internals: era-core dedup, wiring records & duplication sweeps.** The legacy/modern
  dispatch twins now share one core (ADR-0036 — roughly a thousand hand-mirrored lines
  removed), the engine spine is wired through records, and a dozen one-concept-many-copies
  findings collapsed to single homes (percent clamps, random rolls, token expansion, colour
  translation, YAML hardening, book naming). No behaviour change.

- **Message catalogue and colour handling moved to `:platform` (ADR-0033 update).**
  `item.lang` becomes `platform.lang`, with one colour-code translator shared by every
  module.

- **Unified gear-apply gesture (ADR-0041).** The cursor-onto-gear apply gesture is now ONE
  shared template — `feature.apply.ApplyGestureListener` with a single `GestureOutcome` shape
  — that every family (scroll, holy scroll, nametag, crystal, slot orb, heroic, carrier, trak,
  godly-transmog) is a thin leaf of; the per-feature `*Result` records are gone. Give-with-overflow
  is one `platform.item.Inventories.giveOrDrop` helper, and result feedback flows through the
  `Messages` policy seam (`sendText`/`sendLines`). No gameplay behaviour changes beyond three
  accepted deltas: (1) apply and soul-mode feedback now honour the `messages.feedback` gate and
  apply PlaceholderAPI (commands stay exempt, so operators are never silenced); (2) the drag-apply
  no-slots line reads "This item has no free crystal slots ({MAX} max)."; (3) the crystal apply/extract
  lang keys moved from `apply.crystal.*` to `crystal.*` — customisers who overrode an `apply.crystal.*`
  key in their `lang.yml` must rename it to the matching `crystal.*` key.

- **Unified message catalogue — one source of truth (ADR-0033).** Player-facing chat
  messages (§L) were maintained in three hand-synced copies (a Java `Lang.defaults()` map,
  the shipped `lang.yml`, and a full cosmic-pack fork). They are now ONE bundled YAML —
  `se/compile/resources/lang.yml`, parsed by `Lang.defaults()` — with a user's on-disk
  `lang.yml` overlaid on it; the cosmic pack drops to an overlay of only the 5 soul-gem
  strings it re-themes. `CarrierService`'s book/dust/white-scroll outcomes now route through
  the catalogue (`carrier.*` / `white-scroll.*` keys) instead of hardcoded `§` literals, and
  `CrystalService` branches on a typed `ApplyResult.Reason` instead of sniffing rendered
  message text. A new `LangCatalogueDriftTest` fails the build if code references a key the
  catalogue lacks, so the drift can't return.

- **`applies-to` armour lists collapse to the `[ARMOR]` group.** The 11 content files
  that spelled out all four armour slots (`[HELMET, CHESTPLATE, LEGGINGS, BOOTS]`) now
  use `[ARMOR]` — the built-in group already resolves to exactly those four materials
  and already renders as the label "Armor", so the change is behaviour- and
  display-identical, just single-sourced.

### Fixed

- **Interaction-abuse pass — 40 player-exploitable bugs closed.** A parallel review swept every
  player-facing interaction surface for ways to dupe, cheat, or grief. Highlights: the
  Enchanter→Tinkerer infinite-XP printer and the Alchemist guaranteed-book launder; money/XP
  transfer effects minting from nothing; cooldowns and opponent-applied debuffs (teleblock/
  suppression) surviving a relog instead of being wiped; a non-atomic Folia cooldown that
  double-procced across regions; worn-state not refreshing on dual-wield / off-hand-to-chest /
  broken-weapon / same-slot swaps (stale bonuses, one item buffing two players); the Scarecrow
  temp-helmet dupe/destroy and free pumpkin; the default Holy White Scroll acting as a real
  Totem of Undying; holy-scroll death-stash duplication; timed FLY/INVINCIBLE/health buffs
  stranded by logout; temp-block harvesting/deletion and Folia revert loss; vanilla
  anvil/grindstone/smithing laundering plugin gear; whole-stack nametag renames; bounty/MINE
  paying for farmed kills and player-placed blocks; and RUN_COMMAND trusting a crafted player
  name. All fixed with unit coverage; the live Paper+Folia matrix is the pre-release gate.

- **The 1-block FOOTPRINT trail skipped blocks and cut corners.** The trail sampled the
  wearer's feet once per activation, so sprinting left gaps and diagonal movement stamped
  corner-touching blocks. A radius-0 `FOOTPRINT` now draws a 4-connected "snake": the sink
  interpolates a Bresenham staircase from the last stamped block to the current one (every
  consecutive block shares an edge), ground-snapping ±1 for stairs, skipping air (jumping
  pauses the trail), and restarting across teleports/world changes. No content or authoring
  surface change; larger radii and other shapes are untouched.

- **Compounding temp blocks stranded the wrong block on revert.** When two temporary-block
  placements overlapped one tile — the devil set's repeating NETHERRACK footprint walking over
  the Hell's Kitchen MAGMA_BLOCK floor — each placement captured whatever was currently there as
  its "original", so the last revert restored an intermediate temp block (the magma) permanently
  instead of the true ground (stone); the WALKER platform was worse, force-restoring its captured
  states over anything placed since. Both now route through one shared, per-position layered
  ledger (`TempBlockLedger`) that captures the true original exactly once, stacks overlapping
  placements as layers, coalesces same-material re-fires, drops the entry untouched if the world
  changed the tile, and restores the real original only when the last layer expires.

- **Cross-region reads in effect bodies (ADR-0043).** PARTICLE_RING, PARTICLE_LINE,
  WALKER, SPAWN_ENTITY and TELEPORT_BEHIND (plus TELEPORT `to: ACTOR` and VELOCITY `away`)
  read the acting player's live location inside `run()`; on Folia a combat activation
  executes on the victim's region thread, so a cross-region attacker (e.g. a projectile
  shooter) was an unguarded remote read. Kinds now declare the need on their spec and the
  executor captures an actor-origin snapshot (x/y/z, eye, yaw/pitch, world) on the firing
  thread at activation; `run()` reads only the snapshot, remaining per-target live reads are
  region-guarded and fail closed, and WALKER/ring geometry anchors where the actor stood when
  the trigger fired. The same guard now also covers four kinds that read a *resolved target's*
  live location — TEMP_BLOCK, EXPLODE, FALLING_BLOCK and MARK_ZONE — where a `@Attacker`
  selector on a DEFENSE trigger (e.g. the Yeti set's ice-pillar) resolves the remote shooter.

- **Per-player combat state is cleared on quit.** The quit-cleanup listener now covers
  every per-player engine store (several were missing, leaking entries until restart), and
  the dead `ChargeStore` is deleted.

- **Menu chat replies could render as `&c<key>?` markers.** Ten `menu.*` keys (mint /
  operator-console / sets / crystals) lived only in the shipped `lang.yml`, absent from the
  `Lang.defaults()` fallback, so a partial user file (or the unit-test fixture) showed raw
  key markers instead of text. Unifying the catalogue (ADR-0033) removes the split, and
  `soul.activate` / `soul.deactivate` / `soul.empty` are now consistently multi-line blocks
  (the shipped file had drifted to dead single-line forms). An applied cosmic-pack also no
  longer drops the `/se import` help line (its stale full-copy `command.usage` predated the
  feature).

## [1.1.4-beta] - 2026-06-27

### Added

- **Opened enchant books now show the full spec.** The general enchant-book likeness
  (`items/enchant-book.yml`) renders a bold tier-coloured `Name Level` (Roman or Arabic per
  `config.yml` `lore.roman`), the word-wrapped description, an `&a..% Success Rate` /
  `&c..% Failure Rate` pair, and the applies-to kinds grammatically joined (`Sword`,
  `Sword & Axe`, `Boots, Leggings, & Helmet`) plus an `Enchantment` suffix. New placeholders `{TIER_COLOR}`,
  `{SUCCESS}`, `{FAILURE}`, `{KINDS}`, a configurable `wrap` (chars-per-line, colour codes don't
  count toward width), and a colour-aware word-wrap (`item.render.TextWrap`).
- **`/se admin` is now a tier → enchant → level drill-down.** Click a rarity tier to see its
  enchants, click an enchant to see one book per level, click a level to receive that exact
  guaranteed book (the menu stays open to grab several).
- **Tab-completable enchant levels.** `/se give book <player> <enchant> [level]`, `/se book
  <enchant> [level]`, and `/se enchant <key> [level]` now suggest the chosen enchant's valid
  levels (1..max).
- **Level numeral can inherit the tier colour.** `config.yml` `lore.level-color: ""` (blank) makes
  an applied enchant's level render in the enchant's tier colour instead of a fixed colour; the
  `elite-enchantments` pack ships with it blank.

### Fixed

- **Migrated cooldowns were 20× too short.** EliteEnchantments / AdvancedEnchantments author
  cooldowns in *seconds*, but StarEnchants reads the `cooldown` knob in *ticks* — so e.g. Divine
  Immolation imported with a 2-tick cooldown instead of 2 seconds. The migrator now converts
  seconds → ticks (×20, like the REPEATING period), and all 96 shipped `elite-enchantments`
  cooldowns were corrected.
- **Soul gem (and unopened book) ignored the first right-click.** The interact listeners used
  `ignoreCancelled = true`, so a `RIGHT_CLICK_BLOCK` (which arrives cancelled by default-deny /
  protection) silently dropped the gesture until `/se soulmode` was run once. They now run at
  `LOW` priority and read the main-hand item directly, so the first right-click toggles soul mode
  (and opens an unopened book) reliably.
- **Enchant chat messages showed raw `&` codes.** The `MESSAGE` effect's chat / actionbar / title
  output now translates legacy `&` colour codes to `§` (both the modern and 1.8.9 overlays), so a
  proc message renders coloured instead of printing literal `&c&l…`.

## [1.1.3-beta] - 2026-06-27

### Added

- **Enchant descriptions now render on the enchant book.** The general enchant-book likeness
  (`items/enchant-book.yml`) gained a `{DESCRIPTION}` placeholder that expands to the enchant's own
  description — one lore line per description line — so an unapplied book shows what it does, not
  just how to apply it.

### Fixed

- **Tier colours and multi-line descriptions in the lore (EE pack).** Enchant and crystal names in
  the browse/apply/admin GUIs now render in their rarity-tier colour (epic, legendary, …), matching
  the applied-gear lore. Multi-line descriptions now render as multiple lore lines everywhere
  instead of being crammed onto one line: the importers (EliteEnchantments + AdvancedEnchantments)
  join each source description line with a newline rather than a space, the migrator writes them as
  a readable YAML list, and every render site (menu icons + the enchant book) splits on the newline
  (item lore is a list of lines, so an embedded `\n` does not render as a break across the version
  range). All 122 shipped `elite-enchantments` descriptions were regenerated into the multi-line
  form.

## [1.1.2-beta] - 2026-06-26

### Fixed

- **Shipped `elite-enchantments` pack erroring on every affected enchant.** The EE port carried two
  handle tokens no live server can resolve, so every enchant using them logged `E_UNKNOWN_HANDLE`
  and lost the effect: the EE-only `BLEED` particle (no Minecraft equivalent) and the pre-1.13
  `ENDERDRAGON_GROWL` sound. `BLEED` now maps to the real `DAMAGE_INDICATOR` particle (in the pack
  and in the migrator's particle vocabulary, so re-imports stay clean), and `ENDERDRAGON_GROWL` →
  `ENTITY_ENDER_DRAGON_GROWL` is registered in the cross-version `Aliases` (with the `SMOKE_LARGE`/
  `SMOKE_NORMAL` particle renames the same EE vocabulary uses). The `ElitePackValidationTest` now
  resolves material/sound/particle/entity/attribute tokens *strictly* against the floor (1.17.1)
  Bukkit enums, so an unresolvable handle in the shipped pack fails `./gradlew build` instead of
  surfacing only at runtime.

## [1.1.1-beta] - 2026-06-26

### Changed

- **One jar for every version.** Minecraft 1.8.9 support now ships *inside* the single
  `StarEnchants-<version>.jar` as a Multi-Release JAR (base = legacy Java-8/v52 tree,
  `META-INF/versions/17/` = modern Java-17/v61 tree, merged by `scripts/build-mega-jar.sh`):
  a 1.8.x server's JVM loads the v52 tree automatically, a 1.17.1+ JVM loads the v61 tree.
  The separate `StarEnchants-<version>-1.8.9.jar` release asset is gone — `release.yml` now
  publishes exactly one jar. Verified live by booting the same jar on craftbukkit-1.8.8
  (JDK 8), Paper 1.17.1 (JDK 17), and Paper 26.1.2 (JDK 25) via `scripts/mega-smoke.sh`.
- **Order-independent cross-version build.** `-Pse.target=legacy` now compiles into a separate
  `build-legacy/` directory, so the modern and legacy trees can never collide — no clobbered
  jar, no overlay-swap incremental contamination, no build-order dependency. `build-mega-jar.sh`
  enforces a soundness gate that refuses to merge any module whose two trees diverge in class
  set (only the plugin qualifies; the era-specific tester stays two artifacts).

### Fixed

- **1.8 empty-hand condition facts.** The legacy main-hand read NPE'd for an empty-handed
  entity (1.8 `getItemInHand()` returns null where modern returns AIR), silently corrupting
  the `helditem` / `actor.type` condition facts; it now normalizes to AIR to match the modern
  path.
- **Test-gate jar selection.** `legacy-smoke.sh` and `run-matrix.sh` now pin the tester jar by
  the canonical project version — a `find | head -1` could pick a stale older-version jar (a
  false PASS) — and guard an empty-array expansion under `set -u` on non-arm64 macOS.

## [1.1.0-beta] - 2026-06-26

### Added

- **Optional Minecraft 1.8.9 jar** — the whole engine, built from the same source
  via the `-Pse.target=legacy` overlay and lowered to Java 8, shipped as a separate
  `StarEnchants-<version>-1.8.9.jar` release asset. Includes a `v1_8_R3` fake-player
  smoke harness (8/8 live on a real 1.8.8 server under JDK 8), full §6 degrade parity
  (ITEM_DAMAGE / heroic-durability / instant-armour-refresh polls + a real NMS
  knockback-resistance hook), and the legacy sound/particle/material resolver fixes.
  The floor stays 1.17.1 — the 1.8.9 jar is optional and separate
  (docs/legacy-1.8.9-codeshare-design.md, and the Legacy 1.8.9 page on the docs site).
- **CI gate for the 1.8.9 lane** — `.github/workflows/legacy.yml` compiles
  craftbukkit-1.8.8 on the runner (Spigot BuildTools, cached) and runs the live JDK-8
  smoke on every push/PR; `release.yml` runs the same gate and publishes the 1.8.9
  asset only when it is green (§11 ownership made mechanical).

### Fixed

- The per-activation chance roll used a `ThreadLocalRandom` overload JvmDowngrader
  cannot stub for Java 8 (it resolves through the JDK-17 `RandomGenerator` interface),
  which would have thrown on every proc on the 1.8 jar; switched to a downgrade-safe
  form, identical on the modern range.

### Removed

- The empty `compat-modern` placeholder module (no sources, no consumers).

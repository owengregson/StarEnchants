# Changelog

All notable changes to StarEnchants are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning: [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed

- **Bat swarm rework.** Summoned bats now cloud the vision of whoever attacked you most recently —
  orbiting the two-block pillar directly in front of the attacker's face and tracking them as they
  move and turn (within 16 blocks, last 10 seconds, most recent attacker wins; with no such attacker
  they flutter with vanilla AI exactly as before). Swarm spawn heights now scatter ±0.6 blocks
  around the ring.

## [1.9.0-beta] — 2026-07-17

### Added

- **Five new pets.** Sheep (passive: fall damage reduced 25/50/75%, full immunity at level 100), Kraken
  (passive: drowning immunity at every level, plus 10–25% faster swimming on 1.21+ servers), Bat (active:
  a ring of 10–40 vanilla bats bursts from your chest as a 15-second concealment swarm), Owl (active:
  sweeps a 10–20 block radius, revealing nearby players and locking their Invisibility out for 8–15s),
  and Mole (active: sneak-click digs a temporary home — marked by a pulsing ring and a tracer line in the
  pet's colour — then click again within 50 blocks and 30 seconds to instantly return; Teleblock counters it).
- **Pet leveling.** Every pet's name now carries a live `[Lvl. N]` suffix; leveling up plays a sound and
  particle cue. Active pets earn XP on each successful use; every pet slowly levels while carried in your
  inventory (passive pets earn double in the hotbar). One hour carried ≈ half a level.
- **Ice Aspect** (epic, swords, 3 levels): a 50/75/100% chance on hit to freeze the victim solid for 3/4/5
  seconds — blue hearts and the powder-snow chill, working even while they burn — dealing fire-tick-cadence
  damage at double strength and slowing them 5%. 10-second cooldown.
- **Wind and Bolt crystals** (both stackable): Wind adds +1% outgoing damage per copy; Bolt makes your
  enchants' lightning strikes hit 10% harder per copy.
- **Hex colours everywhere.** Every name, lore line, and colour field across the pack now accepts
  `{#RRGGBB}` tokens (1.8.9 clients get the nearest classic colour automatically).

### Changed

- **A fresh coat of paint.** New hex colour palettes for all 14 masks, the pet roster, 13 armor sets, and
  all 7 crystals, plus recoloured likenesses for a dozen items (slot orb, heroic ingot, crystal extractor,
  black scroll, randomizer dust, transmogs, nametag, magic dust, and the four trak gems). The Iron Golem
  pet is now **Golem**; the Supreme set is now **Legacy**. The black scroll is dried kelp and the transmog
  scroll a book.
- **Durability enchants consolidated to four pillars**: Demonforged (attack armor), Reforged (your tool),
  Hardened (your armor), and Soul Hardened — retiered from mythic to soul as the soul-fueled pillar.
- **Enchant tiers rebalanced.** The tiers below mythic now hold an even ~16 enchants each (they ranged
  from 6 to 27); five enchants moved up in tier, thirty-eight settled down.
- **Permafrost** cools five times slower (4m cooldown) and its frost floor thaws in 5 seconds; the name
  reads "Permafrost".
- **Ethereal Dodge** no longer negates fall damage — the attack dodge and Speed burst are unchanged.

### Removed

- Eleven enchants leave the pack: Reflective Block, Titan Trap, Bloody Deep Wounds, Perfect Solitude,
  Unstoppable Momentum, Extreme Insanity, Mighty Cactus, Eagle Eye, Immortal, Blacksmith, and Master
  Blacksmith. Items already carrying them keep working; the dead line renders as "Unknown Enchant" and
  stale books still salvage at the Tinkerer.

### Fixed

- **The enchant-activate sound no longer double-plays on a hit.** The doubling never needed a second damage
  event: one swing could legitimately emit the same cue twice — sibling enchants sharing an identical
  authored sound (Lifesteal + Chain Lifesteal both proc'ing one hit is the same witch-drink twice, and the
  Deathbringer and Rot And Decay families pair up the same way), a Double Strike echo re-firing a
  cooldown-free enchant like Execute in both passes, and Thundering Blow authoring the very thunder its real
  lightning bolt already plays. The engine now plays each sound at most once per hit (a different sound on
  the same hit, and the same sound on the next hit, still play), and Thundering Blow's redundant thunder
  line is gone (ADR-0066).
- **Enchants now fire for every real hit.** The 1.8.8 once-per-hit guard keyed on the victim's
  invulnerability window, so a second attacker's blows — or any hit landing while the victim burned, bled,
  or stood in poison — silently skipped ALL enchant processing on both sides, and rage stacks
  double-advanced on crits. Hits are now deduplicated by identity: same swing skips, everything else procs.
- **Masked helmets can no longer be bricked.** Removing a crystal (or any modification) while wearing a
  masked helmet in creative could overwrite the real helmet with its cosmetic disguise — permanently. The
  disguise now carries the real helmet inside it, write-backs are denied and repaired, and creative
  players always see the true item. Grindstones and anvils also now refuse masked/crystal gear instead of
  silently destroying the components.
- The mole pet's dig sound was silent on 1.8.9.

### Changed (audio)

- **Rage's combo-break cue** is now a layered pair — a bone-block crack under a high item snap — replacing
  the blaze death screech.

## [1.8.8-beta] — 2026-07-15

### Fixed

- **Masks break with the helmet, and the mask pops off.** A mask worn on a helmet used to leave the spent helmet
  stuck at zero durability with the mask on it. Now a masked helmet breaks like any helmet when its durability
  runs out, and the mask pops back off intact into your inventory — or drops at your feet if it's full. Heroic
  helmets keep their durability protection; both normal and Heroic helmets break at zero. (Modern servers; the
  1.8.9 lane is a follow-up.)

- **Enchant sounds no longer double-play on crits.** When a hit is "upgraded" inside the target's invulnerability
  window — a critical hit on the same swing, or a faster weapon landing again — the game fires a second damage
  event for that same hit, and enchants (and their sounds) were proccing twice. Enchants and sounds now fire
  exactly once per hit.

- **Silence no longer disables permanent potion buffs.** Silence and its derivatives (Solitude, Perfect
  Solitude) disable a target's enchants briefly, but this also stripped their permanent while-worn buffs — e.g.
  Overload's max-health could drop mid-fight and kill them. The permanent-buff enchants (Overload, Godly
  Overload, Springs, Aquatic, Glowing, Obsidian Shield, Gears) are now immune to being silenced via a per-enchant
  `suppress-immune` flag; the target's OTHER enchants are still silenced normally.

## [1.8.7-beta] — 2026-07-14

### Fixed

- **Taking a hit now breaks your rage combo (ADR-0058).** Rage is a combo you keep by NOT being hit, but the run
  was only ever broken by your own actions (switching target or letting the combo window lapse) — taking a hit
  did nothing, so you could ride a high rage combo straight through the blows you were trading. Any hit you take
  now — from a player, a mob, or a projectile — resets your rage stacks to zero, and you rebuild from there.
  (Environmental damage like fall/fire and engine-issued damage-over-time ticks do not count; only a real hit
  from something does.)

### Added

- **A direct command for every player menu.** So players don't need the admin-gated `/se menu …`: `/enchants`
  and `/enchanter` open the enchanter, `/pets`, `/masks`, `/tinkerer`, `/alchemist`, `/sets`, `/crystals`, and
  `/catalogue` (the Enchant Catalogue) open their menus. All are permission-free (`starenchants.use`, default
  true), like `/enchants` already was. These replace the old `/enchants`→hub launcher, so every bench and
  browser the hub reached keeps a direct command; the hub itself remains at `/se menu hub`.

## [1.8.6-beta] — 2026-07-14

### Fixed

- **Rage stacks now count only hits dealt WITH a rage weapon in the combo.** The stack count reused the shared
  `%combo%` streak (every melee swing, any weapon), so drawing a rage weapon mid-combo instantly inherited the
  stacks earned by plain-weapon hits. It now advances only on a rage-carrying hit, using the combo streak solely
  as the new-combo signal.
- **A mask/pet head can be shift-clicked into the hotbar again.** The helmet-slot guard cancelled the shift-click
  outright to stop the auto-equip, but that also killed the legitimate hotbar↔storage move (the head could not
  be shift-clicked into the hotbar at all). It now REDIRECTS the head into the other inventory section instead of
  the helmet, so shift-click works everywhere except onto the head slot; the crafting grid, result, and off-hand
  are left to vanilla.

## [1.8.5-beta] — 2026-07-12

### Fixed

- **Overhealth-removal abilities now remove overhealth from EVERY source — not just base hearts (ADR-0057).**
  The Grim pet's trade and Cupid's Lovestruck ("remove your/your victim's overhealth") measured the extra
  hearts from the max-health attribute's BASE value, but every real source of overhealth in the pack lives in
  a MODIFIER: a `HEALTH_BOOST` potion (Overload / Godly Overload, the Nature Crystal) and the worn "+hearts"
  channel are attribute modifiers, invisible to a base read. So the ability played its sound and burned its
  cooldown while the hearts stayed. The drain now measures the EFFECTIVE max (base + modifiers) and removes the
  overhealth with a temporary negative max-health modifier, so it takes hearts from a HEALTH_BOOST potion, a
  worn +hearts modifier, or a base shift alike — and it can no longer be capped short by a large boost. The
  reduction is restored when the window ends and, if you log out mid-window, given back before your data saves
  (never made permanent, never left stranded on disk).
- **Potion lock now truly PREVENTS the effect instead of chasing it (ADR-0057).** A locked potion (Grim's
  Overload lock, druid's and fantasy's Speed lock, clarity's Blindness) was stripped and re-stripped every
  tick — but a worn passive re-applies its buff on its own cadence, so the effect flashed back in on the tick
  it was re-applied and out on the next strip ("the overhealth comes back glitching in then goes away"). The
  lock now cancels the re-application outright, so the buff can never re-establish during the window. (On
  1.8.9, which lacks the needed event, the per-tick re-strip still enforces it.)

## [1.8.4-beta] — 2026-07-12

### Fixed

- **Masks, pets and every SE custom item can no longer be placed as a block — cleanly, with
  no "placed then refunded" flicker (ADR-0056).** The placement was already cancelled outright
  for every plugin-item family (the 1.8.1 guard), but on the older-protocol lanes (notably 1.8.9)
  the client's predicted held-count was never resynced on the cancel, so the item visibly
  decremented and then reappeared. The guard now resyncs the held slot the moment it cancels, so
  the item simply "is not a placeable block" — no ghost that sticks, no refund cycle. Fires only
  on the cancelled place of a plugin item; a harmless no-op on the modern ack-driven lanes.
- **Masks and pets can no longer be worn in the helmet slot — and on 1.21.2+ the client refuses
  the slot itself (ADR-0056).** A mask activates only APPLIED ONTO a helmet (its drag gesture),
  a pet from the HOTBAR, so a raw head in the helmet slot grants nothing and bypasses the pet
  contract. A new always-on server guard cancels every inventory route the head could reach the
  slot by — direct place, shift-click auto-equip, hotbar number-key swap, and (modern-only)
  dispenser-equip; right-click auto-equip was already suppressed as a plugin-item vanilla use.
  On 1.21.2+ the head's `equippable` data component is overridden to a non-head slot at mint, so
  vanilla's own armour-slot check refuses the helmet client-side, no server round-trip. Below
  that era the server guard carries it alone. An UNEQUIP is left alone, so a head slotted before
  this shipped can still come off.
- **The pet EXP bar's trailing pad is restored at max level.** The level-capped bar dropped the
  one trailing space between its last filled square and the closing bracket, so max-level pets
  read tighter than every other level. The full bar now carries the same right-hand pad the
  partial and empty bars show (`&a■ ■ … ■ &7`), so the meter's shape is uniform across all levels.

### Docs

- **ADR-0056** records the decision that SE cosmetic head items do only their intended action —
  the placement resync, the server-side helmet-equip guard + dispenser seam, and the 1.21.2+
  client-side `equippable`-component strip (with the era bounds and the wholesale-override
  rationale over component removal).

## [1.8.3-beta] — 2026-07-12

### Fixed

- **Same-hit rider damage is restored to the pre-1.8.2 meta (ADR-0055).** 1.8.2's fold
  routing sent zero-delay victim-aimed `DAMAGE` riders through the flat bucket, which
  `combat.attack-scale` multiplies (5.0 in the cosmic pack) — riders landed **~5×
  harder** than the numbers they were balanced at (shipped content affected: Sniper
  and Lethal Sniper's bow procs). Riders now land in a dedicated EFFECTIVE bucket:
  the authored amount is exactly what the victim's health pool sees pre-armor — never
  attack-scaled, never priced by the defense terms — precisely what the pre-1.8.2
  bare hurt delivered. Everything 1.8.2 fixed stays fixed (one hurt, one immunity
  window, attribution, the re-entrancy frame); the percent/flat economy, the caps and
  `attack-scale` itself are untouched. `/se damagedebug` now prints the rider bucket
  separately (`eff`).
- **The "missing required data class" dispatch-flush console spam is gone.**
  Which particles REQUIRE data moves between versions (`DUST` always did;
  `ENTITY_EFFECT` gained a required `Color` at 1.20.5; the 1.21.9 line made
  `EFFECT`/`INSTANT_EFFECT` take `Particle.Spell` and `DRAGON_BREATH` a power
  float). Content authors only the particle NAME, so the sink and the fx path
  now supply each version's demanded data with a neutral default (white
  dust/spell, stone block, …) instead of throwing into the flush; a data type
  with no sensible default (a `Vibration` needs a destination) skips its burst
  with a once-per-type warning. A new live suite sweeps the running version's
  whole particle registry so future data-requirement drift fails the matrix,
  not a production console.
- **The mask illusion head now mirrors the real helmet's durability, live.**
  The shown head carries the helmet's `max_damage` + `damage` components
  (explicit heroic max-durability wins over the material default), so the
  wearer's helmet slot — and client-side durability-display mods — read the
  real bar; every durability tick re-derives the dress through the existing
  armour-change refresh, so it tracks in realtime. 1.20.5+ (older versions
  cannot put a durability bar on a head and degrade to the bare dress; 1.8
  skulls never render one).

### Changed

- **Wither mask recolored** from black (`&0`) to dark gray (`&8`) — name and
  description bullet both.

### Docs

- **ADR-0055** records the owner's clarified tuning ground truth (the pre-1.8.2
  numbers are the balanced meta; authored rider numbers are effective units), the
  combat economy's three scaling classes (percent economy / effective riders /
  separate attributed instances), and the full category-by-category 1.8.3 balance
  audit (enchants, masks, pets, sets, crystals, heroic) — every damage path
  classified and diffed against the 1.8.1-beta baseline.

## [1.8.2-beta] — 2026-07-12

### Fixed

- **Same-hit bonus damage no longer eats the attacker's next melee (ADR-0054).** A
  zero-delay `DAMAGE`/`MODIFY_HEALTH take` aimed at the current hit's victim used to
  land as a bare second hurt, re-arming vanilla's immunity window
  (`noDamageTicks`/`lastHurt`) — the very next melee inside that window was silently
  window-rejected with no event at all, so other plugins' hit handling (custom hit
  sounds, damage indicators, knockback delivery) skipped a hit that visibly
  connected. Such riders now join the damage fold and ride the one event: one hurt,
  one immunity window, one knockback — and a rider on a dodged hit dies with its hit.

### Changed

- **Separate damage procs are attributed (ADR-0054).** Bleed-style delayed DoT
  ticks, `LIGHTNING` bolt damage, Hex reflects and Vengeful Diminish overflow now
  apply as `damage(amount, attacker)` — a real attributed
  `EntityDamageByEntityEvent` — so kill credit resolves and downstream combat
  plugins can see whose damage it is. SE's own procs still never chain off SE's own
  damage (the new `EngineDamage` frame preserves the old structural guard), rage
  never builds off DoT ticks, and a blanket `IMMUNE all` still blocks engine damage
  exactly as before.

## [1.8.1-beta] — 2026-07-11

### Fixed

- **The 1.8.0 worn heart bonus was silently dead on 1.21.3+/26.x** (a Nature
  crystal alone changed nothing). The sink resolves its implicit max-health
  attribute by NAME, and the runtime name-lookup skipped the alias chain the
  compiler uses — the 1.21.3 registry rename (`GENERIC_MAX_HEALTH` →
  `max_health`) made it resolve null and the modifier write no-op. The runtime
  lookup now walks the same alias map both directions (this also revives
  `MAX_HEALTH_DRAIN` — Grim/Cupid — on those versions). Live-pinned by a new
  Paper suite (reproduced the failure on 1.21.11/26.1.2, green after the fix)
  and a 1.8.8 smoke check, so a silent attribute-rename break can't ship again.
- **Cage-style pets no longer burn their cooldown on an unsafe volume.** The
  right-click now pre-checks the cage's would-be volume (the same shared
  geometry the build uses) and answers with the normal "You cannot use …"
  fail message instead of consuming the cooldown; on Folia an unreadable
  cross-region volume falls back to the old behavior.
- **Plugin head items (pets, masks) can no longer be placed as blocks** — the
  vanilla-mechanic guard now cancels the placement outright (the old
  interact-deny didn't cover placeable materials).

### Added

- **Masks browser menu** — the pets-style GUI (`/enchants` hub + operator
  console tiles): every mask rendered as its real minted head; operators mint
  from it, everyone else browses.
- **The worn armor slot now hovers like the real helmet.** The client-side
  repaint head carries the helmet's name (or its friendly material name), lore,
  enchant lines + glint, and the vanilla "When on Head" attribute block —
  heroic stats included (1.17.1 shows explicit stats only; 1.8 has no attribute
  tooltips).

### Changed

- **Mask likeness polish:** descriptor bullets are no longer bold; the bonus
  header upper-cases the mask name (`AGENT MASK BONUS` — the set-bonus
  convention).
- **Monopoly calibrated for the pack's attack economy:** authored `amount: 1`
  now lands as the advertised +5% under `combat.attack-scale 5.0` (it was
  landing as +25%). Knight's defense-side 5% was already true (reduction is
  not fold-scaled).
- **Pet exp bar:** a full bar renders flush against its closing bracket (no
  stray pad at level 100).
- **Nightmare pet:** the summoned horse gallops at 2× vanilla base speed (new
  `SPAWN_ENTITY` `speed` multiplier, era-complete).

## [1.8.0-beta] — 2026-07-11

### Added

- **Pets (ADR-0052).** A leveling textured-head content family — the sixth effect
  source. PASSIVE pets work from anywhere in the hotbar; ACTIVE pets fire on
  right-click (full gate sequence, pet-wide cooldown scope) and may open a timed
  armed window whose combat riders join the worn state. Pets level from mob kills,
  vanilla XP, time held, and a drag-applied Pet Food item; ability strength scales
  in sparse authored level brackets. Ships 16 cosmic pets, the pets browser +
  admin level-drill menus, mint-console tiles, and `/se give pet` / `petfood`.
  New engine surface: `CAGE`, `STRIP_SCROLL`, `TEMP_BLOCK` shape `BOX`,
  `MODIFY_MONEY` mode `interest_percent`, `SPAWN_ENTITY` summon flags, the
  `%actor.belowvictim%` fact, and the `TexturedHeads` era seam (1.8-safe heads).
- **Masks (ADR-0053).** A helmet-only applicable head-likeness family. A mask
  drag-applies onto a HELMET exclusively (one per helmet), grants its abilities
  while worn, and **overrides the helmet's WORN appearance** with the mask's
  player head for the wearer and every observer — the inventory item is never
  touched; right-click the masked helmet to pop the mask back off intact. The
  repaint is a client-only equipment override behind a new era seam (modern
  `sendEquipmentChange`, 1.8.9 equipment packet; on Paper 1.17.1 exactly the
  visual override is inert — abilities still work). Ships 14 cosmic masks
  (Agent, Shaman, Chef, Wither, Fisherman, Ghost, Hacker, Medic, Angel, Midas,
  Santa, Monopoly, Knight, Blaze), the "Mask Equipped" gear line below the
  crystal line, and `/se give mask` + mint tiles. New engine surface: `WARD`
  guard flags (mob-target calm, /invsee shield, the owned `/near` interception —
  configurable and opt-out via `masks.near-commands: []` — and the splash-heal
  boost), `IMMUNE` type `fishhook` (rods no longer knock warded players back),
  `SUPPRESS scope: KIND` (disable abilities by effect kind — Chef turns off ANY
  hunger-restoring enchant, present or future), and `IGNORE_HEROIC` (Midas
  negates the enemy's heroic armor share of the damage fold).

### Changed

- **Bonus max-health no longer rides the `HEALTH_BOOST` potion (except Overload).**
  Two sources maintaining the same potion type clobber each other — a Nature
  crystal's two hearts vanished under a Godly Overload because the higher
  amplifier wins the pool. Every worn heart bonus except Overload/Godly Overload
  (which keep the potion pool by design — Grim strips it via `POTION_LOCK`) now
  rides ONE plugin-owned max-health attribute modifier, reconciled from live worn
  state on every refresh: sources **add** instead of clobbering, suppression
  windows drop exactly their source, nothing can milk it away, and a crash or
  relog can neither stack nor strand hearts (the modifier is set by identity,
  never added). Nature crystal and the Santa mask are migrated; authoring stays
  one line: `{ HEALTH: { amount: 4 } }` on PASSIVE.
- **Multi-material floors are per-block noise now.** Mixed-palette `TEMP_BLOCK`
  floors (devil magma/netherrack et al.) picked materials in connected
  `palette-scale` patches; they now pick per block from the palette (still
  deterministic per coordinate, so re-assertions never flicker). The
  `palette-scale` param is removed.
- **Cat pet regeneration buffed** to Regeneration III / IV / V / VI across its
  level brackets.

### Fixed

- **Every shipped `SUPPRESS` op was silently dead on live servers.** The compiler
  erased the op's `mode` as a string while the runtime reads an int, so each
  activation threw inside the executor's per-effect guard and no-oped — Sabotage,
  Soul Trap, Phantom and Corrupt never actually suppressed anything (unit tables
  masked it by feeding already-typed args). `mode` now lowers to its wire ordinal
  at compile, pinned by an erase-stage test and the fuzz bridge.

## [1.7.4-beta] — 2026-07-11

### Fixed

- **"Half-death" — players no longer get healed into a ghost state on the tick they
  die (ADR-0051).** All enchantment heals were scheduled and landed *after* the hit's
  damage applied — so on a lethal hit the death event fired first and the heal then
  set health on the corpse, leaving a dead player holding health (death screen over a
  live health bar; client and server desynced until relog). Two kernel rules now make
  heal/death ordering coherent:
  - **Same-hit heals join the kill decision.** A defensive self-heal proc'd by the hit
    (Phoenix, Death God, Ender Walker) now lands *inside* the damage event, before the
    server decides life or death — so "a blow that would kill you instead restores
    health" genuinely saves you, and a blow that beats the heal kills you cleanly. The
    net outcome (damage minus healing) is what determines death, computed in
    health-space so era-combat damage rewrites can't distort the heal's value.
  - **The dead stay dead.** Any heal that would land after its target already died —
    a delayed (`WAIT`) heal, lifesteal onto an attacker who was killed in the
    meantime — is dropped at execution time instead of resurrecting the corpse.

### Added

- **Live death-race suite.** The Paper+Folia matrix now stages both orders against a
  real vanilla kill decision (a lethal hit with a same-hit save → survives with no
  death event; a heal arriving after a real death → exactly one death, no revival).

## [1.7.3-beta] — 2026-07-10

### Fixed

- **Rage (and Armored) were locked out by their own group-mates.** Gate 6 armed an
  activating enchant's cooldown on all three scopes, so any cooldown-carrying
  legendary (Lifesteal, Silence, Double Strike…) armed the whole `legendary` GROUP
  and every cooldown-0 same-group enchant — Rage's damage, Armored's reduction — was
  refused for the sibling's entire cooldown, near-permanently on a god kit. Cooldowns
  now arm and check the ENCHANT scope only; group/type ids remain suppression match
  keys. This was the primary reason Rage folded zero damage all fight while its
  stack readout climbed, and why the defense reduction flapped between 14% and 5%.
- **Rage stacks no longer build on cancelled attacks.** The combo streak advanced at
  HIGH before a defender's Dodge/Inversion CANCEL was known and was never rolled
  back, so blocked swings still fed `%combo%` and the rage ladder. A cancelled hit
  now reverts the streak advance (and its window refresh) entirely.
- **The rage stack readout renders on the action bar again.** The reflective
  action-bar send resolved `sendMessage` on the runtime `CraftPlayer$…` anonymous
  class (package-private on every modern Paper), threw `IllegalAccessException`, and
  silently degraded to chat. The lookup now targets the public `Player$Spigot` API
  type. The readout also freezes while your Rage is suppressed by Silence-family
  enchants — the display now always matches what the fold will pay.
- **`/se damagedebug` prints the health actually lost.** Each line now ends with
  `hits N` (`event.getFinalDamage()` after our commit — the post-armor truth) and a
  `CANCELLED` marker on negated hits, closing the calibration loop between the
  fold's commit and what players experience.

### Changed

- **The D-space combat economy (ADR-0050 R4).** The damage pipeline was read from
  Mental's source instead of inferred: Mental mints the hit (8 + 6.25 era Sharp V =
  14.25 raw), applies era armor at LOWEST (an untouched god hit lands 0.57 hp), and
  our HIGH commit is redistributed by the frozen vanilla-modern armor curve —
  quadratic in the committed value, deterministic, with bare second-call DAMAGE
  effects (bleed ticks, AoE, thorns) reduced to ~4% in PvP against god armor.
  Every combat value is re-budgeted against that measured curve, Monte-Carlo
  verified: Fantasy-set mirror ≈ 5–7 landed hits, general god kits 7–9, budget kits
  ~13, per-hit texture 5.6 → 11.7 hp with no one-shots. Highlights: attack flats
  ×1.7 (Shadow Assassin → 1.45, Execute → 1.0), Rage → 1.5–3%/stack + 0.06–0.16
  flat/stack, heroic weapon 10% → 20% / heroic armor 3% → 2% per piece, Silence and
  Perfect Solitude become momentary windows (1.5–4 s) instead of near-permanent
  kit shutdowns, Inversion/Ethereal Dodge negate ≤ ~10% of swings combined, Voodoo's
  WEAKEN tops at 12%, Ender Walker/Phoenix sustain trimmed, `max-bonus-damage`
  6.0 → 0.70 as a real one-shot backstop.
- **Era-hostile and degenerate lines rewritten.** Drunk and Berserk trade vanilla
  Strength (which Mental's 1.8 potion math would multiply into the minted base) for
  additive fold bonuses; Mortal Coil's set-health-to-12 only fires as a finisher
  (target ≤ 40% health, chance 8%, 60 s cooldown); Dominate's WEAKEN drops to a 25%
  proc; Divine Immolation's every-2s wither/fire chip becomes a 30% proc.

## [1.7.2-beta] — 2026-07-10

### Changed

- **Flat-forward attack economy (ADR-0050 R3).** The v1.7.1 playtest showed Mental
  reduces damage before our fold reads the event: percent bonuses multiplied an
  already-crushed ~1 hp base (hits stuck at ~2 hearts) while flat damage landed raw
  (Rage's rider alone scaled to 40 hearts). Every attack-side enchant contribution now
  ships as raw-hp `flat` values with percents demoted to seasoning: Shadow
  Assassin/Assassin become the always-on workhorses (~2–3 hp at melee range), Rage
  compresses to +3–8% and +0.04–0.10 hp per stack (full combo ≈ +5.6 hp instead of an
  instant kill), and the finisher/proc family (Execute, Deathbringer, Planetary
  Deathbringer, Rogue, Enrage, Furious Enrage, Bloody Deep Wounds, the Insanity
  family, Barbarian) lands in a +1 to +3.5 hp band with the deathbringers on 8–10 s
  rhythms instead of 25–30 s one-shots. Sets, crystals, and heroic stay percent and
  untouched — the set weapon line holds its 15–17% damage share and its lore stays
  true. Projected: ~3-heart base hits, consistently; ~12 hp at a full 6-stack combo;
  6–8 hit god-kit TTK.
- **Combat noise sweep.** Enchants whose cue could fire on effectively every hit lost
  their sounds (the armor-equip family, the Insanity screams, Assassin/Shadow
  Assassin/Rogue, Heavy/Plated Heavy anvils, Death Pact, Soul Hardened, Piercing,
  Shackle III, and the always-on roars on Barbarian/Enrage/Furious Enrage); Berserk's
  ravager roar is now a wolf growl and Ender Shift's dragon growl an enderman
  teleport. The rage break cue plays at half volume.

### Added

- **`/se damagedebug`** — toggle a per-hit damage-fold readout (the base the fold saw,
  summed percents and flats, the attack scale, the committed result) for every hit you
  land or take. Built to verify the combat pipeline on a live server empirically.

### Fixed

- **Inversion cancelled the holder's own attack.** It was authored on the ATTACK
  trigger, so its CANCEL negated the hit the sword holder dealt. It now rides the
  DEFENSE trigger like the other held-sword defensive enchants and negates the
  incoming hit (healing the holder), as described.
- **Rage's BROKEN flash was a title.** Both rage messages — the stack counter and the
  break flash — now render on the action bar.

## [1.7.1-beta] — 2026-07-10

### Changed

- **Cosmic-pack rebalance for 1.8 combat (ADR-0050 R1+R2).** Live god-kit fights landed
  0.5–1 hp per hit — the old anchor had priced in a Rage that was actually inert (see
  Fixed), and the deployment target is Mental's full 1.8 preset, whose flat armor +
  Prot IV pipeline passes only ~5% of a hit through. The economy is rebuilt in two
  layers. Content stays on a normalized human scale: Rage becomes the centerpiece at
  +20–45% per stack plus a flat rider, Execute finishes for up to +50% below 25%
  health, the Insanity/Barbarian/Assassin/Rogue/Enrage families scale ~2.5–3×, the
  outgoing cap rises to +600%, and set weapon bonuses deliberately stay at 20–30 (a
  ~13–18% share of mid-fight damage — the enchants carry the rest). The new engine
  knob `combat.attack-scale` then adapts that whole custom attack side to the server's
  armor pipeline in one place (post-cap, percent + flat, never the base hit or the
  defense side); the pack ships `5.0` for Mental-1.8 armor, landing ~11.5 hp per
  full-combo hit against light-defense armor and a 6–7 hit mirror-god TTK. Vanilla
  Weakness potions aimed at combatants (Voodoo, Unfocus, the Reaper blade) become
  non-stacking `WEAKEN` percents inside the fold; low-health conditions now compare
  `healthpercent` so finishers and desperation procs fire on boosted HP pools; and the
  Overload family's Health Boost climbs to a 26-heart pool at Godly Overload III — the
  HP-pool meta the new scale assumes.

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

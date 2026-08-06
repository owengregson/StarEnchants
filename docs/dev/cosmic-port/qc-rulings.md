# Cosmic-port QC rulings (owner, 2026-08-05)

The owner answered the full post-port QC questionnaire interactively. These rulings
govern the QC pass and every follow-up wave. Ruling ids are `R-QC<n>`, keyed to the
questionnaire numbers. Where a ruling contradicts an earlier ledger row, THIS file wins;
the ledger row is updated to cite it.

## Content authoring (pure YAML unless noted)

- **R-QC1** — Author the five unauthored mastery enchants: `demonic-gateway`, `horrify`,
  `mark-of-the-beast`, `mortal-coil`, `rot-and-decay` (ledger rows 34/36/37/38/39; all
  primitives shipped). This makes the anti-M-Kit Ghost/Necromancer crystals, the Lover
  mask's immunity line, and the Ghost/Necromancer set pools real.
- **R-QC2** — Fix all 58 unbound `{token}` MESSAGE lines (paradox, nature-wrath,
  smoke-bomb, phoenix, immortal, avenging-angel); every bound number verified against
  the codex.
- **R-QC4** — Teleblock's per-level line shows the TRUE window (8/11/14/17/20 s), not the
  jar's string-concat garbage (`[53s]`). Format stays verbatim; value stops lying.
  Deviation note on D-05-6.
- **R-QC5** — Author Atomic Detonate L1 (`left=1,right=2,up=1,down=2`) and L3
  (`2,3,2,3`) with the shipped asymmetric extents.
- **R-QC6** — Author Permafrost's standing-on-own-frost DEFENSE half behind
  `%actor.ownedground%`.
- **R-QC7** — Author the 7-material `void-materials` list on both Detonate grades.
- **R-QC8** — Cleave + Mighty Cleave take `cooldown-per-victim: true`; their misleading
  comments corrected; the knob documented in the DSL reference.
- **R-QC13** — Verify Pacify/Snare/Hellfire rarities against the codex appendix; correct
  the rungs.
- **R-QC31** — All four measured-fidelity keeps CONFIRMED as shipped: Healing repairs the
  shooter, Deathbringer ×4 compound, highest-worn-level procs, Master Inquisitive's
  payout quirk stays dropped.
- **R-QC35d** — Item materials RULED by owner: KOTH sword = `DIAMOND_SWORD`, KOTH axe =
  `DIAMOND_AXE` (replacing the invented GOLDEN_*), Supreme Fanny Pack = `DIAMOND_SWORD`
  (confirmed). "The KOTH set is diamond just like the others."
- **R-QC36** — Soul Trap's `SOUL_TRANSFER` ratio flips to **1** now (attacker banks the
  stolen souls). No codex check required; this is the ruling.
- **R-QC37** — Verify the source item-set table for Reforged; add `SHOVEL` if the five
  spades hold.
- **R-QC38** — `enchants/ghost`, `enchants/ghostly-ghost`, and `enchants/kill-aura` are
  **DROPPED** (no consumer exists anywhere in the corpus). `enchants/lava-strider` ships
  with real behavior: a `WALKER` lava-walk. Supreme's boots roster reference to Ghost is
  permanently omitted (note in the set file).
- **R-QC54** — Pets `banner`, `alchemist`, `evolution`, `stronghold-sell` are **CUT from
  the pack** (external payloads, hollow shells). `raid-creeper` and `vile-creeper` STAY,
  re-authored: spawn a **charged creeper, invincible to dying, whose fuse starts on any
  damage taken, despawning after a timeout** — the signature-pack creeper shape.
- **R-QC55** — Run the 29-file sound/particle fidelity lift (12 per-target SOUND files,
  17 PARTICLE height-anchor files), per-file judgement, deviation notes where a cue stays
  coarsened.
- **R-QC29/32/33** — Confirmed as shipped: Scarecrow holds hunger at 20; Mother of
  Yijki keeps survive-the-reforge; the Trap/Titan-Trap immunity asymmetry stays.

## Engine work — approved builds

- **R-QC3** — Build content-declarable TYPE suppression scoping (closes ADR-0016's
  deferral); Silence and Mastery Tombstone's windows become real.
- **R-QC9** — Build Infinite Luck's death-save consumer against the UNWEIGHTED
  `%heroicpieces%` count (leather weighting recorded as a deviation, not built).
- **R-QC10 + R-QC22a** — `BREAK_BLOCK` gains drop-transform (smelt) params; Fuse becomes
  live (blast-smelting requires Auto Smelt AND Fuse — the faithful co-enchant rule);
  Atomic Detonate gains its tool-class sublists so Explosives Expert has a gate to lift.
- **R-QC14** — `SPAWN_SWARM` gains `owner:`; Undead Ruse minions stop attacking their
  summoner and the nameplate fills.
- **R-QC15** — Fix the elemental pets' cooldown burn on empty-bucket clicks (refund
  path) and Smite's mob walk-lock; World Destroyer's per-victim shell stays deferred.
- **R-QC17** — #270 CONFIRMED; the five remaining ally-blind targeting paths
  (`firstOtherPlayer`, `nearestOtherPlayer`, `@NearestPlayer`, `@AllPlayers`,
  `EntityInSightSelector`) are routed through the same alliance predicate.
- **R-QC19** — DAMAGE_CAP gets the **post-fold readback**: the cap prices off the arming
  hit's folded damage (the fully correct basis). The pinned one-hit-lag test flips with
  the change.
- **R-QC25** — Rebound: keeps firing on cancelled hits (a); **fires the activation
  listener** so addons observe reflected activations (b); the reflect path gains a roll
  supplier so defender-keyed suppression windows can roll there (c).
- **R-QC30** — Packet work (MOB_DISGUISE + VANISH decoy): **roster-marker for now** (the
  R9 shape); the `PlayerRender` era seam is a future project, not part of this pass.
  The VANISH **end-of-window feedback hook IS built now** (no packets needed).
- **R-QC34** — When R-QC3's TYPE scoping lands, Rift's negation (and Dragon Slayer's)
  re-keys to the measured TYPE scope; the GROUP reading was the workaround.
- **R-QC35a/b/c** — Multi-weapon set support is built (the KOTH axe goes live); Auto
  Bless gains an initial-delay knob + its line is throttled; the claim-footer
  claimant/date tokens are built on the set-lore renderer.
- **R-QC40** — IMPACT scoping: per-ABILITY `group:` override on the enchant reader
  (payloads scope narrow, family match keys untouched); ADR-0074 §4 amended to match
  reality (no mask/crystal stem derivation).
- **R-QC41** — Build BOTH consume-time feedback halves (feedback records on one-shot
  charges + token substitution on the suppression path); the Death Knight mask's two
  lines ship. (R-QC24 still ratifies the MARK-feedback drop — the surface exists for
  Death Knight's consumers; MARK itself stays without it.)
- **R-QC42** — EAT gains a worn-equipment scan; Nutrition ships. The #286 safety test
  (worn abilities on held-flagged triggers) is written regardless.
- **R-QC43** — `RUN_COMMAND` gains a victim token; Target Tracking ships with an
  operator-configurable command string, DISABLED by default.
- **R-QC45** — Build the `who`-targeted summon conversion (or ownership flag on
  SUMMON_REBIND); Hijack ships whole.
- **R-QC46** — Build Blood Lust's bleed-stack publisher + tagged PROXIMITY_EVENT;
  author the enchant; unblock the Phantom/KOTH roster lines.
- **R-QC49** — The per-target subject family (per-target facts + post-selection branch)
  is ONE designed engine feature, planned and built as its own wave AFTER this pass.
- **R-QC51** — Fractional mint `chance` is built now (the half-point rolls author); the
  three exotic roll shapes (overwriting re-roll, exclusive alternatives, duplicates)
  stay recorded.
- **R-QC52** — Build the worn-crystal-count fact; author Ranger/Dragon-Slayer per-piece
  chance scaling.
- **R-QC57** — The FULL B-list ships as wave 3: TNT fuse param, dead-owner payload pin,
  Bleed's bounded slow + player death-clear, Dimension Rift's revert hook, Divine
  Immolation's second particle slot, arrow fire-ticks, Teleblock's charge-at-launch,
  Guided Rocket's riders, Soul Siphon's slot-matched transfer, FREEZE's no-jump knob.
- **R-QC58** — Generation-stamp the four IMPACT-scope carriers (falling-block casts,
  summon couriers, turret casts + volley closure); stale casts are dropped.
- **R-QC59** — An explicit `effects: []` loads silently; a MISSING `effects:` key still
  warns.
- **R-QC60** — LegacyGearPoll: cancel handle kept, setter-overwrite guard added, the
  scoping invariant written into harness docs.

- **R-QC63** — Build the STACKING WHITELIST rule: a per-enchant stacking knob whose default is
  highest-worn-piece-only, one roll per event, and whose opt-in restores per-piece multiplicity.
  The codex's 14 whitelisted stackers (`02-enchants-armor-m-z.md`:154-157) are authored as
  stacking; Tank and Valor keep their ledgered folds (D-02-10, D-02-14). The ~18 files claiming
  non-stacking have their comments made true, and the family behavior lands in `deviations.md`.
- **R-QC64** — Build the ABILITY-SET mint roller as a SECOND roller shape: `M − nextInt(4)`, then a
  25 % one-step shave (codex §A.11). The four M-Kit sets take it; `nearly-maxed` (§A.16) stays the
  plain-set draw. This reverses D-12-37's "one draw is enough" reading.
- **R-QC65** — Fix the pet layer to the ledger's promises: activation-only XP with a ZEROABLE floor
  (a refused use banks NOTHING), comma grouping on the rendered numbers (a `#,###.##` equivalent),
  and the 50-segment proportional EXP bar D-12-2 promises, clamped at full. Both packs are affected.
- **R-QC66** — The per-target subject wave (R-QC49's one feature) is **APPROVED WHOLE**: all six
  stages build, as designed, in order. The three alternatives the design weighed are RECORDED
  REJECTIONS and are not to be resurrected — per-target sub-activations (one gate walk per target),
  a new pipeline gate for the post-selection filter, and naive re-population of `%victim.*%` against
  each body. ADR-0076 carries the reasoning.
- **R-QC67** — `%selected%` publishes **−1** when the ability never activated and **0** when it ran
  and matched nobody. Those are different questions, and the empty-selection refusal lines cannot be
  authored without telling them apart; this closes the design's R1 in favour of the extra rule over
  the two-var marker dance (which stays available, and stays the right tool for other markers).
- **R-QC69** — The wave's two balance deltas are **CONFIRMED**, and land in their content stages with
  deviation rows: Pummel's Metaphysical rebate narrows from a whole-splash veto to a per-body one
  (one Metaphysical victim shields themselves, no longer their whole crowd — strictly a nerf to
  Metaphysical), and a sabotaged Guided Rocket Escape BURNS its 15 s window
  (`rebate-spends-cooldown: true`, faithful to the jar and a real nerf under sabotage pressure).
  Noted here now so neither reads as a silent fix when its stage lands.

## Permanent rejections (close the rows — do NOT re-propose)

- **R-QC68** — `%target.potion.<effect>%` is **CUT** from the per-target subject vocabulary. It is the
  one proposed subject fact that is not UUID-keyed: it reads the live entity, so a cross-region target
  would throw and silently default on Folia. **The no-entity-read rule is absolute** — the per-target
  pass reads no live entity, ever — and no consumer in the cluster needs it. The compiler rejects
  `%target.potion.*%` by construction, like every other live read.

- **R-QC50** — The per-slot heroic ladder and the second (M-Kit) heroic tier are
  **REJECTED PERMANENTLY** ("never add this"). The four M-Kit sets keep the 45% fold as
  their final form and never mint heroic pieces; `items/heroic.yml`'s per-slot/absolute-
  durability/lore-token clauses close as rejected.
- **R-QC38** (above) — Ghost, Ghostly Ghost, Kill Aura dropped.
- **R-QC44** — Skilling dropped (no soft-depend).
- **R-QC39** — No Architect anti-M-Kit crystal file; Death Knight's crystal covers both
  sets (documented).
- **R-QC23** — The LETHAL_CANCEL drop is RATIFIED (this ruling id is the missing
  authority D-07-16 and proposed-primitives now cite).
- **R-QC24** — The MARK consume-feedback drop is RATIFIED.
- **R-QC28c** — The studied-plugin names in production comments STAY (owner declined the
  scrub).
- **R-QC48** — Masks remain operator-minted; no generator, no claim ledger, and NO
  `hidden:` flag (all three deferred without schedule).

## Ship-shape & policy

- **R-QC11** — signature-pack is declared **modern-only**: manifest + docs + a clear
  boot-time message on 1.8.9. A legacy-era twin of the handle test (committed 1.8.8
  sound fixture) is built regardless.
- **R-QC12 + R-QC47** — The cosmic-pack items/menus gap is documented honestly now
  (site + pack README + apply-time notice); the §C support-item decomposition pass is
  COMMISSIONED (produces `matrix/13`), and the missing identities are closed from source
  afterwards, not invented.
- **R-QC18/20/21/26/27** — Confirmed as shipped: DAMAGE_CAP #292; `masks.max-merge: 2`
  (with the masks:/pets:/reforges: sections added to the shipped config for
  discoverability); D-11-12; the anvil-cue flip; derived pack manifest counts.
- **R-QC22b** — STANDING ERA RULE: jar-era block ids widen to their post-flattening
  variants (deepslate/nether ores etc.) wherever content gates on a block id.
- **R-QC28a/b** — `/bless` keeps the first-debuff body; ADR-0072 (and its index row) is
  amended to record it, including the Clarity set's effective fire immunity, which is
  CONFIRMED.
- **R-QC53** — Crystal contract confirmed whole: ADR-0034's no-roll apply, any-armor
  apply, shared anti-M-Kit likeness template.
- **R-QC56** — Attempt recovery of the 17 pet head-texture profiles from the codex; if
  the profiles are not there, report back before choosing a fallback.
- **R-QC61/62** — SpawnerYield's doc corrected to the real interaction (+ golden regen +
  bunny.yml echo); the three stale "warn-and-skip" comments on E_UNKNOWN_HANDLE fixed.
- **Part E** — The 16-row hygiene list executes in full, adjusted for the rulings above
  (row 8's branding scrub removed per R-QC28c; ledger/matrix rows updated to cite the
  R-QC ids where they close or reject entries).

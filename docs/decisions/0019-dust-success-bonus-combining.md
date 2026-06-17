# ADR 0019: Dust carrier kind — success-bonus combining (the last deferred carrier)

- **Status:** Accepted
- **Date:** 2026-06-16
- **Deciders:** project owner + engine work
- **Relates to:** ADR 0016 (content format v2 + the carrier-application economy), which deferred dust

## Context

ADR 0016 shipped the carrier-application economy — minting book/scroll/gem `ItemDef`s and applying them
to gear with a success/destroy roll, plus the white-scroll guard — but left **one** carrier kind
unimplemented: `dust`. The `ItemDef.Grant.successBonus` field, the `dust` kind in `ItemDefReader`, and
its default material were already in place (inert), explicitly flagged "the one deferred carrier kind."

The decompiled EliteEnchantments analysis (local-only, informs WHAT a feature is, never HOW to build it)
describes a "secret dust" that carries a success-rate modifier and combines onto an enchant book to
change the book's success chance, plus a separate "magic dust" rarity-tinkering mechanic (combine two to
get a next-rarity dust; convert a book into a dust). StarEnchants' data model only carries a single flat
`success-bonus` on a dust — it has no `DustType`, no per-group dust pools, and no book↔dust conversion —
so the in-scope feature is the **flat success-bonus dust** only. The rarity-tinkering/conversion
mechanic is a different, larger data model and is **not** adopted (it would be an invented system the
schema does not describe).

## Decision

**A dust is the one carrier-onto-carrier interaction: dragging a dust onto a content book/tome/gem raises
that book's stored success bonus; the book's later book→gear apply rolls against
`clamp(success-chance + bonus, 0, 100)`.**

- **State.** `CarrierData` gains a 4th field, `successBonus` — the bonus *accumulated on this carrier*
  (raised by combining dust onto a book; `0` on a freshly-minted carrier, a dust, or a scroll). The wire
  codec omits the field when zero, so every pre-dust item encodes **byte-for-byte** as the original
  3-field format and an old 3-field payload decodes with `successBonus = 0` — no migration, no key churn.
- **Combine.** `CarrierService.applyDust` adds the dust's `grants.success-bonus` to the target book's
  stored bonus, clamped so the book's *effective* success can never exceed 100% (`bonus ≤ 100 − base`),
  re-renders the book's lore from state, and consumes one dust. A no-op (target not a content book, dust
  confers nothing, or the book is already at 100%) leaves both stacks untouched — never a silent loss.
- **Gesture.** The interaction layer forbids carrier-onto-carrier *except* for dust: `CarrierListener`
  asks `CarrierService.appliesToCarrier(cursor)` (true only for a dust) before letting a carrier land on
  another carrier. A book dropped on a book is still left to the vanilla click.
- **Lore is rendered from state, never parsed back** (item-data-model §4.2): a dust advertises the bonus
  it confers; a content book shows its current *effective* success chance, so a dust has a visible effect.
- **Catalog.** Four carrier items now ship (`items/dust/success-dust`, `items/dust/master-success-dust`,
  `items/scroll/protect-scroll`, `items/book/executioner-book`) — the first authored carriers, so the
  economy is exercised end-to-end by the live `CatalogSuite` on every matrix server.

## Verification

- **Unit** (`CarrierCodecTest`): 4-field round-trip, the zero-bonus field omission (byte-identical
  legacy encoding), 3-field legacy decode → zero bonus, and malformed/over-long payloads → `null`.
- **Build** (`CatalogValidationTest`): the four shipped carrier files compile clean (filesystem walk).
- **Live** (`CarrierSuite`, every Paper + Folia matrix target): a dust records its bonus on a book; the
  bonus caps so effective ≤ 100% and a dust on a maxed book is a no-op; a 0%-book + a 100% dust **always**
  applies its enchant to gear (the bonus flows into the book→gear roll); a dust on plain gear is a no-op.

## Consequences

- The last ADR-0016 carrier deferral is closed; `feature.carrier` now covers book, scroll (protect), and
  dust. Gem/tome remain valid `ItemDef` kinds that mint and apply as content books today.
- The carrier PDC format is now `itemKey:grantKey:level[:successBonus]`; the optional 4th field is the
  only format change and is fully backward-compatible (omitted when zero).
- EE's magic-dust rarity-tinkering and book↔dust conversion remain out of scope (no data model for them).

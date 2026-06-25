export const meta = {
  name: 'v3-gap-audit',
  description: 'Cross-reference every v3 directive section against the actual codebase to find delivery gaps',
  phases: [
    { title: 'Audit', detail: 'one auditor per directive section: required vs delivered' },
    { title: 'Verify', detail: 'adversarially confirm each claimed gap is real' },
    { title: 'Synthesize', detail: 'collate confirmed gaps into one prioritized report' },
  ],
}

const SECTIONS = [
  { key: 'A', title: 'Unified conditions / variables / selectors', focus: 'condition AST, variable vocabulary, selector kinds, Cosmic Enchants-style parity; se/engine condition/selector, compile resolvers' },
  { key: 'B', title: 'Triggers wired plus lifecycle', focus: 'all triggers wired, REPEATING timer, HELD/PASSIVE start+stop lifecycle, COMMAND; se/engine trigger, feature/trigger, EquipListener' },
  { key: 'C', title: 'Effects maximal collapse plus new primitives', focus: 'parameterized primitives (VELOCITY, MODIFY_MONEY/EXP/HEALTH/FOOD, DURABILITY, DAMAGE_MOD, MESSAGE); new primitives (KNOCKBACK_CONTROL, GUARD, KEEP_ON_DEATH, WALKER); se/engine/effect/kind + BuiltinEffects' },
  { key: 'D', title: 'Soul gem', focus: 'soul gem item, deposit-on-any-kill, combine/split/give, soul-cost gate, colour tiers; items/soul-gem.yml, feature/soul' },
  { key: 'E', title: 'Crystals plus multi-crystal', focus: 'crystal item, per-item crystal slots, multi-crystal list, pairs-merge, max-stack; feature crystal, item PDC list' },
  { key: 'F', title: 'Heroic', focus: 'heroic upgrade item, percent multipliers, bounded multiplicative stage + clamp, per-item durability, apply UX/success range/material map; feature/heroic, items/heroic.yml' },
  { key: 'G', title: 'Enchant relationships', focus: 'requires / blacklist / removes-required general mechanism; compiler + runtime gating' },
  { key: 'H', title: 'Slots', focus: 'base slots config, slot expander item, hard total-slot cap in expander config, per-item slot ledger; feature slots, items/slots.yml' },
  { key: 'I', title: 'Other economy items', focus: 'scrolls (holy/white/black/transmog), dust (success-bonus combining), books + unopened, nametag, randomizer; items/*.yml + feature' },
  { key: 'J', title: 'Commands the StarEnchants way', focus: '/se subcommands (reload, enchant, crystal, give variants, menu, migrate, soul, reference), usage/lang, tab-complete; bootstrap/SeCommand' },
  { key: 'K', title: 'GUIs one shared framework', focus: 'shared menu framework + full menu set (enchant apply, browsers, godly transmog, enchanter/alchemist/tinkerer, admin); feature/menu' },
  { key: 'L', title: 'Config plus lang layout, one atomic reload', focus: 'config.yml, lang.yml, items/, menus/ surfacing; atomic all-or-nothing reload over all sources; compile/load + platform/content' },
  { key: 'M', title: 'Auto-doc dictionary', focus: 'generated DSL reference from live registries, drift guard, /se reference commands; engine/doc/ReferenceDoc' },
  { key: 'N', title: 'Integrations LAST', focus: 'protection/economy seams + named providers (Factions/Towny/Lands/mcMMO/PAPI/Vault/WorldGuard); platform/protect, platform/economy, addons. NOTE: planned LAST (task 34), expected not-yet-started.' },
]

const SECTION_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  required: ['section', 'deliveredSummary', 'gaps'],
  properties: {
    section: { type: 'string' },
    deliveredSummary: { type: 'string', description: 'what the directive required that IS implemented, with file evidence' },
    gaps: {
      type: 'array',
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['title', 'kind', 'severity', 'detail', 'evidence'],
        properties: {
          title: { type: 'string' },
          kind: { type: 'string', enum: ['missing', 'partial', 'deviation'] },
          severity: { type: 'string', enum: ['high', 'medium', 'low'] },
          detail: { type: 'string' },
          evidence: { type: 'string' },
        },
      },
    },
  },
}

const VERDICT_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  required: ['confirmed', 'reasoning', 'evidence'],
  properties: {
    confirmed: { type: 'boolean' },
    reasoning: { type: 'string' },
    evidence: { type: 'string' },
  },
}

const DIRECTIVE = 'docs/v3-directives.md'
const PARITY = 'docs/parity/parity-audit.md'

function auditPrompt(s) {
  return [
    'You are auditing the StarEnchants v3 re-architecture for DELIVERY GAPS in ONE section. Repo root is cwd.',
    'Authoritative spec: ' + DIRECTIVE + ' (find heading "## ' + s.key + '."). Section: ' + s.key + ' — ' + s.title + '.',
    'Secondary cross-reference: ' + PARITY + ' (the Cosmic Enchants-style feature inventory) for related features.',
    '',
    'For section ' + s.key + ':',
    '1. Read the directive section fully and list everything it REQUIRES.',
    '2. Determine what is ACTUALLY IMPLEMENTED by reading/grepping the code. Focus: ' + s.focus,
    '   Flat layout: se/<module>/src/<pkg> (modules compile, engine, item, feature, platform, bootstrap, schema, migrate, tester); bundled content/config under se/bootstrap/resources/.',
    '3. Report deliveredSummary (what is done, with file:line evidence) and gaps[] — anything REQUIRED that is missing, partial, or a deviation.',
    '',
    'Be skeptical: only record a gap after grepping for the feature by several plausible names/packages and confirming it is absent/partial. Cite concrete file/symbol evidence. If fully delivered, return empty gaps. Do not invent requirements absent from the directive.',
  ].join('\n')
}

function verifyPrompt(section, g) {
  return [
    'Independently VERIFY this claimed StarEnchants v3 gap in section ' + section.key + ' (' + section.title + '). Repo root is cwd.',
    '',
    'Claimed gap: "' + g.title + '" [' + g.kind + '/' + g.severity + ']',
    'Detail: ' + g.detail,
    "Auditor's evidence: " + g.evidence,
    '',
    'Try to DISPROVE it: search the codebase thoroughly (flat layout se/<module>/src, plus se/bootstrap/resources) for an implementation the auditor may have missed — different module, package, class name, or config file. Read ' + DIRECTIVE + ' section ' + section.key + ' to confirm the directive truly requires this.',
    'Return confirmed=true ONLY if, after a real search, the gap is genuinely a delivery gap. Return confirmed=false if you found it implemented (say where) or the directive does not require it.',
  ].join('\n')
}

phase('Audit')
const audited = await pipeline(
  SECTIONS,
  function (s) {
    return agent(auditPrompt(s), { label: 'audit:' + s.key, phase: 'Audit', schema: SECTION_SCHEMA })
      .then(function (r) { return { section: s, result: r } })
  },
  function (prev) {
    const section = prev.section
    const result = prev.result
    if (!result || !result.gaps || result.gaps.length === 0) {
      return { section: section, result: result, verified: [] }
    }
    return parallel(result.gaps.map(function (g) {
      return function () {
        return agent(verifyPrompt(section, g), {
          label: 'verify:' + section.key + ':' + g.title.slice(0, 20),
          phase: 'Verify',
          schema: VERDICT_SCHEMA,
        }).then(function (v) { return { gap: g, verdict: v } })
      }
    })).then(function (verdicts) {
      return { section: section, result: result, verified: verdicts.filter(Boolean) }
    })
  }
)

const sections = audited.filter(Boolean).map(function (a) {
  const verified = a.verified || []
  return {
    key: a.section.key,
    title: a.section.title,
    delivered: a.result ? a.result.deliveredSummary : '(audit failed)',
    confirmedGaps: verified.filter(function (v) { return v.verdict && v.verdict.confirmed })
      .map(function (v) { return { title: v.gap.title, kind: v.gap.kind, severity: v.gap.severity, detail: v.gap.detail, evidence: v.gap.evidence, verifyEvidence: v.verdict.evidence } }),
    rejectedGaps: verified.filter(function (v) { return v.verdict && !v.verdict.confirmed })
      .map(function (v) { return { title: v.gap.title, why: v.verdict.reasoning } }),
  }
})

const totalConfirmed = sections.reduce(function (n, s) { return n + s.confirmedGaps.length }, 0)
const totalRejected = sections.reduce(function (n, s) { return n + s.rejectedGaps.length }, 0)
log('audited ' + sections.length + ' sections — ' + totalConfirmed + ' confirmed gaps, ' + totalRejected + ' rejected (false) gaps')

phase('Synthesize')
const synthPrompt = [
  'You are the synthesis stage of a StarEnchants v3 delivery-gap audit. Below (JSON) is, per directive section A–N, the delivered summary plus ADVERSARIALLY-CONFIRMED gaps (false gaps already filtered out).',
  '',
  'Produce a single well-structured Markdown gap report:',
  '1. One-paragraph executive summary (overall delivery state; gap count by severity).',
  '2. "Confirmed gaps" grouped by severity (High, then Medium, then Low). Each: bold "section KEY — title" (kind), then detail, then italic Evidence, then italic Recommended action. Keep tight.',
  '3. "Fully delivered" list: section keys with zero confirmed gaps (one line each, naming the section).',
  '4. "Notes" paragraph: flag section N (Integrations) as planned-to-be-LAST (task 34) — if it shows gaps, classify them as planned-not-started rather than unexpected omissions.',
  '',
  'Be accurate; use only the confirmed gaps provided; do not inflate. Output ONLY the Markdown report (saved verbatim).',
  '',
  'DATA (JSON):',
  JSON.stringify(sections, null, 2),
].join('\n')

const report = await agent(synthPrompt, { label: 'synthesize', phase: 'Synthesize' })

return { totalConfirmed: totalConfirmed, totalRejected: totalRejected, sectionsAudited: sections.length, sections: sections, report: report }

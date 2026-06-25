/**
 * Build the on-disk enchant def map from editor state and render it as the
 * exact `content/enchants/<key>.yml` shape (the same map travels in the SE1
 * envelope's `content`). Also: parse a decoded `content` map back into editor
 * state for the import round-trip.
 */

import type {
  EnchantState,
  EffectState,
  LevelState,
  TargetState,
} from './types';
import {
  findEffect,
  findSelector,
  coerceParam,
  nextId,
  defaultTargets,
  newLevel,
  newEnchant,
} from './catalog';

// --- selector rendering: "@Victim" / "@Aoe{r=6, filter=MONSTERS}" -------

/** Map a selector head to its canonical @PascalCase token used in YAML. */
const SELECTOR_TOKEN: Record<string, string> = {
  SELF: 'Self',
  VICTIM: 'Victim',
  ATTACKER: 'Attacker',
  HERE: 'Here',
  AOE: 'Aoe',
  ALLPLAYERS: 'AllPlayers',
  NEAREST: 'Nearest',
  NEARESTPLAYER: 'NearestPlayer',
  PLAYERFROMNAME: 'PlayerFromName',
  ENTITYINSIGHT: 'EntityInSight',
  EYEHEIGHT: 'EyeHeight',
  BLOCK: 'Block',
  BLOCKINDISTANCE: 'BlockInDistance',
  ADD: 'Add',
  TRENCH: 'Trench',
  TUNNEL: 'Tunnel',
  VEIN: 'Vein',
};

function selectorToken(head: string): string {
  return SELECTOR_TOKEN[head] ?? head.charAt(0) + head.slice(1).toLowerCase();
}

/** Render a chosen target slot to its `@Token{a=1, b=2}` string. */
export function renderSelector(t: TargetState): string {
  const sel = findSelector(t.selector);
  const token = '@' + selectorToken(t.selector);
  if (!sel || sel.params.length === 0) return token;
  const parts: string[] = [];
  for (const p of sel.params) {
    const raw = (t.params[p.name] ?? '').trim();
    if (raw === '') continue; // omit empty/default args
    if (p.default !== null && raw === String(p.default)) continue;
    parts.push(`${p.name}=${raw}`);
  }
  return parts.length ? `${token}{${parts.join(', ')}}` : token;
}

// --- def map construction ----------------------------------------------

/** One effect -> `{ HEAD: { param: value, who: "@..." } }`. */
function effectToMap(e: EffectState): Record<string, unknown> {
  const def = findEffect(e.head);
  const args: Record<string, unknown> = {};
  if (def) {
    for (const p of def.params) {
      const raw = e.params[p.name];
      if (raw === undefined || raw.trim() === '') {
        if (p.required && p.default !== null) {
          args[p.name] = coerceParam(p, p.default);
        }
        continue;
      }
      // drop values equal to the default to keep YAML lean
      if (p.default !== null && raw.trim() === String(p.default)) continue;
      args[p.name] = coerceParam(p, raw);
    }
    for (const t of e.targets) {
      const rendered = renderSelector(t);
      const slotDef = def.targets.find((d) => d.name === t.slot);
      // omit the slot when it matches the effect's default selector + no args
      if (slotDef && rendered === '@' + selectorToken(slotDef.selector)) {
        continue;
      }
      args[t.slot] = rendered;
    }
  }
  return {[e.head]: args};
}

function levelToMap(l: LevelState): Record<string, unknown> {
  const m: Record<string, unknown> = {};
  const chance = Number(l.chance);
  m.chance = Number.isFinite(chance) ? chance : l.chance;
  if (l.cooldown.trim() !== '') {
    const cd = Number(l.cooldown);
    m.cooldown = Number.isFinite(cd) ? Math.trunc(cd) : l.cooldown;
  }
  if (l.souls.trim() !== '') {
    const s = Number(l.souls);
    m.souls = Number.isFinite(s) ? Math.trunc(s) : l.souls;
  }
  if (l.condition.trim() !== '') m.condition = l.condition.trim();
  m.effects = l.effects.map(effectToMap);
  return m;
}

/** Build the full def map (the SE1 `content` and the YAML file body). */
export function buildContent(state: EnchantState): Record<string, unknown> {
  const content: Record<string, unknown> = {};
  content.tier = state.tier;
  content.display = state.display;
  if (state.description.trim() !== '') content.description = state.description;
  content.trigger = state.trigger;
  content['applies-to'] = [...state.appliesTo];
  if (state.group.trim() !== '') content.group = state.group.trim();
  const levels: Record<string, unknown> = {};
  state.levels.forEach((l, i) => {
    levels[String(i + 1)] = levelToMap(l);
  });
  content.levels = levels;
  return content;
}

// --- YAML emitter (just enough for this fixed shape) --------------------

function scalar(v: unknown): string {
  if (v === null || v === undefined) return '""';
  if (typeof v === 'number') return String(v);
  if (typeof v === 'boolean') return v ? 'true' : 'false';
  const s = String(v);
  // Quote when empty, or when special chars / leading-trailing space / would
  // be mis-read as a number/bool/null. & color codes always quote safely.
  const needsQuote =
    s === '' ||
    /^[\s]|[\s]$/.test(s) ||
    /[:#{}\[\],&*!?|>'"%@`]/.test(s) ||
    /^(true|false|null|yes|no|~)$/i.test(s) ||
    /^[-+]?[0-9.]/.test(s);
  if (!needsQuote) return s;
  return '"' + s.replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"';
}

/** Render one effect map as inline flow: `{ HEAD: { a: 1, who: "@X" } }`. */
function inlineEffect(effectMap: Record<string, unknown>): string {
  const head = Object.keys(effectMap)[0];
  const args = effectMap[head] as Record<string, unknown>;
  const keys = Object.keys(args);
  if (keys.length === 0) return `{ ${head}: {} }`;
  const inner = keys.map((k) => `${k}: ${scalar(args[k])}`).join(', ');
  return `{ ${head}: { ${inner} } }`;
}

/** Render the def map to YAML matching content/enchants/<key>.yml. */
export function renderYaml(state: EnchantState): string {
  const lines: string[] = [];
  const display = state.display.trim() || '(unnamed)';
  lines.push(`# ${display} — generated by the StarEnchants enchant creator.`);
  lines.push(`tier: ${state.tier}`);
  lines.push(`display: ${scalar(state.display)}`);
  if (state.description.trim() !== '') {
    lines.push(`description: ${scalar(state.description)}`);
  }
  lines.push(`trigger: ${state.trigger}`);
  const applies = state.appliesTo.length
    ? `[${state.appliesTo.join(', ')}]`
    : '[]';
  lines.push(`applies-to: ${applies}`);
  if (state.group.trim() !== '') lines.push(`group: ${state.group.trim()}`);
  lines.push('levels:');

  state.levels.forEach((l, i) => {
    const n = i + 1;
    const lm = levelToMap(l);
    lines.push(`  ${n}:`);
    lines.push(`    chance: ${scalar(lm.chance)}`);
    if ('cooldown' in lm) lines.push(`    cooldown: ${scalar(lm.cooldown)}`);
    if ('souls' in lm) lines.push(`    souls: ${scalar(lm.souls)}`);
    if ('condition' in lm) lines.push(`    condition: ${scalar(lm.condition)}`);
    const effects = lm.effects as Record<string, unknown>[];
    if (effects.length === 0) {
      lines.push('    effects: []');
    } else {
      lines.push('    effects:');
      for (const e of effects) {
        lines.push(`      - ${inlineEffect(e)}`);
      }
    }
  });
  return lines.join('\n') + '\n';
}

// --- import: decoded content map -> editor state ------------------------

function asString(v: unknown): string {
  if (v === null || v === undefined) return '';
  return String(v);
}

/** Parse "@Aoe{r=6, filter=MONSTERS}" into a TargetState for the given slot. */
function parseSelector(slot: string, value: string): TargetState {
  const trimmed = value.trim().replace(/^@/, '');
  const brace = trimmed.indexOf('{');
  let token: string;
  const params: Record<string, string> = {};
  if (brace >= 0) {
    token = trimmed.slice(0, brace).trim();
    const inside = trimmed.slice(brace + 1, trimmed.lastIndexOf('}'));
    for (const pair of inside.split(',')) {
      const eq = pair.indexOf('=');
      if (eq < 0) continue;
      const k = pair.slice(0, eq).trim();
      const val = pair.slice(eq + 1).trim();
      if (k) params[k] = val;
    }
  } else {
    token = trimmed;
  }
  // map the @Token back to a selector head (uppercase, no separators)
  const head = token.toUpperCase();
  return {slot, selector: head, params};
}

/** Reconstruct an effect entry from a decoded `{ HEAD: {...} }` map. */
function parseEffect(raw: unknown): EffectState | null {
  if (typeof raw !== 'object' || raw === null) return null;
  const obj = raw as Record<string, unknown>;
  const head = Object.keys(obj)[0];
  if (!head) return null;
  const args = (obj[head] ?? {}) as Record<string, unknown>;
  const def = findEffect(head);
  const eff: EffectState = {
    id: nextId('eff'),
    head,
    params: {},
    targets: defaultTargets(head),
  };
  const targetSlots = new Set((def?.targets ?? []).map((t) => t.name));
  for (const [k, v] of Object.entries(args)) {
    if (targetSlots.has(k) && typeof v === 'string' && v.trim().startsWith('@')) {
      const parsed = parseSelector(k, v);
      const idx = eff.targets.findIndex((t) => t.slot === k);
      if (idx >= 0) eff.targets[idx] = parsed;
      else eff.targets.push(parsed);
    } else {
      eff.params[k] = asString(v);
    }
  }
  return eff;
}

function parseLevel(raw: unknown): LevelState {
  const l = newLevel();
  if (typeof raw !== 'object' || raw === null) return l;
  const obj = raw as Record<string, unknown>;
  if ('chance' in obj) l.chance = asString(obj.chance);
  if ('cooldown' in obj) l.cooldown = asString(obj.cooldown);
  if ('souls' in obj) l.souls = asString(obj.souls);
  if ('condition' in obj) l.condition = asString(obj.condition);
  const effects = Array.isArray(obj.effects) ? obj.effects : [];
  l.effects = effects
    .map(parseEffect)
    .filter((e): e is EffectState => e !== null);
  return l;
}

/** Build editor state from a decoded SE1 `content` map + its key. */
export function contentToState(
  key: string,
  content: Record<string, unknown>,
): EnchantState {
  const state = newEnchant();
  state.key = key;
  state.keyTouched = true; // imported keys are explicit
  if (typeof content.tier === 'string') state.tier = content.tier;
  state.display = asString(content.display);
  state.description = asString(content.description ?? '');
  if (typeof content.trigger === 'string') state.trigger = content.trigger;
  const applies = content['applies-to'];
  state.appliesTo = Array.isArray(applies) ? applies.map(asString) : [];
  state.group = asString(content.group ?? '');

  const levelsRaw = content.levels;
  const levels: LevelState[] = [];
  if (levelsRaw && typeof levelsRaw === 'object') {
    // preserve numeric order of level keys
    const entries = Object.entries(levelsRaw as Record<string, unknown>).sort(
      (a, b) => Number(a[0]) - Number(b[0]),
    );
    for (const [, lv] of entries) levels.push(parseLevel(lv));
  }
  state.levels = levels.length ? levels : [newLevel()];
  return state;
}

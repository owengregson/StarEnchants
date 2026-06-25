/**
 * Typed access to catalog.json plus factories for fresh editor state.
 * Importing the JSON here keeps the (large) data out of UI components.
 */

import rawCatalog from '@site/src/data/catalog.json';
import type {
  Catalog,
  CatalogEffect,
  CatalogSelector,
  CatalogParam,
  EffectState,
  LevelState,
  EnchantState,
  TargetState,
} from './types';

export const catalog = rawCatalog as unknown as Catalog;

export const EFFECTS: CatalogEffect[] = [...catalog.effects].sort((a, b) =>
  a.head.localeCompare(b.head),
);
export const SELECTORS: CatalogSelector[] = [...catalog.selectors].sort((a, b) =>
  a.head.localeCompare(b.head),
);
export const TRIGGERS = catalog.triggers;

/** The 6 tiers, in display order, sourced to match tiers.yml's set. */
export const TIERS = [
  'common',
  'uncommon',
  'rare',
  'epic',
  'legendary',
  'mythic',
] as const;

/** Item categories an enchant can apply to (extensible — custom allowed). */
export const ITEM_CATEGORIES = [
  'SWORD',
  'AXE',
  'BOW',
  'CROSSBOW',
  'TRIDENT',
  'PICKAXE',
  'SHOVEL',
  'HOE',
  'HELMET',
  'CHESTPLATE',
  'LEGGINGS',
  'BOOTS',
] as const;

const effectByHead = new Map(EFFECTS.map((e) => [e.head, e]));
const selectorByHead = new Map(SELECTORS.map((s) => [s.head, s]));

export function findEffect(head: string): CatalogEffect | undefined {
  return effectByHead.get(head);
}

export function findSelector(head: string): CatalogSelector | undefined {
  return selectorByHead.get(head);
}

/** A short human label for a handle category (autocomplete hint text). */
export function handleHint(handle: string | null): string {
  switch (handle) {
    case 'MATERIAL':
      return 'e.g. DIAMOND, OBSIDIAN, ICE';
    case 'SOUND':
      return 'e.g. ENTITY_GENERIC_EXPLODE';
    case 'PARTICLE':
      return 'e.g. FLAME, CLOUD, WITCH';
    case 'POTION_EFFECT':
      return 'e.g. STRENGTH, SLOWNESS, POISON';
    case 'ENTITY_TYPE':
      return 'e.g. WOLF, IRON_GOLEM, ARROW';
    default:
      return handle ? `a ${handle.toLowerCase()} name` : '';
  }
}

// --- id generation ------------------------------------------------------

let idCounter = 0;
export function nextId(prefix: string): string {
  idCounter += 1;
  return `${prefix}-${idCounter}`;
}

// --- factories ----------------------------------------------------------

/** Build the default target slots for a freshly-picked effect head. */
export function defaultTargets(head: string): TargetState[] {
  const eff = findEffect(head);
  if (!eff) return [];
  return eff.targets.map((t) => ({
    slot: t.name,
    selector: t.selector,
    params: {},
  }));
}

export function newEffect(head: string): EffectState {
  return {
    id: nextId('eff'),
    head,
    params: {},
    targets: defaultTargets(head),
  };
}

export function newLevel(): LevelState {
  return {
    id: nextId('lvl'),
    chance: '100',
    cooldown: '',
    souls: '',
    condition: '',
    effects: [],
  };
}

export function newEnchant(): EnchantState {
  return {
    display: '',
    description: '',
    tier: 'common',
    appliesTo: [],
    group: '',
    trigger: TRIGGERS[0]?.name ?? 'ATTACK',
    key: '',
    keyTouched: false,
    levels: [newLevel()],
  };
}

/** Coerce a string param value to the JS value the YAML should carry. */
export function coerceParam(p: CatalogParam, raw: string): unknown {
  const v = raw.trim();
  switch (p.kind) {
    case 'INT':
    case 'TICKS': {
      const n = Number(v);
      return Number.isFinite(n) ? Math.trunc(n) : v;
    }
    case 'DOUBLE': {
      const n = Number(v);
      return Number.isFinite(n) ? n : v;
    }
    case 'BOOL':
      return v === 'true';
    default:
      return v;
  }
}

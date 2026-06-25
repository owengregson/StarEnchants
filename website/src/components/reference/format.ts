// Turns the raw catalog param schema (kind / label / enum / handle / min-max)
// into the plain-language text an operator who doesn't know the engine can read.
// All of the reference pages funnel through here so the wording stays consistent.

import type {CatalogParam} from './types';

const HANDLE_TEXT: Record<string, string> = {
  MATERIAL: 'a block or item name (e.g. DIAMOND, OBSIDIAN)',
  SOUND: 'a sound name (e.g. ENTITY_GENERIC_EXPLODE)',
  PARTICLE: 'a particle name (e.g. FLAME, HEART)',
  ENTITY_TYPE: 'a mob/entity type (e.g. WOLF, IRON_GOLEM)',
  POTION_EFFECT: 'a potion-effect name (e.g. STRENGTH, POISON)',
};

/** Human "type" phrase for a param, derived only from the catalog. */
export function humanType(p: CatalogParam): string {
  if (p.enum && p.enum.length > 0) {
    return `one of: ${p.enum.join(', ')}`;
  }
  if (p.kind === 'HANDLE' && p.handle && HANDLE_TEXT[p.handle]) {
    return HANDLE_TEXT[p.handle];
  }
  switch (p.kind) {
    case 'BOOL':
      return 'true or false';
    case 'TICKS':
      return 'a duration in ticks (20 ticks = 1 second)';
    case 'INT':
      return 'a whole number';
    case 'DOUBLE':
      return 'a number (decimals allowed)';
    case 'STRING':
      return 'text';
    default:
      return p.label;
  }
}

/** Allowed-range / values cell text, or an em dash when there's no constraint. */
export function rangeText(p: CatalogParam): string {
  if (p.enum && p.enum.length > 0) {
    return p.enum.join(' | ');
  }
  const hasMin = p.min !== null && p.min !== undefined;
  const hasMax = p.max !== null && p.max !== undefined;
  if (hasMin && hasMax) return `${p.min} to ${p.max}`;
  if (hasMin) return `${p.min} or more`;
  if (hasMax) return `up to ${p.max}`;
  return '';
}

/** Default-value cell text; empty string => shown as a dash by the renderer. */
export function defaultText(p: CatalogParam): string {
  if (p.required) return '';
  if (p.default === null || p.default === undefined) return '';
  if (p.default === '') return '(empty)';
  return p.default;
}

// ---- Affinity (how/where an effect runs) -----------------------------------

interface AffinityInfo {
  label: string;
  badgeClass: string;
  /** One plain sentence an operator can act on. */
  hint: string;
}

export const AFFINITY: Record<string, AffinityInfo> = {
  CONTEXT_LOCAL: {
    label: 'Runs inline',
    badgeClass: 'badgeInline',
    hint: 'Runs immediately, in line with the event that triggered it (e.g. shaping the combat hit). No extra thread hop.',
  },
  REGION: {
    label: 'Acts on the world',
    badgeClass: 'badgeRegion',
    hint: 'Touches blocks/world at a location (breaks, particles, spawns). Runs safely on the region that owns that spot.',
  },
  TARGET_ENTITY: {
    label: 'Acts on an entity',
    badgeClass: 'badgeTarget',
    hint: 'Acts on a living target (heal, ignite, potion, teleport). Runs on whoever the effect is aimed at.',
  },
  GLOBAL: {
    label: 'Runs globally',
    badgeClass: 'badgeGlobal',
    hint: 'Runs on the server-wide thread — used for console commands and other global work.',
  },
};

export function affinityInfo(affinity: string): AffinityInfo {
  return (
    AFFINITY[affinity] ?? {
      label: affinity,
      badgeClass: 'badgeNeutral',
      hint: '',
    }
  );
}

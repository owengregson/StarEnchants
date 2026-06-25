/**
 * Shape of catalog.json (ADR 0028) plus the in-form editor model.
 * The catalog types mirror engine.doc.ReferenceCatalogJson's output.
 */

export type ParamKind =
  | 'DOUBLE'
  | 'INT'
  | 'TICKS'
  | 'ENUM'
  | 'BOOL'
  | 'STRING'
  | 'HANDLE';

export interface CatalogParam {
  name: string;
  kind: ParamKind;
  label: string;
  required: boolean;
  default: string | null;
  min: number | null;
  max: number | null;
  enum: string[];
  handle: string | null;
  doc: string;
}

export interface CatalogTarget {
  name: string;
  /** Default selector head, e.g. "VICTIM", "SELF", "HERE". */
  selector: string;
}

export interface CatalogEffect {
  head: string;
  doc: string;
  affinity: string;
  usage: string;
  example: string;
  params: CatalogParam[];
  targets: CatalogTarget[];
}

export interface CatalogSelector {
  head: string;
  doc: string;
  usage: string;
  example: string;
  params: CatalogParam[];
}

export interface CatalogTrigger {
  name: string;
  direction: string;
  usesHeld: boolean;
  scansEquipment: boolean;
  needsTarget: boolean;
}

export interface Catalog {
  version: number;
  effects: CatalogEffect[];
  selectors: CatalogSelector[];
  triggers: CatalogTrigger[];
  conditions: unknown;
  variables: {name: string; type: string}[];
}

// --- editor model -------------------------------------------------------

/** A chosen target selector for one effect target slot. */
export interface TargetState {
  /** the target's param name on the effect (e.g. "who", "at") */
  slot: string;
  /** selected selector head, e.g. "VICTIM" */
  selector: string;
  /** selector param values keyed by param name (string form) */
  params: Record<string, string>;
}

/** One effect entry within a level. */
export interface EffectState {
  /** stable client id for React keys + reordering */
  id: string;
  head: string;
  /** effect param values keyed by param name (string form) */
  params: Record<string, string>;
  /** one entry per catalog target slot the effect declares */
  targets: TargetState[];
}

/** One enchant level. */
export interface LevelState {
  id: string;
  chance: string;
  cooldown: string;
  souls: string;
  condition: string;
  effects: EffectState[];
}

/** The whole enchant being authored. */
export interface EnchantState {
  display: string;
  description: string;
  tier: string;
  appliesTo: string[];
  group: string;
  trigger: string;
  /** edited key; when keyTouched is false it tracks the display name */
  key: string;
  keyTouched: boolean;
  levels: LevelState[];
}

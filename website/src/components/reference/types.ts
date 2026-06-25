// Shape of the catalog.json entries this folder renders. Kept loose on
// purpose: the JSON is the source of truth, so we only name the fields we read.

export type ParamKind =
  | 'BOOL'
  | 'INT'
  | 'DOUBLE'
  | 'TICKS'
  | 'STRING'
  | 'ENUM'
  | 'HANDLE';

export type HandleCategory =
  | 'MATERIAL'
  | 'SOUND'
  | 'PARTICLE'
  | 'ENTITY_TYPE'
  | 'POTION_EFFECT';

export interface CatalogParam {
  name: string;
  kind: ParamKind | string;
  label: string;
  required: boolean;
  default: string | null;
  min: number | null;
  max: number | null;
  enum: string[];
  handle: HandleCategory | string | null;
  doc: string;
}

export interface CatalogTarget {
  name: string;
  selector: string;
}

export type Affinity =
  | 'CONTEXT_LOCAL'
  | 'REGION'
  | 'TARGET_ENTITY'
  | 'GLOBAL'
  | string;

export interface CatalogEffect {
  head: string;
  doc: string;
  affinity: Affinity;
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
  direction: 'ATTACK' | 'DEFENSE' | 'NEUTRAL' | string;
  usesHeld: boolean;
  scansEquipment: boolean;
  needsTarget: boolean;
}

export interface CatalogVariable {
  name: string;
  type: 'NUM' | 'STR' | 'BOOL' | string;
}

export interface CatalogConditions {
  relational: {symbol: string; name: string}[];
  string: {symbol: string; name: string}[];
  flow: {token: string; doc: string}[];
}

export interface Catalog {
  version: number;
  effects: CatalogEffect[];
  selectors: CatalogSelector[];
  triggers: CatalogTrigger[];
  conditions: CatalogConditions;
  variables: CatalogVariable[];
}

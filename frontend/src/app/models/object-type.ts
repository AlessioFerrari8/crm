// Sottoinsieme di ObjectType lato backend (entities/ObjectType.java). NB: i getter Lombok dei
// campi boolean `isXxx` vengono serializzati da Jackson SENZA il prefisso `is` (es. il campo Java
// `isEnabled` -> chiave JSON `enabled`, `isSystem` -> `system`) - verificato sulla risposta live
// di GET /api/objects. Stesso discorso sui FieldDef qui sotto.
export interface ObjectType {
  id: string;
  key: string;
  label: string;
  pluralLabel: string;
  icon: string | null;
  color: string | null;
  system: boolean;
  enabled: boolean;
  // modulo pubblico di lead capture attivo per questo tipo (/f/{key})
  publicForm: boolean;
  sortOrder: number;
  fields: FieldDef[];
}

// Porting dell'enum FieldType (entities/FieldType.java) - 39 valori.
export type FieldType =
  | 'TEXT' | 'VARCHAR' | 'LONGTEXT' | 'RICHTEXT'
  | 'NUMBER' | 'INTEGER' | 'BIGINT' | 'DECIMAL' | 'FLOAT' | 'PERCENT' | 'CURRENCY' | 'RATING' | 'DURATION'
  | 'DATE' | 'DATETIME' | 'TIME' | 'TIMESTAMP'
  | 'BOOLEAN'
  | 'SELECT' | 'MULTISELECT' | 'TAGS'
  | 'EMAIL' | 'PHONE' | 'URL' | 'COLOR' | 'ICON' | 'ADDRESS' | 'GEO' | 'JSON' | 'UUID' | 'AUTONUMBER'
  | 'RELATION' | 'FILE' | 'IMAGE' | 'FORMULA' | 'ROLLUP' | 'LOOKUP' | 'USER' | 'STATUS';

// I 39 FieldType in forma di array, per popolare il <select> del "+ Aggiungi campo".
export const FIELD_TYPES: FieldType[] = [
  'TEXT', 'VARCHAR', 'LONGTEXT', 'RICHTEXT',
  'NUMBER', 'INTEGER', 'BIGINT', 'DECIMAL', 'FLOAT', 'PERCENT', 'CURRENCY', 'RATING', 'DURATION',
  'DATE', 'DATETIME', 'TIME', 'TIMESTAMP',
  'BOOLEAN',
  'SELECT', 'MULTISELECT', 'TAGS',
  'EMAIL', 'PHONE', 'URL', 'COLOR', 'ICON', 'ADDRESS', 'GEO', 'JSON', 'UUID', 'AUTONUMBER',
  'RELATION', 'FILE', 'IMAGE', 'FORMULA', 'ROLLUP', 'LOOKUP', 'USER', 'STATUS'
];

export interface FieldOption {
  value: string;
  label: string;
  color?: string;
}

export interface FieldDef {
  id: string;
  key: string;
  label: string;
  type: FieldType;
  description: string | null;
  placeholder: string | null;
  required: boolean;
  showInList: boolean;
  defaultValue: unknown;
  min: number | null;
  max: number | null;
  step: number | null;
  pattern: string | null;
  section: string | null;
  width: string;
  sortOrder: number;
  options: FieldOption[] | null;
  config: Record<string, unknown> | null;
  // booleani serializzati senza prefisso `is`
  filterable: boolean;
  hidden: boolean;
  indexed: boolean;
  readonly: boolean;
  sortable: boolean;
  unique: boolean;
  icon: string | null;
}

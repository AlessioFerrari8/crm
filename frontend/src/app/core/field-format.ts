import { FieldDef, FieldOption } from '../models/object-type';

// Formattazione type-aware dei valori dei campi, condivisa da record-list (testo nelle celle) e
// record-detail (rendering ricco: badge colorati, link). Metadata-driven: usa options/type del
// FieldDef per etichette, colori, valuta/percentuali/date in it-IT. Porting migliorato del
// renderValue() del riferimento (React), qui riusabile e non duplicato tra le viste.

export interface Chip {
  label: string;
  color?: string;
}

export interface FieldCell {
  kind: 'text' | 'chips' | 'link';
  text: string;
  href?: string;
  chips?: Chip[];
}

export function optionFor(field: FieldDef, value: unknown): FieldOption | undefined {
  return field.options?.find((o) => o.value === value);
}

// rappresentazione testuale semplice (celle di tabella): etichette delle opzioni, valuta/
// percentuali/date formattate; per gli array unisce le etichette
export function formatFieldValue(field: FieldDef, raw: unknown): string {
  if (raw == null || raw === '') return '—';
  if (Array.isArray(raw)) {
    return raw.map((v) => optionFor(field, v)?.label ?? String(v)).join(', ') || '—';
  }
  if (typeof raw === 'boolean') return raw ? 'Sì' : 'No';
  switch (field.type) {
    case 'SELECT':
    case 'STATUS':
      return optionFor(field, raw)?.label ?? String(raw);
    case 'CURRENCY':
      return formatCurrency(raw);
    case 'PERCENT':
      return `${raw}%`;
    case 'DATE':
      return formatDate(raw, false);
    case 'DATETIME':
    case 'TIMESTAMP':
      return formatDate(raw, true);
    default:
      return String(raw);
  }
}

// rappresentazione ricca (dettaglio record): badge colorati per le scelte, link cliccabili per
// url/email/telefono, altrimenti testo formattato come sopra
export function fieldCell(field: FieldDef, raw: unknown): FieldCell {
  if (raw == null || raw === '') return { kind: 'text', text: '—' };

  if (field.type === 'SELECT' || field.type === 'STATUS' || field.type === 'MULTISELECT' || field.type === 'TAGS') {
    const values = Array.isArray(raw) ? raw : [raw];
    const chips = values.map((v) => {
      const o = optionFor(field, v);
      return { label: o?.label ?? String(v), color: o?.color };
    });
    return { kind: 'chips', text: '', chips };
  }
  if (typeof raw === 'string') {
    if (field.type === 'URL') return { kind: 'link', text: raw, href: raw };
    if (field.type === 'EMAIL') return { kind: 'link', text: raw, href: `mailto:${raw}` };
    if (field.type === 'PHONE') return { kind: 'link', text: raw, href: `tel:${raw}` };
  }
  return { kind: 'text', text: formatFieldValue(field, raw) };
}

function formatCurrency(raw: unknown): string {
  const n = Number(raw);
  if (Number.isNaN(n)) return String(raw);
  return n.toLocaleString('it-IT', { style: 'currency', currency: 'EUR' });
}

// le DATE arrivano come 'YYYY-MM-DD': formattate a mano per evitare lo shift di fuso di new Date()
function formatDate(raw: unknown, withTime: boolean): string {
  if (typeof raw === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    const [y, m, d] = raw.split('-');
    return `${d}/${m}/${y}`;
  }
  const dt = new Date(raw as string);
  if (Number.isNaN(dt.getTime())) return String(raw);
  return withTime ? dt.toLocaleString('it-IT') : dt.toLocaleDateString('it-IT');
}

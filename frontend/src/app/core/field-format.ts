import { FieldDef, FieldOption } from '../models/object-type';

import { DEFAULT_PREFERENCE, UserPreference } from '../models/user-preference';

let currentPref: UserPreference = DEFAULT_PREFERENCE;

export function setFormatPreference(pref: UserPreference): void {
  currentPref = pref;
}

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
    case 'RATING': {
      const n = Number(raw) || 0;
      const max = field.max ?? 5;
      return '★'.repeat(n) + '☆'.repeat(Math.max(0, max - n));
    }
    case 'DATE':
      return formatDate(raw, false);
    case 'DATETIME':
    case 'TIMESTAMP':
      return formatDate(raw, true);
    case 'NUMBER':
    case 'DECIMAL':
    case 'FLOAT': return formatNumber(Number(raw), currentPref.currencyDecimals);
    case 'INTEGER':
    case 'BIGINT': return formatNumber(Number(raw), 0);
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
    // COLOR: chip con il colore stesso come sfondo e l'esadecimale come etichetta
    if (field.type === 'COLOR') return { kind: 'chips', text: '', chips: [{ label: raw, color: raw }] };
    if (field.type === 'URL') return { kind: 'link', text: raw, href: raw };
    if (field.type === 'EMAIL') return { kind: 'link', text: raw, href: `mailto:${raw}` };
    if (field.type === 'PHONE') return { kind: 'link', text: raw, href: `tel:${raw}` };
  }
  return { kind: 'text', text: formatFieldValue(field, raw) };
}

function formatCurrency(raw: unknown): string {
  const n = Number(raw);
  if (Number.isNaN(n)) return String(raw);
  return withSymbol(formatNumber(n, currentPref.currencyDecimals));
}

// simbolo dalla valuta preferita, ricavato da Intl invece che da una tabella
// nostra: "EUR" -> "€", "USD" -> "$"; funziona per qualsiasi codice ISO 
function currencySymbol(): string {
  const parts = new Intl.NumberFormat('it-IT', {
    style: 'currency',
    currency: currentPref.currency,
  }).formatToParts(0);
  return parts.find((p) => p.type === 'currency')?.value ?? currentPref.currency;
}

function withSymbol(formatted: string): string {
  return currentPref.symbolPlacement === 'BEFORE'
    ? `${currencySymbol()} ${formatted}`
    : `${formatted} ${currencySymbol()}`;
}

// le DATE arrivano come 'YYYY-MM-DD': formattate a mano per evitare lo shift di fuso di new Date()
function formatDate(raw: unknown, withTime: boolean): string {
  if (typeof raw === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    const [y, m, d] = raw.split('-');
    return currentPref.dateFormat
      .replace('dd', d).replace('MM', m).replace('yyyy', y);
  }
  const dt = new Date(raw as string);
  if (Number.isNaN(dt.getTime())) return String(raw);
  return withTime ? dt.toLocaleString('it-IT') : dt.toLocaleDateString('it-IT');
}


// formattazione a mano e non con toLocaleString: i separatori
// sono scelti dall'utente e possono non corrispondere a nessun locale esistente
function formatNumber(n: number, decimals: number): string {
  const fixed = Math.abs(n).toFixed(decimals);
  let [intPart, decPart = ''] = fixed.split('.');

  if (currentPref.truncateTrailingZeros && decPart) {
    decPart = decPart.replace(/0+$/, '');
  }

  if (currentPref.groupSeparator) {
    intPart = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, currentPref.groupSeparator);
  }

  const sign = n < 0 ? '-' : '';
  return decPart ? `${sign}${intPart}${currentPref.decimalSeparator}${decPart}` : `${sign}${intPart}`;
}


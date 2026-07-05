import { Component, DestroyRef, computed, effect, inject, input, model, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, concatMap, from, map, of } from 'rxjs';

import { FieldDef } from '../../models/object-type';
import { RecordsService } from '../../services/records.service';

type Step = 'pick' | 'map' | 'run' | 'done';

interface RowError {
  row: number;
  message: string;
}

const MAX_ROWS = 500;
// tipi calcolati dal backend: non ha senso importarli
const COMPUTED_TYPES = new Set(['AUTONUMBER', 'FORMULA', 'ROLLUP']);

// Import CSV nella record-list: scegli il file, mappi le colonne sui campi del tipo, importa
// riga per riga (il RecordValidator backend coerce/valida ogni riga: gli errori tornano puntuali
// nel riepilogo). Parser CSV interno (virgolette, ;/,/tab autodetect, BOM) — nessuna dipendenza.
@Component({
  selector: 'app-record-import',
  standalone: true,
  templateUrl: './record-import.html'
})
export class RecordImport {
  readonly open = model(false);
  readonly objectKey = input.required<string>();
  readonly fields = input.required<FieldDef[]>();
  readonly imported = output<void>();

  private readonly recordsService = inject(RecordsService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly step = signal<Step>('pick');
  protected readonly error = signal<string | null>(null);
  protected readonly truncated = signal(false);

  protected readonly headers = signal<string[]>([]);
  protected readonly rows = signal<string[][]>([]);
  // indice colonna CSV -> key del campo destinazione ('' = ignora)
  protected readonly mapping = signal<Record<number, string>>({});

  protected readonly progress = signal(0);
  protected readonly okCount = signal(0);
  protected readonly skipped = signal(0);
  protected readonly failures = signal<RowError[]>([]);

  constructor() {
    // ogni apertura riparte pulita
    effect(() => {
      if (this.open()) this.reset();
    });
  }

  protected readonly eligibleFields = computed(() =>
    this.fields().filter((f) => !COMPUTED_TYPES.has(f.type))
  );

  protected readonly mappedCount = computed(
    () => Object.values(this.mapping()).filter((v) => v).length
  );

  // campi obbligatori non coperti dalla mappatura: l'import fallirebbe su ogni riga
  protected readonly requiredMissing = computed(() => {
    const mapped = new Set(Object.values(this.mapping()));
    return this.eligibleFields()
      .filter((f) => f.required && !mapped.has(f.key))
      .map((f) => f.label);
  });

  protected onFile(event: Event): void {
    const inputEl = event.target as HTMLInputElement;
    const file = inputEl.files?.[0];
    inputEl.value = '';
    if (!file) return;
    this.error.set(null);
    file.text().then((text) => {
      const parsed = parseCsv(text);
      if (parsed.length < 2) {
        this.error.set('Il file deve avere una riga di intestazione e almeno una riga di dati.');
        return;
      }
      const [header, ...data] = parsed;
      this.truncated.set(data.length > MAX_ROWS);
      this.headers.set(header.map((h) => h.trim()));
      this.rows.set(data.slice(0, MAX_ROWS));
      this.mapping.set(autoMap(header, this.eligibleFields()));
      this.step.set('map');
    });
  }

  protected setMapping(colIndex: number, fieldKey: string): void {
    this.mapping.update((m) => ({ ...m, [colIndex]: fieldKey }));
  }

  protected example(colIndex: number): string {
    return this.rows()[0]?.[colIndex] ?? '';
  }

  protected startImport(): void {
    if (this.mappedCount() === 0) return;
    this.step.set('run');
    this.progress.set(0);
    this.okCount.set(0);
    this.skipped.set(0);
    this.failures.set([]);

    const mapping = Object.entries(this.mapping()).filter(([, key]) => key) as [string, string][];
    const rows = this.rows();

    from(rows.map((cells, i) => ({ cells, i })))
      .pipe(
        concatMap(({ cells, i }) => {
          const data: Record<string, unknown> = {};
          for (const [col, key] of mapping) {
            const v = cells[Number(col)]?.trim();
            if (v !== undefined && v !== '') data[key] = v;
          }
          this.progress.set(i + 1);
          if (Object.keys(data).length === 0) {
            this.skipped.update((n) => n + 1);
            return of(null);
          }
          return this.recordsService.create(this.objectKey(), { data }, { silentErrors: true }).pipe(
            map(() => {
              this.okCount.update((n) => n + 1);
              return null;
            }),
            catchError((err) => {
              // +2: riga 1 = intestazione, e le righe dati partono da 1 per l'utente
              this.failures.update((f) => [
                ...f,
                { row: i + 2, message: err?.error?.message ?? 'Errore' }
              ]);
              return of(null);
            })
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        complete: () => {
          this.step.set('done');
          if (this.okCount() > 0) this.imported.emit();
        }
      });
  }

  protected reset(): void {
    this.step.set('pick');
    this.error.set(null);
    this.truncated.set(false);
    this.headers.set([]);
    this.rows.set([]);
    this.mapping.set({});
    this.progress.set(0);
    this.okCount.set(0);
    this.skipped.set(0);
    this.failures.set([]);
  }

  protected close(): void {
    this.open.set(false);
  }

  protected val(e: Event): string {
    return (e.target as HTMLSelectElement).value;
  }
}

// mappatura automatica: header CSV ↔ key/label del campo, confronto normalizzato
function autoMap(header: string[], fields: FieldDef[]): Record<number, string> {
  const norm = (s: string) => s.toLowerCase().normalize('NFD').replace(/[^a-z0-9]/g, '');
  const byNorm = new Map<string, string>();
  for (const f of fields) {
    byNorm.set(norm(f.key), f.key);
    byNorm.set(norm(f.label), f.key);
  }
  const result: Record<number, string> = {};
  header.forEach((h, i) => {
    result[i] = byNorm.get(norm(h)) ?? '';
  });
  return result;
}

// parser CSV minimale ma corretto: virgolette con escape "" , delimitatore autodetect (; , tab),
// BOM iniziale rimosso, righe vuote scartate
function parseCsv(text: string): string[][] {
  const clean = text.replace(/^\uFEFF/, '');
  const firstLine = clean.split(/\r?\n/, 1)[0] ?? '';
  const delim = detectDelimiter(firstLine);

  const rows: string[][] = [];
  let row: string[] = [];
  let cur = '';
  let inQuotes = false;

  const pushRow = () => {
    row.push(cur);
    cur = '';
    if (row.length > 1 || row[0].trim() !== '') rows.push(row);
    row = [];
  };

  for (let i = 0; i < clean.length; i++) {
    const c = clean[i];
    if (inQuotes) {
      if (c === '"') {
        if (clean[i + 1] === '"') {
          cur += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        cur += c;
      }
    } else if (c === '"') {
      inQuotes = true;
    } else if (c === delim) {
      row.push(cur);
      cur = '';
    } else if (c === '\n' || c === '\r') {
      if (c === '\r' && clean[i + 1] === '\n') i++;
      pushRow();
    } else {
      cur += c;
    }
  }
  if (cur !== '' || row.length > 0) pushRow();
  return rows;
}

function detectDelimiter(line: string): string {
  const counts: [string, number][] = [
    [';', (line.match(/;/g) ?? []).length],
    [',', (line.match(/,/g) ?? []).length],
    ['\t', (line.match(/\t/g) ?? []).length]
  ];
  counts.sort((a, b) => b[1] - a[1]);
  return counts[0][1] > 0 ? counts[0][0] : ';';
}

import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { catchError, combineLatest, filter, forkJoin, map, of, switchMap, tap } from 'rxjs';
import { ChevronLeft, ChevronRight, ExternalLink, LucideAngularModule } from 'lucide-angular';

import { UiSpinner } from '../../components/ui/spinner';
import { ObjectType } from '../../models/object-type';
import { ObjectTypeService } from '../../services/object-type.service';
import { RecordsService } from '../../services/records.service';
import { Workspace } from '../../services/workspace';
import { currentLocale } from '../../services/i18n';

// evento del calendario globale: record di un qualunque tipo con campo data, nel mese corrente
interface CalEvent {
  id: string;
  title: string;
  day: number;
  typeKey: string;
  typeLabel: string;
  color: string | null;
}

interface Cell {
  day: number | null;
  events: CalEvent[];
}

const MAX_TYPES = 6; // limite di tipi interrogati per mese (una richiesta per tipo)

// Calendario globale: vista mensile che fonde i record di TUTTI i tipi abilitati con un campo
// DATE/DATETIME (primo campo data di ciascuno), color-coded per tipo. Sotto, l'embed Cal.com
// (link configurato in Impostazioni -> Azienda) per far prenotare appuntamenti dall'esterno.
@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [RouterLink, UiSpinner, LucideAngularModule],
  templateUrl: './calendar.html'
})
export class Calendar {
  private readonly objectTypeService = inject(ObjectTypeService);
  private readonly recordsService = inject(RecordsService);
  private readonly sanitizer = inject(DomSanitizer);
  protected readonly workspace = inject(Workspace);

  protected readonly PrevIcon = ChevronLeft;
  protected readonly NextIcon = ChevronRight;
  protected readonly ExternalIcon = ExternalLink;

  protected readonly loading = signal(true);
  // primo giorno del mese mostrato
  protected readonly cursor = signal(startOfMonth(new Date()));

  constructor() {
    // arrivo diretto sulla pagina: gli object type potrebbero non essere ancora caricati
    if (!this.objectTypeService.objects()) {
      this.objectTypeService.load().subscribe();
    }
  }

  protected readonly monthLabel = computed(() =>
    this.cursor().toLocaleDateString(currentLocale(), { month: 'long', year: 'numeric' })
  );

  protected readonly weekdays = ['Lun', 'Mar', 'Mer', 'Gio', 'Ven', 'Sab', 'Dom'];

  // eventi del mese: una query per tipo (tra inizio e fine mese, sul primo campo data del tipo)
  private readonly events = toSignal(
    combineLatest([
      toObservable(this.cursor),
      toObservable(this.objectTypeService.objects).pipe(filter((o): o is ObjectType[] => o !== null))
    ]).pipe(
      tap(() => this.loading.set(true)),
      switchMap(([cur, objs]) => {
        const from = fmtDate(cur);
        const last = new Date(cur.getFullYear(), cur.getMonth() + 1, 0);
        const to = fmtDate(last) + 'T23:59:59';

        const withDate = objs
          .filter((o) => o.enabled)
          .map((o) => ({ obj: o, dateField: o.fields.find((f) => f.type === 'DATE' || f.type === 'DATETIME') }))
          .filter((x): x is { obj: ObjectType; dateField: NonNullable<typeof x.dateField> } => !!x.dateField)
          .slice(0, MAX_TYPES);

        if (withDate.length === 0) return of<CalEvent[]>([]);

        return forkJoin(
          withDate.map(({ obj, dateField }) =>
            this.recordsService
              .queryAdvanced(obj.key, {
                pageSize: 200,
                filter: {
                  combinator: 'and',
                  conditions: [{ field: dateField.key, op: 'between', value: from, value2: to }]
                },
                sort: [{ field: dateField.key, dir: 'asc' }]
              })
              .pipe(
                map((r) =>
                  r.items
                    .map((rec) => ({
                      id: rec.id,
                      title: rec.title || '(senza titolo)',
                      day: dayOfMonth(String(rec.data[dateField.key] ?? ''), cur),
                      typeKey: obj.key,
                      typeLabel: obj.label,
                      color: obj.color
                    }))
                    .filter((e) => e.day > 0)
                ),
                catchError(() => of<CalEvent[]>([]))
              )
          )
        ).pipe(map((lists) => lists.flat()));
      }),
      tap(() => this.loading.set(false))
    ),
    { initialValue: [] as CalEvent[] }
  );

  // legenda: i tipi effettivamente presenti nel mese
  protected readonly legend = computed(() => {
    const seen = new Map<string, CalEvent>();
    for (const e of this.events()) if (!seen.has(e.typeKey)) seen.set(e.typeKey, e);
    return [...seen.values()];
  });

  // griglia: celle vuote di offset (settimana che parte da lunedì) + un giorno per cella
  protected readonly cells = computed<Cell[]>(() => {
    const cur = this.cursor();
    const daysInMonth = new Date(cur.getFullYear(), cur.getMonth() + 1, 0).getDate();
    const offset = (cur.getDay() + 6) % 7; // getDay: 0=domenica -> lunedì-based
    const byDay = new Map<number, CalEvent[]>();
    for (const e of this.events()) {
      if (!byDay.has(e.day)) byDay.set(e.day, []);
      byDay.get(e.day)!.push(e);
    }
    const cells: Cell[] = Array.from({ length: offset }, () => ({ day: null, events: [] }));
    for (let d = 1; d <= daysInMonth; d++) {
      cells.push({ day: d, events: byDay.get(d) ?? [] });
    }
    return cells;
  });

  protected isToday(day: number | null): boolean {
    if (day == null) return false;
    const now = new Date();
    const cur = this.cursor();
    return now.getFullYear() === cur.getFullYear() && now.getMonth() === cur.getMonth() && now.getDate() === day;
  }

  protected prevMonth(): void {
    const c = this.cursor();
    this.cursor.set(new Date(c.getFullYear(), c.getMonth() - 1, 1));
  }

  protected nextMonth(): void {
    const c = this.cursor();
    this.cursor.set(new Date(c.getFullYear(), c.getMonth() + 1, 1));
  }

  protected goToday(): void {
    this.cursor.set(startOfMonth(new Date()));
  }

  // ── embed Cal.com ───────────────────────────────────────────────────────────
  protected readonly calcomUrl = computed(() => this.workspace.workspace()?.calcomUrl ?? null);

  // l'iframe richiede bypassSecurityTrustResourceUrl: lo concediamo SOLO a host cal.com
  // (l'URL arriva dalla config admin, ma restringere il trust e' comunque la scelta prudente)
  protected readonly calcomEmbedUrl = computed<SafeResourceUrl | null>(() => {
    const url = this.calcomUrl();
    if (!url) return null;
    try {
      const host = new URL(url).hostname;
      if (host === 'cal.com' || host.endsWith('.cal.com')) {
        return this.sanitizer.bypassSecurityTrustResourceUrl(url);
      }
    } catch {
      // URL malformato: nessun embed
    }
    return null;
  });
}

function startOfMonth(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), 1);
}

function fmtDate(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

// giorno del mese (1..31) se la data cade nel mese del cursore, altrimenti 0.
// Le date-only YYYY-MM-DD si leggono dai primi 10 caratteri (niente shift di fuso).
function dayOfMonth(value: string, cursor: Date): number {
  if (!value) return 0;
  let y: number, m: number, d: number;
  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (dateOnly && value.length <= 10) {
    y = Number(dateOnly[1]);
    m = Number(dateOnly[2]) - 1;
    d = Number(dateOnly[3]);
  } else {
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return 0;
    y = parsed.getFullYear();
    m = parsed.getMonth();
    d = parsed.getDate();
  }
  return y === cursor.getFullYear() && m === cursor.getMonth() ? d : 0;
}

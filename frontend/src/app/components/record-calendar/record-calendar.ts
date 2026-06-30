import { Component, computed, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ChevronLeft, ChevronRight, LucideAngularModule } from 'lucide-angular';

import { FieldDef } from '../../models/object-type';
import { RecordItem } from '../../models/record';

// Vista calendario mensile per i record di un object type, plottati sul campo DATE/DATETIME scelto.
// Metadata-driven: funziona per qualunque tipo che abbia un campo data (deal.closeDate,
// task.dueDate, calendar_event.start, ...), diversamente dal CalendarView del reference che era
// hardcoded su 4 tipi/chiavi fissi. Sola lettura: le card linkano al dettaglio.
@Component({
  selector: 'app-record-calendar',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './record-calendar.html'
})
export class RecordCalendar {
  readonly items = input.required<RecordItem[]>();
  readonly dateField = input.required<FieldDef>();
  readonly objectKey = input.required<string>();

  protected readonly ChevronLeftIcon = ChevronLeft;
  protected readonly ChevronRightIcon = ChevronRight;

  protected readonly weekdays = ['Lun', 'Mar', 'Mer', 'Gio', 'Ven', 'Sab', 'Dom'];

  // primo giorno del mese visualizzato (l'ora non conta, si naviga di mese in mese)
  protected readonly cursor = signal<Date>(startOfMonth(new Date()));

  protected readonly monthLabel = computed(() =>
    this.cursor().toLocaleDateString('it-IT', { month: 'long', year: 'numeric' })
  );

  // record del mese corrente indicizzati per giorno, calcolati una volta sola per render
  private readonly byDay = computed(() => {
    const c = this.cursor();
    const map = new Map<number, RecordItem[]>();
    for (const rec of this.items()) {
      const d = this.recordDate(rec);
      if (!d || d.getFullYear() !== c.getFullYear() || d.getMonth() !== c.getMonth()) continue;
      const bucket = map.get(d.getDate());
      if (bucket) bucket.push(rec);
      else map.set(d.getDate(), [rec]);
    }
    return map;
  });

  // celle della griglia: padding iniziale (lun=0) + i giorni del mese con i relativi record
  protected readonly cells = computed(() => {
    const c = this.cursor();
    const year = c.getFullYear();
    const month = c.getMonth();
    const firstOffset = (new Date(year, month, 1).getDay() + 6) % 7;
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const byDay = this.byDay();

    const out: { day: number | null; items: RecordItem[] }[] = [];
    for (let i = 0; i < firstOffset; i++) out.push({ day: null, items: [] });
    for (let d = 1; d <= daysInMonth; d++) out.push({ day: d, items: byDay.get(d) ?? [] });
    return out;
  });

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

  protected isToday(day: number): boolean {
    const t = new Date();
    const c = this.cursor();
    return t.getFullYear() === c.getFullYear() && t.getMonth() === c.getMonth() && t.getDate() === day;
  }

  // legge il valore del campo data dal record. Le stringhe date-only (YYYY-MM-DD) si parsano in
  // locale, non via new Date() che le interpreta come UTC mezzanotte -> off-by-one a fusi negativi.
  private recordDate(rec: RecordItem): Date | null {
    const raw = rec.data[this.dateField().key];
    if (raw == null || raw === '') return null;
    if (typeof raw === 'string') {
      const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(raw);
      if (m) return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
    }
    const d = new Date(raw as string | number);
    return isNaN(d.getTime()) ? null : d;
  }
}

function startOfMonth(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), 1);
}

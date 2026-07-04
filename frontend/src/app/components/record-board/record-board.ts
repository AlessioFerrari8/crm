import { Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { FieldDef, FieldOption } from '../../models/object-type';
import { RecordItem } from '../../models/record';
import { RecordsService } from '../../services/records.service';

// giorni senza aggiornamenti oltre i quali una card e' considerata "ferma" (rotting)
const STALE_DAYS = 14;

// Vista Kanban: una colonna per ogni opzione del campo STATUS, le card sono i record con quello
// status. Le card si trascinano tra le colonne (drag & drop nativo HTML5): al drop viene
// PATCHato il campo status del record e il parent viene avvisato via (changed) per ricaricare.
@Component({
  selector: 'app-record-board',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './record-board.html'
})
export class RecordBoard {
  readonly items = input.required<RecordItem[]>();
  readonly statusField = input.required<FieldDef>();
  readonly objectKey = input.required<string>();
  // tutti i campi del tipo: servono a trovare il campo CURRENCY per i totali di colonna
  readonly fields = input<FieldDef[]>([]);

  // notifica il parent dopo uno spostamento riuscito (ricarica la lista)
  readonly changed = output<void>();

  private readonly recordsService = inject(RecordsService);
  private readonly destroyRef = inject(DestroyRef);

  // colonna evidenziata durante il trascinamento; il record trascinato è stato imperativo
  // (vive solo dentro la gesture, non serve reattività)
  protected readonly dragOver = signal<string | null>(null);
  protected readonly saving = signal(false);
  private dragging: RecordItem | null = null;

  // primo campo CURRENCY del tipo: abilita i totali di valore per colonna (alla Pipedrive)
  private readonly currencyField = computed<FieldDef | null>(
    () => this.fields().find((f) => f.type === 'CURRENCY') ?? null
  );

  // raggruppa i record per valore di status, seguendo l'ordine delle opzioni del campo;
  // per ogni colonna calcola anche il totale del campo valuta (se il tipo ne ha uno)
  protected readonly groups = computed(() => {
    const options: FieldOption[] = this.statusField().options ?? [];
    const items = this.items();
    const cf = this.currencyField();
    return options.map((o) => {
      const records = items.filter((r) => r.status === o.value);
      let total: number | null = null;
      if (cf) {
        total = records.reduce((sum, r) => {
          const v = Number(r.data[cf.key]);
          return sum + (Number.isFinite(v) ? v : 0);
        }, 0);
      }
      return { ...o, records, total };
    });
  });

  protected formatTotal(total: number): string {
    return total.toLocaleString('it-IT', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });
  }

  // "rotting" alla Pipedrive: record fermo (nessun aggiornamento) da troppi giorni -> evidenziato
  protected staleDays(rec: RecordItem): number {
    const updated = new Date(rec.updatedAt).getTime();
    if (Number.isNaN(updated)) return 0;
    return Math.floor((Date.now() - updated) / 86_400_000);
  }

  protected isStale(rec: RecordItem): boolean {
    return this.staleDays(rec) >= STALE_DAYS;
  }

  protected onDragStart(rec: RecordItem): void {
    this.dragging = rec;
  }

  protected onDragEnd(): void {
    this.dragging = null;
    this.dragOver.set(null);
  }

  // preventDefault è obbligatorio per rendere la colonna un drop target valido
  protected onDragOver(event: DragEvent, value: string): void {
    event.preventDefault();
    this.dragOver.set(value);
  }

  protected onDrop(value: string): void {
    const rec = this.dragging;
    this.dragging = null;
    this.dragOver.set(null);
    if (!rec || rec.status === value || this.saving()) return;
    this.saving.set(true);
    this.recordsService
      .update(this.objectKey(), rec.id, { data: { [this.statusField().key]: value } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.changed.emit();
        },
        error: () => this.saving.set(false)
      });
  }
}

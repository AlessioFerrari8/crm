import { Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { FieldDef, FieldOption } from '../../models/object-type';
import { RecordItem } from '../../models/record';
import { RecordsService } from '../../services/records.service';

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

  // notifica il parent dopo uno spostamento riuscito (ricarica la lista)
  readonly changed = output<void>();

  private readonly recordsService = inject(RecordsService);
  private readonly destroyRef = inject(DestroyRef);

  // colonna evidenziata durante il trascinamento; il record trascinato è stato imperativo
  // (vive solo dentro la gesture, non serve reattività)
  protected readonly dragOver = signal<string | null>(null);
  protected readonly saving = signal(false);
  private dragging: RecordItem | null = null;

  // raggruppa i record per valore di status, seguendo l'ordine delle opzioni del campo
  protected readonly groups = computed(() => {
    const options: FieldOption[] = this.statusField().options ?? [];
    const items = this.items();
    return options.map((o) => ({
      ...o,
      records: items.filter((r) => r.status === o.value)
    }));
  });

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

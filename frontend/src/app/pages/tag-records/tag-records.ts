import { DatePipe } from '@angular/common';
import { Component, computed, DestroyRef, effect, inject, signal, WritableSignal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { UiSpinner } from '../../components/ui/spinner';
import { TagService } from '../../services/tag.service';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of } from 'rxjs';
import { RecordItem } from '../../models/record';


// Record che portano un tag, attraverso i moduli: è la metà utile dei tag, perché attaccarne uno
// senza poterlo poi cercare non serve assolutamente a nulla

// L'endpoint `GET /api/tags/{id}/records` torna Record con objectType annidato, quindi il link al
// dettaglio costruisce senza una seconda chiamata
@Component({
  selector: 'app-tag-records',
  imports: [RouterLink, DatePipe, UiSpinner],
  templateUrl: './tag-records.html',
  styles: ``,
})
export class TagRecords {
  // services
  private readonly route = inject(ActivatedRoute);
  private readonly tags = inject(TagService);
  private readonly destroyRef = inject(DestroyRef);

  // in app.config.ts non c'è withComponentInputBinding(), quindi per questo motivo il parametro si legge
  // dalla rotta
  protected readonly tagId = toSignal(
    this.route.paramMap.pipe(map((p) => p.get('tagId') ?? '')),
    { initialValue: '' },
  );

  protected readonly rows: WritableSignal<RecordItem[]> = signal([]);
  protected readonly loading: WritableSignal<boolean> = signal(true);

  // non esiste un GET /api/tags/{id} singolo: il nome del tag si pesca dall'elenco globale già in memoria nel service
  protected readonly tag = computed(
    () => this.tags.all().find((t) => t.id === this.tagId()) ?? null,
  );

  constructor() {
    // l'elenco globale si carica una volta sola per tutta l'app (stesso patto del TagPicker)
    if (this.tags.all().length === 0) {
      this.tags
        .load()
        .pipe(
          catchError(() => of([])),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe();
    }

    // ricarica a ogni cambio di :tagId, anche restando sulla stessa rotta
    effect(() => {
      const id = this.tagId();
      if (!id) return;

      this.loading.set(true);
      this.tags
        .records(id)
        .pipe(
          catchError(() => of<RecordItem[]>([])),
          // il destroyRef va passato, dato che non siamo nel contesto di injection
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe((rows) => {
          this.rows.set(rows);
          this.loading.set(false);
        });
    });
  }

}

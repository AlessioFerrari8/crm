import { ObjectTypeService } from './../../services/object-type.service';
import { Component, computed, DestroyRef, inject, signal, WritableSignal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UiButton } from '../../components/ui/button';
import { UiSpinner } from '../../components/ui/spinner';
import { DuplicateCandidate, MergeService } from '../../services/merge.service';
import { RecordsService } from '../../services/records.service';
import { ToastService } from '../../services/toast.service';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { catchError, forkJoin, of, takeUntil } from 'rxjs';
import { FieldDef, ObjectType } from '../../models/object-type';
import { RecordItem } from '../../models/record';
import { formatFieldValue } from '../../core/field-format';

// duplicati di un modulo e fusione affiancata. Il flusso è il seguente:
// scegli il modulo -> lista delle coppie somiglianti -> confronto campo per campo
// -> scegli quale valore tenere -> fondi


@Component({
  selector: 'app-duplicates',
  imports: [FormsModule, UiButton, UiSpinner],
  templateUrl: './duplicates.html',
  styles: ``,
})
export class Duplicates {
  // services
  private readonly mergeService = inject(MergeService);
  private readonly recordsService = inject(RecordsService);
  private readonly objectTypeService = inject(ObjectTypeService);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly objects = toSignal(
    this.objectTypeService.load().pipe(catchError(() => of<ObjectType[]>([]))),
    { initialValue: [] as ObjectType[] }
  );

  protected readonly objectKey = signal('');
  protected readonly threshold: WritableSignal<number> = signal(0.6);
  protected readonly loading: WritableSignal<boolean> = signal(false);
  protected readonly candidates: WritableSignal<DuplicateCandidate[]> = signal([]);

  // confronto aperto: i due record interi (lista ha solo i titoli)
  protected readonly left: WritableSignal<RecordItem | null> = signal(null);
  protected readonly right: WritableSignal<RecordItem | null> = signal(null);
  protected readonly winnerIsLeft: WritableSignal<boolean> = signal(true);

  // chiavi per cui l'utente ha scelto il valore precedente
  protected readonly fromLoser: WritableSignal<Set<string>> = signal(new Set());
  protected readonly merging: WritableSignal<boolean> = signal(false);

  protected readonly fields = computed<FieldDef[]>(() => {
    const obj = this.objects().find((o) => o.key === this.objectKey());
    return (obj?.fields ?? []).filter((f) => !f.hidden).sort((a, b) => a.sortOrder - b.sortOrder);
  });

  protected search(): void {
    if (!this.objectKey()) return;

    this.loading.set(true);
    this.closeCompare();
    this.mergeService.duplicates(this.objectKey(), this.threshold())
      .pipe(catchError(() => of<DuplicateCandidate[]>([])), takeUntilDestroyed(this.destroyRef))
      .subscribe((rows) => {
        this.candidates.set(rows);
        this.loading.set(false);
      });
  }

  // due findOne in parallelo. La lista dei candidati ha solo gli id e i titoli, per il confronto
  // servono i dati interi
  protected compare(dc: DuplicateCandidate): void {
    forkJoin({
      left: this.recordsService.findOne(this.objectKey(), dc.leftId),
      right: this.recordsService.findOne(this.objectKey(), dc.rightId),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ left, right }) => {
        this.left.set(left.record);
        this.right.set(right.record);
        this.winnerIsLeft.set(true);
        this.fromLoser.set(new Set());
      });
  }

  protected closeCompare(): void {
    this.left.set(null);
    this.right.set(null);
  }

  protected winner(): RecordItem | null {
    return this.winnerIsLeft() ? this.left() : this.right();
  }

  // stesso meccanismo winner
  protected loser(): RecordItem | null {
    return this.winnerIsLeft() ? this.right() : this.left();
  }

  protected valueOf(rec: RecordItem | null, fd: FieldDef): string {
    if (!rec) return '-';

    const raw = fd.key === 'status' ? rec.status : rec.data[fd.key];
    return formatFieldValue(fd, raw);
  }

  // differenza: si evidenziano solo i campi dove i due record non coincidono
  protected differs(fd: FieldDef): boolean {
    return this.valueOf(this.left(), fd) !== this.valueOf(this.right(), fd);
  }

  protected toggleField(key: string): void  {
    this.fromLoser.update((set) => {
      const next = new Set(set);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  }

  protected keeps(fd: FieldDef): 'winner' | 'loser' {
    return this.fromLoser().has(fd.key) ? 'loser' : 'winner';
  }

  protected doMerge(): void {
    const w = this.winner();
    const l = this.loser();

    if (!w || !l || this.merging()) return;
    this.merging.set(true);
    this.mergeService.merge(w.id, l.id, [...this.fromLoser()])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.merging.set(false);
          this.toasts.success('Record uniti. Il duplicato è nel cestino.');
          this.closeCompare();
          this.search();
        },
        error: () => this.merging.set(false)
      });
  }

}

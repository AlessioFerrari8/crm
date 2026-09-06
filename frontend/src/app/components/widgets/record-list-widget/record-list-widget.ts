import {
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  signal,
  WritableSignal,
  OnInit
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { RecordsService } from '../../../services/records.service';
import { ObjectTypeService } from '../../../services/object-type.service';
import { DashboardWidget } from '../../../models/dashboard';
import { RecordItem } from '../../../models/record';
import { FieldDef } from '../../../models/object-type';
import { catchError, of, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { formatFieldValue } from '../../../core/field-format';

// "Ultimi 5 lead", "I miei ticket aperti": una lista di record, non un'aggregazione
// Quindi non passa da /api/reports/run, usa il motore delle liste (queryAdvanced)
// che ha già filtri, ordinamenti e RBAC
@Component({
  selector: 'app-record-list-widget',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './record-list-widget.html',
})
export class RecordListWidget implements OnInit {
  // servizi vari
  private readonly recordsService = inject(RecordsService);
  private readonly objectTypes = inject(ObjectTypeService);
  private readonly destroyRef = inject(DestroyRef);

  readonly widget = input.required<DashboardWidget>();

  protected readonly rows: WritableSignal<RecordItem[]> = signal([]);
  protected readonly columns: WritableSignal<FieldDef[]> = signal([]);
  protected readonly loading: WritableSignal<boolean> = signal(true);

  protected readonly objectKey = computed(() => String(this.widget().query['objectKey'] ?? ''));

  ngOnInit(): void {
    const q = this.widget().query;

    this.objectTypes
      .getByKey(this.objectKey())
      .pipe(
        catchError(() => of(null)),
        switchMap((obj) => {
          // le colonne sono quelle marcate showInList; se cambiassero lì, cambiano anche qui
          this.columns.set((obj?.fields ?? []).filter((f) => f.showInList).slice(0, 3));
          return this.recordsService
            .queryAdvanced(this.objectKey(), {
              filter: (q['filter'] as never) ?? undefined,
              sort: (q['sort'] as never) ?? undefined,
              pageSize: Number(q['limit'] ?? 5),
            })
            .pipe(catchError(() => of({ items: [] as RecordItem[] } as never)));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((res) => {
        this.rows.set(res?.items ?? []);
        this.loading.set(false);
      });
  }

  protected value(rec: RecordItem, f: FieldDef): string {
    return formatFieldValue(f, f.key === 'status' ? rec.status : rec.data[f.key]);
  }
}

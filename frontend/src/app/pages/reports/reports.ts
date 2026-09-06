import { Component, DestroyRef, WritableSignal, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { catchError, of, takeUntil } from 'rxjs';
import { UiButton } from '../../components/ui/button';
import { UiSpinner } from '../../components/ui/spinner';
import { dimLabel } from '../../core/widget-data';
import { Aggregate, Grain, Metric, Report, ReportQuery, ReportResult } from '../../models/report';
import { FieldDef, ObjectType } from '../../models/object-type';
import { ConfirmService } from '../../services/confirm.service';
import { ObjectTypeService } from '../../services/object-type.service';
import { ReportService } from '../../services/report.service';
import { ToastService } from '../../services/toast.service';
import { FilterGroup } from '../../models/filter';
import { FilterBuilder } from '../../components/filter-builder/filter-builder';

const EMPTY_FILTER: FilterGroup = { combinator: 'and', conditions: [] };

// Lista dei report (raggruppati per cartella) + costruttore con anteprima dal vivo.
// L'anteprima è quello che rende il wizard usabile: si vede il risultato mentre
// si compone, senza salvare niente
@Component({
  selector: 'app-reports',
  imports: [FormsModule, UiButton, UiSpinner, FilterBuilder],
  templateUrl: './reports.html',
  styles: ``,
})
export class Reports {
  // services
  private readonly reports = inject(ReportService);
  private readonly objectTypes = inject(ObjectTypeService);
  private readonly toasts = inject(ToastService);
  private readonly confirmService = inject(ConfirmService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly objects = toSignal(
    this.objectTypes.load().pipe(catchError(() => of<ObjectType[]>([]))),
    { initialValue: [] as ObjectType[] },
  );

  protected readonly list: WritableSignal<Report[]> = signal([]);
  protected readonly loading: WritableSignal<boolean> = signal(true);
  protected readonly result: WritableSignal<ReportResult | null> = signal(null);
  protected readonly running: WritableSignal<boolean> = signal(false);

  // la bozza del wizard
  protected readonly draft: WritableSignal<ReportQuery> = signal({
    objectKey: '',
    rows: [],
    columns: [],
    metrics: [],
  });
  protected readonly title = signal('');
  protected readonly render: WritableSignal<Report['render']> = signal('GRID');
  protected readonly folder = signal('');
  protected readonly editorOpen: WritableSignal<boolean> = signal(false);
  protected readonly editingId: WritableSignal<string | null> = signal(null);

  protected readonly GRAINS: Grain[] = ['NONE', 'DAY', 'WEEK', 'MONTH', 'QUARTER', 'YEAR'];
  protected readonly AGGREGATES: Aggregate[] = [
    'COUNT',
    'SUM',
    'AVG',
    'MIN',
    'MAX',
    'AVG_DAYS_BETWEEN',
  ];

  protected readonly fieldKeys = computed(() => {
    const obj = this.objects().find((o) => o.key === this.draft().objectKey);
    return [
      'status',
      'owner_id',
      'created_at',
      'updated_at',
      ...(obj?.fields ?? []).map((f) => f.key),
    ];
  });

  // i campi selezionabili: quelli del modulo scelto più le colonne vere della
  // tabella record, che nel FieldDef non compaiono, ma il motore le accetta comunque
  protected readonly byFolder = computed(() => {
    const groups = new Map<string, Report[]>();

    for (const r of this.list()) {
      const key = r.folder || 'Senza cartella';
      groups.set(key, [...(groups.get(key) ?? []), r]);
    }
    return [...groups.entries()];
  });

  // i campi filtrabili sono quelli del modulo scelto nel wizard, non tutti
  protected readonly filterFields = computed<FieldDef[]>(() =>
    this.objects().find((o) => o.key === this.draft().objectKey)?.fields ?? [],
  );

  // lettura del filtro dalla bozza: il valore vive dentor ReportQuery, qui non si duplica lo stato
  protected readonly filterValue = computed<FilterGroup>(() => this.draft().filter ?? EMPTY_FILTER);

  constructor() {
    this.reload();
  }

  private reload(): void {
    this.loading.set(true);
    this.reports
      .list()
      .pipe(
        catchError(() => of<Report[]>([])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((rows) => {
        this.list.set(rows);
        this.loading.set(false);
      });
  }

  // wizard
  protected newReport(): void {
    this.editingId.set(null);
    this.title.set('');
    this.folder.set('');
    this.render.set('GRID');
    this.draft.set({
      objectKey: '',
      rows: [],
      columns: [],
      metrics: [{ aggregate: 'COUNT', field: null, secondField: null, label: 'Numero' }],
    });

    this.result.set(null);
    this.editorOpen.set(true);
  }

  protected edit(r: Report): void {
    this.editingId.set(r.id);
    this.title.set(r.title);
    this.folder.set(r.folder ?? '');
    this.render.set(r.render);
    this.draft.set({ ...r.query });
    this.editorOpen.set(true);
    this.preview();
  }

  protected patch(part: Partial<ReportQuery>): void {
    this.draft.update((q) => ({ ...q, ...part }));
  }

  protected addDimension(where: 'rows' | 'columns'): void {
    this.draft.update((q) => ({
      ...q,
      [where]: [...(q[where] ?? []), { field: 'status', grain: 'NONE' as Grain }],
    }));
  }

  protected setDimension(
    where: 'rows' | 'columns',
    i: number,
    part: Partial<{ field: string; grain: Grain }>,
  ): void {
    this.draft.update((q) => ({
      ...q,
      [where]: (q[where] ?? []).map((d, idx) => (idx === i ? { ...d, ...part } : d)),
    }));
  }

  protected removeDimension(where: 'rows' | 'columns', i: number): void {
    this.draft.update((q) => ({ ...q, [where]: (q[where] ?? []).filter((_, idx) => idx !== i) }));
  }

  protected addMetric(): void {
    this.draft.update((q) => ({
      ...q,
      metrics: [
        ...q.metrics,
        { aggregate: 'COUNT', field: null, secondField: null, label: 'Numero' },
      ],
    }));
  }

  protected setMetric(i: number, part: Partial<Metric>): void {
    this.draft.update((q) => ({
      ...q,
      metrics: q.metrics.map((m, idx) => (idx === i ? { ...m, ...part } : m)),
    }));
  }

  protected removeMetric(i: number): void {
    this.draft.update((q) => ({ ...q, metrics: q.metrics.filter((_, idx) => idx !== i) }));
  }

  protected preview(): void {
    if (!this.draft().objectKey) return;

    this.running.set(true);
    this.reports
      .preview(this.draft())
      .pipe(
        catchError(() => of(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((r) => {
        this.result.set(r);
        this.running.set(false);
      });
  }

  protected save(): void {
    const body = {
      title: this.title().trim() || 'Report senza titolo',
      render: this.render(),
      objectKey: this.draft().objectKey,
      folder: this.folder().trim() || null,
      personal: true,
      query: this.draft(),
    };
    const call = this.editingId()
      ? this.reports.update(this.editingId()!, body as never)
      : this.reports.create(body as never);

    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.toasts.success('Report salvato');
      this.editorOpen.set(false);
      this.reload();
    });
  }

  protected remove(r: Report): void {
    this.confirmService.ask(`Eliminare "${r.title}"?`).then((ok) => {
      if (!ok) return;

      this.reports
        .remove(r.id)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.reload());
    });
  }

  protected duplicate(r: Report): void {
    this.reports
      .duplicate(r.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload());
  }

  protected run(r: Report): void {
    this.running.set(true);
    this.reports
      .run(r.id)
      .pipe(
        catchError(() => of(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((res) => {
        this.result.set(res);
        this.running.set(false);
      });
  }

  protected label(v: unknown): string {
    return dimLabel(v);
  }

  // scrittura: un gruppo senza condizioni si salva come null, sennò ogni report si porta dietro
  // un filtro vuoto che non filtra niente
  protected setFilter(group: FilterGroup): void {
    this.patch({ filter: group.conditions.length > 0 ? group : null });
  }
}

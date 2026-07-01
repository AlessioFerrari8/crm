import { Component, DestroyRef, WritableSignal, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, catchError, combineLatest, filter, map, of, startWith, switchMap, tap } from 'rxjs';

import { Drawer } from '../../components/drawer/drawer';
import { FilterBuilder } from '../../components/filter-builder/filter-builder';
import { DynamicForm } from '../../components/dynamic-form/dynamic-form';
import { UiButton } from '../../components/ui/button';
import { UiSpinner } from '../../components/ui/spinner';
import { FilterGroup } from '../../models/filter';
import { formatFieldValue } from '../../core/field-format';
import { FieldDef } from '../../models/object-type';
import { RecordItem } from '../../models/record';
import { RecordsService } from '../../services/records.service';
import { RecordBoard } from '../../components/record-board/record-board';
import { RecordCalendar } from '../../components/record-calendar/record-calendar';
import { Calendar, Columns3, LayoutList, LucideAngularModule } from 'lucide-angular';

type RecordView = 'table' | 'board' | 'calendar';

@Component({
  selector: 'app-record-list',
  standalone: true,
  imports: [Drawer, FilterBuilder, DynamicForm, UiButton, UiSpinner, RecordBoard, RecordCalendar, LucideAngularModule],
  templateUrl: './record-list.html'
})
export class RecordList {
  // serivizi vari
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly recordsService = inject(RecordsService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly loading: WritableSignal<boolean> = signal(true);
  protected readonly drawerOpen: WritableSignal<boolean> = signal(false);
  protected readonly submitting: WritableSignal<boolean> = signal(false);


  // vista corrente: tabella, board (Kanban) o calendario
  protected readonly view = signal<RecordView>('table');

  protected readonly LayoutListIcon = LayoutList;
  protected readonly ColumnsIcon = Columns3;
  protected readonly CalendarIcon = Calendar;

  // status field -> abilita la board (le colonne). null se l'object type non ne ha uno
  protected readonly statusField = computed<FieldDef | null>(
    () => this.formFields().find((f) => f.type === 'STATUS' || f.key === 'status') ?? null
  );

  // primo campo data -> abilita il calendario. null se l'object type non ne ha
  protected readonly dateField = computed<FieldDef | null>(
    () => this.formFields().find((f) => f.type === 'DATE' || f.type === 'DATETIME') ?? null
  );

  constructor() {
    // cambiando object type le viste disponibili cambiano (board/calendario possono sparire):
    // si riparte sempre dalla tabella, l'unica vista garantita per qualunque tipo
    effect(() => {
      this.objectKey();
      this.view.set('table');
    });
  }

  // filtri avanzati (AND/OR annidati). Vuoto = nessun filtro.
  protected readonly showFilters = signal(false);
  protected readonly filterGroup = signal<FilterGroup>({ combinator: 'and', conditions: [] });
  protected readonly activeFilterCount = computed(() => this.filterGroup().conditions.length);

  // trigger di ricarica (dopo una create) che si fonde con i cambi di rotta nello stream dati
  private readonly reload$ = new Subject<void>();

  private readonly key$ = this.route.paramMap.pipe(map((p) => p.get('objectKey') ?? ''));

  // la chiave di rotta esposta come signal (serve a openRecord/createRecord)
  protected readonly objectKey = toSignal(this.key$, { initialValue: '' });

  // stream dati: a ogni cambio di key, reload o filtro, interroga il backend via queryAdvanced
  // (POST col body filter+sort). switchMap annulla la richiesta precedente. Il filtro vuoto è
  // innocuo lato backend (nessuna WHERE). Risultato in un signal via toSignal.
  private readonly result = toSignal(
    combineLatest([
      this.key$,
      this.reload$.pipe(startWith(undefined)),
      toObservable(this.filterGroup)
    ]).pipe(
      map(([key]) => key),
      filter((key): key is string => key.length > 0),
      tap(() => this.loading.set(true)),
      switchMap((key) =>
        this.recordsService
          .queryAdvanced(key, { pageSize: 200, filter: this.filterGroup() })
          .pipe(catchError(() => of(null)))
      ),
      tap(() => this.loading.set(false))
    ),
    { initialValue: null }
  );

  // stato derivato dalla risposta
  protected readonly object = computed(() => this.result()?.object ?? null);
  protected readonly records = computed(() => this.result()?.items ?? []);
  protected readonly total = computed(() => this.result()?.total ?? 0);
  protected readonly formFields = computed<FieldDef[]>(() => this.object()?.fields ?? []);

  // colonne: i field con showInList, fallback ai primi 6 (stesso criterio dell'originale)
  protected readonly columns = computed<FieldDef[]>(() => {
    const obj = this.object();
    if (!obj) return [];
    const shown = obj.fields.filter((f) => f.showInList && !f.hidden);
    return (shown.length > 0 ? shown : obj.fields.filter((f) => !f.hidden).slice(0, 6)).sort(
      (a, b) => a.sortOrder - b.sortOrder
    );
  });

  protected cellValue(rec: RecordItem, field: FieldDef): string {
    const raw = field.key === 'status' ? rec.status : rec.data[field.key];
    return formatFieldValue(field, raw);
  }

  protected resetFilter(): void {
    this.filterGroup.set({ combinator: 'and', conditions: [] });
  }

  protected openRecord(rec: RecordItem): void {
    this.router.navigate(['/o', this.objectKey(), rec.id]);
  }

  protected createRecord(data: Record<string, unknown>): void {
    this.submitting.set(true);
    // azione one-shot: subscribe con teardown legato al ciclo di vita del componente
    this.recordsService
      .create(this.objectKey(), { data })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.drawerOpen.set(false);
          this.reload$.next();
        },
        error: () => this.submitting.set(false)
      });
  }
}

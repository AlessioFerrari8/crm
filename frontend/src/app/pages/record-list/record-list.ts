import { Component, DestroyRef, WritableSignal, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import {
  Subject,
  catchError,
  combineLatest,
  debounceTime,
  distinctUntilChanged,
  filter,
  map,
  of,
  startWith,
  switchMap,
  tap
} from 'rxjs';

import { Drawer } from '../../components/drawer/drawer';
import { FilterBuilder } from '../../components/filter-builder/filter-builder';
import { DynamicForm } from '../../components/dynamic-form/dynamic-form';
import { UiButton } from '../../components/ui/button';
import { UiSpinner } from '../../components/ui/spinner';
import { FilterGroup } from '../../models/filter';
import { FieldCell, fieldCell } from '../../core/field-format';
import { FieldDef } from '../../models/object-type';
import { RecordItem, SortSpec } from '../../models/record';
import { RecordsService } from '../../services/records.service';
import { SavedView, SavedViewService } from '../../services/saved-view.service';
import { RecordBoard } from '../../components/record-board/record-board';
import { RecordCalendar } from '../../components/record-calendar/record-calendar';
import {
  ArrowDown,
  ArrowUp,
  ArrowUpDown,
  Bookmark,
  Calendar,
  Columns3,
  LayoutList,
  LucideAngularModule,
  Search,
  Trash2
} from 'lucide-angular';

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
  private readonly savedViewService = inject(SavedViewService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly loading: WritableSignal<boolean> = signal(true);
  protected readonly drawerOpen: WritableSignal<boolean> = signal(false);
  protected readonly submitting: WritableSignal<boolean> = signal(false);


  // vista corrente: tabella, board (Kanban) o calendario
  protected readonly view = signal<RecordView>('table');

  protected readonly LayoutListIcon = LayoutList;
  protected readonly ColumnsIcon = Columns3;
  protected readonly CalendarIcon = Calendar;
  protected readonly SortIcon = ArrowUpDown;
  protected readonly SortAscIcon = ArrowUp;
  protected readonly SortDescIcon = ArrowDown;
  protected readonly SearchIcon = Search;
  protected readonly TrashIcon = Trash2;
  protected readonly BookmarkIcon = Bookmark;

  // ricerca testuale (q, debounced), ordinamento singolo per colonna (asc/desc/nessuno)
  protected readonly search = signal('');
  protected readonly sort = signal<SortSpec[]>([]);

  // paginazione: senza, oltre PAGE_SIZE record la lista veniva semplicemente troncata
  protected readonly page = signal(1);

  // selezione righe + stato del pannello azioni di massa
  protected readonly selected = signal<Set<string>>(new Set());
  protected readonly bulkField = signal('');
  protected readonly bulkValue = signal('');
  protected readonly bulkPending = signal(false);

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
      // reset stato specifico dell'elenco cambiando tipo (equivalente al remount con key= del riferimento)
      this.sort.set([]);
      this.search.set('');
      this.selected.set(new Set());
      this.bulkField.set('');
      this.bulkValue.set('');
      this.activeView.set(null);
    });
    // cambiando filtri/ricerca/ordinamento si riparte dalla prima pagina (l'eventuale doppia
    // emissione criteri+pagina viene annullata dallo switchMap dello stream dati)
    effect(() => {
      this.filterGroup();
      this.search();
      this.sort();
      this.page.set(1);
    });
    // pagina rimasta oltre l'ultima (es. filtro ristretto o record eliminati): torna alla prima
    effect(() => {
      if (!this.loading() && this.records().length === 0 && this.page() > 1) {
        this.page.set(1);
      }
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
  private static readonly PAGE_SIZE = 200;

  private readonly result = toSignal(
    combineLatest([
      this.key$,
      this.reload$.pipe(startWith(undefined)),
      toObservable(this.filterGroup),
      // startWith('') emette subito così il primo caricamento non aspetta i 300ms del debounce;
      // distinctUntilChanged dopo lo startWith evita la query doppia iniziale (due '' consecutivi)
      toObservable(this.search).pipe(debounceTime(300), startWith(''), distinctUntilChanged()),
      toObservable(this.sort),
      toObservable(this.page)
    ]).pipe(
      map(([key]) => key),
      filter((key): key is string => key.length > 0),
      tap(() => this.loading.set(true)),
      switchMap((key) =>
        this.recordsService
          .queryAdvanced(key, {
            page: this.page(),
            pageSize: RecordList.PAGE_SIZE,
            filter: this.filterGroup(),
            q: this.search().trim() || undefined,
            sort: this.sort().length ? this.sort() : undefined
          })
          .pipe(catchError(() => of(null)))
      ),
      tap(() => this.loading.set(false))
    ),
    { initialValue: null }
  );

  protected readonly pageCount = computed(() =>
    Math.max(1, Math.ceil(this.total() / RecordList.PAGE_SIZE))
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

  // campi impostabili in massa (esclude i sola-lettura); e def del campo scelto (per select opzioni)
  protected readonly editableFields = computed<FieldDef[]>(() =>
    this.formFields().filter((f) => !f.readonly)
  );
  protected readonly bulkFieldDef = computed<FieldDef | null>(
    () => this.formFields().find((f) => f.key === this.bulkField()) ?? null
  );

  // cella type-aware (badge colorati, link, testo formattato) - stesso formatter del dettaglio
  protected cell(rec: RecordItem, field: FieldDef): FieldCell {
    const raw = field.key === 'status' ? rec.status : rec.data[field.key];
    return fieldCell(field, raw);
  }

  // ── ricerca / ordinamento ──────────────────────────────────────────────────
  protected onSearch(value: string): void {
    this.search.set(value);
  }

  // ciclo asc → desc → nessuno, ordinamento singolo (come il riferimento)
  protected toggleSort(field: string): void {
    const cur = this.sort()[0];
    if (!cur || cur.field !== field) this.sort.set([{ field, dir: 'asc' }]);
    else if (cur.dir === 'asc') this.sort.set([{ field, dir: 'desc' }]);
    else this.sort.set([]);
  }

  protected sortDir(field: string): 'asc' | 'desc' | null {
    const cur = this.sort()[0];
    return cur && cur.field === field ? cur.dir : null;
  }

  // ── selezione / azioni di massa ─────────────────────────────────────────────
  protected isSelected(id: string): boolean {
    return this.selected().has(id);
  }

  protected toggleSelect(id: string): void {
    const next = new Set(this.selected());
    if (next.has(id)) next.delete(id);
    else next.add(id);
    this.selected.set(next);
  }

  protected allSelected(): boolean {
    const recs = this.records();
    return recs.length > 0 && recs.every((r) => this.selected().has(r.id));
  }

  protected toggleSelectAll(): void {
    this.selected.set(this.allSelected() ? new Set() : new Set(this.records().map((r) => r.id)));
  }

  protected clearSelection(): void {
    this.selected.set(new Set());
    this.bulkField.set('');
    this.bulkValue.set('');
  }

  // cambiando campo azzero il valore (le opzioni valide dipendono dal campo)
  protected setBulkField(key: string): void {
    this.bulkField.set(key);
    this.bulkValue.set('');
  }

  protected applyBulk(): void {
    const field = this.bulkField();
    if (!field || this.bulkPending()) return;
    this.bulkPending.set(true);
    this.recordsService
      .bulk(this.objectKey(), {
        ids: [...this.selected()],
        action: 'UPDATE',
        set: { [field]: this.bulkValue() }
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.bulkPending.set(false);
          this.clearSelection();
          this.reload$.next();
        },
        error: () => this.bulkPending.set(false)
      });
  }

  // ── viste salvate ───────────────────────────────────────────────────────────
  private readonly viewsReload$ = new Subject<void>();

  protected readonly savedViews = toSignal(
    combineLatest([this.key$, this.viewsReload$.pipe(startWith(undefined))]).pipe(
      map(([key]) => key),
      filter((key): key is string => key.length > 0),
      switchMap((key) => this.savedViewService.list(key).pipe(catchError(() => of<SavedView[]>([]))))
    ),
    { initialValue: [] as SavedView[] }
  );

  // vista attiva (solo indicatore visivo: applicare filtri/sort a mano la "sgancia")
  protected readonly activeView = signal<string | null>(null);

  protected applyView(v: SavedView): void {
    const c = v.config ?? {};
    this.filterGroup.set(c.filter ?? { combinator: 'and', conditions: [] });
    this.sort.set(c.sort ?? []);
    this.search.set(c.q ?? '');
    if (c.mode === 'board' && this.statusField()) this.view.set('board');
    else if (c.mode === 'calendar' && this.dateField()) this.view.set('calendar');
    else this.view.set('table');
    this.showFilters.set((c.filter?.conditions?.length ?? 0) > 0);
    this.activeView.set(v.id);
  }

  protected saveCurrentView(): void {
    const name = prompt('Nome della vista:');
    if (!name || !name.trim()) return;
    this.savedViewService
      .create(this.objectKey(), name.trim().slice(0, 100), {
        filter: this.filterGroup(),
        sort: this.sort(),
        q: this.search().trim() || undefined,
        mode: this.view()
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((v) => {
        this.activeView.set(v.id);
        this.viewsReload$.next();
      });
  }

  protected deleteView(v: SavedView, event: Event): void {
    event.stopPropagation();
    if (!confirm(`Eliminare la vista "${v.name}"?`)) return;
    this.savedViewService
      .remove(v.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.activeView() === v.id) this.activeView.set(null);
        this.viewsReload$.next();
      });
  }

  protected deleteBulk(): void {
    const ids = [...this.selected()];
    if (ids.length === 0 || this.bulkPending()) return;
    if (!confirm(`Eliminare ${ids.length} record?`)) return;
    this.bulkPending.set(true);
    this.recordsService
      .bulk(this.objectKey(), { ids, action: 'DELETE' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.bulkPending.set(false);
          this.clearSelection();
          this.reload$.next();
        },
        error: () => this.bulkPending.set(false)
      });
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

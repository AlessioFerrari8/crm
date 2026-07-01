import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';

import { UiSpinner } from '../../components/ui/spinner';
import { SearchResult } from '../../models/record';
import { RecordsService } from '../../services/records.service';

interface SearchState {
  loading: boolean;
  results: SearchResult[];
  searched: boolean;
}

const IDLE: SearchState = { loading: false, results: [], searched: false };

// Ricerca globale: GET /api/records/search?q= (min 2 char lato backend). Debounce per non
// bombardare il backend a ogni tasto. Tutto come stream: l'input -> toSignal di uno stato.
@Component({
  selector: 'app-search',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, UiSpinner],
  templateUrl: './search.html'
})
export class Search {
  private readonly recordsService = inject(RecordsService);
  private readonly route = inject(ActivatedRoute);

  protected readonly query = new FormControl('', { nonNullable: true });

  constructor() {
    // termine iniziale dalla query string (?q=..., arriva dal topbar). Nel costruttore i field
    // initializer (incluso lo stream qui sotto) sono gia' pronti: il setValue emette nello stream.
    const initial = this.route.snapshot.queryParamMap.get('q');
    if (initial) this.query.setValue(initial);
  }

  private readonly state = toSignal(
    this.query.valueChanges.pipe(
      debounceTime(300),
      map((q) => q.trim()),
      distinctUntilChanged(),
      switchMap((term) => {
        if (term.length < 2) return of(IDLE);
        return this.recordsService.search(term).pipe(
          map((results) => ({ loading: false, results, searched: true })),
          catchError(() => of({ loading: false, results: [], searched: true })),
          startWith({ loading: true, results: [] as SearchResult[], searched: false })
        );
      }),
      startWith(IDLE)
    ),
    { initialValue: IDLE }
  );

  protected readonly loading = computed(() => this.state().loading);
  protected readonly results = computed(() => this.state().results);
  protected readonly searched = computed(() => this.state().searched);
}

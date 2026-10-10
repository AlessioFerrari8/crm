import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, combineLatest, of, switchMap } from 'rxjs';
import { API_BASE_URL } from '../../core/api-config';
import { DatePipe } from '@angular/common';

interface HistoryEntry {
  action: string;
  author: string;
  at: string;
  changes: { field: string; from: string; to: string }[];
}

interface Page<T> {
  items: T[];
  page: number;
  totalPages: number;
}

// la cronologia con i valori ("Importo: da 900,00 a 1.100,00"), gli stessi permessi della scheda. Paginata sempre: un record vissuto
// ha centinaia di voci
@Component({
  selector: 'app-record-history',
  imports: [DatePipe],
  templateUrl: './record-history.html',
  styles: ``,
})
export class RecordHistory {
  readonly objectKey = input.required<string>();
  readonly recordId = input.required<string>();
  // cambia a ogni salvataggio della scheda: la cronologia si ricarica da sola
  readonly version = input<unknown>(null);

  private readonly http = inject(HttpClient);
  protected readonly page = signal(0);

  protected readonly data = toSignal(
    combineLatest([toObservable(this.objectKey), toObservable(this.recordId), toObservable(this.page), toObservable(this.version)]).pipe(
      switchMap(([key, id, page]) =>
        this.http
          .get<Page<HistoryEntry>>(`${API_BASE_URL}/api/records/${key}/${id}/history`, {
            params: new HttpParams().set('page', page).set('size', 20)
          })
          .pipe(catchError(() => of(null)))
      )
    ),
    { initialValue: null }
  );
}

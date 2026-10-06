import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service, signal, WritableSignal } from '@angular/core';
import { API_BASE_URL } from '../core/api-config';
import { TimeEntry, TimeSummaryRow } from '../models/time-entry';
import { map, Observable, tap } from 'rxjs';


// il tumer attivo sta in un signal condiviso: la topbar e la scheda del record vedono lo stesso cronometro
@Service()
export class TimeService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/time`;

  readonly running: WritableSignal<TimeEntry | null> = signal(null);

  loadRunning(): Observable<TimeEntry | null> {
    // 204 = nessun timer: il body arriva null
    return this.http.get<TimeEntry | null>(`${this.base}/running`).pipe(tap((t) => this.running.set(t ?? null)));
  }

  start(recordId: string): Observable<TimeEntry> {
    return this.http.post<TimeEntry>(`${this.base}(start)`, {recordId}).pipe(tap((t) => this.running.set(t)));
  }

  stop(): Observable<TimeEntry> {
    return this.http.post<TimeEntry>(`${this.base}/stop`, {}).pipe(tap(() => this.running.set(null)));
  }

  list(recordId: string): Observable<TimeEntry[]> {
    return this.http.get<TimeEntry[]>(this.base, { params: new HttpParams().set('recordId', recordId) });
  }

  summary(recordId: string): Observable<TimeSummaryRow[]> {
    return this.http.get<TimeSummaryRow[]>(`${this.base}/summary`, { params: new HttpParams().set('recordId', recordId) });
  }

  create(body: { recordId: string; startedAt: string; minutes: number; billable: boolean; description: string }): Observable<TimeEntry> {
    return this.http.post<TimeEntry>(this.base, body);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  totalMinutes(recordId: string): Observable<number> {
    return this.summary(recordId).pipe(map((rows) => rows.reduce((s, r) => s + r.minutes, 0)));
  }
}

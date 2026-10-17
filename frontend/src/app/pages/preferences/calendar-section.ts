import { Component, DestroyRef, inject, signal, WritableSignal } from '@angular/core';
import { UiButton } from '../../components/ui/button';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { API_BASE_URL } from '../../core/api-config';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

interface CalendarAccount {
  id: string;
  email: string;
  lastSyncAt: string | null;
  lastError: string | null;
}

// collega Google Calendar (solo import). Il collegamento è una navigazione vera verso Google, che poi torna sul backend e da lì
// qui con ?calendar=connected
@Component({
  selector: 'app-calendar-section',
  imports: [UiButton, DatePipe],
  templateUrl: './calendar-section.html',
  styles: ``,
})
export class CalendarSection {
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly result = signal(inject(ActivatedRoute).snapshot.queryParamMap.get('calendar'));
  protected readonly accounts: WritableSignal<CalendarAccount[]> = signal([]);

  constructor() {
    this.load();
  }

  protected connect(): void {
    this.http.get<{ url: string }>(`${API_BASE_URL}/api/calendar/google/connect`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((r) => (window.location.href = r.url));
  }

  protected disconnect(a: CalendarAccount): void {
    this.http.delete<void>(`${API_BASE_URL}/api/calendar/accounts/${a.id}`)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
  }

  private load(): void {
    this.http.get<CalendarAccount[]>(`${API_BASE_URL}/api/calendar/accounts`)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe((a) => this.accounts.set(a));
  }
}

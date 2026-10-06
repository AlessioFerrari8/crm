import { DatePipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, input, signal } from '@angular/core';
import { TimeScale } from 'chart.js';
import { TimeService } from '../../services/time.service';
import { startWith, Subject, switchMap } from 'rxjs';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { TimeEntry } from '../../models/time-entry';

// la sezione "Tempo" della scheda: avvia il timer su questo record, registrazioni, totale
@Component({
  selector: 'app-record-time',
  imports: [DatePipe],
  templateUrl: './record-time.html',
  styles: ``,
})
export class RecordTime {
  readonly recordId = input.required<string>();

  protected readonly time = inject(TimeService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload$ = new Subject<void>();

  protected readonly entries = toSignal(
    toObservable(this.recordId).pipe(
      switchMap((id) => this.reload$.pipe(startWith(undefined), switchMap(() => this.time.list(id))))
    ),
    { initialValue: [] as TimeEntry[] }
  );

  protected readonly totalMinutes = computed(() => this.entries().reduce((s, e) => s + e.minutes, 0));
  protected readonly runningHere = computed(() => this.time.running()?.recordId === this.recordId());

  protected readonly minutes = signal(30);
  protected readonly description = signal('');

  protected toggle(): void {
    const call = this.runningHere() ? this.time.stop() : this.time.start(this.recordId());
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.reload$.next());
  }

  // "2 ore ieri": la registrazione a mano è quella che si usa davvero il venerdì
  protected addManual(): void {
    const m = this.minutes();
    if (!m || m < 1) return;
    this.time
      .create({
        recordId: this.recordId(),
        startedAt: new Date(Date.now() - m * 60000).toISOString(),
        minutes: m,
        billable: true,
        description: this.description().trim()
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.description.set('');
        this.reload$.next();
      });
  }

  protected remove(e: TimeEntry): void {
    this.time.remove(e.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.reload$.next());
  }

  protected hours(min: number): string {
    return `${Math.floor(min / 60)}h ${String(min % 60).padStart(2, '0')}m`;
  }
}

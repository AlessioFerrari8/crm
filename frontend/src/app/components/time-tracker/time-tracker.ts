import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TimeScale } from 'chart.js';
import { TimeService } from '../../services/time.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';


// il cronometro nella topbar. Il valore è now - startedAt ricalcolato chiaramente ad ogni tick, non un contatore che incrementa:
// quello sbaglia appena la tab va in background e il browser rallenta i timer
@Component({
  selector: 'app-time-tracker',
  imports: [RouterLink],
  templateUrl: './time-tracker.html'
})
export class TimeTracker {
  protected readonly time = inject(TimeService);
  protected readonly destroyRef = inject(DestroyRef);
  protected readonly now = signal(Date.now());

  protected readonly elapsed = computed(() => {
    const t = this.time.running();
    if (!t) return '';
    const s = Math.max(0, Math.floor((this.now() - new Date(t.startedAt).getTime()) / 1000));
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`;
  });

  constructor() {
    this.time.loadRunning().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
    interval(1000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.now.set(Date.now()));
  }

  protected stop(): void {
    this.time.stop().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }
}

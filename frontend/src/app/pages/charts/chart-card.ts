import { Component, computed, inject, input, output, signal, WritableSignal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ChartConfiguration } from 'chart.js';
import { catchError, of, switchMap, tap } from 'rxjs';

import { ChartComponent } from '../../components/chart/chart';
import { UiSpinner } from '../../components/ui/spinner';
import { Chart, ChartRunResponse } from '../../models/chart';
import { ChartService } from '../../services/chart.service';

const PALETTE = ['#2563eb', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#84cc16'];

@Component({
  selector: 'app-chart-card',
  standalone: true,
  imports: [ChartComponent, UiSpinner],
  templateUrl: './chart-card.html'
})
export class ChartCard {
  // servizio per i grafici
  private readonly chartService = inject(ChartService);

  readonly chart = input.required<Chart>();
  readonly remove = output<Chart>();

  // per gestire caricamento
  protected readonly loading: WritableSignal<boolean> = signal(true);

  // esegue il chart quando l'input è disponibile; pattern reattivo input -> stream -> signal
  private readonly run = toSignal(
    toObservable(this.chart).pipe(
      tap(() => this.loading.set(true)),
      switchMap((c) => this.chartService.run(c.id).pipe(catchError(() => of<ChartRunResponse | null>(null)))),
      tap(() => this.loading.set(false))
    ),
    { initialValue: null }
  );

  // gestione punti 
  protected readonly points = computed(() => this.run()?.data ?? []);

  protected readonly isChart = computed(() => ['BAR', 'LINE', 'PIE', 'FUNNEL'].includes(this.chart().type));
  protected readonly isKpi = computed(() => this.chart().type === 'KPI');
  protected readonly isTable = computed(() => this.chart().type === 'TABLE');

  // somma di tutti i valori (per il KPI)
  protected readonly kpiValue = computed(() => this.points().reduce((sum, p) => sum + (Number(p.value) || 0), 0));

  // configurazione chart.js per i tipi grafici (FUNNEL approssimato a barre)
  protected readonly config = computed<ChartConfiguration | null>(() => {
    const c = this.chart();
    if (!this.isChart()) return null;
    const labels = this.points().map((p) => p.label);
    const data = this.points().map((p) => Number(p.value) || 0);
    const jsType = c.type === 'LINE' ? 'line' : c.type === 'PIE' ? 'pie' : 'bar';
    return {
      type: jsType,
      data: {
        labels,
        datasets: [{ label: c.label, data, backgroundColor: jsType === 'pie' ? PALETTE : '#2563eb', borderColor: '#2563eb' }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: jsType === 'pie' } }
      }
    } as ChartConfiguration;
  });
}

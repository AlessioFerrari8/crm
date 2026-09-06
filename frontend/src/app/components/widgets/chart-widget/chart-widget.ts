import { Component, computed, input } from '@angular/core';
import { ChartComponent } from '../../chart/chart';
import { DashboardWidget } from '../../../models/dashboard';
import { ReportQuery } from '../../../models/report';
import { dimLabel, loadWidget } from '../../../core/widget-data';
import { ChartConfiguration } from 'chart.js';

// Riusa app-chart, che c'è già e sa parlare con Chart.js. Qui si traduce solo ReportResult ->
// ChartConfiguration: una serie per metrica, le etichette dalla prima dimensione
@Component({
  selector: 'app-chart-widget',
  standalone: true,
  imports: [ChartComponent],
  templateUrl: './chart-widget.html',
})
export class ChartWidget {
  readonly widget = input.required<DashboardWidget>();

  protected readonly query = computed(() => this.widget().query as unknown as ReportQuery);
  protected readonly state = loadWidget(() => this.query());

  protected readonly config = computed<ChartConfiguration | null>(() => {
    const r = this.state.result();

    if (!r || r.dimensions.length === 0) return null;
    const labels = r.dimensions.map((d) => dimLabel(d[0]));
    const metrics = this.query().metrics ?? [];

    return {
      // il tipo del grafico viene dal render del report; 'bar' è il defualt
      type: (this.widget().query['render'] as 'bar' | 'line' | 'pie') ?? 'bar',
      data: {
        labels,
        datasets: metrics.map((m, i) => ({
          label: m.label,
          data: r.metrics.map((row) => Number(row[i]) || 0),
        })),
      },
      options: { responsive: true, maintainAspectRatio: false },
    };
  });
}

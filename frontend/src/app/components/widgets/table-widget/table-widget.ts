import { Component, computed, input } from '@angular/core';
import { DashboardWidget } from '../../../models/dashboard';
import { ReportQuery } from '../../../models/report';
import { dimLabel, loadWidget } from '../../../core/widget-data';

// Il risultato così com'è: una colonna per dimensione, una per metrica
@Component({
  selector: 'app-table-widget',
  standalone: true,
  templateUrl: './table-widget.html',
})
export class TableWidget {
  readonly widget = input.required<DashboardWidget>();

  protected readonly query = computed(() => this.widget().query as unknown as ReportQuery);
  protected readonly state = loadWidget(() => this.query());

  protected readonly headers = computed(() => [
    ...[...(this.query().rows ?? []), ...(this.query().columns ?? [])].map((d) => d.field),
    ...(this.query().metrics ?? []).map((m) => m.label),
  ]);

  protected readonly rows = computed(() => {
    const r = this.state.result();
    if (!r) return [];

    return r.dimensions.map((dims, i) => [
      ...dims.map(dimLabel),
      ...r.metrics[i].map((n) => String(n ?? ''))
    ]);
  });

}

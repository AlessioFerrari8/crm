import { Component, computed, input } from '@angular/core';
import { DashboardWidget } from '../../../models/dashboard';
import { loadWidget, singleValue } from '../../../core/widget-data';
import { ReportQuery } from '../../../models/report';


// Una cella sola: query senza rows e senza columns, una metrica
@Component({
  selector: 'app-kpi-tile',
  standalone: true,
  templateUrl: './kpi-tile.html'
})
export class KpiTile {
  readonly widget = input.required<DashboardWidget>();

  protected readonly state = loadWidget(() => this.widget().query as unknown as ReportQuery);
  protected readonly value = computed(() => singleValue(this.state.result()));
}

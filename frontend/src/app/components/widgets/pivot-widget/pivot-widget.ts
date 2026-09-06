import { Component, computed, input } from '@angular/core';
import { DashboardWidget } from '../../../models/dashboard';
import { ReportQuery } from '../../../models/report';
import { dimLabel, loadWidget } from '../../../core/widget-data';
import { toMatrix } from '../../../core/pivot';


// L'unico rendering non banale: righe x colonne, con sotto-colonne per metrica e la riga dei totali
@Component({
  selector: 'app-pivot-widget',
  standalone: true,
  templateUrl: './pivot-widget.html'
})
export class PivotWidget {
  readonly widget = input.required<DashboardWidget>();

  protected readonly query = computed(() => this.widget().query as unknown as ReportQuery);
  protected readonly state = loadWidget(() => this.query());

  protected readonly metrics = computed(() => this.query().metrics ?? []);

  protected readonly matrix = computed(() => {
    const r = this.state.result();
    if (!r) return null;
    return toMatrix(r, (this.query().rows ?? []).length, this.metrics().length);
  });

  protected label(v: unknown): string {
    return dimLabel(v);
  }

  protected cellValues(row: string, col: string): (number | string)[] {
    const v = this.matrix()?.cell(row, col);
    return v ?? this.metrics().map(() => '-');
  }
}

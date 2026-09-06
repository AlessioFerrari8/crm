import { Component, computed, input } from '@angular/core';
import { DashboardWidget } from '../../../models/dashboard';
import { ReportQuery } from '../../../models/report';
import { loadWidget, singleValue } from '../../../core/widget-data';

@Component({
  selector: 'app-kpi-split-tile',
  standalone: true,
  templateUrl: './kpi-split-tile.html',
})
export class KpiSplitTile {
  readonly widget = input.required<DashboardWidget>();

  private readonly split = computed(
    () =>
      this.widget().query as {
        left: ReportQuery;
        right: ReportQuery;
        leftLabel?: string;
        rightLabel?: string;
      },
  );

  protected readonly leftState = loadWidget(() => this.split().left);
  protected readonly rightState = loadWidget(() => this.split().right);

  protected readonly leftValue = computed(() => singleValue (this.leftState.result()));
  protected readonly rightValue = computed(() => singleValue(this.rightState.result()));
  protected readonly leftLabel = computed(() => this.split().leftLabel ?? 'A');
  protected readonly rightLabel = computed(() => this.split().rightLabel ?? 'B');
}

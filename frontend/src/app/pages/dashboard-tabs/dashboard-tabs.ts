import { Component, computed, DestroyRef, inject, signal, WritableSignal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UiButton } from '../../components/ui/button';
import { UiSpinner } from '../../components/ui/spinner';
import { KpiTile } from '../../components/widgets/kpi-tile/kpi-tile';
import { KpiSplitTile } from '../../components/widgets/kpi-split-tile/kpi-split-tile';
import { ChartWidget } from '../../components/widgets/chart-widget/chart-widget';
import { TableWidget } from '../../components/widgets/table-widget/table-widget';
import { PivotWidget } from '../../components/widgets/pivot-widget/pivot-widget';
import { RecordListWidget } from '../../components/widgets/record-list-widget/record-list-widget';
import { ConfirmService } from '../../services/confirm.service';
import { DashboardDetail, DashboardSummary, DashboardTab } from '../../models/dashboard';
import { DashboardService } from '../../services/dashboard.service';
import { catchError, of } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-dashboard-tabs',
  imports: [
    FormsModule,
    UiButton,
    UiSpinner,
    KpiTile,
    KpiSplitTile,
    ChartWidget,
    TableWidget,
    PivotWidget,
    RecordListWidget,
  ],
  templateUrl: './dashboard-tabs.html',
})
export class DashboardTabs {
  private readonly dashboards = inject(DashboardService);
  private readonly confirmService = inject(ConfirmService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly list: WritableSignal<DashboardSummary[]> = signal([]);
  protected readonly current: WritableSignal<DashboardDetail | null> = signal(null);
  protected readonly activeTabId: WritableSignal<string | null> = signal(null);
  protected readonly loading: WritableSignal<boolean> = signal(true);
  protected readonly editing: WritableSignal<boolean> = signal(false);
  protected readonly newTabLabel = signal('');

  protected readonly activeTab = computed<DashboardTab | null>(() => {
    const d = this.current();
    if (!d) return null;
    return d.tabs.find((t) => t.id === this.activeTabId()) ?? d.tabs[0] ?? null;
  });

  constructor() {
    this.dashboards
      .list()
      .pipe(
        catchError(() => of<DashboardSummary[]>([])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((rows) => {
        this.list.set(rows);
        if (rows.length > 0) this.open(rows[0].id);
        else this.loading.set(false);
      });
  }

  protected open(id: string): void {
    this.loading.set(true);
    this.dashboards
      .get(id)
      .pipe(
        catchError(() => of(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((detail) => {
        this.current.set(detail);
        this.activeTabId.set(detail?.tabs[0]?.id ?? null);
        this.loading.set(false);
      });
  }

  protected addTab(): void {
    const label = this.newTabLabel().trim();
    const d = this.current();
    if (!label || !d) return;
    this.dashboards
      .addTab(d.id, label)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.newTabLabel.set('');
        this.open(d.id);
      });
  }

  protected removeTab(tabId: string): void {
    this.confirmService.ask('Eliminare questo tab e i suoi widget?').then((ok) => {
      if (!ok) return;
      const d = this.current();
      this.dashboards
        .removeTab(tabId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => d && this.open(d.id));
    });
  }

  protected removeWidget(widgetId: string): void {
    this.confirmService.ask('Eliminare questo widget?').then((ok) => {
      if (!ok) return;
      const d = this.current();
      this.dashboards.removeWidget(widgetId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => d && this.open(d.id));
    });
  }

  protected createDashboard(): void {
    this.dashboards.create('Nuova dashboard', true)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((d) => {
        this.list.update((rows) => [...rows, d]);
        this.open(d.id);
      });
  }

  // la larghezza del widget nella griglia a 4 colonne
  // Le classi sono scritte per esteso e non composte a runtime ("col-span-" + n). Così tailwind fa
  // il purge leggendo il sorgente, e una classe costruita a stringa non finisce nel css
  protected spanClass(span: number): string {
    switch(span) {
      case 4: return 'lg:col-span-4';
      case 3: return 'lg:col-span-3';
      case 2: return 'lg:col-span-2';
      default: return 'lg:col-span-1';
    }
  }
}

import { Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ChartConfiguration } from 'chart.js';
import { catchError, filter, forkJoin, map, of, switchMap, tap } from 'rxjs';

import { ChartComponent } from '../../components/chart/chart';
import { UiCard } from '../../components/ui/card';
import { UiSpinner } from '../../components/ui/spinner';
import { ObjectType } from '../../models/object-type';
import { ObjectTypeService } from '../../services/object-type.service';
import { RecordsService } from '../../services/records.service';
import { IRevenuePoint } from '../../models/IRevenuePoint';
import { IPipelinePoint } from '../../models/IPipelinePoint';
import { IActivityPoint } from '../../models/IActivityPoint';
import { IEfficiencyPoint } from '../../models/IEfficiencyPoint';
import { AnalyticsService } from '../../services/analytics.service';

interface Kpi {
  object: ObjectType;
  count: number;
}

// voce del widget "Prossime scadenze": record di qualunque tipo con un campo data nei prossimi giorni
interface DueItem {
  id: string;
  title: string;
  date: string;
  typeKey: string;
  typeLabel: string;
  color: string | null;
}

interface AnalyticsData {
  revenue: IRevenuePoint[];
  pipeline: IPipelinePoint[];
  activity: IActivityPoint[];
  efficiency: IEfficiencyPoint[];
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink, ChartComponent, UiCard, UiSpinner],
  templateUrl: './dashboard.html'
})
export class Dashboard {
  private readonly objectTypeService = inject(ObjectTypeService);
  private readonly recordsService = inject(RecordsService);
  private readonly analytics = inject(AnalyticsService);

  protected readonly loadingKpis = signal(true);
  protected readonly loadingCharts = signal(true);

  // KPI: una card per ObjectType abilitato (max 8), conteggio via query pageSize=1 leggendo total.
  // Spreco noto (scarica anche 1 record), eredita' dall'originale - niente endpoint count dedicato.
  protected readonly kpis = toSignal(
    this.objectTypeService.load().pipe(
      map((objects) => objects.filter((o) => o.enabled).slice(0, 8)),
      switchMap((enabled) =>
        enabled.length === 0
          ? of<Kpi[]>([])
          : forkJoin(enabled.map((o) => this.recordsService.query(o.key, { pageSize: 1 }))).pipe(
              map((responses) => enabled.map((object, i) => ({ object, count: responses[i].total })))
            )
      ),
      tap(() => this.loadingKpis.set(false)),
      catchError(() => {
        this.loadingKpis.set(false);
        return of<Kpi[]>([]);
      })
    ),
    { initialValue: [] as Kpi[] }
  );

  private readonly data = toSignal(
    forkJoin({
      revenue: this.analytics.revenue(),
      pipeline: this.analytics.pipeline(),
      activity: this.analytics.activity(),
      efficiency: this.analytics.efficiency()
    }).pipe(
      tap(() => this.loadingCharts.set(false)),
      catchError(() => {
        this.loadingCharts.set(false);
        return of<AnalyticsData | null>(null);
      })
    ),
    { initialValue: null as AnalyticsData | null }
  );

  // "Prossime scadenze": metadata-driven, pesca dai primi 4 tipi abilitati che hanno un campo
  // DATE/DATETIME i record con quel campo nei prossimi 7 giorni (between + sort asc lato
  // backend), fonde e ordina. Si aggancia al signal objects già popolato dal load() dei KPI.
  protected readonly upcoming = toSignal(
    toObservable(this.objectTypeService.objects).pipe(
      filter((objs): objs is ObjectType[] => objs !== null),
      switchMap((objs) => {
        const today = new Date();
        const end = new Date(today.getTime() + 7 * 86_400_000);
        const from = today.toISOString().slice(0, 10);
        const to = end.toISOString().slice(0, 10);

        const withDate = objs
          .filter((o) => o.enabled)
          .map((o) => ({ obj: o, dateField: o.fields.find((f) => f.type === 'DATE' || f.type === 'DATETIME') }))
          .filter((x): x is { obj: ObjectType; dateField: NonNullable<typeof x.dateField> } => !!x.dateField)
          .slice(0, 4);

        if (withDate.length === 0) return of<DueItem[]>([]);

        return forkJoin(
          withDate.map(({ obj, dateField }) =>
            this.recordsService
              .queryAdvanced(obj.key, {
                pageSize: 20,
                filter: {
                  combinator: 'and',
                  conditions: [{ field: dateField.key, op: 'between', value: from, value2: to }]
                },
                sort: [{ field: dateField.key, dir: 'asc' }]
              })
              .pipe(
                map((r) =>
                  r.items.map((rec) => ({
                    id: rec.id,
                    title: rec.title || '(senza titolo)',
                    date: String(rec.data[dateField.key] ?? ''),
                    typeKey: obj.key,
                    typeLabel: obj.label,
                    color: obj.color
                  }))
                ),
                catchError(() => of<DueItem[]>([]))
              )
          )
        ).pipe(map((lists) => lists.flat().sort((a, b) => a.date.localeCompare(b.date)).slice(0, 10)));
      })
    ),
    { initialValue: [] as DueItem[] }
  );

  protected dueDate(item: DueItem): string {
    const d = new Date(item.date);
    return Number.isNaN(d.getTime()) ? item.date : d.toLocaleDateString('it-IT', { day: '2-digit', month: 'short' });
  }

  // ogni chart config deriva dai dati con un computed
  protected readonly revenueChart = computed<ChartConfiguration | null>(() => {
    const d = this.data();
    if (!d) return null;
    return {
      type: 'line',
      data: {
        labels: d.revenue.map((p) => p.month),
        datasets: [
          { label: 'Fatturato', data: d.revenue.map((p) => p.fatturato), borderColor: '#2563eb', backgroundColor: '#2563eb33', fill: true, tension: 0.3 },
          { label: 'Costi', data: d.revenue.map((p) => p.costi), borderColor: '#ef4444', backgroundColor: '#ef444433', fill: true, tension: 0.3 }
        ]
      },
      options: CHART_OPTIONS
    };
  });

  protected readonly pipelineChart = computed<ChartConfiguration | null>(() => {
    const d = this.data();
    if (!d) return null;
    return {
      type: 'doughnut',
      data: {
        labels: d.pipeline.map((p) => p.name),
        datasets: [{ data: d.pipeline.map((p) => p.value), backgroundColor: ['#2563eb', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4'] }]
      },
      options: CHART_OPTIONS
    };
  });

  protected readonly activityChart = computed<ChartConfiguration | null>(() => {
    const d = this.data();
    if (!d) return null;
    return {
      type: 'bar',
      data: {
        labels: d.activity.map((p) => p.month),
        datasets: [
          { label: 'Attività', data: d.activity.map((p) => p['attività']), backgroundColor: '#2563eb' },
          { label: 'Completate', data: d.activity.map((p) => p.completate), backgroundColor: '#10b981' }
        ]
      },
      options: CHART_OPTIONS
    };
  });

  protected readonly efficiencyChart = computed<ChartConfiguration | null>(() => {
    const d = this.data();
    if (!d) return null;
    return {
      type: 'line',
      data: {
        labels: d.efficiency.map((p) => p.month),
        datasets: [{ label: 'Efficienza %', data: d.efficiency.map((p) => p.efficienza), borderColor: '#8b5cf6', backgroundColor: '#8b5cf633', fill: true, tension: 0.3 }]
      },
      options: CHART_OPTIONS
    };
  });
}

const CHART_OPTIONS: ChartConfiguration['options'] = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { position: 'bottom' } }
};

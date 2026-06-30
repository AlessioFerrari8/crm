import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject, catchError, of, startWith, switchMap, tap } from 'rxjs';

import { UiButton } from '../../components/ui/button';
import { UiSpinner } from '../../components/ui/spinner';
import { CHART_TYPES, Chart, ChartType } from '../../models/chart';
import { ObjectType } from '../../models/object-type';
import { ChartService } from '../../services/chart.service';
import { ObjectTypeService } from '../../services/object-type.service';
import { ChartCard } from './chart-card';

@Component({
  selector: 'app-charts',
  standalone: true,
  imports: [ReactiveFormsModule, UiButton, UiSpinner, ChartCard],
  templateUrl: './charts.html'
})
export class Charts {
  // injection di servizi
  private readonly fb = inject(FormBuilder);
  private readonly chartService = inject(ChartService);
  private readonly objectTypeService = inject(ObjectTypeService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly chartTypes = CHART_TYPES;
  protected readonly loading = signal(true);
  protected readonly creating = signal(false);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly reload$ = new Subject<void>();

  protected readonly charts = toSignal(
    this.reload$.pipe(
      startWith(undefined),
      tap(() => this.loading.set(true)),
      switchMap(() => this.chartService.list().pipe(catchError(() => of<Chart[]>([])))),
      tap(() => this.loading.set(false))
    ),
    { initialValue: [] as Chart[] }
  );

  // oggetti per il select dell'oggetto sorgente
  protected readonly objects = toSignal(
    this.objectTypeService.load().pipe(catchError(() => of<ObjectType[]>([]))),
    { initialValue: [] as ObjectType[] }
  );

  protected readonly form = this.fb.nonNullable.group({
    label: ['', Validators.required],
    type: ['BAR' as ChartType, Validators.required],
    objectKey: ['', Validators.required],
    groupBy: ['status', Validators.required],
    aggregate: ['count' as 'count' | 'sum', Validators.required],
    field: ['']
  });

  protected create(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { label, type, objectKey, groupBy, aggregate, field } = this.form.getRawValue();
    this.chartService
      .create({
        label,
        type,
        query: { objectKey, groupBy, aggregate, field: aggregate === 'sum' ? field : undefined }
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.creating.set(false);
          this.form.reset({ type: 'BAR', groupBy: 'status', aggregate: 'count' });
          this.reload$.next();
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(err?.error?.message ?? 'Errore nella creazione del grafico');
        }
      });
  }

  protected remove(chart: Chart): void {
    if (!confirm(`Eliminare il grafico "${chart.label}"?`)) return;
    this.chartService
      .remove(chart.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload$.next());
  }
}

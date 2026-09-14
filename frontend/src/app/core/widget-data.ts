import { DestroyRef, effect, inject, signal, WritableSignal } from '@angular/core';
import { ReportQuery, ReportResult } from '../models/report';
import { ReportService } from '../services/report.service';
import { catchError, of } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

// tutti e sei i widget fanno la stessa cosa: prendono la query, la eseguono, mostrano scheletro
// / errore / dati. Scritto una volta come funzione di composizione (non una classe base: l'ereditarietà fra
// componenti angular porta più problemi di quanti ne risolva)

// dentro il costruttore del componente
// protected readonly data = loadWidget(() => this.widget().query as ReportQuery);

export function loadWidget(query: () => ReportQuery) {
  const reports = inject(ReportService);
  const destroyRef = inject(DestroyRef);

  const result: WritableSignal<ReportResult | null> = signal(null);
  const loading: WritableSignal<boolean> = signal(true);
  const failed: WritableSignal<boolean> = signal(false);

  // niente chiamate qui: siamo nell'inizializzazione di campo del componente, gli input non sono ancora
  // assegnati. L'effect parte dopo
  effect((onCleanup) => {
    const q = query();
    if (!q) return;
    loading.set(true);
    failed.set(false);
    const sub = reports.preview(q)
      .pipe(
        catchError(() => { failed.set(true); return of(null); }),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((r) => { result.set(r); loading.set(false); });
    onCleanup(() => sub.unsubscribe());
  });

  return { result, loading, failed };
}

// prima metrica della prima riga: è il valore di un KPI (query senza raggruppamenti -> una cella sola)
export function singleValue(r: ReportResult | null): number | null {
  return r && r.metrics.length > 0 ? r.metrics[0][0] : null;
}

// etichetta leggibile di una dimensione: le date arrivato come timestamp ISO da date_trunc e
// vanno accorciate, il resto è testo
export function dimLabel(value: unknown): string {
  if (value == null || value === '') return '—';
  const s = String(value);
  const iso = /^(\d{4})-(\d{2})-\d{2}/.exec(s);
  return iso ? `${iso[2]}/${iso[1]}` : s;
}



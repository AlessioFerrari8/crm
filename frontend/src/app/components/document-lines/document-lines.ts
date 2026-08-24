import { RecordItem } from './../../models/record';
import {
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  output,
  signal,
  WritableSignal,
} from '@angular/core';
import { DocumentLineService } from '../../services/document-line.service';
import { RecordsService } from '../../services/records.service';
import { ToastService } from '../../services/toast.service';
import { DocumentLineDto, LineDraft } from '../../models/document-line';
import { catchError, debounceTime, of, Subject, switchMap } from 'rxjs';
import { documentTotals, lineTotals } from '../../core/money';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-document-lines',
  imports: [],
  templateUrl: './document-lines.html',
})
export class DocumentLines {
  private readonly lineService = inject(DocumentLineService);
  private readonly recordsService = inject(RecordsService);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly documentId = input.required<string>();
  // sconto e trasporto stanno sulla testata (Record.data), non sulle righe. Qui entrano solo per mostrare il totale finale giusto
  readonly documentDiscount = input(0);
  readonly shippingCost = input(0);
  readonly readonlyMode = input(false);

  // il parente ricarica il record, i totali di testata li riscrive il backend
  readonly saved = output<void>();

  protected readonly lines: WritableSignal<LineDraft[]> = signal([]);
  protected readonly loading: WritableSignal<boolean> = signal(true);
  protected readonly saving: WritableSignal<boolean> = signal(false);
  protected readonly dirty: WritableSignal<boolean> = signal(false);

  // aliquote IVA
  protected readonly vatRates: WritableSignal<RecordItem[]> = signal([]);

  // ricerca articolo per riga
  protected readonly searchOpenFor: WritableSignal<number | null> = signal(null);
  protected readonly productResults: WritableSignal<RecordItem[]> = signal([]);
  private readonly productSearch$ = new Subject<string>();

  protected readonly totals = computed(() => {
    documentTotals(this.lines(), this.documentDiscount(), this.shippingCost());
  });

  costructor() {
    // ricarica quando cambia il documento, permette di navigare fra record senza ricreare il componente
    effect(() => {
      const id = this.documentId();
      if (id) this.reload(id);
    });

    this.recordsService
      .query('vat_rate', { pageSize: 50 })
      .pipe(
        catchError(() => of({ items: [] as RecordItem[] } as never)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((res) => this.vatRates.set(res?.items ?? []));

    this.productSearch$
      .pipe(
        debounceTime(250),
        switchMap((q) =>
          q.trim().length < 1
            ? of({ items: [] as RecordItem[] } as never)
            : this.recordsService
                .query('product', { q: q.trim(), pageSize: 8 })
                .pipe(catchError(() => of({ items: [] as RecordItem[] } as never))),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((res) => this.productResults.set(res?.items ?? []));
  }

  private reload(documentId: string): void {
    this.loading.set(true);
    this.lineService
      .list(documentId)
      .pipe(
        catchError(() => of([])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((lines) => {
        this.lines.set(
          lines.map((l) => ({
            kind: l.kind,
            productId: l.productId,
            productLabel: l.productLabel,
            description: l.description,
            quantity: l.quantity,
            unit: l.unit,
            unitPrice: l.unitPrice,
            discountType: l.discountType,
            discountValue: l.discountValue,
            vatRateId: l.vatRateId,
            vatPercent: l.vatPercent,
          })),
        );
        this.loading.set(false);
        this.dirty.set(false);
      });
  }

  // modifica
  // set() sostituisce l'oggetto invece di mutarlo. Mutando infatti i computed non se ne accorgono
  // e ciò vuol dire che perderemmo tutto il suo contenuto precedente
  protected set<K extends keyof LineDraft>(index: number, key: K, value: LineDraft[K]): void {
    this.lines.update((rows) => rows.map((r, i) => (i === index ? { ...r, [key]: value } : r)));
    this.dirty.set(true);
  }

  protected totalsOf(l: LineDraft) {
    return lineTotals(l);
  }

  protected addRow(): void {
    this.lines.update((rows) => [...rows, emptyLine()]);
    this.dirty.set(true);
  }

  protected removeRow(index: number): void {
    this.lines.update((rows) => rows.filter((_, i) => i !== index));
    this.dirty.set(true);
  }

  // riordino
  // si muove la riga, il lineNo lo riassegna il backend dall'ordine dell'array
  protected move(index: number, delta: number): void {
    const target = index + delta;

    this.lines.update((rows) => {
      if (target < 0 || target >= rows.length) return rows;
      const copy = [...rows];

      [copy[index], copy[target]] = [copy[target], copy[index]];
      return copy;
    });
    this.dirty.set(true);
  }

  // articolo
  protected onProductSearch(index: number, term: string): void {
    this.searchOpenFor.set(index);
    this.productSearch$.next(term);
  }

  // la lookup precompila descrizione, prezzo, unità e aliquota
  // è il motivo per cui esiste un catalogo, restano tutti modificabili a mano
  protected pickProduct(index: number, product: RecordItem): void {
    const data = product.data ?? {};
    this.lines.update((rows) =>
      rows.map((r, i) =>
        i !== index
          ? r
          : {
              ...r,
              productId: product.id,
              productLabel: product.title,
              description: r.description || String(data['description'] ?? product.title),
              unitPrice: Number(data['price'] ?? data['unitPrice'] ?? r.unitPrice) || 0,
              unit: (data['unit'] as string) ?? r.unit,
              vatRateId: (data['vatRate'] as string) ?? r.vatRateId,
              vatPercent: this.percentOf((data['vatRate'] as string) ?? r.vatRateId),
            },
      ),
    );
    this.searchOpenFor.set(null);
    this.productResults.set([]);
    this.dirty.set(true);
  }

  protected onVatRate(index: number, vatRateId: string): void {
    this.lines.update((rows) =>
      rows.map((r, i) =>
        i === index ? { ...r, vatRateId, vatPercent: this.percentOf(vatRateId) } : r,
      ),
    );
    this.dirty.set(true);
  }

  // la percentuale si legge dall'aliquota scelta e si tiene sulla riga, il backend fa lo stesso
  private percentOf(vatRateId: string | null): number {
    if (!vatRateId) return 0;
    const rate = this.vatRates().find((r) => r.id === vatRateId);
    return Number(rate?.data?.['rate'] ?? 0) || 0;
  }

  protected vatLabel(r: RecordItem): string {
    return r.title;
  }

  protected save(): void {
    if (this.saving()) return;
    this.saving.set(true);
    const payload: DocumentLineDto[] = this.lines()
      // le righe completamente vuote non si salvano: sono la riga in fondo
      // che l'utente non ha compilato
      .filter((l) =>
        l.kind === 'TEXT' ? !!l.description.trim() : !!(l.description.trim() || l.productId),
      )
      .map(({ vatPercent, productLabel, ...dto }) => dto);

    this.lineService
      .replaceAll(this.documentId(), payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.dirty.set(false);
          this.toasts.success('Righe salvate');
          // il documento va ricaricato: subtotale, IVA e totale li ha
          // riscritti il backend dentro Record.data
          this.saved.emit();
          this.reload(this.documentId());
        },
        error: () => this.saving.set(false),
      });
  }
}

function emptyLine(): LineDraft {
  return {
    kind: 'PRODUCT',
    productId: null,
    productLabel: null,
    description: '',
    quantity: 1,
    unit: null,
    unitPrice: 0,
    discountType: 'PERCENT',
    discountValue: 0,
    vatRateId: null,
    vatPercent: 0,
  };
}



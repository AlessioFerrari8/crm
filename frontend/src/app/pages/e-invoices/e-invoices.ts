import { Component, DestroyRef, inject, signal, WritableSignal } from '@angular/core';
import { UiButton } from '../../components/ui/button';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { EInvoiceService } from '../../services/e-invoice.service';
import { ToastService } from '../../services/toast.service';
import { EInvoice, EInvoiceStatus } from '../../models/e-invoice';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { takeUntil } from 'rxjs';

// la pagina di controllo: il filtro per stato è la funzione ("quelle non consegnate")
@Component({
  selector: 'app-e-invoices',
  imports: [UiButton, RouterLink, DatePipe],
  templateUrl: './e-invoices.html',
  styles: ``,
})
export class EInvoices {
  private readonly service = inject(EInvoiceService);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly filters: { value: EInvoiceStatus | null; label: string }[] = [
    { value: null, label: 'Tutte' },
    { value: 'READY', label: 'Da caricare' },
    { value: 'SENT', label: 'In attesa di esito' },
    { value: 'REJECTED', label: 'Scartate' },
    { value: 'DELIVERED', label: 'Consegnate' },
  ];

  protected readonly LABEL: Record<EInvoiceStatus, string> = {
    DRAFT: 'Bozza', READY: 'Da caricare', SENT: 'Inviata', DELIVERED: 'Consengata',
    REJECTED: 'Scartata', ACCEPTED: 'Accettata'
  };

  protected readonly BADGE: Record<EInvoiceStatus, string> = {
    DRAFT: 'badge-ghost', READY: 'badge-warning', SENT: 'badge-info', DELIVERED: 'badge-success',
    REJECTED: 'badge-error', ACCEPTED: 'badge-success'
  };

  protected readonly status: WritableSignal<EInvoiceStatus | null> = signal(null);
  protected readonly rows: WritableSignal<EInvoice[]> = signal([]);
  protected readonly busy: WritableSignal<string | null> = signal(null);

  constructor() {
    this.load();
  }

  protected setFilter(s: EInvoiceStatus | null): void {
    this.status.set(s);
    this.load();
  }

  protected download(e: EInvoice): void {
    this.service.xml(e.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = e.filename;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    });
  }

  protected send(e: EInvoice): void {
    this.run(e, this.service.send(e.id), 'Pronta: scarica l\'XML e caricalo sul portale del provider');
  }

  protected markSent(e: EInvoice): void {
    this.run(e, this.service.markSent(e.id), 'Segnata come inviata');
  }

  protected upload(e: EInvoice, input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = ''; // stesso file due volte di fila: senza reset il change non scatta
    if (!file) return;
    this.run(e, this.service.uploadReceipt(e.id, file), 'Ricevuta registrata');
  }

  // l'ultimo scarto per intero: è l'unica cosa che dice cosa correggere
  protected lastRejection(e: EInvoice): string | null {
    const ns = (e.notifications ?? []).filter((n) => n.type === 'NS' || n.type === 'NE');
    return ns.length ? ns[ns.length - 1].message : null;
  }

  private run(e: EInvoice, call: ReturnType<EInvoiceService['send']>, ok: string): void {
    this.busy.set(e.id);
    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.rows.update((rs) => rs.map((r) => (r.id === updated.id ? updated : r)));
        this.busy.set(null);
        this.toasts.success(ok);
      },
      error: () => this.busy.set(null)
    });
  }

  private load(): void {
    this.service
      .list(this.status() ?? undefined)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((rows) => this.rows.set(rows));
  }

}

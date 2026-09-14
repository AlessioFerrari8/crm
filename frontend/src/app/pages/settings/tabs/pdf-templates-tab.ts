import { Component, DestroyRef, inject, OnDestroy, signal, WritableSignal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UiButton } from '../../../components/ui/button';
import { UiSpinner } from '../../../components/ui/spinner';
import { PdfService } from '../../../services/pdf.service';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ConfirmService } from '../../../services/confirm.service';
import { ToastService } from '../../../services/toast.service';
import { PdfTemplate } from '../../../models/pdf-template';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

// Tab "stampe": lista dei template a sinistra, editor a destra
// Nessun editor di codice e nessuna dipendenza nuova: una textarea. Chi scrive un template HTML sa già cos'è l'HTML
// e una dipendenza da 400 kb per colorare le parentesi non vale il peso del bundle
@Component({
  selector: 'app-pdf-templates-tab',
  standalone: true,
  imports: [FormsModule, UiButton, UiSpinner],
  templateUrl: './pdf-templates-tab.html',
})
export class PdfTemplatesTab implements OnDestroy {
  // services
  private readonly pdf = inject(PdfService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly confirm = inject(ConfirmService);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly templates: WritableSignal<PdfTemplate[]> = signal([]);
  protected readonly selected: WritableSignal<PdfTemplate | null> = signal(null);
  protected readonly loading: WritableSignal<boolean> = signal(false);
  protected readonly saving: WritableSignal<boolean> = signal(false);
  protected readonly previewing: WritableSignal<boolean> = signal(false);
  protected readonly error: WritableSignal<string | null> = signal(null);

  // i campi editabili stanno in signal a parte e non nel PdfTemplate selezionato: così "annulla" è un reselect
  // non una copia da tenere in sync
  protected readonly html = signal('');
  protected readonly label = signal('');
  protected readonly pageSize = signal('A4');
  protected readonly marginMm = signal(15);

  protected readonly previewUrl: WritableSignal<SafeResourceUrl | null> = signal(null);
  private lastObjectUrl: string | null = null;

  protected readonly pageSizes = ['A4', 'A5', 'A4 LANDSCAPE', 'LETTER'];

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.pdf
      .templates()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (list) => {
          this.templates.set(list);
          this.loading.set(false);
          // riseleziona lo stesso template dopo un salvataggio, non il primo della lista
          const current = this.selected();
          const again = current ? list.find((t) => t.id === current.id) : list[0];
          if (again) this.select(again);
        },
        error: () => this.loading.set(false)
      });
  }

  protected select(t: PdfTemplate): void {
    this.selected.set(t);
    this.html.set(t.html);
    this.label.set(t.label);
    this.pageSize.set(t.pageSize);
    this.marginMm.set(t.marginMm);
    this.error.set(null);
    this.releaseUrl();
    this.previewUrl.set(null);
  }

  protected save(): void {
    const t = this.selected();
    if (!t || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    this.pdf
      .saveTemplate(t.id, {
        key: t.key,
        label: this.label(),
        objectKey: t.objectKey,
        html: this.html(),
        pageSize: this.pageSize(),
        marginMm: this.marginMm()
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.toasts.success('Template salvato');
          this.reload();
        },
        error: (e) => {
          this.saving.set(false);
          this.error.set(e?.error?.message ?? 'Errore nel salvataggio');
        }
      });
  }

  protected duplicate(): void {
    const t = this.selected();
    if (!t) return;
    this.pdf
      .duplicateTemplate(t.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (copy) => {
          this.selected.set(copy);
          this.reload();
        },
        error: (e) => this.error.set(e?.error?.message ?? 'Errore nella duplicazione')
      });
  }

  protected remove(): void {
    const t = this.selected();
    if (!t) return;
    // ConfirmService.ask restituisce una promise, non un Observable. .then, come fanno gli altri tre
    // punti di record-detail.ts
    this.confirm.ask(`Eliminare il template "${t.label}"?`).then((ok) => {
      if (!ok) return;
      this.pdf
        .removeTemplate(t.id)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: () => {
            this.selected.set(null);
            this.reload();
          },
          error: (e) => this.error.set(e?.error?.message ?? 'Errore nell\'eliminazione')
        });
    });
  }

  // non [srcdoc] con l'html grezzo: si vedrebbe l'html nel browser, non il pdf che uscirà
  // Si chiede al backend il pdf vero e lo si mette in un <iframe [src]>
  protected refreshPreview(): void {
    const t = this.selected();
    if (!t || this.previewing()) return;
    this.previewing.set(true);
    this.error.set(null);
    this.pdf
      .preview({
        html: this.html(),
        objectKey: t.objectKey,
        pageSize: this.pageSize(),
        marginMm: this.marginMm()
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          this.releaseUrl();
          this.lastObjectUrl = URL.createObjectURL(blob);
          // bypassSecurityTrustResourceUrl serve: Angular blocca un blob: in [src] di un iframe, e lo blocca in silenzio
          this.previewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(this.lastObjectUrl));
          this.previewing.set(false);
        },
        error: (e) => {
          this.readBlobError(e);
          this.previewing.set(false);
        }
      });
  }

  // con responseType 'blob' anche l'errore arriva come blob: il 400 con dentro "Template error: riga 12" non è leggibile
  // da e.error.message, e senza questo l'utente vede "[object Blob]". In una pagina dove si scrive codice è la differenza
  // tra usabile e inutile
  private readBlobError(e: unknown): void {
    const err = (e as { error?: unknown }).error;
    if (err instanceof Blob) {
      err.text().then((text) => {
        try {
          this.error.set(JSON.parse(text).message ?? text);
        } catch {
          this.error.set(text);
        }
      });
      return;
    }
    this.error.set('Errore nel template');
  }

  // senza revoke ogni anteprima lascia un pdf in memoria finché non ricarichi la pagina
  private releaseUrl(): void {
    if (this.lastObjectUrl) URL.revokeObjectURL(this.lastObjectUrl);
    this.lastObjectUrl = null;
  }

  ngOnDestroy(): void {
    this.releaseUrl();
  }
}

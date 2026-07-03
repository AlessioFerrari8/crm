import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { UiButton } from '../../../components/ui/button';
import { Workspace } from '../../../services/workspace';

// Tab "Azienda" (solo admin): legge il profilo da /profile per il prefill e salva i campi brand
// (nome, colore, logo) via PATCH. updateBrand aggiorna il signal brand condiviso -> la topbar si
// aggiorna all'istante.
@Component({
  selector: 'app-company-tab',
  standalone: true,
  imports: [ReactiveFormsModule, UiButton],
  templateUrl: './company-tab.html'
})
export class CompanyTab {
  private readonly fb = inject(FormBuilder);
  private readonly workspaceService = inject(Workspace);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly saved = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    brandColor: ['#0A84FF', [Validators.required, Validators.pattern(/^#[0-9a-fA-F]{6}$/)]],
    logoUrl: ['', [Validators.maxLength(500)]],
    // link di prenotazione Cal.com (pagina Calendario); vuoto = integrazione spenta
    calcomUrl: ['', [Validators.maxLength(500), Validators.pattern(/^$|^https:\/\/.+/)]]
  });

  constructor() {
    // prefill dal profilo admin (/profile). emitEvent:false per non far scattare valueChanges.
    this.workspaceService
      .loadProfile()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((p) => {
        if (p) {
          this.form.setValue(
            { name: p.name, brandColor: p.brandColor, logoUrl: p.logoUrl ?? '', calcomUrl: p.calcomUrl ?? '' },
            { emitEvent: false }
          );
        }
      });
  }

  protected save(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.saved.set(false);
    const v = this.form.getRawValue();
    this.workspaceService
      .updateBrand({ name: v.name, brandColor: v.brandColor, logoUrl: v.logoUrl.trim() || null, calcomUrl: v.calcomUrl.trim() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.saved.set(true);
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(err?.error?.message ?? 'Errore nel salvataggio');
        }
      });
  }
}

import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { UiButton } from '../../../components/ui/button';
import { Workspace } from '../../../services/workspace';

// Tab "Azienda" (solo admin): legge il profilo da /profile per il prefill e salva i campi brand
// (nome, colore, logo) via PATCH. updateBrand aggiorna il signal brand condiviso -> la topbar si
// aggiorna all'istante
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
    calcomUrl: ['', [Validators.maxLength(500), Validators.pattern(/^$|^https:\/\/.+/)]],
    // anagrafica fiscale: i maxLength sono quelli delle colonne, non un'opinione
    legalName: ['', [Validators.maxLength(255)]],
    vatNumber: ['', [Validators.maxLength(20)]],
    taxCode: ['', [Validators.maxLength(20)]],
    address: ['', [Validators.maxLength(1000)]],
    contactLine: ['', [Validators.maxLength(255)]],
    // sede in pezzi per la fattura elettronica: stessi pattern dell'XSD e di UpdateWorkspaceDto
    fiscalRegime: ['RF01', [Validators.pattern(/^RF(0[1-9]|1[0-9])$/)]],
    street: ['', [Validators.maxLength(60)]],
    zipCode: ['', [Validators.pattern(/^$|^[0-9]{5}$/)]],
    city: ['', [Validators.maxLength(60)]],
    province: ['', [Validators.pattern(/^$|^[A-Za-z]{2}$/)]],
    countryCode: ['IT', [Validators.pattern(/^[A-Z]{2}$/)]]
  });

  constructor() {
    // prefill dal profilo admin (/profile). emitEvent:false per non far scattare valueChanges.
    this.workspaceService
      .loadProfile()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((p) => {
        if (p) {
          this.form.setValue(
            {
              name: p.name,
              brandColor: p.brandColor,
              logoUrl: p.logoUrl ?? '',
              calcomUrl: p.calcomUrl ?? '',
              legalName: p.legalName ?? '',
              vatNumber: p.vatNumber ?? '',
              taxCode: p.taxCode ?? '',
              address: p.address ?? '',
              contactLine: p.contactLine ?? '',
              fiscalRegime: p.fiscalRegime ?? 'RF01',
              street: p.street ?? '',
              zipCode: p.zipCode ?? '',
              city: p.city ?? '',
              province: p.province ?? '',
              countryCode: p.countryCode ?? 'IT'
            },
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
      .updateCompany({
        name: v.name,
        brandColor: v.brandColor,
        logoUrl: v.logoUrl.trim() || null,
        calcomUrl: v.calcomUrl.trim(),
        // trim e basta: il backend legge "" come "svuota" e non come "non toccare"
        legalName: v.legalName.trim(),
        vatNumber: v.vatNumber.trim(),
        taxCode: v.taxCode.trim(),
        address: v.address.trim(),
        contactLine: v.contactLine.trim(),
        fiscalRegime: v.fiscalRegime.trim(),
        street: v.street.trim(),
        zipCode: v.zipCode.trim(),
        city: v.city.trim(),
        province: v.province.trim().toUpperCase(),
        countryCode: v.countryCode
      })
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

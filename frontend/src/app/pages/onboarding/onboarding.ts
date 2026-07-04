import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { UiButton } from '../../components/ui/button';
import { UiPasswordRules, passwordValid } from '../../components/ui/password-rules';
import { UiCard } from '../../components/ui/card';
import { UiLabel } from '../../components/ui/label';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-onboarding',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, UiButton, UiCard, UiLabel, UiPasswordRules],
  templateUrl: './onboarding.html'
})
export class Onboarding {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  // due step come l'originale React: workspace+brand color, poi owner+password
  protected readonly step = signal(1);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    workspaceName: ['', [Validators.required]],
    color: ['#2563eb'],
    name: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    // stesso minimo del backend (OnboardingRequest @Size(min=8))
    password: ['', [Validators.required, Validators.minLength(8)]]
  });

  // valore live della password per la checklist dei requisiti (PasswordPolicy backend)
  protected readonly pwValue = toSignal(this.form.controls.password.valueChanges, { initialValue: '' });

  protected next(): void {
    if (this.form.controls.workspaceName.invalid) {
      this.form.controls.workspaceName.markAsTouched();
      return;
    }
    this.step.set(2);
  }

  protected back(): void {
    this.step.set(1);
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    if (!passwordValid(this.form.controls.password.value)) {
      this.error.set('La password non rispetta tutti i requisiti elencati');
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { workspaceName, name, email, password } = this.form.getRawValue();
    this.auth
      .onboarding({ workspaceName, name, email, password })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.router.navigate(['/']);
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(err?.error?.message ?? 'Errore durante la configurazione');
        }
      });
  }
}

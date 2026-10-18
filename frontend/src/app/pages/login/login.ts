import { Component, DestroyRef, inject, signal, WritableSignal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { UiButton } from '../../components/ui/button';
import { UiCard } from '../../components/ui/card';
import { UiLabel } from '../../components/ui/label';
import { AuthService } from '../../services/auth.service';
import { catchError, of } from 'rxjs';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, UiButton, UiCard, UiLabel],
  templateUrl: './login.html'
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);


  // secondo passo: password già accettata, si chiede il codice dell'app, oppure uno di recupero
  protected readonly mfaStep: WritableSignal<boolean> = signal(false);
  protected readonly useRecovery: WritableSignal<boolean> = signal(false);
  protected readonly code = signal('');

  // decide quale link mostrare in fondo: undefined = in caricamento, null = backend non raggiungibile
  protected readonly status = toSignal(this.auth.status().pipe(catchError(() => of(null))), {
    initialValue: undefined
  });

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth
      .login(email, password)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (r) => {
          this.submitting.set(false);
          if (r === 'mfa') {
            this.mfaStep.set(true);
            return;
          }
          this.router.navigate(['/']);
        },
        error: () => {
          this.submitting.set(false);
          this.error.set('Credenziali non valide');
        }
      });
  }

  protected verify(): void {
    const value = this.code().trim();
    if (!value || this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);
    const call = this.useRecovery()
      ? this.auth.verifyMfa(null, value)
      : this.auth.verifyMfa(value);

    call.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigate(['/']);
      },
      error: (err) => {
        this.submitting.set(false);
        // 401 col token di sfida scaduto (5 min): si ricomincia dalla password
        if (err?.status === 401 && !this.auth.mfaToken()) this.mfaStep.set(false);
        this.error.set('Codice non valido');
        this.code.set('');
      }
    });
  }
}

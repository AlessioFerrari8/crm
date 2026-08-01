import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { UiButton } from '../../components/ui/button';
import { UiPasswordRules, passwordValid } from '../../components/ui/password-rules';
import { errorKey } from '../../core/error-message';
import { AuthService } from '../../services/auth.service';
import { I18nService } from '../../services/i18n';

// Accettazione invito (pagina pubblica, /invite?token=...): l'invitato sceglie nome e password
// e diventa un utente attivo. Il token arriva dal link che l'admin copia dalla users-tab.
@Component({
  selector: 'app-accept-invite',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, UiButton, UiPasswordRules],
  templateUrl: './accept-invite.html'
})
export class AcceptInvite {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly i18n = inject(I18nService);

  protected readonly token = signal(this.route.snapshot.queryParamMap.get('token') ?? '');
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly done = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    password: ['', [Validators.required, Validators.minLength(8)]],
    confirm: ['', Validators.required]
  });

  // valore live della password per la checklist dei requisiti
  protected readonly pwValue = toSignal(this.form.controls.password.valueChanges, { initialValue: '' });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const { name, password, confirm } = this.form.getRawValue();
    if (!passwordValid(password)) {
      this.error.set('La password non rispetta tutti i requisiti elencati');
      return;
    }
    if (password !== confirm) {
      this.error.set('Le password non coincidono');
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.auth
      .acceptInvite(this.token(), name, password)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.done.set(true);
        },
        error: (err) => {
          this.submitting.set(false);
          // questo endpoint e' escluso da errorToastInterceptor (UI di errore inline), quindi
          // la traduzione per codice va fatta qui
          const key = errorKey(err);
          this.error.set(key ? this.i18n.t(key) : (err?.error?.message ?? 'Invito non valido o scaduto'));
        }
      });
  }
}

import { Component, DestroyRef, inject, signal, WritableSignal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { UiButton } from '../../components/ui/button';
import { UiCard } from '../../components/ui/card';
import { UiLabel } from '../../components/ui/label';
import { UiPasswordRules, passwordValid } from '../../components/ui/password-rules';
import { AuthService } from '../../services/auth.service';
import { I18nService } from '../../services/i18n';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { errorKey } from '../../core/error-message';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, UiButton, UiCard, UiLabel, UiPasswordRules],
  templateUrl: './register.html',
  styles: ``,
})
export class Register {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly i18n = inject(I18nService);

  protected readonly submitting: WritableSignal<boolean> = signal(false);
  protected readonly error: WritableSignal<string | null> = signal(null);

  // indirizzo a cui è partita la mail di conferma; null = form ancora da compilare
  protected readonly sentTo: WritableSignal<string | null> = signal(null);
  protected readonly resending: WritableSignal<boolean> = signal(false);
  protected readonly resendMsg: WritableSignal<string | null> = signal(null);

  // undefined = sto ancora caricando, null = il backend non risponde. La get serve anche a ricevere il cookie XSRF-TOKEN, senza il
  // quale il POST di registrazione verrebbe rifiutato
  protected readonly status = toSignal(this.auth.status().pipe(catchError(() => of(null))), {
    initialValue: undefined
  });

  protected readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    // l'istanza e' condivisa: va detto chiaramente prima di creare l'account
    accept: [false, [Validators.requiredTrue]]
  });

  // valore live della password per la checklist dei requisiti (PasswordPolicy backend)
  protected readonly pwValue = toSignal(this.form.controls.password.valueChanges, { initialValue: '' });

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    if (!passwordValid(this.form.controls.password.value)) {
      this.error.set(this.i18n.t('register.weakPassword'));
      return;
    }

    this.submitting.set(true);
    this.error.set(null);
    const { name, email, password } = this.form.getRawValue();
    this.auth
      .register({ name: name.trim(), email: email.trim(), password })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (outcome) => {
          this.submitting.set(false);
          if (outcome === 'verify') {
            // utente creato, ma entra solo dopo il link: mostro "controlla la posta"
            this.sentTo.set(email.trim());
            return;
          }
          this.router.navigate(['/']);
        },
        error: (err) => {
          this.submitting.set(false);
          // /api/auth/* è escluso da errorToastInterceptor: l'errore si mostra qui
          const key = errorKey(err);
          this.error.set(key ? this.i18n.t(key) : (err?.error?.message ?? this.i18n.t('register.failed')));
        }
      });
  }

  protected resend(): void {
    const to = this.sentTo();
    if (!to || this.resending()) return;
    this.resending.set(true);
    this.resendMsg.set(null);
    this.auth
      .resendVerification(to)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.resending.set(false);
          this.resendMsg.set(this.i18n.t('register.resendSent'))
        },
        error: () => {
          this.resending.set(false);
          this.resendMsg.set(this.i18n.t('auth.tooMany'));
        }
      });
  }

}

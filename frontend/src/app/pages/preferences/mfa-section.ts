import { Component, computed, DestroyRef, inject, signal, WritableSignal } from '@angular/core';
import { UiButton } from '../../components/ui/button';
import { AuthService } from '../../services/auth.service';
import { MfaSetup } from '../../models/mfa';
import { switchMap, takeUntil } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-mfa-section',
  imports: [UiButton],
  templateUrl: './mfa-section.html'
})
export class MfaSection {
  // services
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly enabled = computed(() => this.auth.user()?.mfaEnabled ?? false);
  protected readonly setup: WritableSignal<MfaSetup | null> = signal(null);
  protected readonly recoveryCodes: WritableSignal<string[] | null> = signal(null);
  protected readonly code = signal('');
  protected readonly password = signal('');
  protected readonly busy: WritableSignal<boolean> = signal(false);
  protected readonly error: WritableSignal<string | null> = signal(null);

  protected start(): void {
    this.call(this.auth.mfaSetup(), (s) => this.setup.set(s));
  }

  protected activate(): void {
    // dopo l'attivazione si ricarica /me, così `enabled` passa a true
    this.call(this.auth.mfaActivate(this.code().trim()).pipe(
      switchMap((codes) => {
        this.recoveryCodes.set(codes);
        return this.auth.load();
      })), () => {
        this.setup.set(null);
        this.code.set('');
      });
  }

  protected disable(): void {
    this.call(this.auth.mfaDisable(this.password()).pipe(switchMap(() => this.auth.load())), () => this.password.set(''));
  }

  protected copyCodes(): void {
    void navigator.clipboard.writeText((this.recoveryCodes() ?? []).join('\n'));
  }

  protected downloadCodes(): void {
    const blob = new Blob([(this.recoveryCodes() ?? []).join('\n') + '\n'], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'northleap-codici-recupero.txt';
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  private call<T>(obs: import('rxjs').Observable<T>, done: (v: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    obs.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (v) => {
        this.busy.set(false);
        done(v);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.message ?? 'Operazione non riuscita');
      }
    });
  }
}

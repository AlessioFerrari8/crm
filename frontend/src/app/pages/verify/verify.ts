import { Component, DestroyRef, inject, signal, WritableSignal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { UiCard } from '../../components/ui/card';
import { AuthService } from '../../services/auth.service';
import { I18nService } from '../../services/i18n';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

// pagina aperta dal link della mail: /verify?token=...
@Component({
  selector: 'app-verify',
  imports: [RouterLink, UiCard],
  templateUrl: './verify.html',
  styles: ``,
})
export class Verify {
  protected readonly i18n = inject(I18nService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state: WritableSignal<'loading' | 'ok' | 'error'> = signal('loading');

  constructor() {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.state.set('error');
      return;
    }
    this.auth
      .verifyEmail(token)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.state.set('ok'),
        error: () => this.state.set('error')
      });
  }
}

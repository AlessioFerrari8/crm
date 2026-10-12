import { Component, inject, signal, WritableSignal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { PortalService } from '../../../services/portal.service';



// login e accettazione dell'invito nella stessa pagina: /portal/login e /portal/invite?token=...
@Component({
  selector: 'app-portal-login',
  imports: [FormsModule],
  templateUrl: './portal-login.html',
  styles: ``,
})
export class PortalLogin {

  private readonly portal = inject(PortalService);
  private readonly router = inject(Router);
  protected readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');

  protected email = '';
  protected password = '';
  protected readonly busy: WritableSignal<boolean> = signal(false);
  protected readonly error: WritableSignal<string | null> = signal(null);
  protected readonly info: WritableSignal<string | null> = signal(null);

  protected submit(): void {
    this.busy.set(true);
    this.error.set(null);
    if (this.token) {
      this.portal.acceptInvite(this.token, this.password).subscribe({
        next: () => {
          this.busy.set(false);
          this.info.set('Password salvata. Ora accedi con la tua email.');
          this.router.navigate(['/portal/login']);
        },
        error: (e) => {
          this.busy.set(false);
          this.error.set(e?.error?.message ?? 'Invito non valido o scaduto');
        }
      });
      return;
    }
    this.portal.login(this.email, this.password).subscribe({
      next: () => this.router.navigate(['/portal']),
      error: () => {
        this.busy.set(false);
        this.error.set('Credenziali non valide');
      }
    });
  }

}

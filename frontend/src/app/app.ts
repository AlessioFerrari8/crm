import { Component, computed, inject } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';

import { CommandPalette } from './components/command-palette/command-palette';
import { Sidebar } from './components/sidebar/sidebar';
import { Topbar } from './components/topbar/topbar';
import { UiConfirmDialog } from './components/ui/confirm-dialog';
import { UiToasts } from './components/ui/toasts';
import { AuthService } from './services/auth.service';
import { PwaUpdateService } from './services/pwa-update.service';
import { PwaInstallService } from './services/pwa-install.service';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';

// Root: ospita la shell (sidebar/topbar + contenuto via router-outlet). La shell si // mostra solo quando l'utente è autenticato;
// login/onboarding (non autenticato) rendono solo il router-outlet, senza sidebar
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Topbar, Sidebar, CommandPalette, UiToasts, UiConfirmDialog],
  templateUrl: './app.html'
})
export class App {
  private readonly authenticated = inject(AuthService).isAuthenticated;
  private readonly url = toSignal(
    inject(Router).events.pipe(filter((e) => e instanceof NavigationEnd), map((e) => (e as NavigationEnd).urlAfterRedirects)),
    { initialValue: location.pathname }
  );

  // pagine per chi non è un utente interno: niente shell anche se nello stesso browser c'è una sessione interna
  private readonly bare = computed(() => /^\/(portal|q\/|nps\/|f\/)/.test(this.url()));
  protected readonly isAuthenticated = computed(() => this.authenticated() && !this.bare());

  // una volta sola, dal componente radice. Finché nessuno li chiama il compilatore non guarda nemmeno i due file,
  // ed è per questo che ng build era verde
  constructor() {
    inject(PwaUpdateService).init();
    inject(PwaInstallService).init();
  }
}

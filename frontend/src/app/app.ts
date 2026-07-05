import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { CommandPalette } from './components/command-palette/command-palette';
import { Sidebar } from './components/sidebar/sidebar';
import { Topbar } from './components/topbar/topbar';
import { UiConfirmDialog } from './components/ui/confirm-dialog';
import { UiToasts } from './components/ui/toasts';
import { AuthService } from './services/auth.service';

// Root: ospita la shell (sidebar/topbar + contenuto via router-outlet). La shell si
// mostra solo quando l'utente e' autenticato; login/onboarding (non autenticato) rendono solo il
// router-outlet, senza sidebar.
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Topbar, Sidebar, CommandPalette, UiToasts, UiConfirmDialog],
  templateUrl: './app.html'
})
export class App {
  protected readonly isAuthenticated = inject(AuthService).isAuthenticated;
}

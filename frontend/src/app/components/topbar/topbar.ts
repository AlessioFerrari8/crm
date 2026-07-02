import { Component, DestroyRef, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Bell, Languages, LogOut, LucideAngularModule, Moon, Search, Settings, Sun } from 'lucide-angular';
import { switchMap, timer } from 'rxjs';

import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';
import { ThemeService } from '../../services/theme.service';
import { I18nService } from '../../services/i18n';
import { Workspace } from '../../services/workspace';

const NOTIFICATION_POLL_MS = 60_000;

// Barra superiore: logo, hamburger (toggle del drawer in app.html via id), tema, campanella
// notifiche, menu utente. Possiede il polling delle notifiche (niente realtime lato backend).
// :host display:contents -> il <header> diventa figlio diretto del flex .drawer-content, layout
// identico a prima della scomposizione.
@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [RouterLink, LucideAngularModule],
  templateUrl: './topbar.html',
  styles: ':host { display: contents; }'
})
export class Topbar {
  // service notifiche
  private readonly notificationService = inject(NotificationService);
  private readonly auth = inject(AuthService);
  private readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  // servizio per la lingua
  readonly i18n = inject(I18nService);
  protected readonly workspace = inject(Workspace);

  protected readonly user = this.auth.user;
  protected readonly notifications = this.notificationService.items;
  protected readonly unreadCount = this.notificationService.unreadCount;
  protected readonly isDark = computed(() => this.theme.theme() === 'dark');

  // libreria lucide
  protected readonly BellIcon = Bell;
  protected readonly SunIcon = Sun;
  protected readonly MoonIcon = Moon;
  protected readonly SettingsIcon = Settings;
  protected readonly LogOutIcon = LogOut;
  protected readonly Languages = Languages;
  protected readonly SearchIcon = Search;

  constructor() {
    this.workspace.load().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
    timer(0, NOTIFICATION_POLL_MS)
      .pipe(
        switchMap(() => this.notificationService.refresh()),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe();
  }

  protected toggleTheme(): void {
    this.theme.toggle();
  }

  // invio nella barra di ricerca -> pagina di ricerca globale col termine precompilato
  protected goSearch(term: string): void {
    const q = term.trim();
    this.router.navigate(['/search'], q ? { queryParams: { q } } : undefined);
  }

  protected markAllRead(): void {
    this.notificationService.markAllRead().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }

  // click su una notifica: la segna letta e, se ha un link interno (es. /o/task/{id} generato
  // dall'azione notify_user dei workflow), ci naviga. I link esterni vengono ignorati.
  protected openNotification(n: { id: string; readAt: string | null; link: string | null }): void {
    if (!n.readAt) {
      this.notificationService.markRead(n.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
    }
    if (n.link?.startsWith('/')) {
      this.router.navigateByUrl(n.link);
    }
  }

  protected logout(): void {
    this.auth
      .logout()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.router.navigate(['/login']));
  }
}

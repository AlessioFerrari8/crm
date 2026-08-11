import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Bell, KeyRound, Languages, LogOut, LucideAngularModule, Moon, Search, Settings, SlidersHorizontal, Sun } from 'lucide-angular';
import { switchMap, timer } from 'rxjs';

import { UiPasswordRules, passwordValid } from '../ui/password-rules';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';
import { ThemeService } from '../../services/theme.service';
import { UserPreferenceService } from '../../services/user-preference.service';
import { errorKey } from '../../core/error-message';
import { I18nService } from '../../services/i18n';
import { Workspace } from '../../services/workspace';

const NOTIFICATION_POLL_MS = 300_000;

// Barra superiore: logo, hamburger (toggle del drawer in app.html via id), tema, campanella
// notifiche, menu utente. Possiede lo stream SSE delle notifiche piu' un polling di riserva a
// 5 minuti, che copre il caso in cui l'SSE si rompa in silenzio (es. buffering di un proxy).
// :host display:contents -> il <header> diventa figlio diretto del flex .drawer-content, layout
// identico a prima della scomposizione.
@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [RouterLink, LucideAngularModule, UiPasswordRules],
  templateUrl: './topbar.html',
  styles: ':host { display: contents; }'
})
export class Topbar {
  // service notifiche
  private readonly notificationService = inject(NotificationService);
  private readonly auth = inject(AuthService);
  private readonly theme = inject(ThemeService);
  private readonly userPreferences = inject(UserPreferenceService);
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
  protected readonly KeyIcon = KeyRound;
  protected readonly SlidersIcon = SlidersHorizontal;

  constructor() {
    this.workspace.load().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
    timer(0, NOTIFICATION_POLL_MS)
      .pipe(
        switchMap(() => this.notificationService.refresh()),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe();

    this.notificationService.openConnection();
    this.destroyRef.onDestroy(() => this.notificationService.closeConnection());

    // preferenze di formattazione: la topbar è il primo componente montato dell'area
    // autenticata, quindi è qui che va il bootstrap. Senza, field-format resta sui DEFAULT
    // e le liste mostrano i numeri con la formattazione sbagliata
    this.userPreferences.load().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
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
      .subscribe(() => {
        // le preferenze sono per utente: senza reset il prossimo login vedrebbe quelle
        // del precedente finché la GET non torna
        this.userPreferences.reset();
        this.router.navigate(['/login']);
      });
  }

  // ── cambio password self-service (modal) ───────────────────────────────────
  protected readonly changePwOpen = signal(false);
  protected readonly cpCurrent = signal('');
  protected readonly cpNew = signal('');
  protected readonly cpConfirm = signal('');
  protected readonly cpSubmitting = signal(false);
  protected readonly cpError = signal<string | null>(null);
  protected readonly cpDone = signal(false);
  protected readonly cpNewOk = computed(() => passwordValid(this.cpNew()));

  protected openChangePassword(): void {
    this.cpCurrent.set('');
    this.cpNew.set('');
    this.cpConfirm.set('');
    this.cpError.set(null);
    this.cpDone.set(false);
    this.changePwOpen.set(true);
  }

  protected submitChangePassword(): void {
    if (!this.cpCurrent() || !this.cpNewOk() || this.cpSubmitting()) return;
    if (this.cpNew() !== this.cpConfirm()) {
      this.cpError.set('Le nuove password non coincidono');
      return;
    }
    this.cpSubmitting.set(true);
    this.cpError.set(null);
    this.auth
      .changePassword(this.cpCurrent(), this.cpNew())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.cpSubmitting.set(false);
          this.cpDone.set(true);
          setTimeout(() => this.changePwOpen.set(false), 1500);
        },
        error: (err) => {
          this.cpSubmitting.set(false);
          // /api/auth/* e' escluso da errorToastInterceptor: traduzione per codice qui
          const key = errorKey(err);
          this.cpError.set(key ? this.i18n.t(key) : (err?.error?.message ?? 'Cambio password non riuscito'));
        }
      });
  }
}

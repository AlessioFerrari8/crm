import { HttpClient } from '@angular/common/http';
import { Injectable, WritableSignal, computed, inject, signal } from '@angular/core';
import { Observable, catchError, of, switchMap, tap } from 'rxjs';

import { API_BASE_URL } from '../core/api-config';
import { IAuthResponse } from '../models/IAuthResponse';
import { IMeResponse } from '../models/IMeResponse';
import { IOnboardingPayload } from '../models/IOnboardingPayload';

// I token JWT vivono solo nei cookie httpOnly impostati dal backend (mai letti/scritti qui via
// JS) - questo service tiene solo lo stato "chi e' loggato", popolato da GET /auth/me.
@Injectable({ providedIn: 'root' })
export class AuthService {
  // client http
  private readonly http = inject(HttpClient);

  // utente
  private readonly _user: WritableSignal<IMeResponse | null> = signal(null);
  private readonly _loading = signal(true);

  readonly user = this._user.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);

  // chiamata una volta al bootstrap (provideAppInitializer in app.config.ts) e dopo ogni
  // login/onboarding riuscito, per popolare/aggiornare user+roles da /me
  load(): Observable<IMeResponse | null> {
    this._loading.set(true);
    return this.http.get<IMeResponse>(`${API_BASE_URL}/api/auth/me`).pipe(
      tap((user) => {
        this._user.set(user);
        this._loading.set(false);
      }),
      catchError(() => {
        this._user.set(null);
        this._loading.set(false);
        return of(null);
      })
    );
  }

  // metodo login che punta all'omonimo endpoint
  login(email: string, password: string): Observable<IMeResponse | null> {
    return this.http
      .post<IAuthResponse>(`${API_BASE_URL}/api/auth/login`, { email, password })
      .pipe(switchMap(() => this.load()));
  }

  onboarding(payload: IOnboardingPayload): Observable<IMeResponse | null> {
    return this.http
      .post<IAuthResponse>(`${API_BASE_URL}/api/auth/onboarding`, payload)
      .pipe(switchMap(() => this.load()));
  }

  // accettazione invito (endpoint pubblico): l'invitato sceglie nome e password col token
  // ricevuto dall'admin. Non logga: dopo il successo si passa dal login normale.
  acceptInvite(token: string, name: string, password: string): Observable<unknown> {
    return this.http.post<unknown>(`${API_BASE_URL}/api/users/accept-invite`, { token, name, password });
  }

  // cambio password self-service: la sessione corrente resta viva, le altre vengono revocate
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.patch<void>(`${API_BASE_URL}/api/auth/password`, { currentPassword, newPassword });
  }

  // usata internamente dall'interceptor su 401/403; nessun refresh token nel body, viaggia
  // come cookie automaticamente
  refresh(): Observable<IAuthResponse> {
    return this.http.post<IAuthResponse>(`${API_BASE_URL}/api/auth/refresh`, {});
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/api/auth/logout`, {}).pipe(
      tap(() => this.clearSession()),
      catchError(() => {
        this.clearSession();
        return of(void 0);
      })
    );
  }

  clearSession(): void {
    this._user.set(null);
  }
}

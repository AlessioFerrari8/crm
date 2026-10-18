import { API_BASE_URL } from './../core/api-config';
import { HttpClient } from '@angular/common/http';
import { Injectable, Service, WritableSignal, computed, inject, signal } from '@angular/core';
import { Observable, catchError, of, switchMap, tap } from 'rxjs';

import { IAuthResponse } from '../models/IAuthResponse';
import { IMeResponse } from '../models/IMeResponse';
import { IOnboardingPayload } from '../models/IOnboardingPayload';
import { MfaChallenge, MfaSetup } from '../models/mfa';
import { IAuthStatus } from '../models/IAuthStatus';
import { IRegisterPayload } from '../models/IRegisterPayload';

// I token JWT vivono solo nei cookie httpOnly impostati dal backend (mai letti/scritti qui via
// JS) - questo service tiene solo lo stato "chi e' loggato", popolato da GET /auth/me.
@Service()
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

  // il token di sfida vive in un signal, non in localStorage. è una sorta di mezzo login, e localStorage è leggibile
  // da qualunque XSS, quindi evitiamo
  readonly mfaToken: WritableSignal<string | null> = signal(null);

  // 'mfa' = password giusta, ora serve il codice: la pagina mostra il secondo passo
  login(email: string, password: string): Observable<IMeResponse | null | 'mfa'> {
    return this.http
      .post<IAuthResponse | MfaChallenge>(`${API_BASE_URL}/api/auth/login`, { email, password })
      .pipe(
        switchMap((r) => {
          if ('mfaRequired' in r && r.mfaRequired) {
            this.mfaToken.set(r.mfaToken);
            return of('mfa' as const);
          }
          return this.load();
        })
      );
  }

  verifyMfa(code: string | null, recoveryCode?: string): Observable<IMeResponse | null> {
    return this.http
      .post<IAuthResponse>(`${API_BASE_URL}/api/auth/mfa/verify`, { mfaToken: this.mfaToken(), code, recoveryCode })
      .pipe(
        tap(() => this.mfaToken.set(null)),
        switchMap(() => this.load())
      );
  }

  onboarding(payload: IOnboardingPayload): Observable<IMeResponse | null> {
    return this.http
      .post<IAuthResponse>(`${API_BASE_URL}/api/auth/onboarding`, payload)
      .pipe(switchMap(() => this.load()));
  }

  // stato pubblico: serve al login per scegliere il link giusto e alla pagina /register
  status(): Observable<IAuthStatus> {
    return this.http.get<IAuthStatus>(`${API_BASE_URL}/api/auth/status`);
  }

  // registrazione pubblica: crea un utente "member" e lo logga (cookie), poi carica /me
  register(payload: IRegisterPayload): Observable<IMeResponse | null> {
    return this.http
      .post<IAuthResponse>(`${API_BASE_URL}/api/auth/register`, payload)
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

  mfaSetup(): Observable<MfaSetup> {
    return this.http.post<MfaSetup>(`${API_BASE_URL}/api/auth/mfa/setup`, {});
  }

  mfaActivate(code: string) {
    return this.http.post<string[]>(`${API_BASE_URL}/api/auth/mfa/activate`, { code });
  }

  mfaDisable(password: string) {
    return this.http.delete<void>(`${API_BASE_URL}/api/auth/mfa`, { body: { password } });
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

import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, of, switchMap, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';
import { API_BASE_URL } from './api-config';

const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

// le richieste su /api/auth/* non devono mai innescare un retry-via-refresh (evita loop:
// un 401/403 su /refresh stesso, o su /login con credenziali sbagliate, non deve tentare
// un altro refresh)
const NO_RETRY_PATHS = ['/api/auth/'];

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : null;
}

// Angular HttpClient allega l'header X-XSRF-TOKEN in automatico SOLO per richieste
// same-origin; qui frontend (:4200) e backend (:8080) sono origin diverse, quindi il cookie
// XSRF-TOKEN (non httpOnly, leggibile da JS apposta - vedi SecurityConfig lato backend) va
// letto e allegato a mano.
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(API_BASE_URL)) {
    return next(req);
  }

  const baseReq = req.clone({ withCredentials: true });
  const http = inject(HttpClient);
  const authService = inject(AuthService);
  const isAuthPath = NO_RETRY_PATHS.some((path) => req.url.includes(path));

  const withXsrf = (): Observable<typeof baseReq> => {
    if (!MUTATING_METHODS.has(req.method)) {
      return of(baseReq);
    }
    const cookie = readCookie('XSRF-TOKEN');
    if (cookie) {
      return of(baseReq.clone({ setHeaders: { 'X-XSRF-TOKEN': cookie } }));
    }
    // Il cookie XSRF-TOKEN viene emesso dal backend solo quando una richiesta arriva davvero
    // a destinazione. Se la prima chiamata del bootstrap (GET /auth/me) e' fallita a livello
    // di rete (backend non ancora pronto), il cookie non esiste e ogni POST successivo
    // verrebbe respinto con 403 dal CsrfFilter. GET /auth/status e' pubblico e lo rilascia.
    return http
      .get(`${API_BASE_URL}/api/auth/status`, { withCredentials: true })
      .pipe(
        catchError(() => of(null)),
        switchMap(() => {
          const refreshed = readCookie('XSRF-TOKEN');
          return of(refreshed ? baseReq.clone({ setHeaders: { 'X-XSRF-TOKEN': refreshed } }) : baseReq);
        })
      );
  };

  return withXsrf().pipe(switchMap((authReq) => next(authReq).pipe(
    catchError((error: unknown) => {
      // questo backend risponde 403 (non 401) per le richieste non autenticate quando manca un
      // AuthenticationEntryPoint dedicato (vedi nota in JwtAuthenticationFilter lato backend) -
      // trattiamo entrambi gli status come "prova un refresh" per le richieste non-auth
      if (
        error instanceof HttpErrorResponse &&
        (error.status === 401 || error.status === 403) &&
        !isAuthPath
      ) {
        return authService.refresh().pipe(
          switchMap(() => next(authReq)),
          catchError((refreshError: unknown) => {
            authService.clearSession();
            return throwError(() => refreshError);
          })
        );
      }
      return throwError(() => error);
    })
  )));
};

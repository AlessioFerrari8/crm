import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { ToastService } from '../services/toast.service';

// opt-out per i chiamanti che gestiscono gli errori da soli riga per riga (es. import CSV:
// senza, 500 righe fallite = 500 toast)
export const SKIP_ERROR_TOAST = new HttpContextToken<boolean>(() => false);

const MUTATING = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

// Toast automatico sugli errori delle richieste MUTANTI: prima un 400 di validazione o un 403
// RBAC morivano in silenzio nei catchError dei componenti. I GET restano silenziosi (le liste
// hanno i loro fallback), /api/auth/* e accept-invite hanno gia' UI inline dedicate, il 401 e'
// il flusso di refresh. Registrato PRIMA di authInterceptor nell'array: sulla risposta gira per
// ultimo, quindi vede solo gli errori definitivi (dopo l'eventuale refresh+retry).
export const errorToastInterceptor: HttpInterceptorFn = (req, next) => {
  const toasts = inject(ToastService);
  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && shouldToast(req.method, req.url, err.status)
          && !req.context.get(SKIP_ERROR_TOAST)) {
        toasts.error(messageFor(err));
      }
      return throwError(() => err);
    })
  );
};

function shouldToast(method: string, url: string, status: number): boolean {
  if (!MUTATING.has(method)) return false;
  if (url.includes('/api/auth/')) return false;
  if (url.includes('/api/users/accept-invite')) return false;
  if (status === 401) return false;
  return status >= 400 || status === 0;
}

function messageFor(err: HttpErrorResponse): string {
  const m = (err.error as { message?: unknown } | null)?.message;
  if (typeof m === 'string' && m.length > 0) return m;
  if (err.status === 403) return 'Non hai i permessi per questa operazione';
  if (err.status === 429) return 'Troppe richieste, riprova tra poco';
  if (err.status === 0) return 'Server non raggiungibile';
  return 'Operazione non riuscita';
}

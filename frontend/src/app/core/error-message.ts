import { HttpErrorResponse } from '@angular/common/http';

import { TranslationKey } from '../services/i18n';

// Specchio di ErrorCodes.java: i codici che il backend manda nel campo `code` di una 400.
// Solo questi vengono tradotti, tutto il resto ricade sul `message` del server, che resta la
// fallback e non sparisce mai.
//
// Sta qui e non dentro l'interceptor perche' meta' di questi codici non passa mai di la':
// shouldToast() esclude /api/auth/* e /api/users/accept-invite, che hanno UI di errore inline.
const CODE_KEYS: Record<string, TranslationKey> = {
  PASSWORD_TOO_WEAK: 'error.passwordTooWeak',
  INVITE_INVALID: 'error.inviteInvalid',
  EMAIL_TAKEN: 'error.emailTaken',
  CURRENT_PASSWORD_INVALID: 'error.currentPasswordInvalid',
};

// null = nessun codice noto, il chiamante decide come degradare
export function errorKey(err: unknown): TranslationKey | null {
  if (!(err instanceof HttpErrorResponse)) return null;
  const code = (err.error as { code?: unknown } | null)?.code;
  return typeof code === 'string' ? CODE_KEYS[code] ?? null : null;
}

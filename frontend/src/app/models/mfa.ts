// risposta di /api/auth/login quando l'utente ha l'MFA attivo: niente cookie, solo il token di sfida
export interface MfaChallenge {
  mfaRequired: true;
  mfaToken: string;
}

export interface MfaSetup {
  secretBase32: string;
  qrPng: string; // già data:image/png;base64,...
}

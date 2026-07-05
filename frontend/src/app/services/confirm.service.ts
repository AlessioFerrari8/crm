import { Injectable, signal } from '@angular/core';

interface ConfirmRequest {
  message: string;
  resolve: (confirmed: boolean) => void;
}

// Sostituisce i confirm() nativi del browser con una modal propria (ui-confirm-dialog nel root):
// ask(msg) risolve true/false. Una richiesta alla volta (le conferme sono per natura modali).
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  readonly pending = signal<ConfirmRequest | null>(null);

  ask(message: string): Promise<boolean> {
    return new Promise((resolve) => this.pending.set({ message, resolve }));
  }

  answer(confirmed: boolean): void {
    this.pending()?.resolve(confirmed);
    this.pending.set(null);
  }
}

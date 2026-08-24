import { Injectable, Service, signal } from '@angular/core';

export interface Toast {
  id: number;
  kind: 'success' | 'error' | 'info';
  text: string;
}

// Toast globali (ui-toasts nel root): feedback immediato su azioni riuscite e, tramite
// errorToastInterceptor, su ogni errore server delle richieste mutanti — prima un 400 di
// validazione sul salvataggio spariva nel nulla.
@Service()
export class ToastService {
  private seq = 0;
  readonly toasts = signal<Toast[]>([]);

  success(text: string): void {
    this.push('success', text, 3500);
  }

  error(text: string): void {
    this.push('error', text, 6000);
  }

  info(text: string): void {
    this.push('info', text, 3500);
  }

  dismiss(id: number): void {
    this.toasts.update((ts) => ts.filter((t) => t.id !== id));
  }

  private push(kind: Toast['kind'], text: string, ttlMs: number): void {
    const id = ++this.seq;
    this.toasts.update((ts) => [...ts, { id, kind, text }]);
    setTimeout(() => this.dismiss(id), ttlMs);
  }
}

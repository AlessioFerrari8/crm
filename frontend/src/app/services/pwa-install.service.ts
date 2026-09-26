import { Service, signal } from '@angular/core';

// beforeinstallprompt non è nei tipi standard di TS
declare global {
  interface WindowEventMap {
    beforeinstallprompt: Event & { prompt: () => Promise<void> };
  }
}

@Service()
export class PwaInstallService {
  readonly deferred = signal<(Event & { prompt: () => Promise<void> }) | null>(null);

  init(): void {
    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault(); // blocca il banner del browser
      this.deferred.set(e);
    });
  }

  // solo dentro un gesto dell'utente, e solo una volta per evento catturato: chiamarlo al caricamento
  // non fa niente e non dà errore
  async install(): Promise<void> {
    const e = this.deferred();
    if (!e) return;
    await e.prompt();
    this.deferred.set(null);
  }
}

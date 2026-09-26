import { inject, Service } from '@angular/core';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter } from 'rxjs/operators';
import { ToastService } from './toast.service';

@Service()
export class PwaUpdateService {
  private readonly updates = inject(SwUpdate);
  private readonly toast = inject(ToastService);
  private started = false;

  init(): void {
    if (!this.updates.isEnabled || this.started) return;
    this.started = true;

    this.updates.versionUpdates
      .pipe(filter((e): e is VersionReadyEvent => e.type === 'VERSION_READY'))
      .subscribe(() => {
        // niente toast con bottone: ToastService non ce l'ha. Si avvisa e si
        // ricarica al prossimo giro di navigazione dell'utente
        this.toast.info('Nuova versione disponibile: ricarica la pagina');
      });

    // senza il controllo periodico una tab lasciata aperta per giorni non
    // scopre mai un aggiornamento
    setInterval(() => void this.updates.checkForUpdate(), 6 * 60 * 60 * 1000);
  }
}

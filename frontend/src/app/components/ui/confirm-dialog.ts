import { Component, HostListener, inject } from '@angular/core';

import { ConfirmService } from '../../services/confirm.service';

// Modal di conferma globale (montata una volta nel root), pilotata da ConfirmService.ask()
@Component({
  selector: 'ui-confirm-dialog',
  standalone: true,
  template: `
    @if (svc.pending(); as req) {
      <div class="fixed inset-0 z-[150] bg-black/40 flex items-start justify-center p-4 pt-32"
           (click)="svc.answer(false)">
        <div class="w-full max-w-sm bg-base-100 border border-base-300 rounded-sm shadow-xl p-4"
             (click)="$event.stopPropagation()">
          <p class="text-sm mb-4">{{ req.message }}</p>
          <div class="flex justify-end gap-2">
            <button class="btn btn-ghost btn-sm rounded-sm" (click)="svc.answer(false)">Annulla</button>
            <button class="btn btn-error btn-sm rounded-sm" (click)="svc.answer(true)" autofocus>Conferma</button>
          </div>
        </div>
      </div>
    }
  `
})
export class UiConfirmDialog {
  protected readonly svc = inject(ConfirmService);

  @HostListener('document:keydown.escape')
  protected onEsc(): void {
    if (this.svc.pending()) this.svc.answer(false);
  }
}

import { Component, inject } from '@angular/core';

import { ToastService } from '../../services/toast.service';

// Contenitore dei toast, in basso a destra sopra tutto; click per chiudere subito
@Component({
  selector: 'ui-toasts',
  standalone: true,
  template: `
    <div class="toast toast-end toast-bottom z-[200]">
      @for (t of svc.toasts(); track t.id) {
        <div class="alert rounded-sm shadow-lg py-2 text-sm cursor-pointer max-w-sm whitespace-normal"
             [class.alert-success]="t.kind === 'success'"
             [class.alert-error]="t.kind === 'error'"
             [class.alert-info]="t.kind === 'info'"
             (click)="svc.dismiss(t.id)">
          {{ t.text }}
        </div>
      }
    </div>
  `
})
export class UiToasts {
  protected readonly svc = inject(ToastService);
}

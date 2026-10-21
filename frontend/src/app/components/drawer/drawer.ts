import { Component, inject, input, output } from '@angular/core';
import { I18nService } from '../../services/i18n';

// pannello laterale
@Component({
  selector: 'app-drawer',
  standalone: true,
  templateUrl: './drawer.html'
})
export class Drawer {
  protected readonly i18n = inject(I18nService);

  readonly open = input(false);
  readonly title = input('');
  readonly close = output<void>();
}

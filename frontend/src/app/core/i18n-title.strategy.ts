import { effect, inject, Injectable } from "@angular/core";
import { Title } from "@angular/platform-browser";
import { RouterStateSnapshot, TitleStrategy } from "@angular/router";
import { I18nService, TranslationKey } from "../services/i18n";

// le rotte hanno title: 'route.login' (una chiave); qui la chiave diventa il titolo della scheda e si riscrive da sola quando
// l'utente cambia lingua
@Injectable({ providedIn: 'root' })
export class I18nTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly i18n = inject(I18nService);
  private key: string | undefined;

  constructor() {
    super();
    effect(() => {
      this.i18n.lang();
      this.apply();
    });
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.key = this.buildTitle(snapshot);
    this.apply();
  }

  private apply(): void {
    this.title.setTitle(this.key ? this.i18n.t(this.key as TranslationKey) : 'CRM');
  }
}


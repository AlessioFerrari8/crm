import { Injectable, Service, signal, WritableSignal } from '@angular/core';
import { it } from '../i18n/it';
import { en } from '../i18n/en';

type Lang = 'it' | 'en';

// Le chiavi valide sono quelle del dizionario italiano. Usandole come tipo ottengo due cose: l'autocomplete sulle chiavi
// quando chiamo t('...'), e soprattutto la PARITÀ garantita tra le lingue -> se in en.ts manca una chiave presente in it.ts,
// il progetto non compila.
export type TranslationKey = keyof typeof it;

const DICT: Record<Lang, Record<TranslationKey, string>> = { it, en };

// copia non reattiva della lingua: serve alle funzioni che non sono in un componente
let current: Lang = readInitialLang();
document.documentElement.lang = current;

// 'en-GB' e non 'en-US': giorno/mese in ordine europeo, come in italiano
export function currentLocale(): string {
  return current === 'it' ? 'it-IT' : 'en-GB';
}

@Service()
export class I18nService {
  readonly lang: WritableSignal<Lang> = signal<Lang>(current);

    use(lang: Lang): void {
      current = lang;
      document.documentElement.lang = lang;
      this.lang.set(lang);
      localStorage.setItem('lang', lang);
    }

    toggle(): void {
      this.use(this.lang() === 'it' ? 'en' : 'it');
    }

    // t('common.save')  /  t('list.selected', { n: 3 })  con "{n} selezionati" nel dizionario
    t(key: TranslationKey, params?: Record<string, string | number>): string {
      const text = DICT[this.lang()][key];
      if (!params) return text;
      return text.replace(/\{(\w+)\}/g, (m, k) => (k in params ? String(params[k]) : m));
    }
}

// lingua iniziale: prima quella salvata, altrimenti autodetect dal browser, default inglese
function readInitialLang(): Lang {
  const saved = localStorage.getItem('lang');
  if (saved === 'it' || saved === 'en') return saved;
  return navigator.language.startsWith('it') ? 'it' : 'en';
}


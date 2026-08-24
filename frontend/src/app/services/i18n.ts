import { Injectable, Service, signal, WritableSignal } from '@angular/core';
import { it } from '../i18n/it';
import { en } from '../i18n/en';

type Lang = 'it' | 'en';

// Le chiavi valide sono quelle del dizionario italiano. Usandole come tipo ottengo due cose:
// l'autocomplete sulle chiavi quando chiamo t('...'), e soprattutto la PARITA' garantita tra le
// lingue -> se in en.ts manca una chiave presente in it.ts, il progetto non compila.
export type TranslationKey = keyof typeof it;

const DICT: Record<Lang, Record<TranslationKey, string>> = { it, en };

@Service()
export class I18nService {
    readonly lang: WritableSignal<Lang> = signal<Lang>(readInitialLang());

    use(lang: Lang): void {
        this.lang.set(lang);
        localStorage.setItem('lang', lang);
    }

    toggle(): void {
        this.use(this.lang() === 'it' ? 'en' : 'it');
    }

    // traduzione: legge il signal lang() -> diventa reattiva, al cambio lingua il template si aggiorna
    t(key: TranslationKey): string {
        return DICT[this.lang()][key];
    }
}

// lingua iniziale: prima quella salvata, altrimenti autodetect dal browser, default inglese
function readInitialLang(): Lang {
    const saved = localStorage.getItem('lang');
    if (saved === 'it' || saved === 'en') return saved;
    return navigator.language.startsWith('it') ? 'it' : 'en';
}


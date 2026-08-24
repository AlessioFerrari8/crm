import { Injectable, Service, WritableSignal, effect, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'theme';

@Service()
export class ThemeService {

  // servizio per cambiare tema
  private readonly _theme: WritableSignal<Theme> = signal<Theme>(readInitialTheme());
  readonly theme = this._theme.asReadonly();

  // inizializzo il tema e salvo nel local storage per ricordare
  constructor() {
    effect(() => {
      const theme = this._theme();
      document.documentElement.setAttribute('data-theme', theme);
      localStorage.setItem(STORAGE_KEY, theme);
    });
  }


  // metodi per cambiare e settare un differente tema
  toggle(): void {
    this._theme.set(this._theme() === 'dark' ? 'light' : 'dark');
  }

  set(theme: Theme): void {
    this._theme.set(theme);
  }
}

// metodo che legge dal localStorage il tema iniziale, per poi caricarlo nel
// costruttore
function readInitialTheme(): Theme {
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === 'light' || stored === 'dark') {
    return stored;
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

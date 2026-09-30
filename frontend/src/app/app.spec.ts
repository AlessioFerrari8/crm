import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';

import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      // App inietta AuthService (-> HttpClient), usa il router-outlet e PwaUpdateService (-> SwUpdate, spento nei test)
      providers: [provideRouter([]), provideHttpClient(), provideServiceWorker('ngsw-worker.js', { enabled: false })]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render only the router-outlet when not authenticated', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    // shell assente finche' non si e' autenticati
    expect(el.querySelector('app-topbar')).toBeNull();
    expect(el.querySelector('app-sidebar')).toBeNull();
  });
});

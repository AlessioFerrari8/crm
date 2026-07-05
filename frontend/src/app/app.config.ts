import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { authInterceptor } from './core/auth.interceptor';
import { errorToastInterceptor } from './core/error-toast.interceptor';
import { routes } from './app.routes';
import { AuthService } from './services/auth.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // errorToastInterceptor per primo nell'array: sulla risposta gira per ultimo, quindi vede
    // solo gli errori definitivi (dopo il refresh+retry di authInterceptor)
    provideHttpClient(withFetch(), withInterceptors([errorToastInterceptor, authInterceptor])),
    // aspetta il primo GET /auth/me (mai un errore: AuthService.load() lo cattura internamente)
    // prima che il router attivi qualunque rotta, cosi' authGuard vede sempre uno stato gia' risolto
    provideAppInitializer(() => firstValueFrom(inject(AuthService).load()))
  ]
};

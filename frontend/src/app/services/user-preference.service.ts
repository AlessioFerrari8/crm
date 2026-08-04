import { HttpClient } from '@angular/common/http';
import { inject, Service, signal } from '@angular/core';
import { setFormatPreference } from '../core/field-format';

import { API_BASE_URL } from '../core/api-config';
import { DEFAULT_PREFERENCE, UserPreference } from '../models/user-preference';
import { tap } from 'rxjs';

@Service()
export class UserPreferenceService {
    // inject servizi
    private readonly http = inject(HttpClient);
    private readonly base = `${API_BASE_URL}/api/me/preferences`;

    private readonly _pref = signal<UserPreference>(DEFAULT_PREFERENCE);
    readonly pref = this._pref.asReadonly();

    // da chiamare dopo il login, insieme al resto del bootstrap dell'app
    load() {
        return this.http.get<UserPreference>(this.base).pipe(tap((p) => {
            this._pref.set(p);
            setFormatPreference(p);
        }));
    }

    save(pref: UserPreference) {
        return this.http.put<UserPreference>(this.base, pref).pipe(tap((p) => {
            this._pref.set(p)
            setFormatPreference(p)
        }));
    }

    reset(): void {
      this._pref.set(DEFAULT_PREFERENCE);
      setFormatPreference(DEFAULT_PREFERENCE);
    }

}

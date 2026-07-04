import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../core/api-config';
import { FieldDef } from '../models/object-type';

// definizione del modulo pubblico (solo i campi compilabili, filtrati dal backend)
export interface PublicFormDefinition {
  key: string;
  label: string;
  color: string | null;
  fields: FieldDef[];
}

// Web form pubblico di lead capture: endpoint senza autenticazione, usati dalla pagina /f/{key}
@Injectable({ providedIn: 'root' })
export class PublicFormService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/public/forms`;

  definition(key: string): Observable<PublicFormDefinition> {
    return this.http.get<PublicFormDefinition>(`${this.base}/${key}`);
  }

  // website = honeypot: il campo invisibile del form, vuoto per gli umani
  submit(key: string, data: Record<string, unknown>, website: string): Observable<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>(`${this.base}/${key}`, { data, website });
  }
}

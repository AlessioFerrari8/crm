import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../core/api-config';
import { FilterGroup } from '../models/filter';
import { SortSpec } from '../models/record';

// Configurazione catturata da una vista salvata: filtri, ordinamento, ricerca e modalità della
// record-list. Vive nel jsonb `config` dell'entity SavedView.
export interface SavedViewConfig {
  filter?: FilterGroup;
  sort?: SortSpec[];
  q?: string;
  mode?: 'table' | 'board' | 'calendar';
}

// entities/SavedView.java — vista personale (ownerId = utente corrente, gestito dal backend)
export interface SavedView {
  id: string;
  objectKey: string;
  name: string;
  ownerId: string;
  config: SavedViewConfig;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class SavedViewService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/views`;

  list(objectKey: string): Observable<SavedView[]> {
    const params = new HttpParams().set('objectKey', objectKey);
    return this.http.get<SavedView[]>(this.base, { params });
  }

  create(objectKey: string, name: string, config: SavedViewConfig): Observable<SavedView> {
    return this.http.post<SavedView>(this.base, { objectKey, name, config });
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}

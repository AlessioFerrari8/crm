import { HttpClient } from '@angular/common/http';
import { Injectable, Service, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../core/api-config';
import { Chart, ChartQuery, ChartRunResponse, ChartType } from '../models/chart';

// CRUD sui Chart persistiti + esecuzione (run). Il run torna i punti già aggregati dal backend.
@Service()
export class ChartService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/charts`;

  list(): Observable<Chart[]> {
    return this.http.get<Chart[]>(this.base);
  }

  run(id: string): Observable<ChartRunResponse> {
    return this.http.get<ChartRunResponse>(`${this.base}/${id}/run`);
  }

  create(dto: { label: string; type: ChartType; query: ChartQuery }): Observable<Chart> {
    return this.http.post<Chart>(this.base, dto);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}

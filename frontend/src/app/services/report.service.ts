import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { API_BASE_URL } from '../core/api-config';
import { Observable } from 'rxjs';
import { Report, ReportQuery, ReportResult } from '../models/report';

@Service()
export class ReportService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/reports`;

  list(): Observable<Report[]> {
    return this.http.get<Report[]>(this.base);
  }

  create(body: Partial<Report> & { personal: boolean }): Observable<Report> {
    return this.http.post<Report>(this.base, body);
  }

  update(id: string, body: Partial<Report> & { personal: boolean }): Observable<Report> {
    return this.http.put<Report>(`${this.base}/${id}`, body);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  run(id: string): Observable<ReportResult> {
    return this.http.post<ReportResult>(`${this.base}/${id}/run`, {});
  }

  // anteprima del wizard e engine dei widget, stessa chiamata
  // Un widget non è altro che un report non salvato con un rendering diverso
  preview(query: ReportQuery): Observable<ReportResult> {
    return this.http.post<ReportResult>(`${this.base}/run`, query);
  }

  duplicate(id: string): Observable<Report> {
    return this.http.post<Report>(`${this.base}/${id}/duplicate`, {});
  }

}

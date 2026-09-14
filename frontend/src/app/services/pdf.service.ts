import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { API_BASE_URL } from '../core/api-config';
import { Observable } from 'rxjs';
import { PdfTemplate } from '../models/pdf-template';

@Service()
export class PdfService {
  // services
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/pdf`;
  private readonly templatesBase = `${API_BASE_URL}/api/pdf-templates`;

  // responseType 'blob'. Senza, httpClient prova JSON.parse su dati binari
  print(recordId: string, template?: string): Observable<Blob> {
    const params = template ? new HttpParams().set('template', template) : undefined;
    return this.http.get(`${this.base}/${recordId}`, { params, responseType: 'blob' });
  }

  batch(recordIds: string[], template?: string): Observable<Blob> {
    return this.http.post(`${this.base}/batch`, { recordIds, template }, { responseType: 'blob' });
  }

  preview(body: { html: string; objectKey: string, pageSize: string; marginMm: number; recordId?: string }): Observable<Blob> {
    return this.http.post(`${this.base}/preview`, body, { responseType: 'blob' });
  }

  templates(objectKey?: string): Observable<PdfTemplate[]> {
    const params = objectKey ? new HttpParams().set('objectKey', objectKey) : undefined;
    return this.http.get<PdfTemplate[]>(this.templatesBase, { params });
  }

  saveTemplate(id: string | null, body: Partial<PdfTemplate>): Observable<PdfTemplate> {
    return id
      ? this.http.put<PdfTemplate>(`${this.templatesBase}/${id}`, body)
      : this.http.post<PdfTemplate>(this.templatesBase, body);
  }

  duplicateTemplate(id: string): Observable<PdfTemplate> {
    return this.http.post<PdfTemplate>(`${this.templatesBase}/${id}/duplicate`, {});
  }

  removeTemplate(id: string): Observable<void> {
    return this.http.delete<void>(`${this.templatesBase}/${id}`);
  }

}

import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { API_BASE_URL } from '../core/api-config';
import { EInvoice, EInvoiceStatus } from '../models/e-invoice';
import { Observable } from 'rxjs';

@Service()
export class EInvoiceService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/e-invoices`;

  generate(invoiceId: string): Observable<EInvoice> {
    return this.http.post<EInvoice>(`${API_BASE_URL}/api/invoices/${invoiceId}/e-invoice`, {});
  }

  list(status?: EInvoiceStatus): Observable<EInvoice[]> {
    const params = status ? new HttpParams().set('status', status) : undefined;
    return this.http.get<EInvoice[]>(this.base, { params });
  }

  // blob: file da scaricare, non JSON
  xml(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/xml`, { responseType: 'blob' });
  }

  send(id: string): Observable<EInvoice> {
    return this.http.post<EInvoice>(`${this.base}/${id}/send`, {});
  }

  markSent(id: string): Observable<EInvoice> {
    return this.http.post<EInvoice>(`${this.base}/${id}/mark-sent`, {});
  }

  // FormData senza Content-Type: il boundary del multipart lo mette il browser
  uploadReceipt(id: string, file: File): Observable<EInvoice> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<EInvoice>(`${this.base}/${id}/notifications`, body);
  }


}

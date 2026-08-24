import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { API_BASE_URL } from '../core/api-config';
import { Observable } from 'rxjs';
import { DocumentLine, DocumentLineDto } from '../models/document-line';

// righe di un documento. Il put sostituisce tutto il blocco righe: è idempotente e i titoli di testata
// li ricalcola il backend a ogni salvataggio (DocumentLineService.writeTotals). Dopo il save va quindi
// ricaricato anche il record del documento, non solo le righe.
@Service()
export class DocumentLineService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/records`;

  list(documentId: string): Observable<DocumentLine[]> {
    return this.http.get<DocumentLine[]>(`${this.base}/${documentId}/lines`);
  }

  replaceAll(documentId: string, lines: DocumentLineDto[]): Observable<DocumentLine[]> {
    return this.http.put<DocumentLine[]>(`${this.base}/${documentId}/lines`, lines);
  }

  convertQuoteToOrder(quoteId: string): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.base}/quote/${quoteId}/convert-to-order`, {});
  }

  reviseQuote(quoteId: string): Observable<{ id: string }> {
    return this.http.post<{ id: string }>(`${this.base}/quote/${quoteId}/revise`, {});
  }
}

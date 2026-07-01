import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../core/api-config';
import {
  BulkRecordsRequest,
  BulkResult,
  QueryRecordsDto,
  RecordDetailResponse,
  RecordItem,
  RecordQueryResponse,
  SearchResult,
  UpsertRecordDto
} from '../models/record';

// CRUD generico sul motore dinamico: {key} seleziona l'ObjectType (records.controller.ts lato
// backend). Un solo service per tutti i tipi, esattamente come il backend ha un solo controller.
@Injectable({ providedIn: 'root' })
export class RecordsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/records`;

  // GET semplice (q/status/page/pageSize in query string). Per filtri AND/OR annidati usare
  // queryAdvanced (POST col body).
  query(key: string, params: QueryRecordsDto = {}): Observable<RecordQueryResponse> {
    let httpParams = new HttpParams();
    if (params.q) httpParams = httpParams.set('q', params.q);
    if (params.status) httpParams = httpParams.set('status', params.status);
    if (params.page != null) httpParams = httpParams.set('page', params.page);
    if (params.pageSize != null) httpParams = httpParams.set('pageSize', params.pageSize);
    return this.http.get<RecordQueryResponse>(`${this.base}/${key}`, { params: httpParams });
  }

  queryAdvanced(key: string, body: QueryRecordsDto): Observable<RecordQueryResponse> {
    return this.http.post<RecordQueryResponse>(`${this.base}/${key}/query`, body);
  }

  findOne(key: string, id: string): Observable<RecordDetailResponse> {
    return this.http.get<RecordDetailResponse>(`${this.base}/${key}/${id}`);
  }

  create(key: string, dto: UpsertRecordDto): Observable<RecordItem> {
    return this.http.post<RecordItem>(`${this.base}/${key}`, dto);
  }

  update(key: string, id: string, dto: UpsertRecordDto): Observable<RecordItem> {
    return this.http.patch<RecordItem>(`${this.base}/${key}/${id}`, dto);
  }

  remove(key: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${key}/${id}`);
  }

  // azione di massa su più record (update di un campo o delete). Il backend applica RBAC + scope OWN
  // e cappa a 500 id lato server.
  bulk(key: string, body: BulkRecordsRequest): Observable<BulkResult> {
    return this.http.post<BulkResult>(`${this.base}/${key}/bulk`, body);
  }

  search(q: string): Observable<SearchResult[]> {
    const params = new HttpParams().set('q', q);
    return this.http.get<SearchResult[]>(`${this.base}/search`, { params });
  }
}

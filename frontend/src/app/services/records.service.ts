import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, Service, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../core/api-config';
import { SKIP_ERROR_TOAST } from '../core/error-toast.interceptor';
import {
  BulkRecordsRequest,
  BulkResult,
  QueryRecordsDto,
  RecordDetailResponse,
  RecordItem,
  RecordLink,
  RecordNote,
  RecordQueryResponse,
  SearchResult,
  UpsertRecordDto
} from '../models/record';
import { tap } from 'rxjs'
import { mergeRelationTitles } from '../core/field-format';

// CRUD generico sul motore dinamico: {key} seleziona l'ObjectType (records.controller.ts lato
// backend). Un solo service per tutti i tipi, esattamente come il backend ha un solo controller.
@Service()
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
    return this.http.get<RecordQueryResponse>(`${this.base}/${key}`, { params: httpParams })
      .pipe(tap((r) => mergeRelationTitles(r.relationTitles)));
  }

  queryAdvanced(key: string, body: QueryRecordsDto): Observable<RecordQueryResponse> {
    return this.http.post<RecordQueryResponse>(`${this.base}/${key}/query`, body)
      .pipe(tap((r) => mergeRelationTitles(r.relationTitles)));
  }

  findOne(key: string, id: string): Observable<RecordDetailResponse> {
    return this.http.get<RecordDetailResponse>(`${this.base}/${key}/${id}`)
      .pipe(tap((r) => mergeRelationTitles(r.relationTitles)));
  }

  // silentErrors: niente toast automatico sugli errori (per chi li gestisce riga per riga,
  // es. l'import CSV che colleziona i fallimenti nel proprio riepilogo)
  create(key: string, dto: UpsertRecordDto, options?: { silentErrors?: boolean }): Observable<RecordItem> {
    const context = options?.silentErrors ? new HttpContext().set(SKIP_ERROR_TOAST, true) : undefined;
    return this.http.post<RecordItem>(`${this.base}/${key}`, dto, { context });
  }

  update(key: string, id: string, dto: UpsertRecordDto): Observable<RecordItem> {
    return this.http.patch<RecordItem>(`${this.base}/${key}/${id}`, dto);
  }

  remove(key: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${key}/${id}`);
  }

  // cestino: ultimi record soft-deleted del tipo, e ripristino
  listTrash(key: string): Observable<RecordItem[]> {
    return this.http.get<RecordItem[]>(`${this.base}/${key}/trash`);
  }

  restore(key: string, id: string): Observable<RecordItem> {
    return this.http.post<RecordItem>(`${this.base}/${key}/${id}/restore`, {});
  }

  // cancellazione definitiva dal cestino
  purge(key: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${key}/trash/${id}`);
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

  // collegamenti tra record (RelationsController). Il backend fa upsert su
  // (source, target, relationKey): ricollegare la stessa coppia non duplica.
  addLink(recordId: string, targetId: string, relationKey: string): Observable<RecordLink> {
    return this.http.post<RecordLink>(`${this.base}/${recordId}/links`, { targetId, relationKey });
  }

  removeLink(recordId: string, linkId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${recordId}/links/${linkId}`);
  }

  // note sulla timeline del record (NotesController). Eliminabili solo dall'autore (403 altrimenti).
  listNotes(recordId: string): Observable<RecordNote[]> {
    return this.http.get<RecordNote[]>(`${this.base}/${recordId}/notes`);
  }

  addNote(recordId: string, body: string): Observable<RecordNote> {
    return this.http.post<RecordNote>(`${this.base}/${recordId}/notes`, { body });
  }

  removeNote(recordId: string, noteId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${recordId}/notes/${noteId}`);
  }
}

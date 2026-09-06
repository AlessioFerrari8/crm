import { HttpClient } from '@angular/common/http';
import { inject, Service, signal } from '@angular/core';
import { API_BASE_URL } from '../core/api-config';
import { Observable, tap } from 'rxjs';
import { CreateTagDto, Tag } from '../models/tag';
import { RecordItem } from '../models/record';

@Service()
export class TagService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/tags`;

  private readonly _all = signal<Tag[]>([]);
  readonly all = this._all.asReadonly();

  load(): Observable<Tag[]> {
    return this.http.get<Tag[]>(this.base).pipe(tap((tags) => this._all.set(tags)));
  }

  create(dto: CreateTagDto): Observable<Tag> {
    return this.http
      .post<Tag>(this.base, dto)
      .pipe(
        tap((tag) =>
          this._all.update((tags) => [...tags, tag].sort((a, b) => a.name.localeCompare(b.name))),
        ),
      );
  }

  remove(tagId: string): Observable<void> {
    return this.http
      .delete<void>(`${this.base}/${tagId}`)
      .pipe(tap(() => this._all.update((tags) => tags.filter((t) => t.id !== tagId))));
  }

  records(tagId: string): Observable<RecordItem[]> {
    return this.http.get<RecordItem[]>(`${this.base}/${tagId}/records`);
  }

  ofRecord(recordId: string): Observable<Tag[]> {
    return this.http.get<Tag[]>(`${API_BASE_URL}/api/records/${recordId}/tags`);
  }

  // i tag di più record in una chiamata sola, senza la lista ne farebbe una per riga
  ofRecords(recordIds: string[]): Observable<Record<string, Tag[]>> {
    return this.http.get<Record<string, Tag[]>>(`${API_BASE_URL}/api/records/tags`, {
      params: { ids: recordIds.join(',') },
    });
  }

  // put e non post: attaccare due volte lo stesso tag non è un errore
  attach(recordId: string, tagId: string): Observable<void> {
    return this.http.put<void>(`${API_BASE_URL}/api/records/${recordId}/tags/${tagId}`, {});
  }

  detach(recordId: string, tagId: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/api/records/${recordId}/tags/${tagId}`);
  }
}

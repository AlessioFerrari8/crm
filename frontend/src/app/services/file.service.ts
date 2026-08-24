import { HttpClient } from '@angular/common/http';
import { Injectable, Service, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../core/api-config';
import { FileObject } from '../models/file-object';

// Allegati: upload (multipart), lista per record, e URL di download diretto. Il download è una
// GET sull'origine API: il cookie httpOnly viaggia da solo sulla navigazione, niente CSRF (è safe).
@Service()
export class FileService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/files`;

  list(recordId: string): Observable<FileObject[]> {
    return this.http.get<FileObject[]>(this.base, { params: { recordId } });
  }

  upload(file: File, recordId: string): Observable<FileObject> {
    const form = new FormData();
    form.append('file', file);
    form.append('recordId', recordId);
    return this.http.post<FileObject>(this.base, form);
  }

  downloadUrl(id: string): string {
    return `${this.base}/${id}`;
  }
}

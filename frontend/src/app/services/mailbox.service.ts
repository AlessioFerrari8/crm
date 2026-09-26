import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { API_BASE_URL } from '../core/api-config';
import { Observable } from 'rxjs';
import { Mailbox, MailboxInput } from '../models/models';

@Service()
export class MailboxService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/mailboxes`;

  list(): Observable<Mailbox[]> {
    return this.http.get<Mailbox[]>(this.base);
  }

  save(id: string | null, body: MailboxInput): Observable<Mailbox> {
    return id ? this.http.patch<Mailbox>(`${this.base}/${id}`, body) : this.http.post<Mailbox>(this.base, body);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  test(id: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.base}/${id}/test`, {});
  }

  scan(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/scan`, {});
  }
}

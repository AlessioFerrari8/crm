import { HttpClient } from '@angular/common/http';
import { inject, Service, signal, WritableSignal } from '@angular/core';
import { API_BASE_URL } from '../core/api-config';
import { catchError, map, Observable, of, tap } from 'rxjs';


export interface PortalTicket { id: string; number: string | null; subject: string | null; status: string; createdAt: string; }
export interface PortalMessage { author: string; body: string; at: string; mine: boolean; }
export interface PortalFile { id: string; name: string; size: number; }
export interface PortalTicketDetail { ticket: PortalTicket; description: string | null; messages: PortalMessage[]; files: PortalFile[]; }
export interface PortalDocument { id: string; kind: string; number: string | null; date: string | null; total: number; status: string; acceptable: boolean; }

// l'area clienti parla solo con /api/portal: stesso dominio del CRM, cookie con Path=/api/portal
@Service()
export class PortalService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/portal`;

  readonly email: WritableSignal<string | null> = signal(null);

  me(): Observable<string | null> {
    return this.http.get<{ email: string }>(`${this.base}/me`).pipe(
      map((r) => r.email),
      catchError(() => of(null)),
      tap((e) => this.email.set(e))
    );
  }

  login(email: string, password: string): Observable<unknown> {
    return this.http.post<{ email: string }>(`${this.base}/auth/login`, { email, password }).pipe(tap((r) => this.email.set(r.email)));
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/logout`, {}).pipe(tap(() => this.email.set(null)));
  }

  acceptInvite(token: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/accept-invite`, { token, password });
  }

  tickets(): Observable<PortalTicket[]> {
    return this.http.get<PortalTicket[]>(`${this.base}/tickets`);
  }

  ticket(id: string): Observable<PortalTicketDetail> {
    return this.http.get<PortalTicketDetail>(`${this.base}/tickets/${id}`);
  }

  openTicket(subject: string, description: string): Observable<PortalTicket> {
    return this.http.post<PortalTicket>(`${this.base}/tickets`, { subject, description });
  }

  reply(id: string, body: string): Observable<PortalMessage> {
    return this.http.post<PortalMessage>(`${this.base}/tickets/${id}/messages`, { body });
  }

  documents(): Observable<PortalDocument[]> {
    return this.http.get<PortalDocument[]>(`${this.base}/documents`);
  }

  acceptQuote(id: string, note: string): Observable<void> {
    return this.http.post<void>(`${this.base}/quotes/${id}/accept`, { body: note });
  }

  pdfUrl(id: string): string {
    return `${this.base}/documents/${id}/pdf`;
  }

  fileUrl(id: string): string {
    return `${this.base}/files/${id}`;
  }

}

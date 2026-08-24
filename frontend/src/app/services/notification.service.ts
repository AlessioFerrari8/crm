import { HttpClient } from '@angular/common/http';
import { Injectable, Service, WritableSignal, computed, inject, signal } from '@angular/core';
import { catchError, of, tap } from 'rxjs';

import { API_BASE_URL } from '../core/api-config';
import { Notification } from '../models/notification';
import { AuthService } from './auth.service';

// backoff esponenziale fra i tentativi di riapertura dello stream, 2, 4, fino a 60 secondi
const RECONNECT_BASE_MS = 2_000;
const RECONNECT_MAX_MS = 60_000;

@Service()
export class NotificationService {
  // importo http client
  private readonly http = inject(HttpClient);
  // importo service di auth
  private readonly auth = inject(AuthService);

  // endpoint base
  private readonly base = `${API_BASE_URL}/api/notifications`;

  // array che contiene le notifiche
  private readonly _items: WritableSignal<Notification[]> = signal([]);
  readonly items = this._items.asReadonly();
  // array filtrato, con numero di notifiche non lette
  readonly unreadCount = computed(() => this._items().filter((n) => n.readAt == null).length);

  // connessione sse + stato riconnessione manuale
  private stream: EventSource | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempts = 0;
  private closedByUs = false;

  refresh() {
    return this.http.get<Notification[]>(this.base).pipe(tap((items) => this._items.set(items)));
  }

  markRead(id: string) {
    return this.http.patch<void>(`${this.base}/${id}/read`, {}).pipe(
      tap(() => this._items.update((items) => items.map((n) => (n.id === id ? { ...n, readAt: new Date().toISOString() } : n))))
    );
  }

  markAllRead() {
    return this.http.patch<void>(`${this.base}/read-all`, {}).pipe(
      tap(() => this._items.update((items) => items.map((n) => ({ ...n, readAt: n.readAt ?? new Date().toISOString() }))))
    );
  }

  openConnection() {
    // controllo se il campo è già valorizzato
    if (this.stream != null) return;
    this.closedByUs = false;

    const es = new EventSource(`${this.base}/stream`, { withCredentials: true });
    this.stream = es;

    es.addEventListener('open', () => {
      this.reconnectAttempts = 0;
      // recupera quello che è arrivato mentre lo stream era giù
      this.refresh().subscribe();
    });

    es.addEventListener('notification', (event) => {
      const n = JSON.parse((event as MessageEvent).data) as Notification;
      // scarta i doppioni
      this._items.update((items) => (items.some((i) => i.id === n.id) ? items : [n, ...items]));
    });

    // EventSource ritenta da solo finché non è CLOSED: il backoff manuale parte solo dopo,
    // altrimenti si sovrapporrebbe ai suoi tentativi automatici
    es.addEventListener('error', () => {
      if (this.closedByUs || es.readyState !== EventSource.CLOSED) return;
      es.close();
      this.stream = null;
      this.scheduleReconnect();
    });
  }

  closeConnection(): void {
    this.closedByUs = true;
    if (this.reconnectTimer != null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.stream?.close();
    this.stream = null;
    this.reconnectAttempts = 0;
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer != null || this.closedByUs) return;

    const delay = Math.min(RECONNECT_BASE_MS * 2 ** this.reconnectAttempts, RECONNECT_MAX_MS);
    this.reconnectAttempts++;

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      if (this.closedByUs) return;
      // rinnova il cookie access_token prima di riaprire: se lo stream è caduto perché
      // l'access token è scaduto, riaprire senza refresh fallirebbe subito allo stesso modo
      this.auth
        .refresh()
        .pipe(catchError(() => of(null)))
        .subscribe(() => this.openConnection());
    }, delay);
  }
}

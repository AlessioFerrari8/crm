import { Component, inject, signal, WritableSignal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { HttpClient, HttpParams } from '@angular/common/http';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';

import { API_BASE_URL } from '../../core/api-config';

interface PublicQuote {
  number: string;
  customer: string;
  total: string;
  validUntil: string | null;
  accepted: boolean;
}

// il cliente apre il link, vede il PDF, preme Accetto. Nessun login: vale il token firmato nell'URL
@Component({
  selector: 'app-quote-accept',
  imports: [DecimalPipe],
  templateUrl: './quote-accept.html',
  styles: ``,
})
export class QuoteAccept {
  // servizi
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly id = this.route.paramMap.get('id') ?? '';
  private readonly params = new HttpParams()
    .set('t', this.route.queryParamMap.get('t') ?? '')
    .set('s', this.route.queryParamMap.get('s') ?? '');
  private readonly base = `${API_BASE_URL}/api/public/quote/${this.id}`

  protected readonly quote: WritableSignal<PublicQuote | null> = signal(null);
  protected readonly state: WritableSignal<'loading' | 'ready' | 'done' | 'invalid'> = signal('loading');
  protected readonly note = signal('');
  protected readonly busy: WritableSignal<boolean> = signal(false);
  protected readonly error: WritableSignal<string | null> = signal(null);
  protected readonly pdfUrl = `${this.base}/pdf?${this.params.toString}`

  // l'iframe mostra un blob: il bakend risponde con X-Frame-Options DENY, e un iframe puntato all'API resterebbe bianco
  protected readonly preview: WritableSignal<SafeResourceUrl | null> = signal(null);
  protected readonly sanitizer = inject(DomSanitizer);

  constructor() {
    this.http.get<PublicQuote>(this.base, { params: this.params }).subscribe({
      next: (q) => {
        this.quote.set(q);
        this.state.set(q.accepted ? 'done' : 'ready');
        this.http.get(this.pdfUrl, { responseType: 'blob' }).subscribe((b) =>
          this.preview.set(this.sanitizer.bypassSecurityTrustResourceUrl(URL.createObjectURL(b))));
      },
      error: () => this.state.set('invalid')
    });
  }

  protected num(v: string): number {
    return Number(v);
  }

  protected accept(): void {
    this.busy.set(true);
    this.error.set(null);
    this.http.post(`${this.base}/accept`, { note: this.note().trim() || null }, { params: this.params }).subscribe({
      next: () => this.state.set('done'),
      error: (e) => {
        this.busy.set(false);
        this.error.set(e?.error?.message ?? 'Non è stato possibile accettare il preventivo');
      }
    });
  }

}

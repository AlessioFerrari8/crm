import { HttpClient } from '@angular/common/http';
import { Component, inject, signal, WritableSignal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { API_BASE_URL } from '../../core/api-config';


// la pagina che il cliente apre dalla mail: nessun layout, undici pulsanti grandi per il pollice, e il commento compare dopo il
// voto (chiederlo prima abbassa di sicuro il tasso di risposta)
@Component({
  selector: 'app-nps-public',
  templateUrl: './nps-public.html'
})
export class NpsPublic {

  private readonly http = inject(HttpClient);
  private readonly token = inject(ActivatedRoute).snapshot.paramMap.get('token') ?? '';
  private readonly url = `${API_BASE_URL}/api/public/nps/${this.token}`;

  protected readonly scores = Array.from({ length: 11 }, (_, i) => i);
  protected readonly question: WritableSignal<string | null> = signal(null);
  protected readonly state: WritableSignal<'loading' | 'ask' | 'comment' | 'done' | 'gone' | 'invalid'> = signal('loading');
  protected readonly score: WritableSignal<number | null> = signal(null);
  protected readonly comment = signal('');

  constructor() {
    this.http.get<{ question: string }>(this.url).subscribe({
      next: (q) => {
        this.question.set(q.question);
        this.state.set('ask');
      },
      error: (e) => this.state.set(e.status === 410 ? 'gone' : 'invalid')
    });
  }

  protected pick(n: number): void {
    this.score.set(n);
    this.state.set('comment');
  }

  protected submit(): void {
    this.http.post(this.url, { score: this.score(), comment: this.comment().trim() || null }).subscribe({
      next: () => this.state.set('done'),
      error: (e) => this.state.set(e.status === 410 ? 'gone' : 'invalid')
    });
  }
}

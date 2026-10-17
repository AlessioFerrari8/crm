import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal, WritableSignal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { PortalService, PortalTicketDetail } from '../../../services/portal.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-portal-ticket',
  imports: [RouterLink, DatePipe],
  templateUrl: './portal-ticket.html',
  styles: ``,
})
export class PortalTicket {

  protected readonly portal = inject(PortalService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id') ?? '';

  protected readonly detail: WritableSignal<PortalTicketDetail | null> = signal(null);
  protected readonly body = signal('');

  constructor() {
    this.load();
  }

  protected send(): void {
    this.portal.reply(this.id, this.body()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.body.set('');
      this.load();
    });
  }

  private load(): void {
    this.portal.ticket(this.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (d) => this.detail.set(d),
      // 404 per un ticket non suo, 401 per la sessione scaduta: in entrambi i casi si torna alla home
      error: () => this.router.navigate(['/portal'])
    });
  }
}

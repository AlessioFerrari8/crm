import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal, WritableSignal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PortalDocument, PortalService, PortalTicket } from '../../../services/portal.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-portal-home',
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe],
  templateUrl: './portal-home.html',
  styles: ``,
})
export class PortalHome {
  protected readonly portal = inject(PortalService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly tickets: WritableSignal<PortalTicket[]> = signal([]);
  protected readonly documents: WritableSignal<PortalDocument[]> = signal([]);
  protected subject = '';
  protected description = '';

  constructor() {
    this.portal.me().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((email) => {
      if (!email) {
        this.router.navigate(['/portal/login']);
        return;
      }
      this.load();
    });
  }

  protected open(): void {
    if (!this.subject.trim()) return;
    this.portal.openTicket(this.subject, this.description).pipe(takeUntilDestroyed(this.destroyRef)).subscribe((t) => {
      this.subject = '';
      this.description = '';
      this.router.navigate(['/portal/tickets', t.id]);
    });
  }

  protected accept(d: PortalDocument): void {
    this.portal.acceptQuote(d.id, '').pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
  }

  protected logout(): void {
    this.portal.logout().subscribe(() => this.router.navigate(['/portal/login']));
  }

  private load(): void {
    this.portal.tickets().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((t) => this.tickets.set(t));
    this.portal.documents().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((d) => this.documents.set(d));
  }
}

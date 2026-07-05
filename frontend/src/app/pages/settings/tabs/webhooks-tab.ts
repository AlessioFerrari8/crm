import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject, catchError, of, startWith, switchMap, tap } from 'rxjs';

import { UiButton } from '../../../components/ui/button';
import { UiSpinner } from '../../../components/ui/spinner';
import { Webhook, WebhookDirection } from '../../../models/admin';
import { AdminService } from '../../../services/admin.service';
import { ConfirmService } from '../../../services/confirm.service';

const DIRECTIONS: WebhookDirection[] = ['OUTBOUND', 'INBOUND'];

@Component({
  selector: 'app-webhooks-tab',
  standalone: true,
  imports: [ReactiveFormsModule, UiButton, UiSpinner, DatePipe],
  templateUrl: './webhooks-tab.html'
})
export class WebhooksTab {
  private readonly fb = inject(FormBuilder);
  private readonly admin = inject(AdminService);
  private readonly confirmService = inject(ConfirmService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly directions = DIRECTIONS;
  protected readonly loading = signal(true);
  protected readonly creating = signal(false);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  // secret mostrato una sola volta dopo la creazione (poi non è più recuperabile)
  protected readonly createdSecret = signal<string | null>(null);

  private readonly reload$ = new Subject<void>();

  protected readonly webhooks = toSignal(
    this.reload$.pipe(
      startWith(undefined),
      tap(() => this.loading.set(true)),
      switchMap(() => this.admin.listWebhooks().pipe(catchError(() => of<Webhook[]>([])))),
      tap(() => this.loading.set(false))
    ),
    { initialValue: [] as Webhook[] }
  );

  protected readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    direction: ['OUTBOUND' as WebhookDirection, Validators.required],
    url: [''],
    // eventi come stringa CSV nel form, splittata in array all'invio
    events: ['']
  });

  protected create(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { name, direction, url, events } = this.form.getRawValue();
    const eventList = events.split(',').map((e) => e.trim()).filter((e) => e.length > 0);
    this.admin
      .createWebhook({ name, direction, url: url || undefined, events: eventList.length ? eventList : undefined })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.submitting.set(false);
          this.creating.set(false);
          this.createdSecret.set(res.secret);
          this.form.reset({ direction: 'OUTBOUND' });
          this.reload$.next();
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(err?.error?.message ?? 'Errore nella creazione del webhook');
        }
      });
  }

  protected remove(webhook: Webhook): void {
    this.confirmService.ask(`Eliminare il webhook "${webhook.name}"?`).then((ok) => {
      if (!ok) return;
      this.admin
        .removeWebhook(webhook.id)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.reload$.next());
    });
  }
}

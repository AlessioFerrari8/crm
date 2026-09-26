import { Component, DestroyRef, inject, signal, WritableSignal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UiButton } from '../../../components/ui/button';
import { DatePipe } from '@angular/common';
import { MailboxService } from '../../../services/mailbox.service';
import { AdminService } from '../../../services/admin.service';
import { ToastService } from '../../../services/toast.service';
import { Mailbox } from '../../../models/models';
import { UserSummary } from '../../../models/admin';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';


// caselle IMAP da cui nascono i ticket. Le due cose che contano: "prova connessione" in vista, e lastError mostrato in chiaro
// (una casella che non scarica non deve sembrare una casella vuota)
@Component({
  selector: 'app-mailboxes-tab',
  imports: [ReactiveFormsModule, UiButton, DatePipe],
  templateUrl: './mailboxes-tab.html'
})
export class MailboxesTab {
  // services
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(MailboxService);
  private readonly admin = inject(AdminService);
  private readonly toasts = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly rows: WritableSignal<Mailbox[]> = signal([]);
  protected readonly users: WritableSignal<UserSummary[]> = signal([]);
  protected readonly editing: WritableSignal<string | null | undefined> = signal(undefined); // undefined = form chiuso, null = nuova
  protected readonly busy: WritableSignal<string | null> = signal(null);

  protected readonly form = this.fb.nonNullable.group({
    label: ['', [Validators.required, Validators.maxLength(120)]],
    host: ['', [Validators.required]],
    port: [993, [Validators.required, Validators.min(1), Validators.max(65535)]],
    ssl: [true],
    username: ['', Validators.required],
    password: [''],
    folder: ['INBOX'],
    targetObjectKey: ['ticket'],
    defaultOwnerId: ['', Validators.required],
    active: [true]
  });

  constructor() {
    this.load();
    this.admin.listUsers().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((u) => this.users.set(u));
  }

  protected edit(m: Mailbox | null): void {
    this.editing.set(m?.id ?? null);
    this.form.reset();
    if (m) this.form.patchValue({ ...m, password: '' });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { password, ...rest } = this.form.getRawValue();
    // password vuota = non toccarla: mandarla come "" la cancellerebbe
    const body = password ? { ...rest, password } : rest;
    this.service.save(this.editing() ?? null, body).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.editing.set(undefined);
      this.toasts.success('Casella salvata');
      this.load();
    });
  }

  protected test(m: Mailbox): void {
    this.busy.set(m.id);
    this.service.test(m.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (r) => {
        this.busy.set(null);
        this.toasts.success(r.message);
      },
      error: () => this.busy.set(null)
    });
  }

  protected scan(m: Mailbox): void {
    this.service.scan(m.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.toasts.info('Scansione avviata');
      this.load();
    });
  }

  protected remove(m: Mailbox): void {
    this.service.remove(m.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
  }

  private load(): void {
    this.service.list().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((r) => this.rows.set(r));
  }

}

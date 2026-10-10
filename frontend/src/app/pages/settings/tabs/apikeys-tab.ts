import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject, catchError, of, startWith, switchMap, tap } from 'rxjs';

import { UiButton } from '../../../components/ui/button';
import { UiSpinner } from '../../../components/ui/spinner';
import { ROLE_KEYS } from '../../../core/roles';
import { ApiKeySummary } from '../../../models/admin';
import { AdminService } from '../../../services/admin.service';
import { ConfirmService } from '../../../services/confirm.service';

@Component({
  selector: 'app-apikeys-tab',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe, UiButton, UiSpinner],
  templateUrl: './apikeys-tab.html'
})
export class ApiKeysTab {
  private readonly fb = inject(FormBuilder);
  private readonly admin = inject(AdminService);
  private readonly confirmService = inject(ConfirmService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly roleKeys = ROLE_KEYS;
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  // chiave in chiaro mostrata UNA sola volta dopo la creazione
  protected readonly createdKey = signal<string | null>(null);

  private readonly reload$ = new Subject<void>();

  protected readonly keys = toSignal(
    this.reload$.pipe(
      startWith(undefined),
      tap(() => this.loading.set(true)),
      switchMap(() => this.admin.listApiKeys().pipe(catchError(() => of<ApiKeySummary[]>([])))),
      tap(() => this.loading.set(false))
    ),
    { initialValue: [] as ApiKeySummary[] }
  );

  protected readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    roleKey: ['agent', Validators.required]
  });

  protected create(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const { name, roleKey } = this.form.getRawValue();
    this.admin
      .createApiKey(name, roleKey || undefined)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.submitting.set(false);
          this.createdKey.set(res.apiKey);
          this.form.reset({ roleKey: 'agent' });
          this.reload$.next();
        },
        error: () => this.submitting.set(false)
      });
  }

  protected revoke(key: ApiKeySummary): void {
    this.confirmService.ask(`Revocare la chiave "${key.name}"?`).then((ok) => {
      if (!ok) return;
      this.admin
        .revokeApiKey(key.id)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(() => this.reload$.next());
    });
  }
}

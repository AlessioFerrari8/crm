import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject, catchError, of, startWith, switchMap, tap } from 'rxjs';

import { UiButton } from '../../../components/ui/button';
import { UiSpinner } from '../../../components/ui/spinner';
import { ROLE_KEYS } from '../../../core/roles';
import { UserSummary } from '../../../models/admin';
import { AdminService } from '../../../services/admin.service';

@Component({
  selector: 'app-users-tab',
  standalone: true,
  imports: [ReactiveFormsModule, UiButton, UiSpinner],
  templateUrl: './users-tab.html'
})
export class UsersTab {
  private readonly fb = inject(FormBuilder);
  private readonly admin = inject(AdminService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly roleKeys = ROLE_KEYS;
  protected readonly loading = signal(true);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  // token mostrato una sola volta dopo l'invito (il backend non manda email in questo porting)
  protected readonly inviteToken = signal<string | null>(null);
  protected readonly copied = signal(false);

  // link completo alla pagina di accettazione (/invite), da passare all'invitato
  protected inviteLink(token: string): string {
    return `${location.origin}/invite?token=${token}`;
  }

  protected copyInviteLink(token: string): void {
    navigator.clipboard.writeText(this.inviteLink(token)).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    });
  }

  private readonly reload$ = new Subject<void>();

  protected readonly users = toSignal(
    this.reload$.pipe(
      startWith(undefined),
      tap(() => this.loading.set(true)),
      switchMap(() => this.admin.listUsers().pipe(catchError(() => of<UserSummary[]>([])))),
      tap(() => this.loading.set(false))
    ),
    { initialValue: [] as UserSummary[] }
  );

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    roleKey: ['agent', Validators.required]
  });

  protected invite(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    const { email, roleKey } = this.form.getRawValue();
    this.admin
      .invite(email, roleKey)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.submitting.set(false);
          this.inviteToken.set(res.inviteToken);
          this.form.reset({ roleKey: 'agent' });
          this.reload$.next();
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(err?.error?.message ?? 'Errore nell\'invito');
        }
      });
  }

  protected deactivate(user: UserSummary): void {
    if (!confirm(`Disattivare ${user.email}?`)) return;
    this.admin
      .deactivateUser(user.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload$.next());
  }
}

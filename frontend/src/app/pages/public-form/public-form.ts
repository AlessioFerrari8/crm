import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { DynamicForm } from '../../components/dynamic-form/dynamic-form';
import { UiSpinner } from '../../components/ui/spinner';
import { PublicFormDefinition, PublicFormService } from '../../services/public-form.service';

// Pagina pubblica del web form (/f/{key}): condivisibile come link o incorporabile via iframe in
// qualunque sito. Rende i campi del tipo (whitelist decisa dal backend) col DynamicForm e invia
// la submission all'endpoint pubblico. L'honeypot e' un input invisibile: i bot lo compilano, gli
// umani no — il backend scarta in silenzio le submission col campo pieno.
@Component({
  selector: 'app-public-form',
  standalone: true,
  imports: [DynamicForm, UiSpinner],
  templateUrl: './public-form.html'
})
export class PublicForm {
  private readonly route = inject(ActivatedRoute);
  private readonly publicFormService = inject(PublicFormService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly objectKey = this.route.snapshot.paramMap.get('objectKey') ?? '';

  protected readonly submitting = signal(false);
  protected readonly done = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly honeypot = signal('');

  private readonly state = toSignal(
    this.publicFormService.definition(this.objectKey).pipe(
      map((def) => ({ loading: false, def, missing: false })),
      catchError(() => of({ loading: false, def: null as PublicFormDefinition | null, missing: true }))
    ),
    { initialValue: { loading: true, def: null as PublicFormDefinition | null, missing: false } }
  );

  protected readonly loading = computed(() => this.state().loading);
  protected readonly def = computed(() => this.state().def);
  protected readonly missing = computed(() => this.state().missing);

  protected submit(data: Record<string, unknown>): void {
    if (this.submitting()) return;
    this.submitting.set(true);
    this.error.set(null);
    this.publicFormService
      .submit(this.objectKey, data, this.honeypot())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.done.set(true);
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(err?.error?.message ?? 'Invio non riuscito, riprova');
        }
      });
  }
}

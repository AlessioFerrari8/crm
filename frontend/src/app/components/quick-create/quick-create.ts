import { FieldDef, ObjectType } from './../../models/object-type';
import { Toast, ToastService } from './../../services/toast.service';
import { Component, computed, DestroyRef, inject, signal, WritableSignal } from '@angular/core';
import { DynamicForm } from '../dynamic-form/dynamic-form';
import { UiButton } from '../ui/button';
import { ObjectTypeService } from '../../services/object-type.service';
import { RecordsService } from '../../services/records.service';
import { Router } from '@angular/router';
import { catchError, of } from 'rxjs';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { I18nService } from '../../services/i18n';


// Pannello a due colonne dei moduli più usati + modal con i soli campi obbligatori
// È tutto grazie all'architettura metadata-driven: non c'è un form per modulo da scrivere,
// si filtrano i FieldDef che ci sono già
@Component({
  selector: 'app-quick-create',
  standalone: true,
  imports: [DynamicForm, UiButton],
  templateUrl: './quick-create.html',
  styles: ``,
})
export class QuickCreate {
  // services
  private readonly objectTypes = inject(ObjectTypeService);
  private readonly recordService = inject(RecordsService);
  private readonly toasts = inject(ToastService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly i18n = inject(I18nService);

  // l'ordine è quello del pannello della demo, due colonne da quattro
  private static readonly QUICK_KEYS = [
    'lead', 'company', 'ticket', 'calendar_event', 'contact',
    'opportunity', 'project', 'todo'
  ];

  protected readonly open: WritableSignal<boolean> = signal(false);
  protected readonly selected: WritableSignal<ObjectType | null> = signal(null);
  protected readonly saving: WritableSignal<boolean> = signal(false);

  protected readonly all = toSignal(
    this.objectTypes.load().pipe(catchError(() => of<ObjectType[]>([]))),
    { initialValue: [] as ObjectType[] }
  );

  protected readonly quick = computed(() =>
    QuickCreate.QUICK_KEYS
      .map((k) => this.all().find((o) => o.key === k))
      .filter((o): o is ObjectType => !!o && o.enabled)
  );

  // solo i campi obbligatori e visibili: il form intero lo si compila dopo, sul dettaglio
  // Un modal di creazioen veloce con 25 non è proprio veloce
  protected readonly quickFields = computed<FieldDef[]>(() =>
    (this.selected()?.fields ?? []).filter((f) => f.required && !f.hidden)
      .sort((a, b) => a.sortOrder - b.sortOrder)
  );

  protected pick(obj: ObjectType): void {
    this.selected.set(obj);
  }

  protected create(data: Record<string, unknown>): void {
    const obj = this.selected();
    if (!obj || this.saving()) return;
    this.saving.set(true);
    this.recordService.create(obj.key, { data })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.saving.set(false);
          this.open.set(false);
          this.selected.set(null);
          this.toasts.success(`${obj.label} creato`);
          // si va sul dettaglio: il resto dei campi si compila lì
          this.router.navigate(['/o', obj.key, created.id]);
        },
        error: () => this.saving.set(false)
      });
  }
}

import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { LucideAngularModule } from 'lucide-angular';
import { Subject, catchError, of, startWith, switchMap, tap } from 'rxjs';

import { ICON_KEYS, resolveObjectIcon } from '../../../core/object-icons';
import { UiButton } from '../../../components/ui/button';
import { UiSpinner } from '../../../components/ui/spinner';
import { FIELD_TYPES, FieldDef, FieldOption, FieldType, ObjectType } from '../../../models/object-type';
import { FieldUpsert, ObjectTypeService } from '../../../services/object-type.service';

@Component({
  selector: 'app-objects-tab',
  standalone: true,
  imports: [ReactiveFormsModule, UiButton, UiSpinner, LucideAngularModule],
  templateUrl: './objects-tab.html'
})
export class ObjectsTab {
  private readonly fb = inject(FormBuilder);
  private readonly objectTypeService = inject(ObjectTypeService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly loading = signal(true);
  protected readonly expanded = signal<string | null>(null);
  protected readonly creating = signal(false);
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  private readonly reload$ = new Subject<void>();

  protected readonly objects = toSignal(
    this.reload$.pipe(
      startWith(undefined),
      tap(() => this.loading.set(true)),
      switchMap(() => this.objectTypeService.load().pipe(catchError(() => of<ObjectType[]>([])))),
      tap(() => this.loading.set(false))
    ),
    { initialValue: [] as ObjectType[] }
  );

  protected readonly form = this.fb.nonNullable.group({
    key: ['', [Validators.required, Validators.pattern('^[a-zA-Z0-9_]{1,64}$')]],
    label: ['', Validators.required],
    pluralLabel: ['', Validators.required],
    icon: [''],
    color: ['#2563eb']
  });

  // --- gestione campi (aggiungi/elimina) ---
  protected readonly fieldTypes = FIELD_TYPES;
  // icone selezionabili + resolver, riusati dal registro condiviso (core/object-icons)
  protected readonly iconKeys = ICON_KEYS;
  protected readonly resolveIcon = resolveObjectIcon;
  protected readonly addingField = signal(false);
  protected readonly fieldSubmitting = signal(false);
  protected readonly fieldError = signal<string | null>(null);

  protected readonly fieldForm = this.fb.nonNullable.group({
    key: ['', [Validators.required, Validators.pattern('^[a-zA-Z0-9_]{1,64}$')]],
    label: ['', Validators.required],
    type: ['TEXT' as FieldType, Validators.required],
    required: [false],
    icon: [''],
    // opzioni per i tipi a scelta: una riga per voce, formato "valore" oppure "valore|etichetta"
    optionsText: [''],
    // configurazione relazione
    targetObject: [''],
    multiple: [false],
    // config avanzata
    description: [''],
    placeholder: [''],
    section: [''],
    width: ['full'],
    min: this.fb.control<number | null>(null),
    max: this.fb.control<number | null>(null),
    step: this.fb.control<number | null>(null),
    pattern: [''],
    showInList: [true],
    filterable: [true],
    sortable: [true],
    unique: [false],
    indexed: [false],
    readonly: [false],
    hidden: [false]
  });

  // tipi che richiedono una lista di opzioni / una relazione (per mostrare i campi giusti nel form)
  protected readonly selectTypes: FieldType[] = ['SELECT', 'MULTISELECT', 'STATUS', 'TAGS'];
  protected readonly relationTypes: FieldType[] = ['RELATION', 'LOOKUP'];
  // tipi con vincoli numerici (min/max/step) e testuali (min/max lunghezza + pattern)
  protected readonly numericTypes: FieldType[] = [
    'NUMBER', 'INTEGER', 'BIGINT', 'DECIMAL', 'FLOAT', 'PERCENT', 'CURRENCY', 'RATING', 'DURATION'
  ];
  protected readonly textualTypes: FieldType[] = ['TEXT', 'VARCHAR', 'LONGTEXT', 'RICHTEXT'];
  protected readonly widths = [
    { value: 'full', label: 'Intera' },
    { value: 'half', label: 'Metà' },
    { value: 'third', label: 'Un terzo' }
  ];

  // chiave del campo in modifica (null = stiamo aggiungendo un campo nuovo)
  protected readonly editingFieldKey = signal<string | null>(null);

  protected toggle(key: string): void {
    this.expanded.set(this.expanded() === key ? null : key);
    // chiudendo/cambiando oggetto azzero il form campo
    this.closeFieldForm();
  }

  // apre il form in modalità "aggiungi"
  protected startAddField(): void {
    this.editingFieldKey.set(null);
    this.fieldError.set(null);
    this.fieldForm.reset({ type: 'TEXT', required: false });
    this.fieldForm.controls.key.enable();
    this.addingField.set(true);
  }

  // apre il form in modalità "modifica", precompilato; la chiave resta bloccata (identifica il campo)
  protected startEditField(field: FieldDef): void {
    if (field.required) return; // gli obbligatori non si toccano
    this.editingFieldKey.set(field.key);
    this.fieldError.set(null);
    this.fieldForm.reset({
      key: field.key,
      label: field.label,
      type: field.type,
      required: field.required,
      icon: field.icon ?? '',
      optionsText: this.optionsToText(field.options),
      targetObject: (field.config?.['targetObject'] as string) ?? '',
      multiple: field.config?.['multiple'] === true,
      description: field.description ?? '',
      placeholder: field.placeholder ?? '',
      section: field.section ?? '',
      width: field.width || 'full',
      min: field.min,
      max: field.max,
      step: field.step,
      pattern: field.pattern ?? '',
      showInList: field.showInList,
      filterable: field.filterable,
      sortable: field.sortable,
      unique: field.unique,
      indexed: field.indexed,
      readonly: field.readonly,
      hidden: field.hidden
    });
    this.fieldForm.controls.key.disable();
    this.addingField.set(true);
  }

  protected closeFieldForm(): void {
    this.addingField.set(false);
    this.editingFieldKey.set(null);
    this.fieldError.set(null);
    this.fieldForm.controls.key.enable();
  }

  // submit unico: PATCH se stiamo modificando, POST se stiamo aggiungendo
  protected submitField(objKey: string): void {
    if (this.fieldForm.invalid || this.fieldSubmitting()) {
      this.fieldForm.markAllAsTouched();
      return;
    }
    this.fieldSubmitting.set(true);
    this.fieldError.set(null);
    const raw = this.fieldForm.getRawValue(); // include anche la chiave disabilitata, in modifica
    const dto: FieldUpsert = {
      key: raw.key,
      label: raw.label,
      type: raw.type,
      required: raw.required,
      icon: raw.icon,
      description: raw.description,
      placeholder: raw.placeholder,
      section: raw.section,
      width: raw.width,
      showInList: raw.showInList,
      isFilterable: raw.filterable,
      isSortable: raw.sortable,
      isUnique: raw.unique,
      isIndexed: raw.indexed,
      isReadonly: raw.readonly,
      isHidden: raw.hidden
    };
    // vincoli numerici (min/max/step) e testuali (min/max lunghezza + pattern) solo per i tipi pertinenti
    if (this.numericTypes.includes(raw.type) || this.textualTypes.includes(raw.type)) {
      dto.min = raw.min ?? undefined;
      dto.max = raw.max ?? undefined;
    }
    if (this.numericTypes.includes(raw.type)) {
      dto.step = raw.step ?? undefined;
    }
    if (this.textualTypes.includes(raw.type)) {
      dto.pattern = raw.pattern || undefined;
    }
    // allego options/config solo per i tipi che li usano
    if (this.selectTypes.includes(raw.type)) {
      dto.options = this.parseOptions(raw.optionsText);
    } else if (this.relationTypes.includes(raw.type)) {
      dto.config = { targetObject: raw.targetObject, multiple: raw.multiple };
    }
    const editing = this.editingFieldKey();
    const request$ = editing
      ? this.objectTypeService.updateField(objKey, editing, dto)
      : this.objectTypeService.addField(objKey, dto);
    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.fieldSubmitting.set(false);
        this.closeFieldForm();
        this.fieldForm.reset({ type: 'TEXT', required: false });
        this.reload$.next();
      },
      error: (err) => {
        this.fieldSubmitting.set(false);
        this.fieldError.set(err?.error?.message ?? 'Errore nel salvataggio del campo');
      }
    });
  }

  // "valore" o "valore|etichetta" per riga -> [{value,label}]
  private parseOptions(text: string): FieldOption[] {
    return text
      .split('\n')
      .map((l) => l.trim())
      .filter((l) => l.length > 0)
      .map((line) => {
        const [value, label] = line.split('|').map((s) => s.trim());
        return { value, label: label || value };
      });
  }

  // inverso di parseOptions, per precompilare la textarea in modifica
  private optionsToText(options: FieldOption[] | null): string {
    return (options ?? [])
      .map((o) => (o.label && o.label !== o.value ? `${o.value}|${o.label}` : o.value))
      .join('\n');
  }

  protected removeField(objKey: string, field: FieldDef): void {
    if (field.required) return; // bloccati, non eliminabili
    if (!confirm(`Eliminare il campo "${field.label}"?`)) return;
    this.objectTypeService
      .removeField(objKey, field.key)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.reload$.next(),
        error: (err) => this.fieldError.set(err?.error?.message ?? 'Errore nella rimozione del campo')
      });
  }

  protected create(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.objectTypeService
      .create(this.form.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.creating.set(false);
          this.form.reset({ color: '#2563eb' });
          this.reload$.next();
        },
        error: (err) => {
          this.submitting.set(false);
          this.error.set(err?.error?.message ?? 'Errore nella creazione');
        }
      });
  }

  protected remove(obj: ObjectType): void {
    if (obj.system) return;
    if (!confirm(`Eliminare l'oggetto "${obj.label}" e tutti i suoi record?`)) return;
    this.objectTypeService
      .remove(obj.key)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload$.next());
  }
}

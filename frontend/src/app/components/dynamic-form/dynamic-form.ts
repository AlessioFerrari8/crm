import { Component, OnInit, computed, inject, input, output } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, ValidatorFn, Validators } from '@angular/forms';

import { FieldDef, FieldType } from '../../models/object-type';
import { RelationInput } from '../relation-input/relation-input';
import { UiButton } from '../ui/button';
import { UiLabel } from '../ui/label';

// come renderizzare un campo in base al suo FieldType (semplifica i 39 tipi nei pochi widget
// realmente diversi). I tipi avanzati restanti (FILE/JSON/FORMULA/...) ricadono su un input
// testuale per ora - gli editor dedicati arrivano piu' avanti.
type RenderKind = 'text' | 'number' | 'textarea' | 'boolean' | 'select' | 'multiselect' | 'date' | 'datetime' | 'time' | 'color' | 'relation';

function renderKind(type: FieldType): RenderKind {
  switch (type) {
    case 'LONGTEXT':
    case 'RICHTEXT':
    case 'ADDRESS':
    case 'JSON':
      return 'textarea';
    case 'NUMBER':
    case 'INTEGER':
    case 'BIGINT':
    case 'DECIMAL':
    case 'FLOAT':
    case 'PERCENT':
    case 'CURRENCY':
    case 'RATING':
    case 'DURATION':
      return 'number';
    case 'BOOLEAN':
      return 'boolean';
    case 'SELECT':
    case 'STATUS':
      return 'select';
    case 'MULTISELECT':
    case 'TAGS':
      return 'multiselect';
    case 'DATE':
      return 'date';
    case 'DATETIME':
    case 'TIMESTAMP':
      return 'datetime';
    case 'TIME':
      return 'time';
    case 'COLOR':
      return 'color';
    case 'RELATION':
    case 'LOOKUP':
      return 'relation';
    default:
      return 'text';
  }
}

interface RenderField {
  def: FieldDef;
  kind: RenderKind;
  inputType: string;
}

interface Section {
  name: string;
  fields: RenderField[];
}

@Component({
  selector: 'app-dynamic-form',
  standalone: true,
  imports: [ReactiveFormsModule, RelationInput, UiButton, UiLabel],
  templateUrl: './dynamic-form.html'
})
export class DynamicForm implements OnInit {
  private readonly fb = inject(FormBuilder);

  readonly fields = input.required<FieldDef[]>();
  readonly initialData = input<Record<string, unknown>>({});
  readonly submitting = input(false);
  // personalizzazioni per il riuso nel form pubblico (/f/{key}): etichetta submit e niente Annulla
  readonly submitLabel = input('Salva');
  readonly showCancel = input(true);

  readonly save = output<Record<string, unknown>>();
  readonly cancel = output<void>();

  // FormGroup costruito UNA volta in ngOnInit (gli input sono disponibili e stabili a quel
  // punto: questo componente viene ricreato a ogni apertura del drawer create/edit). Non lo
  // costruiamo in un computed() perche' un FormGroup e' stato mutabile, non valore derivato.
  protected form!: FormGroup;

  ngOnInit(): void {
    this.form = this.buildForm();
  }

  private buildForm(): FormGroup {
    const group: Record<string, unknown> = {};
    const data = this.initialData();
    for (const def of this.fields()) {
      if (def.type === 'AUTONUMBER' || def.type === 'FORMULA' || def.type === 'ROLLUP') continue;
      const initial = data[def.key] ?? def.defaultValue ?? defaultFor(def);
      group[def.key] = [{ value: initial, disabled: def.readonly }, buildValidators(def)];
    }
    return this.fb.group(group);
  }

  // i campi visibili e non readonly, in ordine, raggruppati per `section` (preservando l'ordine
  // di prima apparizione della sezione - stesso comportamento del DynamicForm React originale)
  protected readonly sections = computed<Section[]>(() => {
    const visible = this.fields()
      .filter((f) => !f.hidden && f.type !== 'AUTONUMBER' && f.type !== 'FORMULA' && f.type !== 'ROLLUP')
      .sort((a, b) => a.sortOrder - b.sortOrder);

    const order: string[] = [];
    const bySection = new Map<string, RenderField[]>();
    for (const def of visible) {
      const name = def.section ?? 'Generale';
      if (!bySection.has(name)) {
        bySection.set(name, []);
        order.push(name);
      }
      const kind = renderKind(def.type);
      bySection.get(name)!.push({ def, kind, inputType: inputTypeFor(def.type, kind) });
    }
    return order.map((name) => ({ name, fields: bySection.get(name)! }));
  });

  protected optionsOf(def: FieldDef) {
    return def.options ?? [];
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.save.emit(this.form.getRawValue());
  }
}

function inputTypeFor(type: FieldType, kind: RenderKind): string {
  if (kind === 'date') return 'date';
  if (kind === 'datetime') return 'datetime-local';
  if (kind === 'time') return 'time';
  if (kind === 'color') return 'color';
  if (kind === 'number') return 'number';
  switch (type) {
    case 'EMAIL':
      return 'email';
    case 'URL':
      return 'url';
    case 'PHONE':
      return 'tel';
    default:
      return 'text';
  }
}

function defaultFor(def: FieldDef): unknown {
  const kind = renderKind(def.type);
  if (kind === 'boolean') return false;
  if (kind === 'multiselect') return [];
  return '';
}

function buildValidators(def: FieldDef): ValidatorFn[] {
  const validators: ValidatorFn[] = [];
  if (def.required) validators.push(Validators.required);
  if (def.type === 'EMAIL') validators.push(Validators.email);
  if (def.min != null) validators.push(Validators.min(def.min));
  if (def.max != null) validators.push(Validators.max(def.max));
  if (def.pattern) validators.push(Validators.pattern(def.pattern));
  return validators;
}

import { HttpClient } from '@angular/common/http';
import { Injectable, WritableSignal, inject, signal } from '@angular/core';
import { tap } from 'rxjs';

import { API_BASE_URL } from '../core/api-config';
import { FieldOption, FieldType, ObjectType } from '../models/object-type';

// payload di creazione/modifica campo (chiave inclusa in creazione, ignorata in modifica).
// NB i booleani usano i nomi del DTO backend (isUnique/isIndexed/... showInList) - vedi FieldDefDto.java.
// In update i campi null/omessi vengono ignorati (non azzerati): per svuotare un testo mandare "".
export interface FieldUpsert {
  key?: string;
  label?: string;
  type?: FieldType;
  required?: boolean;
  icon?: string;
  options?: FieldOption[];
  config?: Record<string, unknown>;
  // config avanzata
  description?: string;
  placeholder?: string;
  section?: string;
  width?: string;
  min?: number;
  max?: number;
  step?: number;
  pattern?: string;
  showInList?: boolean;
  isFilterable?: boolean;
  isSortable?: boolean;
  isUnique?: boolean;
  isIndexed?: boolean;
  isReadonly?: boolean;
  isHidden?: boolean;
}

// service che prende solo la lista di oggetti
@Injectable({ providedIn: 'root' })
export class ObjectTypeService {
  // inject client http
  private readonly http = inject(HttpClient);

  // storro gli object che mi passa
  private _objects: WritableSignal<ObjectType[] | null> = signal(null);
  readonly objects = this._objects.asReadonly();

  // metodo che fa una richiesta http all'endpoint /api/objects
  // poi lo passo alla variabile objects, come readonly
  load() {
    return this.http
      .get<ObjectType[]>(`${API_BASE_URL}/api/objects`)
      .pipe(tap((objects) => this._objects.set(objects)));
  }

  // metodo che prende un determinato object, in base alla chiave passata
  getByKey(key: string) {
    return this.http.get<ObjectType>(`${API_BASE_URL}/api/objects/${key}`);
  }

  // metodo che crea un oggetto, validato tramite il dto
  create(dto: { key: string; label: string; pluralLabel: string; icon?: string; color?: string }) {
    return this.http.post<ObjectType>(`${API_BASE_URL}/api/objects`, dto);
  }

  // modifica un oggetto esistente (la chiave non si cambia). NB il DTO backend usa isEnabled,
  // ma la risposta serializza il boolean come `enabled` (getter Lombok senza prefisso is)
  update(
    key: string,
    dto: {
      label?: string;
      pluralLabel?: string;
      icon?: string;
      color?: string;
      isEnabled?: boolean;
      sortOrder?: number;
      publicForm?: boolean;
    }
  ) {
    return this.http.patch<ObjectType>(`${API_BASE_URL}/api/objects/${key}`, dto);
  }

  // metodo che toglie un oggetto
  remove(key: string) {
    return this.http.delete<void>(`${API_BASE_URL}/api/objects/${key}`);
  }

  // aggiunge un campo a un object type (il backend ritorna l'oggetto aggiornato).
  // options -> per SELECT/MULTISELECT/STATUS/TAGS; config -> per RELATION/LOOKUP (targetObject, multiple)
  addField(key: string, dto: FieldUpsert) {
    return this.http.post<ObjectType>(`${API_BASE_URL}/api/objects/${key}/fields`, dto);
  }

  // modifica un campo non obbligatorio (il backend rifiuta gli obbligatori con un 400);
  // la chiave non si cambia, identifica il campo nell'URL
  updateField(key: string, fieldKey: string, dto: FieldUpsert) {
    return this.http.patch<ObjectType>(`${API_BASE_URL}/api/objects/${key}/fields/${fieldKey}`, dto);
  }

  // toglie un campo (il backend rifiuta i campi obbligatori con un 400)
  removeField(key: string, fieldKey: string) {
    return this.http.delete<ObjectType>(`${API_BASE_URL}/api/objects/${key}/fields/${fieldKey}`);
  }
}

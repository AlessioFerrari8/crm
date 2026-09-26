import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UserPreferenceService } from '../../services/user-preference.service';
import { previewFormat } from '../../core/field-format';
import { UserPreference } from '../../models/user-preference';
import { UiButton } from '../../components/ui/button';
import { MfaSection } from './mfa-section';

@Component({
  selector: 'app-preferences',
  imports: [FormsModule, UiButton, MfaSection],
  templateUrl: './preferences.html'
})
export class Preferences {
  // inject
  private readonly prefs = inject(UserPreferenceService);

  // copia locale modificabile: il signal del service resta la verità finché
  // non salvo, così annullatre = ricaricare la pagina senza avere però
  // effetti collaterali
  readonly draft = signal<UserPreference>({ ...this.prefs.pref() });
  readonly saving = signal(false);
  readonly saved = signal(false);

  // anteprima dal vivo, è quello che rende la pagina comprensibile
  // l'utente vede 1.234,56 € cambiare mentre tocca i separatori.
  // La formattazione è quella vera di field-format, con la preferenza in bozza passata
  // esplicitamente: una seconda copia qui divergerebbe dalla prima al primo ritocco
  readonly preview = computed(() => previewFormat(this.draft(), 1234.5));

  // la data di esempio segue lo stesso formato che field-format applica alle DATE
  readonly datePreview = computed(() =>
    this.draft().dateFormat.replace('dd', '31').replace('MM', '12').replace('yyyy', '2025')
  );

  // ngModelChange su una chiave sola: il signal va sostituito, non mutato, altrimenti
  // computed() non se ne accorge
  set<K extends keyof UserPreference>(key: K, value: UserPreference[K]): void {
    this.draft.update((p) => ({ ...p, [key]: value }));
    this.saved.set(false);
  }

  save(): void {
    this.saving.set(true)
    this.prefs.save(this.draft()).subscribe({
      complete: () => { this.saving.set(false); this.saved.set(true); },
      error: () => this.saving.set(false),
    })
  }
}

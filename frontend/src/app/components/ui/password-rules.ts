import { Component, input } from '@angular/core';

// Regole password, specchio esatto della PasswordPolicy backend (che resta la garanzia vera):
// la checklist serve a non far scoprire i requisiti a colpi di 400.
export const PASSWORD_RULES: { label: string; test: (pw: string) => boolean }[] = [
  { label: 'Almeno 8 caratteri', test: (p) => p.length >= 8 },
  { label: 'Una lettera maiuscola', test: (p) => /[A-Z]/.test(p) },
  { label: 'Una lettera minuscola', test: (p) => /[a-z]/.test(p) },
  { label: 'Un numero', test: (p) => /\d/.test(p) },
  { label: 'Un carattere speciale (!, @, #, …)', test: (p) => /[^A-Za-z0-9]/.test(p) }
];

export function passwordValid(pw: string): boolean {
  return PASSWORD_RULES.every((r) => r.test(pw));
}

// Checklist live sotto i campi password: ✓ verde per le regole soddisfatte mentre si digita
@Component({
  selector: 'ui-password-rules',
  standalone: true,
  template: `
    <ul class="mt-1.5 space-y-0.5 text-xs">
      @for (r of rules; track r.label) {
        <li class="flex items-center gap-1.5 transition-colors"
            [class.text-success]="r.test(password())"
            [class.opacity-50]="!r.test(password())">
          <span class="w-3 inline-block">{{ r.test(password()) ? '✓' : '○' }}</span>
          {{ r.label }}
        </li>
      }
    </ul>
  `
})
export class UiPasswordRules {
  readonly password = input('');
  protected readonly rules = PASSWORD_RULES;
}

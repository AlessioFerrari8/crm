import { Component, computed, inject, signal } from '@angular/core';

import { AuthService } from '../../services/auth.service';
import { ApiKeysTab } from './tabs/apikeys-tab';
import { AuditTab } from './tabs/audit-tab';
import { CompanyTab } from './tabs/company-tab';
import { ObjectsTab } from './tabs/objects-tab';
import { RolesTab } from './tabs/roles-tab';
import { UsersTab } from './tabs/users-tab';
import { WebhooksTab } from './tabs/webhooks-tab';
import { WorkflowsTab } from './tabs/workflows-tab';
import { PdfTemplatesTab } from './tabs/pdf-templates-tab';
import { MailboxesTab } from './tabs/mailboxes-tab';

type Tab = 'objects' | 'users' | 'apikeys' | 'workflows' | 'webhooks' | 'audit' | 'roles' | 'company' | 'pdf' | 'mailboxes';

// ruoli che possono gestire l'organizzazione (anagrafica azienda): allineati al gate backend
// user/WRITE, ma ristretti agli admin "di organizzazione".
const ORG_ADMIN_ROLES = ['owner', 'admin', 'manager'];

// Settings a tab con stato locale (niente routing per-tab, stesso approccio dell'originale React).
@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [ObjectsTab, UsersTab, ApiKeysTab, WorkflowsTab, WebhooksTab, AuditTab, RolesTab, CompanyTab, PdfTemplatesTab, MailboxesTab],
  templateUrl: './settings.html'
})
export class Settings {
  private readonly auth = inject(AuthService);

  protected readonly tab = signal<Tab>('objects');

  private readonly isOrgAdmin = computed(() =>
    (this.auth.user()?.roles ?? []).some((r) => ORG_ADMIN_ROLES.includes(r))
  );

  // la tab "Azienda" compare solo agli org-admin (per gli altri non ha senso e il salvataggio
  // sarebbe comunque negato dal backend)
  protected readonly tabs = computed<{ id: Tab; label: string }[]>(() => {
    const base: { id: Tab; label: string }[] = [
      { id: 'objects', label: 'Oggetti' },
      { id: 'users', label: 'Utenti' },
      { id: 'apikeys', label: 'API Key' },
      { id: 'workflows', label: 'Workflow' },
      { id: 'webhooks', label: 'Webhooks' },
      { id: 'audit', label: 'Audit' },
      { id: 'roles', label: 'Ruoli' },
      { id: 'pdf', label: 'Stampe' },
      { id: 'mailboxes', label: 'Caselle email' }
    ];
    if (this.isOrgAdmin()) {
      base.push({ id: 'company', label: 'Azienda' });
    }
    return base;
  });
}

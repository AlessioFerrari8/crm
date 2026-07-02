// dtos/UserSummary.java — NB: e' un record Java, quindi Jackson mantiene il nome del componente
// `isActive` (a differenza delle entity con Lombok @Getter, dove `isXxx` -> `xxx`). Verificato live.
export interface UserSummary {
  id: string;
  email: string;
  name: string;
  avatarUrl: string | null;
  isActive: boolean;
  roles: string[];
  createdAt: string;
}

// dtos/InviteCreatedResponse.java — token in chiaro, mostrato una sola volta
export interface InviteCreatedResponse {
  inviteToken: string;
  email: string;
}

// entities/PermScope.java — ampiezza OWN < TEAM < ALL
export type PermScope = 'OWN' | 'TEAM' | 'ALL';

// dtos/RoleResponse.PermissionView — riga della matrice permessi
export interface Permission {
  resource: string;
  canRead: boolean;
  canWrite: boolean;
  canExecute: boolean;
  scope: PermScope;
}

// dtos/RoleResponse.java — ruolo + matrice permessi (composta a livello DTO). `system` = di sistema
// (non eliminabile).
export interface Role {
  id: string;
  key: string;
  label: string;
  system: boolean;
  permissions: Permission[];
}

// dtos/ApiKeySummary.java
export interface ApiKeySummary {
  id: string;
  name: string;
  prefix: string;
  lastUsedAt: string | null;
  createdAt: string;
}

// dtos/ApiKeyCreatedResponse.java — chiave in chiaro, UNA sola volta
export interface ApiKeyCreatedResponse {
  apiKey: string;
}

// entities/Workflow.java — entity Lombok: il boolean isActive serializza come `active`
export interface Workflow {
  id: string;
  name: string;
  description: string | null;
  trigger: Record<string, unknown>;
  conditions: Record<string, unknown> | null;
  actions: Record<string, unknown>[] | null;
  graph: Record<string, unknown> | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

// entities/WorkflowRun.java — log di un'esecuzione (ultime 20 in WorkflowDetailResponse)
export interface WorkflowRun {
  id: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';
  error: string | null;
  startedAt: string;
  finishedAt: string | null;
  steps: Record<string, unknown>[] | null;
}

// dtos/WorkflowDetailResponse.java
export interface WorkflowDetail {
  workflow: Workflow;
  runs: WorkflowRun[];
}

export type WebhookDirection = 'INBOUND' | 'OUTBOUND';

// dtos/WebhookSummary.java — proiezione di lista, niente `secret` (visibile solo alla creazione).
// Il boolean `active` (non `isActive`): l'entity Lombok serializza isActive() -> `active`.
export interface Webhook {
  id: string;
  direction: WebhookDirection;
  name: string;
  url: string | null;
  events: string[] | null;
  active: boolean;
  createdAt: string;
}

// entities/Webhook.java — risposta di create(): include il `secret` in chiaro UNA sola volta
// (per firmare/verificare l'HMAC), come la chiave di ApiKeyCreatedResponse.
export interface WebhookCreated extends Webhook {
  secret: string;
}

// entities/AuditLog.java — sola lettura (GET /api/logs). `diff` è il jsonb before/after.
export interface AuditLog {
  id: string;
  actorId: string | null;
  actorType: string;
  action: string;
  resource: string;
  resourceId: string | null;
  diff: Record<string, unknown> | null;
  ip: string | null;
  createdAt: string;
}

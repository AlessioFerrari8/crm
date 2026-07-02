import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../core/api-config';
import {
  ApiKeyCreatedResponse,
  ApiKeySummary,
  AuditLog,
  InviteCreatedResponse,
  Permission,
  Role,
  UserSummary,
  Webhook,
  WebhookCreated,
  WebhookDirection,
  Workflow,
  WorkflowDetail
} from '../models/admin';
import { WfGraph } from '../models/workflow-graph';

// service per gestione user admin, e gestione impostazioni
@Injectable({ providedIn: 'root' })
export class AdminService {
  // http client con api base
  private readonly http = inject(HttpClient);
  private readonly api = `${API_BASE_URL}/api`;

  // utenti
  /*
    lista di utenti
    invita
    disattiva un utente
  */
  listUsers(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>(`${this.api}/users`);
  }

  invite(email: string, roleKey: string): Observable<InviteCreatedResponse> {
    return this.http.post<InviteCreatedResponse>(`${this.api}/users/invite`, { email, roleKey });
  }

  deactivateUser(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/users/${id}`);
  }

  // api keys
  /*
    lista di api-keys
    crea una nuova api-key
    revoca un api-key
  */
  listApiKeys(): Observable<ApiKeySummary[]> {
    return this.http.get<ApiKeySummary[]>(`${this.api}/api-keys`);
  }

  createApiKey(name: string, roleKey?: string): Observable<ApiKeyCreatedResponse> {
    return this.http.post<ApiKeyCreatedResponse>(`${this.api}/api-keys`, { name, roleKey });
  }

  revokeApiKey(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/api-keys/${id}`);
  }

  // workflow
  /*
    lista di workflows
    attivazione determinato workflow
    runna un determinato workflow
    rimuove un determinato workflaw
  */
  listWorkflows(): Observable<Workflow[]> {
    return this.http.get<Workflow[]>(`${this.api}/workflows`);
  }

  // dettaglio con le ultime 20 esecuzioni (WorkflowDetailResponse)
  getWorkflow(id: string): Observable<WorkflowDetail> {
    return this.http.get<WorkflowDetail>(`${this.api}/workflows/${id}`);
  }

  // nuovo workflow vuoto: solo il nodo trigger nel grafo, da configurare poi nell'editor
  createWorkflow(name: string): Observable<Workflow> {
    return this.http.post<Workflow>(`${this.api}/workflows`, {
      name,
      trigger: { type: 'record.created' },
      graph: { nodes: [{ id: 'trigger', type: 'trigger', x: 40, y: 40, data: { type: 'record.created' } }], edges: [] }
    });
  }

  // salva il grafo dell'editor; `trigger` (matcher evento) viene estratto dal nodo trigger
  saveWorkflowGraph(id: string, graph: WfGraph, trigger: Record<string, unknown>): Observable<Workflow> {
    return this.http.patch<Workflow>(`${this.api}/workflows/${id}`, { graph, trigger });
  }

  setWorkflowActive(id: string, active: boolean): Observable<Workflow> {
    return this.http.patch<Workflow>(`${this.api}/workflows/${id}`, { isActive: active });
  }

  runWorkflow(id: string): Observable<unknown> {
    return this.http.post<unknown>(`${this.api}/workflows/${id}/run`, {});
  }

  removeWorkflow(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/workflows/${id}`);
  }

  // webhooks
  /*
    lista (senza secret)
    crea (ritorna il secret in chiaro una sola volta)
    rimuove
  */
  listWebhooks(): Observable<Webhook[]> {
    return this.http.get<Webhook[]>(`${this.api}/webhooks`);
  }

  createWebhook(dto: { direction: WebhookDirection; name: string; url?: string; events?: string[] }): Observable<WebhookCreated> {
    return this.http.post<WebhookCreated>(`${this.api}/webhooks`, dto);
  }

  removeWebhook(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/webhooks/${id}`);
  }

  // ruoli / permessi (RBAC). listRoles ritorna ogni ruolo con la sua matrice permessi.
  listRoles(): Observable<Role[]> {
    return this.http.get<Role[]>(`${this.api}/roles`);
  }

  createRole(key: string, label: string): Observable<Role> {
    return this.http.post<Role>(`${this.api}/roles`, { key, label });
  }

  // upsert della matrice: invia tutte le righe (risorsa × R/W/X + scope)
  setRolePermissions(id: string, permissions: Permission[]): Observable<Role> {
    return this.http.put<Role>(`${this.api}/roles/${id}/permissions`, { permissions });
  }

  removeRole(id: string): Observable<void> {
    return this.http.delete<void>(`${this.api}/roles/${id}`);
  }

  // audit log (sola lettura, con filtri opzionali resource/resourceId)
  listLogs(resource?: string, resourceId?: string): Observable<AuditLog[]> {
    let params = new HttpParams();
    if (resource) params = params.set('resource', resource);
    if (resourceId) params = params.set('resourceId', resourceId);
    return this.http.get<AuditLog[]>(`${this.api}/logs`, { params });
  }
}

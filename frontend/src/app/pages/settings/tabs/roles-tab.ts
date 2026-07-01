import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { Subject, catchError, of, startWith, switchMap, tap } from 'rxjs';

import { UiButton } from '../../../components/ui/button';
import { UiSpinner } from '../../../components/ui/spinner';
import { Permission, PermScope, Role } from '../../../models/admin';
import { ObjectType } from '../../../models/object-type';
import { AdminService } from '../../../services/admin.service';
import { ObjectTypeService } from '../../../services/object-type.service';

// risorse di sistema fisse (oltre alle key degli ObjectType), come nel riferimento
const SYS_RESOURCES = ['page', 'chart', 'workflow', 'user', 'apikey'];

// Gestione RBAC ruoli + matrice permessi (porting di rbac.module.ts + RolesTab/PermMatrix del
// riferimento). Le entità Role/Permission esistevano già lato backend, ora c'è la superficie REST.
@Component({
  selector: 'app-roles-tab',
  standalone: true,
  imports: [UiButton, UiSpinner],
  templateUrl: './roles-tab.html'
})
export class RolesTab {
  private readonly admin = inject(AdminService);
  private readonly objectTypeService = inject(ObjectTypeService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly loading = signal(true);
  protected readonly scopes: PermScope[] = ['OWN', 'TEAM', 'ALL'];

  private readonly reload$ = new Subject<void>();

  protected readonly roles = toSignal(
    this.reload$.pipe(
      startWith(undefined),
      tap(() => this.loading.set(true)),
      switchMap(() => this.admin.listRoles().pipe(catchError(() => of<Role[]>([])))),
      tap(() => this.loading.set(false))
    ),
    { initialValue: [] as Role[] }
  );

  private readonly objects = toSignal(
    this.objectTypeService.load().pipe(catchError(() => of<ObjectType[]>([]))),
    { initialValue: [] as ObjectType[] }
  );

  // risorse gestibili = key degli ObjectType + risorse di sistema
  protected readonly resources = computed<string[]>(() => [
    ...this.objects().map((o) => o.key),
    ...SYS_RESOURCES
  ]);

  // riga espansa (matrice) + bozza modificabile della matrice del ruolo aperto
  protected readonly openRole = signal<string | null>(null);
  protected readonly draft = signal<Record<string, Permission>>({});
  protected readonly saving = signal(false);

  // creazione ruolo
  protected readonly creating = signal(false);
  protected readonly newKey = signal('');
  protected readonly newLabel = signal('');

  // apre/chiude la matrice; all'apertura costruisce la bozza da permessi esistenti + tutte le risorse
  protected toggleMatrix(role: Role): void {
    if (this.openRole() === role.id) {
      this.openRole.set(null);
      return;
    }
    const byRes = new Map(role.permissions.map((p) => [p.resource, p]));
    const draft: Record<string, Permission> = {};
    for (const res of this.resources()) {
      const p = byRes.get(res);
      draft[res] = {
        resource: res,
        canRead: p?.canRead ?? false,
        canWrite: p?.canWrite ?? false,
        canExecute: p?.canExecute ?? false,
        scope: p?.scope ?? 'ALL'
      };
    }
    this.draft.set(draft);
    this.openRole.set(role.id);
  }

  protected draftRow(resource: string): Permission {
    return this.draft()[resource] ?? this.emptyRow(resource);
  }

  // il backend rifiuta le modifiche ai permessi di owner (anti-lockout): matrice in sola lettura
  protected isLocked(role: Role): boolean {
    return role.system && role.key === 'owner';
  }

  protected togglePerm(resource: string, key: 'canRead' | 'canWrite' | 'canExecute'): void {
    const d = this.draft();
    const row = d[resource] ?? this.emptyRow(resource);
    this.draft.set({ ...d, [resource]: { ...row, [key]: !row[key] } });
  }

  protected setScope(resource: string, scope: string): void {
    const d = this.draft();
    const row = d[resource] ?? this.emptyRow(resource);
    this.draft.set({ ...d, [resource]: { ...row, scope: scope as PermScope } });
  }

  protected saveMatrix(role: Role): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.admin
      .setRolePermissions(role.id, Object.values(this.draft()))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.reload$.next();
        },
        error: () => this.saving.set(false)
      });
  }

  // ── creazione / eliminazione ────────────────────────────────────────────────
  protected startCreate(): void {
    this.newKey.set('');
    this.newLabel.set('');
    this.creating.set(true);
  }

  // auto-slug della chiave dalla label mentre si digita
  protected onLabelInput(value: string): void {
    this.newLabel.set(value);
    this.newKey.set(this.slug(value));
  }

  // toglie accenti/diacritici e normalizza in [a-z0-9_]
  private slug(s: string): string {
    return s
      .toLowerCase()
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .replace(/[^a-z0-9]+/g, '_')
      .replace(/^_|_$/g, '');
  }

  protected confirmCreate(): void {
    const key = this.newKey().trim();
    const label = this.newLabel().trim();
    if (!key || !label) return;
    this.admin
      .createRole(key, label)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.creating.set(false);
        this.reload$.next();
      });
  }

  protected remove(role: Role): void {
    if (role.system) return;
    if (!confirm(`Eliminare il ruolo "${role.label}"?`)) return;
    this.admin
      .removeRole(role.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.openRole() === role.id) this.openRole.set(null);
        this.reload$.next();
      });
  }

  protected val(e: Event): string {
    return (e.target as HTMLInputElement | HTMLSelectElement).value;
  }

  private emptyRow(resource: string): Permission {
    return { resource, canRead: false, canWrite: false, canExecute: false, scope: 'ALL' };
  }
}

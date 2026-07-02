import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { Subject, catchError, of, startWith, switchMap, tap } from 'rxjs';

import { UiSpinner } from '../../../components/ui/spinner';
import { WorkflowEditor } from '../../../components/workflow-editor/workflow-editor';
import { Workflow, WorkflowRun } from '../../../models/admin';
import { WfGraph, synthGraph } from '../../../models/workflow-graph';
import { AdminService } from '../../../services/admin.service';

@Component({
  selector: 'app-workflows-tab',
  standalone: true,
  imports: [UiSpinner, WorkflowEditor],
  templateUrl: './workflows-tab.html'
})
export class WorkflowsTab {
  private readonly admin = inject(AdminService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly loading = signal(true);
  protected readonly ranId = signal<string | null>(null);

  // editor: workflow in modifica + grafo locale (mutato dall'editor a due vie)
  protected readonly openWf = signal<Workflow | null>(null);
  protected readonly graph = signal<WfGraph>({ nodes: [], edges: [] });
  protected readonly saved = signal(false);

  // creazione: form inline col nome
  protected readonly creating = signal(false);
  protected readonly newName = signal('');

  private readonly reload$ = new Subject<void>();

  protected readonly workflows = toSignal(
    this.reload$.pipe(
      startWith(undefined),
      tap(() => this.loading.set(true)),
      switchMap(() => this.admin.listWorkflows().pipe(catchError(() => of<Workflow[]>([])))),
      tap(() => this.loading.set(false))
    ),
    { initialValue: [] as Workflow[] }
  );

  protected triggerType(w: Workflow): string {
    return (w.trigger?.['type'] as string) ?? '—';
  }

  protected blockCount(w: Workflow): number {
    const nodes = (w.graph as { nodes?: unknown[] } | null)?.nodes;
    return Array.isArray(nodes) ? nodes.length : 0;
  }

  protected toggle(w: Workflow): void {
    this.admin
      .setWorkflowActive(w.id, !w.active)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload$.next());
  }

  protected run(w: Workflow): void {
    this.admin
      .runWorkflow(w.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.ranId.set(w.id);
        setTimeout(() => this.ranId.set(null), 2500);
      });
  }

  protected remove(w: Workflow): void {
    if (!confirm(`Eliminare il workflow "${w.name}"?`)) return;
    this.admin
      .removeWorkflow(w.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload$.next());
  }

  // ── editor ───────────────────────────────────────────────────────────────
  protected openEditor(w: Workflow): void {
    const raw = w.graph as unknown as WfGraph | null;
    const g = raw && Array.isArray(raw.nodes) && raw.nodes.length ? raw : synthGraph(w);
    // copia profonda: l'editor muta il grafo, non deve toccare l'item della lista
    this.graph.set(structuredClone(g));
    this.openWf.set(w);
    this.saved.set(false);
    this.loadRuns(w.id);
  }

  // ── esecuzioni recenti del workflow aperto ─────────────────────────────────
  protected readonly runs = signal<WorkflowRun[]>([]);
  protected readonly runsLoading = signal(false);

  protected loadRuns(id: string): void {
    this.runsLoading.set(true);
    this.admin
      .getWorkflow(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.runs.set(detail.runs);
          this.runsLoading.set(false);
        },
        error: () => this.runsLoading.set(false)
      });
  }

  protected runStatusClass(r: WorkflowRun): string {
    switch (r.status) {
      case 'SUCCESS': return 'badge-success';
      case 'FAILED': return 'badge-error';
      default: return 'badge-ghost';
    }
  }

  protected runDate(r: WorkflowRun): string {
    return new Date(r.startedAt).toLocaleString('it-IT');
  }

  protected closeEditor(): void {
    this.openWf.set(null);
  }

  protected save(): void {
    const w = this.openWf();
    if (!w) return;
    const g = this.graph();
    const triggerNode = g.nodes.find((n) => n.type === 'trigger');
    const trigger = (triggerNode?.data ?? { type: 'record.created' }) as Record<string, unknown>;
    this.admin
      .saveWorkflowGraph(w.id, g, trigger)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((updated) => {
        this.openWf.set(updated);
        this.saved.set(true);
        setTimeout(() => this.saved.set(false), 2000);
        this.reload$.next();
      });
  }

  protected toggleOpen(): void {
    const w = this.openWf();
    if (!w) return;
    this.admin
      .setWorkflowActive(w.id, !w.active)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((updated) => {
        this.openWf.set(updated);
        this.reload$.next();
      });
  }

  protected runOpen(): void {
    const w = this.openWf();
    if (!w) return;
    this.run(w);
    // l'esecuzione e' asincrona lato server: un refresh ritardato cattura l'esito nella maggior
    // parte dei casi; per il resto c'e' il bottone di aggiornamento manuale
    setTimeout(() => this.loadRuns(w.id), 1500);
  }

  // ── creazione ──────────────────────────────────────────────────────────────
  protected startCreate(): void {
    this.newName.set('');
    this.creating.set(true);
  }

  protected confirmCreate(): void {
    const name = this.newName().trim();
    if (!name) return;
    this.admin
      .createWorkflow(name)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((w) => {
        this.creating.set(false);
        this.reload$.next();
        this.openEditor(w);
      });
  }

  protected val(e: Event): string {
    return (e.target as HTMLInputElement).value;
  }
}

import { Component, ElementRef, computed, model, signal, viewChild } from '@angular/core';
import { Clock, Filter, LucideAngularModule, Play, Repeat, Trash2, Zap } from 'lucide-angular';

import { WfGraph, WfNode, WfNodeType } from '../../models/workflow-graph';

const NODE_W = 200;
const NODE_H = 64;

interface PaletteItem {
  type: WfNodeType;
  label: string;
  icon: unknown;
  color: string;
}

// Editor visuale a grafo dei workflow (porting di WorkflowEditor.tsx). Si collega col modello a due
// vie [(value)]: ogni mutazione (aggiungi nodo, collega, sposta, configura) riscrive il grafo nel
// signal del parent. Le connessioni si fanno a click (handle sorgente -> blocco destinazione), lo
// spostamento dei nodi a trascinamento. Il formato prodotto è eseguito tale e quale dal backend.
@Component({
  selector: 'app-workflow-editor',
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './workflow-editor.html'
})
export class WorkflowEditor {
  readonly value = model.required<WfGraph>();

  private readonly canvas = viewChild.required<ElementRef<HTMLDivElement>>('canvas');

  protected readonly NODE_W = NODE_W;
  protected readonly NODE_H = NODE_H;
  protected readonly CANVAS_W = 920;
  protected readonly CANVAS_H = 640;

  protected readonly TrashIcon = Trash2;

  protected readonly palette: PaletteItem[] = [
    { type: 'action', label: 'Azione', icon: Zap, color: '#2563eb' },
    { type: 'condition', label: 'Condizione', icon: Filter, color: '#f59e0b' },
    { type: 'loop', label: 'Ciclo', icon: Repeat, color: '#8b5cf6' },
    { type: 'delay', label: 'Attesa', icon: Clock, color: '#10b981' }
  ];

  protected readonly handles: Record<WfNodeType, string[]> = {
    trigger: ['out'],
    action: ['out'],
    condition: ['true', 'false'],
    loop: ['each', 'done'],
    delay: ['out']
  };

  protected readonly actionTypes = [
    'update_record', 'create_record', 'create_link', 'create_task', 'create_reminder',
    'create_calendar_event', 'notify_user', 'send_email', 'send_webhook', 'call_api', 'delay'
  ];

  protected readonly triggerEvents = [
    { value: 'record.created', label: 'Record creato' },
    { value: 'record.updated', label: 'Record aggiornato' },
    { value: 'record.deleted', label: 'Record eliminato' },
    { value: 'field.changed', label: 'Campo modificato' },
    { value: 'schedule', label: 'Pianificato' }
  ];

  protected readonly conditionOps = ['eq', 'neq', 'gt', 'lt', 'contains', 'is_set', 'in_days'];

  // placeholder letterale per l'hint (evita che il template lo interpreti come interpolazione)
  protected readonly recordPlaceholder = '{{record.campo}}';

  protected readonly selected = signal<string | null>(null);
  protected readonly connecting = signal<{ node: string; handle: string } | null>(null);

  // stato di trascinamento, imperativo: non serve reattività, cambia a ogni pointermove
  private drag: { id: string; dx: number; dy: number } | null = null;

  protected readonly selectedNode = computed(() =>
    this.value().nodes.find((n) => n.id === this.selected()) ?? null
  );

  // data del nodo selezionato tipizzato lasco, per accessi comodi dall'inspector nel template
  protected readonly selectedData = computed<Record<string, any>>(
    () => (this.selectedNode()?.data ?? {}) as Record<string, any>
  );

  // archi precompilati in path SVG (curva di Bézier) con colore in base all'handle sorgente
  protected readonly edgePaths = computed(() => {
    const g = this.value();
    const out: { id: string; d: string; color: string }[] = [];
    for (const e of g.edges) {
      const s = g.nodes.find((n) => n.id === e.source);
      const t = g.nodes.find((n) => n.id === e.target);
      if (!s || !t) continue;
      const p1 = this.handlePos(s, e.sourceHandle ?? 'out');
      const p2 = { x: t.x + NODE_W / 2, y: t.y };
      const color = e.sourceHandle === 'false' ? '#ef4444' : e.sourceHandle === 'true' ? '#10b981' : '#9aa1ac';
      out.push({ id: e.id, color, d: `M${p1.x},${p1.y} C${p1.x},${p1.y + 50} ${p2.x},${p2.y - 50} ${p2.x},${p2.y}` });
    }
    return out;
  });

  protected paletteFor(type: WfNodeType): PaletteItem | undefined {
    return this.palette.find((p) => p.type === type);
  }

  protected nodeIcon(n: WfNode): unknown {
    return n.type === 'trigger' ? Play : this.paletteFor(n.type)?.icon;
  }

  protected nodeColor(n: WfNode): string {
    return n.type === 'trigger' ? '#64748b' : this.paletteFor(n.type)?.color ?? '#64748b';
  }

  protected handlePos(n: WfNode, handle: string): { x: number; y: number } {
    const hs = this.handles[n.type];
    const idx = Math.max(0, hs.indexOf(handle));
    const step = NODE_W / (hs.length + 1);
    return { x: n.x + step * (idx + 1), y: n.y + NODE_H };
  }

  protected nodeTitle(n: WfNode): string {
    const d = n.data as Record<string, any>;
    switch (n.type) {
      case 'trigger': return d['objectKey'] ? `On ${d['objectKey']}` : 'Trigger';
      case 'action': return (d['type'] as string) ?? 'Azione';
      case 'delay': return `Attesa ${d['ms'] ?? 0}ms`;
      case 'loop': return `Ciclo: ${d['field'] ?? '…'}`;
      case 'condition': return 'Condizione';
      default: return n.type;
    }
  }

  protected addNode(type: WfNodeType): void {
    const n: WfNode = {
      id: uid(),
      type,
      x: 60 + Math.random() * 200,
      y: 120 + Math.random() * 200,
      data: type === 'action' ? { type: 'notify_user' } : {}
    };
    const g = this.value();
    this.value.set({ ...g, nodes: [...g.nodes, n] });
    this.selected.set(n.id);
  }

  protected removeNode(id: string): void {
    if (id === 'trigger') return;
    const g = this.value();
    this.value.set({
      nodes: g.nodes.filter((n) => n.id !== id),
      edges: g.edges.filter((e) => e.source !== id && e.target !== id)
    });
    this.selected.set(null);
  }

  protected patchSelected(data: Record<string, unknown>): void {
    const id = this.selected();
    if (!id) return;
    const g = this.value();
    this.value.set({
      ...g,
      nodes: g.nodes.map((n) => (n.id === id ? { ...n, data: { ...n.data, ...data } } : n))
    });
  }

  protected onHandleClick(nodeId: string, handle: string): void {
    this.connecting.set({ node: nodeId, handle });
  }

  protected isConnecting(nodeId: string, handle: string): boolean {
    const c = this.connecting();
    return c?.node === nodeId && c?.handle === handle;
  }

  protected cancelConnect(): void {
    this.connecting.set(null);
  }

  // click su un blocco: se sto collegando, crea l'arco (uno solo per handle sorgente); altrimenti
  // seleziona il blocco per configurarlo
  protected onNodeClick(nodeId: string): void {
    const c = this.connecting();
    if (c && c.node !== nodeId) {
      const g = this.value();
      const edges = g.edges.filter((e) => !(e.source === c.node && (e.sourceHandle ?? 'out') === c.handle));
      edges.push({ id: uid(), source: c.node, target: nodeId, sourceHandle: c.handle });
      this.value.set({ ...g, edges });
      this.connecting.set(null);
    } else {
      this.selected.set(nodeId);
    }
  }

  protected onPointerDown(n: WfNode, e: PointerEvent): void {
    const pt = this.canvasPoint(e);
    this.drag = { id: n.id, dx: pt.x - n.x, dy: pt.y - n.y };
  }

  protected onPointerMove(e: PointerEvent): void {
    if (!this.drag) return;
    const pt = this.canvasPoint(e);
    const id = this.drag.id;
    const x = Math.max(0, pt.x - this.drag.dx);
    const y = Math.max(0, pt.y - this.drag.dy);
    const g = this.value();
    this.value.set({ ...g, nodes: g.nodes.map((n) => (n.id === id ? { ...n, x, y } : n)) });
  }

  protected onPointerUp(): void {
    this.drag = null;
  }

  // config dell'azione (tutto tranne `type`) serializzata per la textarea dell'inspector
  protected actionConfigJson(): string {
    const rest = { ...this.selectedData() };
    delete rest['type'];
    return JSON.stringify(rest, null, 2);
  }

  protected onActionConfigInput(raw: string): void {
    try {
      const parsed = JSON.parse(raw || '{}') as Record<string, unknown>;
      this.patchSelected(parsed);
    } catch {
      // JSON momentaneamente invalido mentre si digita: si ignora finché non torna valido
    }
  }

  // legge il valore di un input/select/textarea da un evento del DOM
  protected val(e: Event): string {
    return (e.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value;
  }

  private canvasPoint(e: PointerEvent): { x: number; y: number } {
    const el = this.canvas().nativeElement;
    const rect = el.getBoundingClientRect();
    return { x: e.clientX - rect.left + el.scrollLeft, y: e.clientY - rect.top + el.scrollTop };
  }
}

function uid(): string {
  return Math.random().toString(36).slice(2, 9);
}

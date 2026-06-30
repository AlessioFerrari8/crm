// Formato a grafo dei workflow, eseguito dal backend (GraphWorkflowRunner): un nodo `trigger`
// è il punto di partenza, gli archi hanno un `sourceHandle` (out / true|false per condition /
// each|done per loop). La config del trigger vive nel `data` del nodo trigger e al salvataggio
// viene estratta anche nel campo `trigger` del workflow (matcher dell'evento).
export type WfNodeType = 'trigger' | 'action' | 'condition' | 'loop' | 'delay';

export interface WfNode {
  id: string;
  type: WfNodeType;
  x: number;
  y: number;
  data: Record<string, unknown>;
}

export interface WfEdge {
  id: string;
  source: string;
  target: string;
  sourceHandle?: string;
}

export interface WfGraph {
  nodes: WfNode[];
  edges: WfEdge[];
}

interface LegacyWorkflow {
  trigger?: Record<string, unknown> | null;
  conditions?: Record<string, unknown> | null;
  actions?: Record<string, unknown>[] | null;
}

// Sintetizza un grafo lineare leggibile da un workflow legacy (trigger + conditions + actions[]),
// così anche i workflow seedati nel formato vecchio si aprono e si modificano nell'editor visuale.
export function synthGraph(wf: LegacyWorkflow): WfGraph {
  const nodes: WfNode[] = [
    { id: 'trigger', type: 'trigger', x: 60, y: 40, data: wf.trigger ?? { type: 'record.created' } }
  ];
  const edges: WfEdge[] = [];
  let prev = 'trigger';
  let y = 160;

  if (wf.conditions) {
    nodes.push({ id: 'cond', type: 'condition', x: 60, y, data: { conditions: wf.conditions } });
    edges.push({ id: `e-${prev}-cond`, source: prev, target: 'cond', sourceHandle: 'out' });
    prev = 'cond';
    y += 120;
  }

  (wf.actions ?? []).forEach((a, i) => {
    const nid = `a${i}`;
    nodes.push({ id: nid, type: 'action', x: 60, y, data: a });
    edges.push({ id: `e-${prev}-${nid}`, source: prev, target: nid, sourceHandle: prev === 'cond' ? 'true' : 'out' });
    prev = nid;
    y += 120;
  });

  return { nodes, edges };
}

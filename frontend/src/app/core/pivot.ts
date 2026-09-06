// Da risultato piatto a matrice. Le prime N dimensioni sono le righe, le successive le colonne
// l'ordine è quello con cui il motore le ha messe nel SELECT, quindi prima rows e poi columns

import { ReportResult } from "../models/report";

// ed è un contratto fra ReportEngine e questa funzione. Se cambia lì, di conseguenza cambia qui
export interface Matrix {
  rows: string[];
  cols: string[];
  cell(row: string, col: string): number[] | undefined;
  rowTotals: Map<string, number[]>;
  colTotals: Map<string, number[]>;
}

export function toMatrix(result: ReportResult, rowDimCount: number, metricCount: number): Matrix {
  const rows: string[] = [];
  const cols: string[] = [];
  const cells = new Map<string, number[]>();

  result.dimensions.forEach((dims, i) => {
    const rowKey = dims.slice(0, rowDimCount).map(String).join(' | ') || '—';
    const colKey = dims.slice(rowDimCount).map(String).join(' | ') || '—';
    if (!rows.includes(rowKey)) rows.push(rowKey);
    if (!cols.includes(colKey)) cols.push(colKey);
    cells.set(`${rowKey}#${colKey}`, result.metrics[i]);
  });

  // I totali si sommano qui, sul client: i dati ce li abbiamo già tutti in mano, chiedere al backend un
  // secondo giro con il rollup sarebbe una query più per un dato già presente
  const rowTotals = new Map<string, number[]>();
  const colTotals = new Map<string, number[]>();
  const zeros = () => new Array(metricCount).fill(0) as number[];

  for (const r of rows) {
    const acc = zeros();
    for (const c of cols) {
      const v = cells.get(`${r}#${c}`);
      if (!v) continue;
      v.forEach((n, i) => (acc[i] += Number(n) || 0));
      const col = colTotals.get(c) ?? zeros();
      v.forEach((n, i) => (col[i] += Number(n) || 0));
      colTotals.set(c, col);
    }
    rowTotals.set(r, acc);
  }

  return { rows, cols, cell: (r, c) => cells.get(`${r}#${c}`), rowTotals, colTotals };
}

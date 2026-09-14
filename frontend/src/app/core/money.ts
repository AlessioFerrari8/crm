// specchio di DocumentLineCalculator.java

import { DiscountType, LineDraft } from '../models/document-line';

export interface LineTotals {
  net: number;
  vat: number;
  gross: number;
}

export function lineTotals(l: {
  kind: string;
  quantity: number;
  unitPrice: number;
  discountType: DiscountType;
  discountValue: number;
  vatPercent: number;
}): LineTotals {
  if (l.kind === 'TEXT') return { net: 0, vat: 0, gross: 0 };

  const gross = (l.quantity || 0) * (l.unitPrice || 0);
  const rawDiscount =
    l.discountType === 'PERCENT' ? (gross * (l.discountValue || 0)) / 100 : l.discountValue || 0;
  // uno sconto più grande dell'imponibile è quasi sempre un errore di battitura. Perciò si limita, non si va sotto
  // zero (stessa cosa fatta lato Java)
  const discount = Math.min(rawDiscount, gross);
  const net = round2(gross - discount);
  const vat = round2((net * (l.vatPercent || 0)) / 100);
  return { net, vat, gross: round2(net + vat) };
}

export function round2(n: number): number {
  return (Math.sign(n) * Math.round(Math.abs(n) * 100)) / 100;
}

export interface DocumentTotalsPreview {
  subtotal: number; // somma dei netti riga, prima dello sconto di testata
  taxable: number; // imponibile finale, su cui si calcola l'IVA
  vatTotal: number;
  total: number;
}

// specchio di DocumentTotalService.java
export function documentTotals(lines: LineDraft[], docDiscount = 0, shipping = 0): DocumentTotalsPreview  {
  const billable = lines.filter((l) => l.kind !== 'TEXT');
  const subtotal = round2(billable.reduce((s, l) => s + lineTotals({ ...l }).net, 0));

  const byRate = new Map<number, number>();
  for (const l of billable) {
    const rate = l.vatPercent || 0;
    byRate.set(rate, round2((byRate.get(rate) ?? 0) + lineTotals({ ...l }).net));
  }

  // il trasporto è un imponibile a sè e va sull'aliquota più alta del documento (spese accessorie)
  // senza righe non c'è un'aliquota da cui dedurlo
  const ship = shipping || 0;
  if (ship !== 0) {
    const rate = byRate.size ? Math.max(...byRate.keys()) : 22;
    byRate.set(rate, round2((byRate.get(rate) ?? 0) + ship));
  }

  const base = round2(subtotal + ship);
  const discount = docDiscount || 0;

  let taxable = 0;
  let vatTotal = 0;
  for (const [rate, net] of byRate) {
    const share = base === 0 ? 0 : (discount * net) / base;
    const rowTaxable = round2(net - share);
    taxable = round2(taxable + rowTaxable);
    vatTotal = round2(vatTotal + round2((rowTaxable * rate) / 100));
  }

  return { subtotal, taxable, vatTotal, total: round2(taxable + vatTotal) };
}

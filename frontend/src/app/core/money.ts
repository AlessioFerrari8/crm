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

export function documentTotals(lines: LineDraft[], docDiscount = 0, shipping = 0) {
  const subtotal = round2(lines.reduce((s, l) => s + lineTotals({ ...l }).net, 0));
  const vatTotal = round2(lines.reduce((s, l) => s + lineTotals({ ...l }).vat, 0));
  return {
    subtotal,
    vatTotal,
    total: round2(subtotal - (docDiscount || 0) + vatTotal + (shipping || 0)),
  };
}

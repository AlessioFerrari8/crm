// provenienza: entities/DocumentLine.java + dtos/DocumentLineResponse.java

export type LineKind = 'PRODUCT' | 'TEXT' | 'SUBTOTAL';
export type DiscountType = 'PERCENT' | 'AMOUNT';

export interface DocumentLine {
  id: string;
  lineNo: number;
  kind: LineKind;
  productId: string | null;
  productLabel: string | null;
  description: string;
  quantity: number;
  unit: string | null;
  unitPrice: number;
  discountType: DiscountType;
  discountValue: number;
  vatRateId: string | null;
  vatPercent: number;
  netTotal: number;
  vatTotal: number;
  grossTotal: number;
}

// dtos/DocumentLineDto.java, quello che si manda in put. Niente id: le righe
// si sostituiscono in blocco, non si aggiornano una per una
export interface DocumentLineDto {
  kind: LineKind;
  productId: string | null;
  description: string;
  quantity: number;
  unit: string | null;
  unitPrice: number;
  discountType: DiscountType;
  discountValue: number;
  vatRateId: string | null;
}

export interface LineDraft extends DocumentLineDto {
  vatPercent: number;
  productLabel: string | null;
}

// i 4 moduli che hanno le righe
export const DOCUMENT_KEYS = ['quote', 'sales_order', 'delivery_note', 'invoice'];

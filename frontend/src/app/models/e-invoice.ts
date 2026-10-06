export type EInvoiceStatus = 'DRAFT' | 'READY' | 'SENT' | 'DELIVERED' | 'REJECTED' | 'ACCEPTED';

export interface EInvoiceNotification {
  type: 'RC' | 'NS' | 'MC' | 'NE' | 'DT';
  receivedAt: string;
  message: string;
}

export interface EInvoice {
  id: string;
  invoiceId: string;
  invoiceTitle: string;
  filename: string;
  status: EInvoiceStatus;
  sdiIdentifier: string | null;
  notifications: EInvoiceNotification[] | null;
  sentAt: string | null;
  createdAt: string;
}

export interface Mailbox {
  id: string;
  label: string;
  host: string;
  port: number;
  ssl: boolean;
  username: string;
  folder: string;
  targetObjectKey: string;
  defaultOwnerId: string;
  active: boolean;
  lastUid: number;
  lastScanAt: string | null;
  lastError: string | null;
}

// password assente = non toccarla (PATCH)
export type MailboxInput = Omit<Mailbox, 'id' | 'lastUid' | 'lastScanAt' | 'lastError'> & { password?: string };

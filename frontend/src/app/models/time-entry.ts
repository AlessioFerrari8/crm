export interface TimeEntry {
  id: string;
  userId: string;
  recordId: string;
  objectKey: string;
  startedAt: string;
  endedAt: string | null; // null = il timer gira
  minutes: number;
  billable: number;
  description: string | null;
  hourlyRate: number | null;
  autoClosed: boolean;
}

export interface TimeSummaryRow {
  userId: string;
  minutes: number;
  billableMinutes: number;
  amount: number;
}

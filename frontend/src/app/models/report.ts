// provenienza: dtos/ReportQuery.java
export type Grain = 'NONE' | 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR';
export type Aggregate = 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX' | 'AVG_DAYS_BETWEEN';
export type ReportRender = 'BAR' | 'LINE' | 'PIE' | 'GRID';

export interface Dimension {
  field: string;
  grain: Grain;
}

export interface Metric {
  aggregate: Aggregate;
  field: string | null;
  secondField: string | null;
  label: string;
}

export interface ReportQuery {
  objectKey: string;
  filter?: FilterGroup | null;
  rows?: Dimension[];
  columns?: Dimension[];
  metrics: Metric[];
  // "dim0" | "met0" ordinamento per posizione
  orderBy?: string | null;
  orderDesc?: boolean;
  limit?: number | null;
}

// provenienza: dtos/ReportResult.java
export interface ReportResult {
  dimensions: unknown[][];
  metrics: number[][];
}

// provenienza: entities/Report.java
export interface Report {
  id: string;
  title: string;
  render: ReportRender;
  objectKey: string;
  folder: string | null;
  ownerId: string | null;
  query: ReportQuery;
}

import { FilterGroup } from './filter';

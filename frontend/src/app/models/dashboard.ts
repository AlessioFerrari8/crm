import { ReportQuery } from './report';

export type WidgetKind = 'KPI' | 'KPI_SPLIT' | 'CHART' | 'TABLE' | 'PIVOT' | 'RECORD_LIST';

// provenienza: entities/DashboardWidget.java
export interface DashboardWidget {
  id: string;
  label: string;
  kind: WidgetKind;
  color: string | null;
  icon: string | null;
  gridSpan: number;
  sortOrder: number;
  query: Record<string, unknown>;
}

export interface DashboardTab {
  id: string;
  label: string;
  sortOrder: number;
  widgets: DashboardWidget[];
}

// provenienza: dtos/DashboardResponse.java
export interface DashboardDetail {
  id: string;
  name: string;
  ownerId: string | null;
  roleIds: string[] | null;
  sortOrder: number;
  tabs: DashboardTab[];
}

export interface DashboardSummary {
  id: string;
  name: string;
  ownerId: string | null;
  sortOrder: number;
}

import { DashboardDetail, DashboardSummary, DashboardTab, DashboardWidget } from './../models/dashboard';
import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { API_BASE_URL } from '../core/api-config';
import { Observable } from 'rxjs';

@Service()
export class Dashboard {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/dashboards`;

  list(): Observable<DashboardSummary[]> {
    return this.http.get<DashboardSummary[]>(this.base);
  }

  // tab e widget sono in una chiamata sola; i dati dei widget no, quelli se li
  // prende ogni widget per conto suo
  get(id: string): Observable<DashboardDetail> {
    return this.http.get<DashboardDetail>(`${this.base}/${id}`);
  }

  create(name: string, personal: boolean): Observable<DashboardSummary> {
    return this.http.post<DashboardSummary>(this.base, { name, personal, roleIds: [] });
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  addTab(dashboardId: string, label: string): Observable<DashboardTab> {
    return this.http.post<DashboardTab>(`${this.base}/${dashboardId}/tabs`, { label });
  }

  removeTab(tabId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/tabs/${tabId}`);
  }

  addWidget(tabId: string, widget: Partial<DashboardWidget>): Observable<DashboardWidget> {
    return this.http.post<DashboardWidget>(`${this.base}/tabs/${tabId}/widgets`, widget);
  }

  updateWidget(widgetId: string, widget: Partial<DashboardWidget>): Observable<DashboardWidget> {
    return this.http.put<DashboardWidget>(`${this.base}/widgets/${widgetId}`, widget);
  }

  removeWidget(widgetId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/widgets/${widgetId}`);
  }
}

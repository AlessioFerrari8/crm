import { HttpClient } from '@angular/common/http';
import { Injectable, Service, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../core/api-config';
import { IRevenuePoint } from '../models/IRevenuePoint';
import { IEfficiencyPoint } from '../models/IEfficiencyPoint';
import { IPipelinePoint } from '../models/IPipelinePoint';
import { IActivityPoint } from '../models/IActivityPoint';

@Service()
export class AnalyticsService {
  // http client + url base
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/analytics`;

  // 4 endpoint per le analitycs
  revenue(): Observable<IRevenuePoint[]> {
    return this.http.get<IRevenuePoint[]>(`${this.base}/revenue`);
  }

  efficiency(): Observable<IEfficiencyPoint[]> {
    return this.http.get<IEfficiencyPoint[]>(`${this.base}/efficiency`);
  }

  pipeline(): Observable<IPipelinePoint[]> {
    return this.http.get<IPipelinePoint[]>(`${this.base}/pipeline`);
  }

  activity(): Observable<IActivityPoint[]> {
    return this.http.get<IActivityPoint[]>(`${this.base}/activity`);
  }
}

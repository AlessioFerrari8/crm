import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { API_BASE_URL } from '../core/api-config';
import { Observable } from 'rxjs';
import { Campaign, CampaignAudience, CampaignStats, CampaignInput, NpsStats, NpsSurvey } from '../models/campaign';

@Service()
export class CampaignService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/campaigns`;
  private readonly nps = `${API_BASE_URL}/api/nps`;

  list(): Observable<Campaign[]> {
    return this.http.get<Campaign[]>(this.base);
  }

  save(id: string | null, body: CampaignInput): Observable<Campaign> {
    return id ? this.http.patch<Campaign>(`${this.base}/${id}`, body) : this.http.post<Campaign>(this.base, body);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  audience(id: string): Observable<CampaignAudience> {
    return this.http.post<CampaignAudience>(`${this.base}/${id}/preview-recipients`, {});
  }

  test(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/test`, {});
  }

  // confirmCount: il numero che l'utente ha digitato. Se nel frattempo è cambiato, il backend rifiuta
  send(id: string, confirmCount: number): Observable<Campaign> {
    return this.http.post<Campaign>(`${this.base}/${id}/send`, { confirmCount });
  }

  pause(id: string): Observable<Campaign> {
    return this.http.post<Campaign>(`${this.base}/${id}/pause`, {});
  }

  stats(id: string): Observable<CampaignStats> {
    return this.http.get<CampaignStats>(`${this.base}/${id}/stats`);
  }

  surveys(): Observable<NpsSurvey[]> {
    return this.http.get<NpsSurvey[]>(this.nps);
  }

  saveSurvey(id: string | null, body: Omit<NpsSurvey, 'id'>): Observable<NpsSurvey> {
    return id ? this.http.patch<NpsSurvey>(`${this.nps}/${id}`, body) : this.http.post<NpsSurvey>(this.nps, body);
  }

  surveyStats(id: string): Observable<NpsStats> {
    return this.http.get<NpsStats>(`${this.nps}/${id}/stats`);
  }
}

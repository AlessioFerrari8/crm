import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { API_BASE_URL } from '../core/api-config';
import { Observable } from 'rxjs';
import { RecordItem } from '../models/record';

// dtos/DuplicateCandidateDto.java
export interface DuplicateCandidate {
  leftId: string;
  leftTitle: string;
  rightId: string;
  rightTitle: string;
  score: number;
}

@Service()
export class MergeServiceTs {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_BASE_URL}/api/records`;

  // soglia: 0.6 è il default del backend. Sotto 0.4 escono coppie a caso,
  // sopra 0.8 non esce quasi niente
  duplicates(objectKey: string, threshold = 0.6): Observable<DuplicateCandidate[]> {
    const params = new HttpParams().set('threshold', threshold);
    return this.http.get<DuplicateCandidate[]>(`${this.base}/duplicates/${objectKey}`, { params });
  }

  // il perdente non viene cancellato: finisce nel cestino, da cui si recupera
  merge(winnerId: string, loserId: string, fieldsFromLoser: string[]): Observable<RecordItem> {
    return this.http.post<RecordItem>(`${this.base}/${winnerId}/merge/${loserId}`, {
      fieldsFromLoser,
    });
  }
}

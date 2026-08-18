import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { catchError, Observable, of } from 'rxjs';
import { apiUrl } from '../utils/api-url';

export interface SummonerSuggestion {
  puuid: string;
  gameName: string;
  tagLine: string;
  riotId: string;
  profileIconId: number | null;
}

@Injectable({ providedIn: 'root' })
export class SummonerSearchService {
  constructor(private readonly http: HttpClient) {}

  search(query: string): Observable<SummonerSuggestion[]> {
    const q = query.trim();
    if (q.length < 2) {
      return of([]);
    }
    return this.http.get<SummonerSuggestion[]>(apiUrl('/api/summoners/search'), {
      params: { q },
    }).pipe(catchError(() => of([])));
  }

  resolve(riotId: string): Observable<SummonerSuggestion> {
    return this.http.get<SummonerSuggestion>(apiUrl('/api/summoners/resolve'), {
      params: { riotId },
    });
  }
}

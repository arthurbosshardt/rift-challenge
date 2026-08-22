import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { catchError, Observable, of } from 'rxjs';
import { I18nService } from '../i18n/i18n.service';
import { apiUrl } from '../utils/api-url';
import { normalizeRiotIdForLocale } from '../utils/riot-id';

export interface SummonerSuggestion {
  puuid: string;
  gameName: string;
  tagLine: string;
  riotId: string;
  profileIconId: number | null;
}

@Injectable({ providedIn: 'root' })
export class SummonerSearchService {
  private readonly i18n = inject(I18nService);

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
      params: { riotId: normalizeRiotIdForLocale(riotId, this.i18n.locale()) },
    });
  }
}

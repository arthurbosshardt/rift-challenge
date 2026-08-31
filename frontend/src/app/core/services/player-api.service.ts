import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { AccountRecentGames, ChallengeListResponse } from '../models/challenge.models';
import { SummonerSuggestion } from './summoner-search.service';
import { apiUrl } from '../utils/api-url';
import { normalizeChallengeListResponse, type RawChallengeListResponse } from '../utils/challenge-summary';

@Injectable({ providedIn: 'root' })
export class PlayerApiService {
  private readonly baseUrl = apiUrl('/api/players');

  constructor(private readonly http: HttpClient) {}

  resolve(riotId: string): Observable<SummonerSuggestion> {
    return this.http.get<SummonerSuggestion>(`${this.baseUrl}/${encodeURIComponent(riotId)}`);
  }

  getActivity(riotId: string): Observable<AccountRecentGames> {
    return this.http.get<AccountRecentGames>(`${this.baseUrl}/${encodeURIComponent(riotId)}/activity`);
  }

  refreshActivity(riotId: string): Observable<AccountRecentGames> {
    return this.http.post<AccountRecentGames>(`${this.baseUrl}/${encodeURIComponent(riotId)}/activity/refresh`, {});
  }

  getChallenges(riotId: string): Observable<ChallengeListResponse> {
    return this.http
      .get<RawChallengeListResponse>(`${this.baseUrl}/${encodeURIComponent(riotId)}/challenges`)
      .pipe(map((raw) => normalizeChallengeListResponse(raw)));
  }
}

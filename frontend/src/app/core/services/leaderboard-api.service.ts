import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { LeaderboardResponse } from '../models/leaderboard.models';
import { MatchDetail } from '../models/challenge.models';
import { apiUrl } from '../utils/api-url';

@Injectable({ providedIn: 'root' })
export class LeaderboardApiService {
  constructor(private readonly http: HttpClient) {}

  getLeaderboard(): Observable<LeaderboardResponse> {
    return this.http.get<LeaderboardResponse>(apiUrl('/api/leaderboard'));
  }

  refreshLeaderboard(): Observable<LeaderboardResponse> {
    return this.http.post<LeaderboardResponse>(apiUrl('/api/leaderboard/refresh'), {});
  }

  getPlayerMatchDetail(puuid: string, matchId: string): Observable<MatchDetail> {
    return this.http.get<MatchDetail>(apiUrl(`/api/leaderboard/players/${puuid}/matches/${matchId}`));
  }
}

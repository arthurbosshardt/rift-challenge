import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import {
  AddDuoRequest,
  AddParticipantRequest,
  AuthMeResponse,
  CreateChallengeRequest,
  ChallengeDetail,
  ChallengeListResponse,
  UpdateChallengeEndRequest,
  UpdateChallengeScheduleRequest,
  UpdateChallengeStartRequest,
  UpdateChallengeNameRequest,
  UpdateChallengeRequest,
  AccountRecentGames,
  MatchDetail,
} from '../models/challenge.models';
import { apiUrl } from '../utils/api-url';
import { normalizeRiotId } from '../utils/riot-id';
import { normalizeChallengeListResponse, type RawChallengeListResponse } from '../utils/challenge-summary';

@Injectable({ providedIn: 'root' })
export class ChallengeApiService {
  private readonly baseUrl = apiUrl('/api/challenges');

  constructor(private readonly http: HttpClient) {}

  listPublicChallenges(): Observable<ChallengeListResponse> {
    return this.listChallenges(`${this.baseUrl}/public`);
  }

  refreshPublicChallenges(): Observable<ChallengeListResponse> {
    return this.http
      .post<RawChallengeListResponse>(`${this.baseUrl}/public/refresh`, {})
      .pipe(map((raw) => normalizeChallengeListResponse(raw)));
  }

  listParticipatingChallenges(): Observable<ChallengeListResponse> {
    return this.listChallenges(`${this.baseUrl}/participating`);
  }

  private listChallenges(url: string): Observable<ChallengeListResponse> {
    return this.http
      .get<RawChallengeListResponse>(url)
      .pipe(map((raw) => normalizeChallengeListResponse(raw)));
  }

  getChallengeByShareSlug(shareSlug: string, bustCache = false): Observable<ChallengeDetail> {
    const suffix = bustCache ? `?_=${Date.now()}` : '';
    const encodedSlug = encodeURIComponent(shareSlug);
    return this.http.get<ChallengeDetail>(`${this.baseUrl}/share/${encodedSlug}${suffix}`);
  }

  createChallenge(request: CreateChallengeRequest): Observable<ChallengeDetail> {
    return this.http.post<ChallengeDetail>(this.baseUrl, request);
  }

  updateChallenge(challengeId: string, request: UpdateChallengeRequest): Observable<ChallengeDetail> {
    return this.http.patch<ChallengeDetail>(`${this.baseUrl}/${challengeId}`, request);
  }

  updateChallengeSchedule(challengeId: string, request: UpdateChallengeScheduleRequest): Observable<ChallengeDetail> {
    return this.http.patch<ChallengeDetail>(`${this.baseUrl}/${challengeId}/schedule`, request);
  }

  updateChallengeEnd(challengeId: string, request: UpdateChallengeEndRequest): Observable<ChallengeDetail> {
    return this.http.patch<ChallengeDetail>(`${this.baseUrl}/${challengeId}/end`, request);
  }

  updateChallengeStart(challengeId: string, request: UpdateChallengeStartRequest): Observable<ChallengeDetail> {
    return this.http.patch<ChallengeDetail>(`${this.baseUrl}/${challengeId}/start`, request);
  }

  updateChallengeName(challengeId: string, request: UpdateChallengeNameRequest): Observable<ChallengeDetail> {
    return this.http.patch<ChallengeDetail>(`${this.baseUrl}/${challengeId}/name`, request);
  }

  addDuo(challengeId: string, request: AddDuoRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${challengeId}/duos`, {
      player1RiotId: normalizeRiotId(request.player1RiotId),
      player2RiotId: normalizeRiotId(request.player2RiotId),
    });
  }

  removeDuo(challengeId: string, duoId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${challengeId}/duos/${duoId}`);
  }

  addParticipant(challengeId: string, request: AddParticipantRequest): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/${challengeId}/participants`, {
      riotId: normalizeRiotId(request.riotId),
    });
  }

  removeParticipant(challengeId: string, participantId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${challengeId}/participants/${participantId}`);
  }

  deleteChallenge(challengeId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${challengeId}`);
  }

  refreshChallenge(challengeId: string): Observable<ChallengeDetail> {
    return this.http.post<ChallengeDetail>(`${this.baseUrl}/${challengeId}/refresh`, {});
  }

  getCurrentUser(): Observable<AuthMeResponse> {
    return this.http.get<AuthMeResponse>(apiUrl('/api/auth/me'));
  }

  listRecentGames(options?: { refresh?: boolean }): Observable<AccountRecentGames[]> {
    const refresh = options?.refresh ?? false;
    return this.http.get<AccountRecentGames[]>(
      apiUrl('/api/challenges/recent'),
      refresh ? { params: { refresh: 'true' } } : undefined,
    );
  }

  getAccountMatchDetail(accountId: string, matchId: string): Observable<MatchDetail> {
    return this.http.get<MatchDetail>(apiUrl(`/api/me/riot-accounts/${accountId}/matches/${matchId}`));
  }

  getParticipantMatchDetail(challengeId: string, participantId: string, matchId: string): Observable<MatchDetail> {
    return this.http.get<MatchDetail>(
      `${this.baseUrl}/${challengeId}/participants/${participantId}/matches/${matchId}`,
    );
  }

  getDuoMatchDetail(challengeId: string, duoId: string, matchId: string): Observable<MatchDetail> {
    return this.http.get<MatchDetail>(`${this.baseUrl}/${challengeId}/duos/${duoId}/matches/${matchId}`);
  }
}

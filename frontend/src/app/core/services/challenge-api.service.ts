import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { forkJoin, Observable, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import {
  AddDuoRequest,
  AddParticipantRequest,
  AuthMeResponse,
  CreateChallengeRequest,
  ChallengeDetail,
  ChallengeSummary,
  UpdateChallengeEndRequest,
  UpdateChallengeScheduleRequest,
  UpdateChallengeStartRequest,
  UpdateChallengeVisibilityRequest,
  UpdateChallengeNameRequest,
} from '../models/challenge.models';
import { apiUrl } from '../utils/api-url';
import { normalizeRiotId } from '../utils/riot-id';
import {
  enrichSummaryFromDetail,
  normalizeChallengeSummaries,
  summaryChallengeNeedsEnrichment,
  summaryChallengeNeedsStatsRefresh,
  type RawChallengeSummary,
} from '../utils/challenge-summary';

@Injectable({ providedIn: 'root' })
export class ChallengeApiService {
  private readonly baseUrl = apiUrl('/api/challenges');

  constructor(private readonly http: HttpClient) {}

  listPublicChallenges(forceRefresh = false): Observable<ChallengeSummary[]> {
    const url = forceRefresh
      ? `${this.baseUrl}/public?_=${Date.now()}`
      : `${this.baseUrl}/public`;
    return this.listChallenges(url, forceRefresh);
  }

  listOwnedChallenges(): Observable<ChallengeSummary[]> {
    return this.listChallenges(`${this.baseUrl}/owned`);
  }

  listParticipatingChallenges(): Observable<ChallengeSummary[]> {
    return this.listChallenges(`${this.baseUrl}/participating`);
  }

  private listChallenges(url: string, forceRefresh = false): Observable<ChallengeSummary[]> {
    return this.http.get<RawChallengeSummary[]>(url).pipe(
      switchMap((raw) => {
        const normalized = normalizeChallengeSummaries(raw);
        if (normalized.length === 0) {
          return of(normalized);
        }

        return forkJoin(
          normalized.map((challenge, index) => {
            const needsEnrichment =
              summaryChallengeNeedsEnrichment(raw[index]) ||
              (forceRefresh && summaryChallengeNeedsStatsRefresh(challenge));

            if (!needsEnrichment) {
              return of(challenge);
            }

            return this.getChallengeByShareSlug(challenge.shareSlug, forceRefresh).pipe(
              map((detail) => enrichSummaryFromDetail(challenge, detail)),
              catchError(() => of(challenge)),
            );
          }),
        );
      }),
    );
  }

  /** @deprecated Use listOwnedChallenges() */
  listMyChallenges(): Observable<ChallengeSummary[]> {
    return this.listOwnedChallenges();
  }

  getChallengeByShareSlug(shareSlug: string, bustCache = false): Observable<ChallengeDetail> {
    const suffix = bustCache ? `?_=${Date.now()}` : '';
    return this.http.get<ChallengeDetail>(`${this.baseUrl}/share/${shareSlug}${suffix}`);
  }

  createChallenge(request: CreateChallengeRequest): Observable<ChallengeDetail> {
    return this.http.post<ChallengeDetail>(this.baseUrl, request);
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

  updateChallengeVisibility(challengeId: string, request: UpdateChallengeVisibilityRequest): Observable<ChallengeDetail> {
    return this.http.patch<ChallengeDetail>(`${this.baseUrl}/${challengeId}/visibility`, request);
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
}

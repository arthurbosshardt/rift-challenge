import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { forkJoin, Observable, of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import {
  AddDuoRequest,
  AddParticipantRequest,
  AuthMeResponse,
  CreateRaceRequest,
  RaceDetail,
  RaceSummary,
  UpdateRaceEndRequest,
  UpdateRaceScheduleRequest,
  UpdateRaceStartRequest,
  UpdateRaceVisibilityRequest,
  UpdateRaceNameRequest,
} from '../models/race.models';
import { apiUrl } from '../utils/api-url';
import { normalizeRiotId } from '../utils/riot-id';
import {
  enrichSummaryFromDetail,
  normalizeRaceSummaries,
  summaryRaceNeedsEnrichment,
  type RawRaceSummary,
} from '../utils/race-summary';

@Injectable({ providedIn: 'root' })
export class RaceApiService {
  private readonly baseUrl = apiUrl('/api/races');

  constructor(private readonly http: HttpClient) {}

  listPublicRaces(): Observable<RaceSummary[]> {
    return this.listRaces(`${this.baseUrl}/public`);
  }

  listOwnedRaces(): Observable<RaceSummary[]> {
    return this.listRaces(`${this.baseUrl}/owned`);
  }

  listParticipatingRaces(): Observable<RaceSummary[]> {
    return this.listRaces(`${this.baseUrl}/participating`);
  }

  private listRaces(url: string): Observable<RaceSummary[]> {
    return this.http.get<RawRaceSummary[]>(url).pipe(
      switchMap((raw) => {
        const normalized = normalizeRaceSummaries(raw);
        if (normalized.length === 0) {
          return of(normalized);
        }

        return forkJoin(
          normalized.map((race, index) => {
            if (!summaryRaceNeedsEnrichment(raw[index])) {
              return of(race);
            }

            return this.getRaceByShareSlug(race.shareSlug).pipe(
              map((detail) => enrichSummaryFromDetail(race, detail)),
              catchError(() => of(race)),
            );
          }),
        );
      }),
    );
  }

  /** @deprecated Use listOwnedRaces() */
  listMyRaces(): Observable<RaceSummary[]> {
    return this.listOwnedRaces();
  }

  getRaceByShareSlug(shareSlug: string): Observable<RaceDetail> {
    return this.http.get<RaceDetail>(`${this.baseUrl}/share/${shareSlug}`);
  }

  createRace(request: CreateRaceRequest): Observable<RaceDetail> {
    return this.http.post<RaceDetail>(this.baseUrl, request);
  }

  updateRaceSchedule(raceId: string, request: UpdateRaceScheduleRequest): Observable<RaceDetail> {
    return this.http.patch<RaceDetail>(`${this.baseUrl}/${raceId}/schedule`, request);
  }

  updateRaceEnd(raceId: string, request: UpdateRaceEndRequest): Observable<RaceDetail> {
    return this.http.patch<RaceDetail>(`${this.baseUrl}/${raceId}/end`, request);
  }

  updateRaceStart(raceId: string, request: UpdateRaceStartRequest): Observable<RaceDetail> {
    return this.http.patch<RaceDetail>(`${this.baseUrl}/${raceId}/start`, request);
  }

  updateRaceVisibility(raceId: string, request: UpdateRaceVisibilityRequest): Observable<RaceDetail> {
    return this.http.patch<RaceDetail>(`${this.baseUrl}/${raceId}/visibility`, request);
  }

  updateRaceName(raceId: string, request: UpdateRaceNameRequest): Observable<RaceDetail> {
    return this.http.patch<RaceDetail>(`${this.baseUrl}/${raceId}/name`, request);
  }

  addDuo(raceId: string, request: AddDuoRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${raceId}/duos`, {
      player1RiotId: normalizeRiotId(request.player1RiotId),
      player2RiotId: normalizeRiotId(request.player2RiotId),
    });
  }

  removeDuo(raceId: string, duoId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${raceId}/duos/${duoId}`);
  }

  addParticipant(raceId: string, request: AddParticipantRequest): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/${raceId}/participants`, {
      riotId: normalizeRiotId(request.riotId),
    });
  }

  removeParticipant(raceId: string, participantId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${raceId}/participants/${participantId}`);
  }

  refreshRace(raceId: string): Observable<RaceDetail> {
    return this.http.post<RaceDetail>(`${this.baseUrl}/${raceId}/refresh`, {});
  }

  getCurrentUser(): Observable<AuthMeResponse> {
    return this.http.get<AuthMeResponse>(apiUrl('/api/auth/me'));
  }
}

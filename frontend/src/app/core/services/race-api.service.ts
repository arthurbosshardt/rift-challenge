import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
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
} from '../models/race.models';
import { apiUrl } from '../utils/api-url';

@Injectable({ providedIn: 'root' })
export class RaceApiService {
  private readonly baseUrl = apiUrl('/api/races');

  constructor(private readonly http: HttpClient) {}

  listPublicRaces(): Observable<RaceSummary[]> {
    return this.http.get<RaceSummary[]>(`${this.baseUrl}/public`);
  }

  listOwnedRaces(): Observable<RaceSummary[]> {
    return this.http.get<RaceSummary[]>(`${this.baseUrl}/owned`);
  }

  listParticipatingRaces(): Observable<RaceSummary[]> {
    return this.http.get<RaceSummary[]>(`${this.baseUrl}/participating`);
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

  addDuo(raceId: string, request: AddDuoRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${raceId}/duos`, request);
  }

  removeDuo(raceId: string, duoId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${raceId}/duos/${duoId}`);
  }

  addParticipant(raceId: string, request: AddParticipantRequest): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/${raceId}/participants`, request);
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

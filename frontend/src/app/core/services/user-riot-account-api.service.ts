import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { LinkRiotAccountRequest, UserRiotAccount } from '../models/race.models';
import { apiUrl } from '../utils/api-url';

@Injectable({ providedIn: 'root' })
export class UserRiotAccountApiService {
  private readonly baseUrl = apiUrl('/api/me/riot-accounts');

  constructor(private readonly http: HttpClient) {}

  listAccounts(): Observable<UserRiotAccount[]> {
    return this.http.get<UserRiotAccount[]>(this.baseUrl);
  }

  linkAccount(request: LinkRiotAccountRequest): Observable<UserRiotAccount> {
    return this.http.post<UserRiotAccount>(this.baseUrl, request);
  }

  unlinkAccount(accountId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${accountId}`);
  }
}

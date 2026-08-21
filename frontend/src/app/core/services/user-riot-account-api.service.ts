import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { LinkRiotAccountRequest, UserRiotAccount } from '../models/challenge.models';
import { apiUrl } from '../utils/api-url';
import { normalizeRiotId } from '../utils/riot-id';

@Injectable({ providedIn: 'root' })
export class UserRiotAccountApiService {
  private readonly baseUrl = apiUrl('/api/me/riot-accounts');

  constructor(private readonly http: HttpClient) {}

  listAccounts(): Observable<UserRiotAccount[]> {
    return this.http.get<UserRiotAccount[]>(this.baseUrl);
  }

  linkAccount(request: LinkRiotAccountRequest): Observable<UserRiotAccount> {
    return this.http.post<UserRiotAccount>(this.baseUrl, {
      riotId: normalizeRiotId(request.riotId),
    });
  }

  unlinkAccount(accountId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${accountId}`);
  }
}

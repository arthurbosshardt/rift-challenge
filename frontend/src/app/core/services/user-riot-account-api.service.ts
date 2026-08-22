import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { I18nService } from '../i18n/i18n.service';
import { LinkRiotAccountRequest, UserRiotAccount } from '../models/challenge.models';
import { apiUrl } from '../utils/api-url';
import { normalizeRiotIdForLocale } from '../utils/riot-id';

@Injectable({ providedIn: 'root' })
export class UserRiotAccountApiService {
  private readonly baseUrl = apiUrl('/api/me/riot-accounts');
  private readonly i18n = inject(I18nService);

  constructor(private readonly http: HttpClient) {}

  listAccounts(): Observable<UserRiotAccount[]> {
    return this.http.get<UserRiotAccount[]>(this.baseUrl);
  }

  linkAccount(request: LinkRiotAccountRequest): Observable<UserRiotAccount> {
    return this.http.post<UserRiotAccount>(this.baseUrl, {
      riotId: normalizeRiotIdForLocale(request.riotId, this.i18n.locale()),
    });
  }

  unlinkAccount(accountId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${accountId}`);
  }
}

import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, from, of, switchMap, timeout } from 'rxjs';
import { AuthService } from '../services/auth.service';

const AUTH_RESOLVE_TIMEOUT_MS = 8_000;

function isPublicApiRequest(url: string): boolean {
  return url.includes('/api/challenges/public') || url.includes('/api/health');
}

function isExternalAssetRequest(url: string): boolean {
  return (
    url.includes('ddragon.leagueoflegends.com') ||
    url.includes('communitydragon.org') ||
    url.includes('communitydragon.net')
  );
}

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (request.method === 'OPTIONS' || isPublicApiRequest(request.url) || isExternalAssetRequest(request.url)) {
    return next(request);
  }

  const auth = inject(AuthService);

  return from(auth.resolveAccessToken()).pipe(
    timeout(AUTH_RESOLVE_TIMEOUT_MS),
    catchError(() => of(auth.peekAccessToken())),
    switchMap((token) => {
      if (!token) {
        return next(request);
      }

      return next(
        request.clone({
          setHeaders: {
            Authorization: `Bearer ${token}`,
          },
        }),
      );
    }),
  );
};

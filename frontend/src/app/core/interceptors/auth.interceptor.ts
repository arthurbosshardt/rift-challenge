import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { AuthService } from '../services/auth.service';

function isPublicApiRequest(url: string): boolean {
  return url.includes('/api/races/public');
}

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (isPublicApiRequest(request.url)) {
    return next(request);
  }

  const auth = inject(AuthService);

  return from(auth.resolveAccessToken()).pipe(
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

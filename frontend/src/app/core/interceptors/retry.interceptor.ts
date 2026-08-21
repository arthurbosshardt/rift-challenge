import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { retry, throwError, timer } from 'rxjs';

const MAX_RETRIES = 2;
const RETRY_DELAY_MS = 500;

/** Network drop (status 0) or the backend's own gateway restarting — not a client error, not the app's rate limiting. */
const RETRYABLE_STATUSES = new Set([0, 502, 503, 504]);

function isRetryable(request: HttpRequest<unknown>, error: unknown): boolean {
  return (
    request.method === 'GET' && error instanceof HttpErrorResponse && RETRYABLE_STATUSES.has(error.status)
  );
}

/** Retries idempotent GET requests on transient network/gateway failures only. */
export const retryInterceptor: HttpInterceptorFn = (request, next) =>
  next(request).pipe(
    retry({
      count: MAX_RETRIES,
      delay: (error, retryCount) =>
        isRetryable(request, error) ? timer(retryCount * RETRY_DELAY_MS) : throwError(() => error),
    }),
  );

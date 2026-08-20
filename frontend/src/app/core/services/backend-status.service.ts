import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { catchError, of, timeout } from 'rxjs';
import { apiUrl } from '../utils/api-url';

const POLL_DOWN_MS = 4000;
const POLL_UP_MS = 32000;
const REQUEST_TIMEOUT_MS = 8000;
const WAKE_TIMEOUT_MS = 90000;

@Injectable({ providedIn: 'root' })
export class BackendStatusService {
  private readonly readyState = signal(false);
  private readonly checkingState = signal(true);
  private pollId: number | null = null;
  private firstCheck = true;
  private readonly pendingRetries: Array<() => void> = [];

  readonly ready = this.readyState.asReadonly();
  readonly checking = this.checkingState.asReadonly();

  constructor(private readonly http: HttpClient) {}

  start(): void {
    if (this.pollId != null) {
      return;
    }
    this.tick();
  }

  /** Runs `callback` now if the backend is already ready, otherwise once on the next time it becomes ready. */
  onReady(callback: () => void): void {
    if (this.readyState()) {
      callback();
      return;
    }
    this.pendingRetries.push(callback);
  }

  private tick(): void {
    const waitMs = this.firstCheck ? WAKE_TIMEOUT_MS : REQUEST_TIMEOUT_MS;
    this.firstCheck = false;
    this.http
      .get<{ status?: string }>(apiUrl('/api/health'))
      .pipe(
        timeout(waitMs),
        catchError(() => of({ status: 'DOWN' })),
      )
      .subscribe((body) => {
        const up = body?.status === 'UP';
        this.readyState.set(up);
        this.checkingState.set(false);
        this.scheduleNext(up ? POLL_UP_MS : POLL_DOWN_MS);
        if (up && this.pendingRetries.length > 0) {
          this.pendingRetries.splice(0).forEach((callback) => callback());
        }
      });
  }

  private scheduleNext(delayMs: number): void {
    if (this.pollId != null) {
      window.clearTimeout(this.pollId);
    }
    this.pollId = window.setTimeout(() => this.tick(), delayMs);
  }
}

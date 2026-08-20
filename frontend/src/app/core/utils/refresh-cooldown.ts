import { signal } from '@angular/core';

const DEFAULT_COOLDOWN_MS = 5000;

/** Rate-limits a manual refresh button: after start(), `seconds` counts down
 * to 0 once per tick so the button can stay disabled meanwhile. */
export class RefreshCooldown {
  readonly seconds = signal(0);
  private interval: ReturnType<typeof setInterval> | null = null;

  constructor(private readonly durationMs = DEFAULT_COOLDOWN_MS) {}

  get active(): boolean {
    return this.seconds() > 0;
  }

  start(): void {
    this.clear();
    this.seconds.set(Math.round(this.durationMs / 1000));
    this.interval = setInterval(() => {
      const next = this.seconds() - 1;
      if (next <= 0) {
        this.seconds.set(0);
        this.clear();
        return;
      }
      this.seconds.set(next);
    }, 1000);
  }

  clear(): void {
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = null;
    }
  }
}

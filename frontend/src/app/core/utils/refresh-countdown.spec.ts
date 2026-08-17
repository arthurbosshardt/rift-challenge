import { describe, expect, it } from 'vitest';
import { formatRefreshCountdown, isRefreshCooldownActive } from './refresh-countdown';

describe('refresh-countdown', () => {
  it('formats remaining time as MM:SS', () => {
    const now = Date.parse('2026-08-16T10:00:00.000Z');
    const next = '2026-08-16T10:01:05.000Z';

    expect(formatRefreshCountdown(next, now)).toBe('1:05');
  });

  it('returns null when cooldown elapsed', () => {
    const now = Date.parse('2026-08-16T10:02:00.000Z');
    const next = '2026-08-16T10:00:00.000Z';

    expect(formatRefreshCountdown(next, now)).toBeNull();
  });

  it('detects active cooldown', () => {
    const now = Date.parse('2026-08-16T10:00:00.000Z');
    const next = '2026-08-16T10:01:00.000Z';

    expect(
      isRefreshCooldownActive(
        { refreshAvailable: false, nextRefreshAvailableAt: next },
        now,
      ),
    ).toBe(true);
    expect(formatRefreshCountdown(next, now)).toBe('1:00');
  });
});

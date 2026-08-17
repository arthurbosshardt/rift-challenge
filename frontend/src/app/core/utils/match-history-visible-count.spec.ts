import { describe, expect, it } from 'vitest';
import { resolveVisibleMatchHistoryCount } from './match-history-visible-count';

describe('match-history-visible-count', () => {
  it('returns zero when there is no history', () => {
    expect(resolveVisibleMatchHistoryCount(400, 4.75, 0)).toBe(0);
  });

  it('keeps at least one entry when space is tight', () => {
    expect(resolveVisibleMatchHistoryCount(40, 3.75, 6, 16, 0)).toBe(1);
  });

  it('fits as many entries as the track width allows', () => {
    expect(resolveVisibleMatchHistoryCount(640, 3.75, 12, 16, 0)).toBe(10);
    expect(resolveVisibleMatchHistoryCount(640, 3.75, 12, 16, 0)).toBeLessThan(12);
  });
});

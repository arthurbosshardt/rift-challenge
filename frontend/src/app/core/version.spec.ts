import { describe, expect, it } from 'vitest';
import { formatLastUpdatedDate } from './version';

describe('formatLastUpdatedDate', () => {
  it('formats a calendar date for French and English locales', () => {
    expect(formatLastUpdatedDate('2026-08-19', 'fr')).toBe('19/08/2026');
    expect(formatLastUpdatedDate('2026-08-19', 'en')).toBe('08/19/2026');
  });

  it('formats a date and time for French and English locales', () => {
    expect(formatLastUpdatedDate('2026-08-19T20:11', 'fr')).toBe('19/08/2026 à 20h11');
    expect(formatLastUpdatedDate('2026-08-19T20:11', 'en')).toBe('08/19/2026 at 20h11');
  });
});

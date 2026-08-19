import { describe, expect, it } from 'vitest';
import { formatLastUpdatedDate } from './version';

describe('formatLastUpdatedDate', () => {
  it('formats a calendar date for French and English locales', () => {
    expect(formatLastUpdatedDate('2026-08-19', 'fr')).toBe('19/08/2026');
    expect(formatLastUpdatedDate('2026-08-19', 'en')).toBe('08/19/2026');
  });
});

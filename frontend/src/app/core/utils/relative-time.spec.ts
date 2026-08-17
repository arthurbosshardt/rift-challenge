import { describe, expect, it } from 'vitest';
import { formatTimeSince } from './relative-time';

describe('relative-time', () => {
  const now = Date.parse('2026-08-17T14:00:00.000Z');

  it('formats recent times in French', () => {
    expect(formatTimeSince('2026-08-17T13:59:55.000Z', now, 'fr')).toBe('à l\'instant');
    expect(formatTimeSince('2026-08-17T13:59:30.000Z', now, 'fr')).toBe('il y a 30 s');
    expect(formatTimeSince('2026-08-17T13:55:00.000Z', now, 'fr')).toBe('il y a 5 min');
    expect(formatTimeSince('2026-08-17T11:00:00.000Z', now, 'fr')).toBe('il y a 3 h');
    expect(formatTimeSince('2026-08-15T14:00:00.000Z', now, 'fr')).toBe('il y a 2 j');
  });

  it('formats recent times in English', () => {
    expect(formatTimeSince('2026-08-17T13:59:55.000Z', now, 'en')).toBe('just now');
    expect(formatTimeSince('2026-08-17T13:55:00.000Z', now, 'en')).toBe('5 min ago');
  });
});

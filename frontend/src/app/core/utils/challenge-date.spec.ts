import { describe, expect, it } from 'vitest';
import {
  addDaysToIso,
  buildLocalStartAtIso,
  formatChallengeDateTime,
  splitLocalDateHour,
} from './challenge-date';

describe('challenge-date', () => {
  it('formats date as dd/MM/yyyy HHh without minutes', () => {
    const formatted = formatChallengeDateTime(new Date(2026, 7, 17, 14, 30, 0));
    expect(formatted).toBe('17/08/2026 à 14h');
  });

  it('formats English dates as MM/dd/yyyy at HHh', () => {
    const formatted = formatChallengeDateTime(new Date(2026, 7, 17, 14, 30, 0), 'en');
    expect(formatted).toBe('08/17/2026 at 14h');
  });

  it('builds ISO string from local date and hour', () => {
    const iso = buildLocalStartAtIso('2026-08-17', 14);
    expect(iso).not.toBeNull();
    expect(new Date(iso!).getHours()).toBe(14);
    expect(new Date(iso!).getMinutes()).toBe(0);
  });

  it('adds whole days to an ISO timestamp', () => {
    const iso = addDaysToIso('2026-08-17T12:00:00.000Z', 7);
    expect(iso).toBe('2026-08-24T12:00:00.000Z');
  });

  it('splits a local date and hour from ISO', () => {
    const parts = splitLocalDateHour(new Date(2026, 7, 17, 14, 0, 0).toISOString());
    expect(parts).toEqual({ date: '2026-08-17', hour: 14 });
  });
});

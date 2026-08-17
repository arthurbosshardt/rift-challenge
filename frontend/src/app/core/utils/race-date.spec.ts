import { describe, expect, it } from 'vitest';
import { buildLocalStartAtIso, formatRaceDateTime } from './race-date';

describe('race-date', () => {
  it('formats date as dd/MM/yyyy HHh without minutes', () => {
    const formatted = formatRaceDateTime(new Date(2026, 7, 17, 14, 30, 0));
    expect(formatted).toBe('17/08/2026 14h');
  });

  it('builds ISO string from local date and hour', () => {
    const iso = buildLocalStartAtIso('2026-08-17', 14);
    expect(iso).not.toBeNull();
    expect(new Date(iso!).getHours()).toBe(14);
    expect(new Date(iso!).getMinutes()).toBe(0);
  });
});

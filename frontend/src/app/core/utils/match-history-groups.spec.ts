import { describe, expect, it } from 'vitest';
import {
  formatMatchHistoryDayLabel,
  groupMatchHistoryByLocalDay,
  localDayKey,
} from './match-history-groups';

describe('match-history-groups', () => {
  it('groups entries by local day while preserving order', () => {
    const entries = [
      { playedAt: '2026-08-17T20:00:00', id: 'a' },
      { playedAt: '2026-08-17T08:00:00', id: 'b' },
      { playedAt: '2026-08-15T12:00:00', id: 'c' },
    ];

    const groups = groupMatchHistoryByLocalDay(entries);

    expect(groups).toHaveLength(2);
    expect(groups[0].entries.map((entry) => entry.id)).toEqual(['a', 'b']);
    expect(groups[1].entries.map((entry) => entry.id)).toEqual(['c']);
  });

  it('builds a stable local day key', () => {
    expect(localDayKey('2026-08-17T10:00:00.000Z')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('formats day labels for match history groups', () => {
    const now = new Date(2026, 7, 17, 12, 0, 0).getTime();
    expect(formatMatchHistoryDayLabel('2026-08-17', 'fr', now)).toBe("aujourd'hui");
    expect(formatMatchHistoryDayLabel('2026-08-16', 'fr', now)).toBe('il y a 1 jour');
    expect(formatMatchHistoryDayLabel('2026-08-14', 'fr', now)).toBe('il y a 3 jours');
    expect(formatMatchHistoryDayLabel('2026-08-16', 'en', now)).toBe('1 day ago');
  });
});

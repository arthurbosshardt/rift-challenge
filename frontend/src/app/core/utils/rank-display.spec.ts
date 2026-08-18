import { describe, expect, it } from 'vitest';
import { formatDurationCountdown, formatFinishedRankLabel, formatRankLabel, rankEmblemUrl, tierLabelFr } from './rank-display';

describe('rank-display', () => {
  it('maps tier to French label', () => {
    expect(tierLabelFr('GOLD')).toBe('Or');
    expect(tierLabelFr('EMERALD')).toBe('Émeraude');
  });

  it('builds rank emblem URL', () => {
    expect(rankEmblemUrl('CHALLENGER')).toContain('/medals_mini/challenger.png');
  });

  it('formats rank with division', () => {
    expect(formatRankLabel('GOLD', 'II', 75)).toBe('Or II · 75 LP');
    expect(formatRankLabel('GOLD', 'II', 75, 'en')).toBe('Gold II · 75 LP');
  });

  it('formats high tier without division', () => {
    expect(formatRankLabel('CHALLENGER', null, 1234)).toBe('Challenger · 1234 LP');
  });

  it('omits LP when includeLp is false', () => {
    expect(formatRankLabel('DIAMOND', 'IV', 0, 'fr', false)).toBe('Diamant IV');
    expect(formatRankLabel('CHALLENGER', null, 1234, 'en', false)).toBe('Challenger');
  });

  it('formats finished rank labels without LP', () => {
    expect(formatFinishedRankLabel('DIAMOND', 'IV', 42, 'fr')).toBe('a terminé Diamant IV');
    expect(formatFinishedRankLabel('DIAMOND', 'IV', 42, 'en')).toBe('finished at Diamond IV');
  });

  it('formats duration countdown with days', () => {
    const now = Date.parse('2026-08-16T10:00:00.000Z');
    const target = '2026-08-18T10:30:00.000Z';

    expect(formatDurationCountdown(target, now)).toBe('2j 0h 30m');
    expect(formatDurationCountdown(target, now, 'en')).toBe('2d 0h 30m');
  });
});

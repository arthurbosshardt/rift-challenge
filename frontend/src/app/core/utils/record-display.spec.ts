import { describe, expect, it } from 'vitest';
import { hasPlayedRecord, winRateLabel, winRateToneModifier } from './record-display';

describe('record-display', () => {
  it('formats win rate labels', () => {
    expect(winRateLabel(0.5, 5, 5)).toBe('50 %');
    expect(winRateLabel(0, 0, 0)).toBe('—');
  });

  it('detects played records', () => {
    expect(hasPlayedRecord(0, 0)).toBe(false);
    expect(hasPlayedRecord(1, 0)).toBe(true);
  });

  it('assigns win rate tone modifiers', () => {
    expect(winRateToneModifier(0.5, 5, 5)).toBe('positive');
    expect(winRateToneModifier(0.49, 4, 6)).toBe('negative');
    expect(winRateToneModifier(0, 0, 0)).toBe('neutral');
  });
});

import { describe, expect, it } from 'vitest';
import {
  raceCardPreviewGridColumn,
  raceCardPreviewGridRow,
  shouldUseRaceCardPreviewColumns,
} from './race-card-preview-grid';

describe('race-card-preview-grid', () => {
  it('enables columns only on wide viewports with 1 to 8 items', () => {
    expect(shouldUseRaceCardPreviewColumns(899, 6)).toBe(false);
    expect(shouldUseRaceCardPreviewColumns(900, 0)).toBe(false);
    expect(shouldUseRaceCardPreviewColumns(900, 1)).toBe(true);
    expect(shouldUseRaceCardPreviewColumns(900, 8)).toBe(true);
    expect(shouldUseRaceCardPreviewColumns(900, 9)).toBe(false);
  });

  it('keeps the top three entries in the first column', () => {
    expect(raceCardPreviewGridColumn(0, true)).toBe(1);
    expect(raceCardPreviewGridColumn(1, true)).toBe(1);
    expect(raceCardPreviewGridColumn(2, true)).toBe(1);
    expect(raceCardPreviewGridColumn(3, true)).toBe(2);
    expect(raceCardPreviewGridColumn(7, true)).toBe(2);
  });

  it('rows top three sequentially in column one and continues in column two', () => {
    expect(raceCardPreviewGridRow(0, true)).toBe(1);
    expect(raceCardPreviewGridRow(1, true)).toBe(2);
    expect(raceCardPreviewGridRow(2, true)).toBe(3);
    expect(raceCardPreviewGridRow(3, true)).toBe(1);
    expect(raceCardPreviewGridRow(4, true)).toBe(2);
    expect(raceCardPreviewGridRow(7, true)).toBe(5);
  });

  it('returns null placement when columns are disabled', () => {
    expect(raceCardPreviewGridColumn(0, false)).toBeNull();
    expect(raceCardPreviewGridRow(0, false)).toBeNull();
  });
});

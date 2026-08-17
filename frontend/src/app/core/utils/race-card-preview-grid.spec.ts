import { describe, expect, it } from 'vitest';
import {
  raceCardPreviewGridColumn,
  raceCardPreviewGridRow,
  resolveRaceCardPreviewColumnCount,
  shouldUseRaceCardPreviewColumns,
} from './race-card-preview-grid';

describe('race-card-preview-grid', () => {
  it('resolves column count from viewport and displayed items', () => {
    expect(resolveRaceCardPreviewColumnCount(899, 6)).toBe(1);
    expect(resolveRaceCardPreviewColumnCount(900, 0)).toBe(1);
    expect(resolveRaceCardPreviewColumnCount(900, 4)).toBe(2);
    expect(resolveRaceCardPreviewColumnCount(1279, 8)).toBe(2);
    expect(resolveRaceCardPreviewColumnCount(1280, 7)).toBe(3);
    expect(resolveRaceCardPreviewColumnCount(1280, 9)).toBe(3);
    expect(resolveRaceCardPreviewColumnCount(900, 10)).toBe(1);
  });

  it('enables columns when layout uses more than one column', () => {
    expect(shouldUseRaceCardPreviewColumns(900, 6)).toBe(true);
    expect(shouldUseRaceCardPreviewColumns(899, 6)).toBe(false);
  });

  it('keeps the top three entries in the first column with two columns', () => {
    expect(raceCardPreviewGridColumn(0, 2)).toBe(1);
    expect(raceCardPreviewGridColumn(2, 2)).toBe(1);
    expect(raceCardPreviewGridColumn(3, 2)).toBe(2);
    expect(raceCardPreviewGridRow(3, 2)).toBe(1);
    expect(raceCardPreviewGridRow(7, 2)).toBe(5);
  });

  it('spreads entries across three columns after the podium', () => {
    expect(raceCardPreviewGridColumn(5, 3)).toBe(2);
    expect(raceCardPreviewGridColumn(6, 3)).toBe(3);
    expect(raceCardPreviewGridRow(6, 3)).toBe(1);
    expect(raceCardPreviewGridRow(8, 3)).toBe(3);
  });

  it('returns null placement for single-column layouts', () => {
    expect(raceCardPreviewGridColumn(0, 1)).toBeNull();
    expect(raceCardPreviewGridRow(0, 1)).toBeNull();
  });
});

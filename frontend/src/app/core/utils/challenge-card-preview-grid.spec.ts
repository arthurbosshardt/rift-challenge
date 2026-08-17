import { describe, expect, it } from 'vitest';
import {
  groupChallengeCardPreviewItems,
  challengeCardPreviewGridColumn,
  resolveChallengeCardPreviewColumnCount,
  resolveChallengeCardPreviewVisibleCount,
} from './challenge-card-preview-grid';

describe('challenge-card-preview-grid', () => {
  it('limits visible items to the top three in narrow viewports', () => {
    expect(resolveChallengeCardPreviewVisibleCount(420, 8)).toBe(3);
    expect(resolveChallengeCardPreviewVisibleCount(300, 2)).toBe(2);
    expect(resolveChallengeCardPreviewVisibleCount(500, 8)).toBe(8);
    expect(resolveChallengeCardPreviewVisibleCount(720, 8, 760)).toBe(3);
    expect(resolveChallengeCardPreviewVisibleCount(720, 8, 761)).toBe(8);
  });

  it('resolves column count from preview viewport width', () => {
    expect(resolveChallengeCardPreviewColumnCount(420, 6)).toBe(1);
    expect(resolveChallengeCardPreviewColumnCount(421, 0)).toBe(1);
    expect(resolveChallengeCardPreviewColumnCount(500, 4)).toBe(2);
    expect(resolveChallengeCardPreviewColumnCount(679, 8)).toBe(2);
    expect(resolveChallengeCardPreviewColumnCount(680, 7)).toBe(3);
    expect(resolveChallengeCardPreviewColumnCount(680, 9)).toBe(3);
    expect(resolveChallengeCardPreviewColumnCount(500, 10)).toBe(1);
    expect(resolveChallengeCardPreviewColumnCount(720, 8, 760)).toBe(1);
    expect(resolveChallengeCardPreviewColumnCount(720, 8, 761)).toBe(2);
  });

  it('keeps the top three entries in the first column', () => {
    expect(challengeCardPreviewGridColumn(0, 2)).toBe(1);
    expect(challengeCardPreviewGridColumn(2, 2)).toBe(1);
    expect(challengeCardPreviewGridColumn(3, 2)).toBe(2);
    expect(challengeCardPreviewGridColumn(5, 3)).toBe(2);
    expect(challengeCardPreviewGridColumn(6, 3)).toBe(3);
  });

  it('groups items into vertical columns', () => {
    expect(groupChallengeCardPreviewItems(['a', 'b', 'c', 'd', 'e'], 2)).toEqual([
      ['a', 'b', 'c'],
      ['d', 'e'],
    ]);
    expect(groupChallengeCardPreviewItems(['a', 'b', 'c', 'd', 'e', 'f', 'g'], 3)).toEqual([
      ['a', 'b', 'c'],
      ['d', 'e', 'f'],
      ['g'],
    ]);
  });
});

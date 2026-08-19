import { describe, expect, it } from 'vitest';
import {
  groupChallengeCardPreviewItems,
  challengeCardPreviewGridColumn,
  resolveChallengeCardPreviewColumnCount,
  resolveChallengeCardPreviewVisibleCount,
} from './challenge-card-preview-grid';

describe('challenge-card-preview-grid', () => {
  it('limits visible items when a single column would be too narrow to read', () => {
    expect(resolveChallengeCardPreviewVisibleCount(279, 8)).toBe(3);
    expect(resolveChallengeCardPreviewVisibleCount(100, 2)).toBe(2);
    expect(resolveChallengeCardPreviewVisibleCount(280, 8)).toBe(8);
    expect(resolveChallengeCardPreviewVisibleCount(500, 8)).toBe(8);
    expect(resolveChallengeCardPreviewVisibleCount(1200, 8)).toBe(8);
  });

  it('resolves column count from the actual preview width, not a fixed item count', () => {
    expect(resolveChallengeCardPreviewColumnCount(279, 6)).toBe(1);
    expect(resolveChallengeCardPreviewColumnCount(280, 6)).toBe(1);
    expect(resolveChallengeCardPreviewColumnCount(560, 3)).toBe(1);
    expect(resolveChallengeCardPreviewColumnCount(560, 6)).toBe(2);
    expect(resolveChallengeCardPreviewColumnCount(840, 6)).toBe(2);
    expect(resolveChallengeCardPreviewColumnCount(840, 7)).toBe(3);
    expect(resolveChallengeCardPreviewColumnCount(500, 10)).toBe(1);
    expect(resolveChallengeCardPreviewColumnCount(1200, 0)).toBe(1);
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

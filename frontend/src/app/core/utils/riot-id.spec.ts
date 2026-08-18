import { describe, expect, it } from 'vitest';
import {
  normalizeGameName,
  normalizeTagLine,
  normalizeRiotId,
  parseRiotId,
  buildRiotId,
} from './riot-id';

describe('Riot ID Utilities', () => {
  describe('normalizeGameName', () => {
    it('removes all whitespace', () => {
      expect(normalizeGameName('Game Name')).toBe('GameName');
      expect(normalizeGameName('Game  Name')).toBe('GameName');
      expect(normalizeGameName(' GameName ')).toBe('GameName');
    });

    it('preserves non-whitespace characters', () => {
      expect(normalizeGameName('JohnDoe123')).toBe('JohnDoe123');
    });
  });

  describe('normalizeTagLine', () => {
    it('removes leading hash symbols', () => {
      expect(normalizeTagLine('##NA1')).toBe('NA1');
      expect(normalizeTagLine('#NA1')).toBe('NA1');
    });

    it('handles non-breaking spaces', () => {
      expect(normalizeTagLine('\u00A0NA1')).toBe('NA1');
    });

    it('trims regular spaces', () => {
      expect(normalizeTagLine(' NA1 ')).toBe('NA1');
    });

    it('combines all normalizations', () => {
      expect(normalizeTagLine('\u00A0 ##NA1 ')).toBe('NA1');
    });
  });

  describe('normalizeRiotId', () => {
    it('normalizes valid riot IDs', () => {
      expect(normalizeRiotId('JohnDoe#NA1')).toBe('JohnDoe#NA1');
    });

    it('removes spaces from game name', () => {
      expect(normalizeRiotId('John Doe#NA1')).toBe('JohnDoe#NA1');
    });

    it('normalizes tag line', () => {
      expect(normalizeRiotId('JohnDoe##NA1')).toBe('JohnDoe#NA1');
    });

    it('returns original if hash is missing', () => {
      const input = 'JohnDoeNA1';
      expect(normalizeRiotId(input)).toBe(input);
    });

    it('returns original if hash is at start', () => {
      const input = '#NA1';
      expect(normalizeRiotId(input)).toBe(input);
    });

    it('returns original if hash is at end', () => {
      const input = 'JohnDoe#';
      expect(normalizeRiotId(input)).toBe(input);
    });
  });

  describe('parseRiotId', () => {
    it('parses valid riot ID', () => {
      const result = parseRiotId('JohnDoe#NA1');
      expect(result).toEqual({ gameName: 'JohnDoe', tagLine: 'NA1' });
    });

    it('parses with spaces (normalizes first)', () => {
      const result = parseRiotId('John Doe#NA1');
      expect(result).toEqual({ gameName: 'JohnDoe', tagLine: 'NA1' });
    });

    it('returns null for missing hash', () => {
      expect(parseRiotId('JohnDoeNA1')).toBeNull();
    });

    it('returns null for hash at start', () => {
      expect(parseRiotId('#NA1')).toBeNull();
    });

    it('returns null for hash at end', () => {
      expect(parseRiotId('JohnDoe#')).toBeNull();
    });

    it('returns null for empty gameName after normalization', () => {
      expect(parseRiotId('#')).toBeNull();
    });
  });

  describe('buildRiotId', () => {
    it('builds valid riot ID', () => {
      expect(buildRiotId('JohnDoe', 'NA1')).toBe('JohnDoe#NA1');
    });

    it('normalizes game name', () => {
      expect(buildRiotId('John Doe', 'NA1')).toBe('JohnDoe#NA1');
    });

    it('normalizes tag line', () => {
      expect(buildRiotId('JohnDoe', '#NA1')).toBe('JohnDoe#NA1');
    });

    it('returns null if game name is empty after normalization', () => {
      expect(buildRiotId('', 'NA1')).toBeNull();
    });

    it('returns null if tag line is empty after normalization', () => {
      expect(buildRiotId('JohnDoe', '')).toBeNull();
    });

    it('returns null if both are empty', () => {
      expect(buildRiotId('', '')).toBeNull();
    });
  });
});

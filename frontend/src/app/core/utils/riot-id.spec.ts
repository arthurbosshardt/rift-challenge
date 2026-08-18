import { describe, expect, it } from 'vitest';
import { buildRiotId, normalizeGameName, normalizeRiotId, normalizeTagLine, parseRiotId } from './riot-id';

describe('riot-id utils', () => {
  it('buildRiotId combines name and tag', () => {
    expect(buildRiotId('JohnDoe', 'EUW')).toBe('JohnDoe#EUW');
  });

  it('buildRiotId removes all spaces from game name and trims tag', () => {
    expect(buildRiotId('  JohnDoe  ', ' EUW ')).toBe('JohnDoe#EUW');
    expect(buildRiotId('Hide on bush', 'EUW1')).toBe('Hideonbush#EUW1');
    expect(buildRiotId('\u00A0JohnDoe\u00A0', 'EUW')).toBe('JohnDoe#EUW');
  });

  it('normalizeRiotId strips spaces from game name', () => {
    expect(normalizeRiotId('  JohnDoe # EUW ')).toBe('JohnDoe#EUW');
    expect(normalizeRiotId('Hide on bush#EUW1')).toBe('Hideonbush#EUW1');
  });

  it('normalizeGameName removes all whitespace', () => {
    expect(normalizeGameName('  Hide on bush  ')).toBe('Hideonbush');
  });

  it('buildRiotId strips leading hash from tag', () => {
    expect(buildRiotId('JohnDoe', '#EUW')).toBe('JohnDoe#EUW');
  });

  it('buildRiotId returns null when incomplete', () => {
    expect(buildRiotId('', 'EUW')).toBeNull();
    expect(buildRiotId('JohnDoe', '')).toBeNull();
  });

  it('normalizeTagLine removes hash prefix', () => {
    expect(normalizeTagLine('  #EUW1  ')).toBe('EUW1');
  });

  it('parseRiotId splits a normalized riot id', () => {
    expect(parseRiotId('  JohnDoe # EUW ')).toEqual({ gameName: 'JohnDoe', tagLine: 'EUW' });
    expect(parseRiotId('Hide on bush#EUW1')).toEqual({ gameName: 'Hideonbush', tagLine: 'EUW1' });
  });

  it('parseRiotId returns null when format is invalid', () => {
    expect(parseRiotId('JohnDoe')).toBeNull();
    expect(parseRiotId('#EUW')).toBeNull();
    expect(parseRiotId('JohnDoe#')).toBeNull();
  });
});

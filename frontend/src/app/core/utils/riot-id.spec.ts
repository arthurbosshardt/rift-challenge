import { describe, expect, it } from 'vitest';
import { buildRiotId, normalizeGameName, normalizeRiotId, normalizeTagLine } from './riot-id';

describe('riot-id utils', () => {
  it('buildRiotId combines name and tag', () => {
    expect(buildRiotId('Tanor', '7154')).toBe('Tanor#7154');
  });

  it('buildRiotId removes all spaces from game name and trims tag', () => {
    expect(buildRiotId('  Tanor  ', ' 7154 ')).toBe('Tanor#7154');
    expect(buildRiotId('Hide on bush', 'EUW1')).toBe('Hideonbush#EUW1');
    expect(buildRiotId('\u00A0Tanor\u00A0', '7154')).toBe('Tanor#7154');
  });

  it('normalizeRiotId strips spaces from game name', () => {
    expect(normalizeRiotId('  Tanor # 7154 ')).toBe('Tanor#7154');
    expect(normalizeRiotId('Hide on bush#EUW1')).toBe('Hideonbush#EUW1');
  });

  it('normalizeGameName removes all whitespace', () => {
    expect(normalizeGameName('  Hide on bush  ')).toBe('Hideonbush');
  });

  it('buildRiotId strips leading hash from tag', () => {
    expect(buildRiotId('Tanor', '#7154')).toBe('Tanor#7154');
  });

  it('buildRiotId returns null when incomplete', () => {
    expect(buildRiotId('', '7154')).toBeNull();
    expect(buildRiotId('Tanor', '')).toBeNull();
  });

  it('normalizeTagLine removes hash prefix', () => {
    expect(normalizeTagLine('  #EUW1  ')).toBe('EUW1');
  });
});

import { describe, expect, it } from 'vitest';
import { buildRiotId, normalizeTagLine } from './riot-id';

describe('riot-id utils', () => {
  it('buildRiotId combines name and tag', () => {
    expect(buildRiotId('Tanor', '7154')).toBe('Tanor#7154');
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

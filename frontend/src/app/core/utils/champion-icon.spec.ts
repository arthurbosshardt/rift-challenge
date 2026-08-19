import { describe, expect, it } from 'vitest';
import { championIconUrls, championIconUrl } from './champion-icon';

describe('Champion Icon Utilities', () => {
  it('returns empty array for null championId', () => {
    expect(championIconUrls(null)).toEqual([]);
  });

  it('returns empty array for undefined championId', () => {
    expect(championIconUrls(undefined)).toEqual([]);
  });

  it('returns empty array for zero championId', () => {
    expect(championIconUrls(0)).toEqual([]);
  });

  it('returns empty array for negative championId', () => {
    expect(championIconUrls(-1)).toEqual([]);
  });

  it('returns URLs for valid championId', () => {
    const urls = championIconUrls(1);
    expect(urls).toHaveLength(2);
    expect(urls[0]).toContain('raw.communitydragon.org');
    expect(urls[0]).toContain('/1.png');
    expect(urls[1]).toContain('cdn.communitydragon.org');
    expect(urls[1]).toContain('/1.png');
  });

  it('returns first URL from championIconUrl for valid championId', () => {
    const url = championIconUrl(157);
    expect(url).toContain('raw.communitydragon.org');
    expect(url).toContain('/157.png');
  });

  it('returns null from championIconUrl for null championId', () => {
    expect(championIconUrl(null)).toBeNull();
  });

  it('returns null from championIconUrl for undefined championId', () => {
    expect(championIconUrl(undefined)).toBeNull();
  });

  it('returns null from championIconUrl for zero championId', () => {
    expect(championIconUrl(0)).toBeNull();
  });
});

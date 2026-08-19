import { describe, expect, it } from 'vitest';
import { profileIconUrls, profileIconUrl, profileIconInitial } from './profile-icon';

describe('Profile Icon Utilities', () => {
  it('returns empty array for null profileIconId', () => {
    expect(profileIconUrls(null)).toEqual([]);
  });

  it('returns empty array for undefined profileIconId', () => {
    expect(profileIconUrls(undefined)).toEqual([]);
  });

  it('returns URLs for valid profileIconId', () => {
    const urls = profileIconUrls(1);
    expect(urls).toHaveLength(2);
    expect(urls[0]).toContain('ddragon.leagueoflegends.com');
    expect(urls[0]).toContain('/1.png');
    expect(urls[1]).toContain('raw.communitydragon.net');
    expect(urls[1]).toContain('/1.jpg');
  });

  it('returns first URL from profileIconUrl for valid profileIconId', () => {
    const url = profileIconUrl(5050);
    expect(url).toContain('ddragon.leagueoflegends.com');
    expect(url).toContain('/5050.png');
  });

  it('returns null from profileIconUrl for null profileIconId', () => {
    expect(profileIconUrl(null)).toBeNull();
  });

  it('returns null from profileIconUrl for undefined profileIconId', () => {
    expect(profileIconUrl(undefined)).toBeNull();
  });

  it('returns first character uppercase for profileIconInitial with valid gameName', () => {
    expect(profileIconInitial('summoner')).toBe('S');
    expect(profileIconInitial('JohnDoe')).toBe('J');
  });

  it('returns question mark for null gameName', () => {
    expect(profileIconInitial(null)).toBe('?');
  });

  it('returns question mark for undefined gameName', () => {
    expect(profileIconInitial(undefined)).toBe('?');
  });

  it('returns question mark for empty gameName', () => {
    expect(profileIconInitial('')).toBe('?');
  });
});

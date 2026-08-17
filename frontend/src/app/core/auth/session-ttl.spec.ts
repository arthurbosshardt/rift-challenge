import { describe, expect, it } from 'vitest';
import { isSessionExpired, parseLastSeen, SESSION_TTL_MS } from './session-ttl';

describe('session-ttl', () => {
  it('keeps a session without last-seen timestamp (first load after deploy)', () => {
    expect(isSessionExpired(null, Date.now())).toBe(false);
  });

  it('keeps a session seen 10 minutes ago', () => {
    const now = 1_700_000_000_000;
    expect(isSessionExpired(now - 10 * 60 * 1000, now, SESSION_TTL_MS)).toBe(false);
  });

  it('expires a session seen more than one hour ago', () => {
    const now = 1_700_000_000_000;
    expect(isSessionExpired(now - SESSION_TTL_MS - 1, now, SESSION_TTL_MS)).toBe(true);
  });

  it('parses stored timestamps', () => {
    expect(parseLastSeen('1700000000000')).toBe(1_700_000_000_000);
    expect(parseLastSeen('nope')).toBeNull();
    expect(parseLastSeen(null)).toBeNull();
  });
});

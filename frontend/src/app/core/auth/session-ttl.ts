/** Durée pendant laquelle la session survit à la fermeture du navigateur. */
export const SESSION_TTL_MS = 60 * 60 * 1000;

export const SESSION_LAST_SEEN_KEY = 'riftrace.session.lastSeen';

export function isSessionExpired(
  lastSeenMs: number | null,
  nowMs: number,
  ttlMs: number = SESSION_TTL_MS,
): boolean {
  if (lastSeenMs == null) {
    return false;
  }
  return nowMs - lastSeenMs > ttlMs;
}

export function parseLastSeen(raw: string | null): number | null {
  if (!raw) {
    return null;
  }
  const value = Number(raw);
  return Number.isFinite(value) ? value : null;
}

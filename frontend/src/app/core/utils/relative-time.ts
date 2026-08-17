export function formatTimeSince(
  pastIso: string,
  nowMs = Date.now(),
  locale: 'fr' | 'en' = 'fr',
): string | null {
  const pastMs = new Date(pastIso).getTime();
  if (Number.isNaN(pastMs)) {
    return null;
  }

  const diffMs = Math.max(0, nowMs - pastMs);
  const seconds = Math.floor(diffMs / 1000);

  if (seconds < 10) {
    return locale === 'en' ? 'just now' : 'à l\'instant';
  }
  if (seconds < 60) {
    return locale === 'en' ? `${seconds}s ago` : `il y a ${seconds} s`;
  }

  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return locale === 'en' ? `${minutes} min ago` : `il y a ${minutes} min`;
  }

  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return locale === 'en' ? `${hours} h ago` : `il y a ${hours} h`;
  }

  const days = Math.floor(hours / 24);
  return locale === 'en' ? `${days} d ago` : `il y a ${days} j`;
}

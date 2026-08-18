export interface MatchHistoryDayGroup<T> {
  dayKey: string;
  entries: T[];
}

export function localDayKey(playedAt: string): string {
  const date = new Date(playedAt);
  const year = date.getFullYear();
  const month = (date.getMonth() + 1).toString().padStart(2, '0');
  const day = date.getDate().toString().padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function formatAbsoluteMatchHistoryDayLabel(dayKey: string, locale: 'fr' | 'en'): string {
  const [year, month, day] = dayKey.split('-').map(Number);
  if (!year || !month || !day) {
    return dayKey;
  }

  const dd = day.toString().padStart(2, '0');
  const mm = month.toString().padStart(2, '0');

  if (locale === 'fr') {
    return `${dd}/${mm}/${year}`;
  }

  return `${mm}/${dd}/${year}`;
}

export function formatMatchHistoryDayLabel(
  dayKey: string,
  locale: 'fr' | 'en',
  nowMs = Date.now(),
): string {
  const [year, month, day] = dayKey.split('-').map(Number);
  if (!year || !month || !day) {
    return dayKey;
  }

  const groupStart = new Date(year, month - 1, day).getTime();
  const now = new Date(nowMs);
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const diffDays = Math.max(0, Math.round((todayStart - groupStart) / 86_400_000));

  if (diffDays > 30) {
    return formatAbsoluteMatchHistoryDayLabel(dayKey, locale);
  }

  if (diffDays === 0) {
    return locale === 'fr' ? "aujourd'hui" : 'today';
  }
  if (diffDays === 1) {
    return locale === 'fr' ? 'il y a 1 jour' : '1 day ago';
  }
  return locale === 'fr' ? `il y a ${diffDays} jours` : `${diffDays} days ago`;
}

export function groupMatchHistoryByLocalDay<T extends { playedAt: string }>(
  entries: readonly T[],
): MatchHistoryDayGroup<T>[] {
  const groups: MatchHistoryDayGroup<T>[] = [];
  let currentKey: string | null = null;
  let currentEntries: T[] = [];

  for (const entry of entries) {
    const dayKey = localDayKey(entry.playedAt);
    if (dayKey !== currentKey) {
      if (currentEntries.length > 0 && currentKey) {
        groups.push({ dayKey: currentKey, entries: currentEntries });
      }
      currentKey = dayKey;
      currentEntries = [entry];
      continue;
    }
    currentEntries.push(entry);
  }

  if (currentEntries.length > 0 && currentKey) {
    groups.push({ dayKey: currentKey, entries: currentEntries });
  }

  return groups;
}

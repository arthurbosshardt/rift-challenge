export function formatChallengeDateTime(
  value: string | Date | null | undefined,
  locale: 'fr' | 'en' = 'fr',
): string {
  if (!value) {
    return '—';
  }

  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }

  const day = date.getDate().toString().padStart(2, '0');
  const month = (date.getMonth() + 1).toString().padStart(2, '0');
  const year = date.getFullYear();
  const hour = date.getHours();

  if (locale === 'en') {
    return `${month}/${day}/${year} at ${hour}h`;
  }
  return `${day}/${month}/${year} à ${hour}h`;
}

export function formatChallengeDateCompact(
  value: string | Date | null | undefined,
  locale: 'fr' | 'en' = 'fr',
): string {
  if (!value) {
    return '—';
  }

  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }

  const day = date.getDate().toString().padStart(2, '0');
  const month = (date.getMonth() + 1).toString().padStart(2, '0');
  const hour = date.getHours();

  if (locale === 'en') {
    return `${month}/${day} at ${hour}h`;
  }
  return `${day}/${month} à ${hour}h`;
}

export function buildLocalStartAtIso(dateValue: string, hour: number): string | null {
  if (!dateValue || hour < 0 || hour > 23) {
    return null;
  }

  const [year, month, day] = dateValue.split('-').map(Number);
  if (!year || !month || !day) {
    return null;
  }

  const date = new Date(year, month - 1, day, hour, 0, 0, 0);
  if (Number.isNaN(date.getTime())) {
    return null;
  }

  return date.toISOString();
}

export function addDaysToIso(iso: string, days: number): string | null {
  const wholeDays = Number(days);
  if (!Number.isInteger(wholeDays) || wholeDays < 1) {
    return null;
  }

  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return null;
  }

  date.setDate(date.getDate() + wholeDays);
  return date.toISOString();
}

export function splitLocalDateHour(iso: string | null | undefined): { date: string; hour: number } | null {
  if (!iso) {
    return null;
  }

  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return null;
  }

  const year = date.getFullYear();
  const month = (date.getMonth() + 1).toString().padStart(2, '0');
  const day = date.getDate().toString().padStart(2, '0');
  return { date: `${year}-${month}-${day}`, hour: date.getHours() };
}

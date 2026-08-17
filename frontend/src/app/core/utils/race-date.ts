export function formatRaceDateTime(
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
    return `${month}/${day}/${year} ${hour}h`;
  }
  return `${day}/${month}/${year} ${hour}h`;
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

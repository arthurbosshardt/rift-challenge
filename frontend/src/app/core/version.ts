/** Calendar date of the last production update (YYYY-MM-DD). */
export const LAST_UPDATED_AT = '2026-08-19';

export function formatLastUpdatedDate(isoDate: string, locale: 'fr' | 'en'): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  if (!year || !month || !day) {
    return isoDate;
  }

  const dd = day.toString().padStart(2, '0');
  const mm = month.toString().padStart(2, '0');
  return locale === 'fr' ? `${dd}/${mm}/${year}` : `${mm}/${dd}/${year}`;
}

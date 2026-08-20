export { LAST_UPDATED_AT } from './last-updated.generated';

export function formatLastUpdatedDate(isoDateTime: string, locale: 'fr' | 'en'): string {
  const [datePart, timePart] = isoDateTime.split('T');
  const [year, month, day] = datePart.split('-').map(Number);
  if (!year || !month || !day) {
    return isoDateTime;
  }

  const dd = day.toString().padStart(2, '0');
  const mm = month.toString().padStart(2, '0');
  const date = locale === 'fr' ? `${dd}/${mm}/${year}` : `${mm}/${dd}/${year}`;

  if (!timePart) {
    return date;
  }
  const [hours, minutes] = timePart.split(':');
  const time = `${hours}h${minutes}`;
  return locale === 'fr' ? `${date} à ${time}` : `${date} at ${time}`;
}

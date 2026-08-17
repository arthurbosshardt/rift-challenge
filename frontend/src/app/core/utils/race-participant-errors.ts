import { HttpErrorResponse } from '@angular/common/http';
import { I18nService } from '../i18n/i18n.service';

export function mapParticipantError(err: HttpErrorResponse, i18n: I18nService): string {
  const message = typeof err.error?.message === 'string' ? err.error.message : '';

  if (err.status === 404 && message.includes('Riot account')) {
    return i18n.t('errors.riotNotFound');
  }
  if (err.status === 409) {
    return i18n.t('errors.alreadyAdded');
  }
  if (err.status === 400 && message.includes('Duo limit')) {
    return i18n.t('errors.duoLimit');
  }
  if (err.status === 400 && message.includes('Participant limit')) {
    return i18n.t('errors.participantLimit');
  }
  if (err.status === 400 && message.includes('duo endpoint')) {
    return i18n.t('errors.useDuoEndpoint');
  }
  if (err.status === 400 && message.includes('two different players')) {
    return i18n.t('errors.duoDifferentPlayers');
  }
  if (err.status === 400 && message.includes('gameName#tagLine')) {
    return i18n.t('errors.riotIdFormat');
  }
  if (err.status === 429) {
    return i18n.t('errors.riotRateLimit');
  }
  if (err.status === 502 || err.status === 503 || message.includes('Riot API')) {
    return i18n.t('errors.riotUnavailable');
  }
  if (err.status === 401) {
    return i18n.t('errors.authRequired');
  }

  return i18n.t('errors.addParticipant');
}

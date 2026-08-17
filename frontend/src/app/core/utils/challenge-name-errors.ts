import { HttpErrorResponse } from '@angular/common/http';
import { I18nService } from '../i18n/i18n.service';

export function isChallengeNameTakenError(err: HttpErrorResponse): boolean {
  const message = typeof err.error?.message === 'string' ? err.error.message : '';
  return err.status === 409 && /challenge name already taken|challenge name already taken/i.test(message);
}

export function mapChallengeNameError(err: HttpErrorResponse, i18n: I18nService): string {
  if (isChallengeNameTakenError(err)) {
    return i18n.t('errors.challengeNameTaken');
  }

  return i18n.t('errors.challengeNameUnavailable');
}

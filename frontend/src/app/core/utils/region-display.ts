import { ChallengeRegion } from '../models/challenge.models';
import { I18nService } from '../i18n/i18n.service';

export const CHALLENGE_REGIONS: ChallengeRegion[] = ['EUW', 'EUNE', 'NA', 'KR'];

export function regionLabel(region: ChallengeRegion, i18n: I18nService): string {
  switch (region) {
    case 'EUNE':
      return i18n.t('challenge.regionEune');
    case 'NA':
      return i18n.t('challenge.regionNa');
    case 'KR':
      return i18n.t('challenge.regionKr');
    default:
      return i18n.t('challenge.regionEuw');
  }
}

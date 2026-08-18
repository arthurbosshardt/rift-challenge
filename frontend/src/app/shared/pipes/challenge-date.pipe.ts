import { Pipe, PipeTransform, inject } from '@angular/core';
import { formatChallengeDateCompact, formatChallengeDateTime } from '../../core/utils/challenge-date';
import { I18nService } from '../../core/i18n/i18n.service';

@Pipe({
  name: 'challengeDate',
  pure: false,
})
export class ChallengeDatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(value: string | Date | null | undefined, format: 'full' | 'compact' = 'full'): string {
    const locale = this.i18n.locale();
    return format === 'compact'
        ? formatChallengeDateCompact(value, locale)
        : formatChallengeDateTime(value, locale);
  }
}

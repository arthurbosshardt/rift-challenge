import { Pipe, PipeTransform, inject } from '@angular/core';
import { formatChallengeDateTime } from '../../core/utils/challenge-date';
import { I18nService } from '../../core/i18n/i18n.service';

@Pipe({
  name: 'challengeDate',
  pure: false,
})
export class ChallengeDatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(value: string | Date | null | undefined): string {
    this.i18n.locale();
    return formatChallengeDateTime(value, this.i18n.locale());
  }
}

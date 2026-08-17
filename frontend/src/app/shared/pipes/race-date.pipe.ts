import { Pipe, PipeTransform, inject } from '@angular/core';
import { formatRaceDateTime } from '../../core/utils/race-date';
import { I18nService } from '../../core/i18n/i18n.service';

@Pipe({
  name: 'raceDate',
  pure: false,
})
export class RaceDatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(value: string | Date | null | undefined): string {
    this.i18n.locale();
    return formatRaceDateTime(value, this.i18n.locale());
  }
}

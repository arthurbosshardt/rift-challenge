import { Pipe, PipeTransform } from '@angular/core';
import { formatRaceDateTime } from '../../core/utils/race-date';

@Pipe({
  name: 'raceDate',
})
export class RaceDatePipe implements PipeTransform {
  transform(value: string | Date | null | undefined): string {
    return formatRaceDateTime(value);
  }
}

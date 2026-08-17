import { Component, inject, input, ChangeDetectionStrategy } from '@angular/core';
import { RouterLink } from '@angular/router';
import { RaceSummary } from '../../../core/models/race.models';
import { RaceDatePipe } from '../../pipes/race-date.pipe';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';

@Component({
  selector: 'app-race-card',
  imports: [RouterLink, RaceDatePipe, TranslatePipe],
  templateUrl: './race-card.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './race-card.component.scss',
})
export class RaceCardComponent {
  readonly race = input.required<RaceSummary>();
  private readonly i18n = inject(I18nService);

  statusLabel(status: RaceSummary['status']): string {
    if (status === 'NOT_STARTED') {
      return this.i18n.t('race.statusNotStarted');
    }
    if (status === 'FINISHED') {
      return this.i18n.t('race.statusFinished');
    }
    return this.i18n.t('race.statusActive');
  }

  typeLabel(type: RaceSummary['type']): string {
    return type === 'SOLOQ' ? 'SoloQ' : 'DuoQ';
  }
}

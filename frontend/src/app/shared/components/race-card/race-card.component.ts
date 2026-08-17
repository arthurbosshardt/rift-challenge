import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { RaceSummary } from '../../../core/models/race.models';
import { RaceDatePipe } from '../../pipes/race-date.pipe';

@Component({
  selector: 'app-race-card',
  imports: [RouterLink, RaceDatePipe],
  templateUrl: './race-card.component.html',
  styleUrl: './race-card.component.scss',
})
export class RaceCardComponent {
  readonly race = input.required<RaceSummary>();

  statusLabel(status: RaceSummary['status']): string {
    return status === 'NOT_STARTED' ? 'Pas encore commencée' : 'En cours';
  }

  typeLabel(type: RaceSummary['type']): string {
    return type === 'SOLOQ' ? 'SoloQ' : 'DuoQ';
  }
}

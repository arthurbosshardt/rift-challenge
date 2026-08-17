import { Component, input, ChangeDetectionStrategy } from '@angular/core';
import { PlayerAvatarComponent } from '../player-avatar/player-avatar.component';

@Component({
  selector: 'app-player-identity',
  imports: [PlayerAvatarComponent],
  templateUrl: './player-identity.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './player-identity.component.scss',
})
export class PlayerIdentityComponent {
  readonly gameName = input.required<string>();
  readonly tagLine = input.required<string>();
  readonly riotId = input.required<string>();
  readonly profileIconId = input<number | null>(null);
  readonly tier = input<string | null>(null);
  readonly rankLabel = input<string | null>(null);
  readonly compact = input(false);
}

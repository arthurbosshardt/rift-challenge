import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { LeaderboardSkeletonComponent } from '../leaderboard-skeleton/leaderboard-skeleton.component';
import { LeaderboardSortControlsComponent } from '../leaderboard-sort-controls/leaderboard-sort-controls.component';

@Component({
  selector: 'app-challenge-detail-skeleton',
  imports: [LeaderboardSkeletonComponent, LeaderboardSortControlsComponent],
  templateUrl: './challenge-detail-skeleton.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './challenge-detail-skeleton.component.scss',
})
export class ChallengeDetailSkeletonComponent {
  readonly ariaLabel = input('Loading');
  readonly rowCount = input(4);
}

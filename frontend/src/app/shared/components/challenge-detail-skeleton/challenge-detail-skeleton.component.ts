import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { LeaderboardSkeletonComponent } from '../leaderboard-skeleton/leaderboard-skeleton.component';
import { SkeletonComponent } from '../skeleton/skeleton.component';

@Component({
  selector: 'app-challenge-detail-skeleton',
  imports: [LeaderboardSkeletonComponent, SkeletonComponent],
  templateUrl: './challenge-detail-skeleton.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './challenge-detail-skeleton.component.scss',
})
export class ChallengeDetailSkeletonComponent {
  readonly ariaLabel = input('Loading');
  readonly rowCount = input(4);
}

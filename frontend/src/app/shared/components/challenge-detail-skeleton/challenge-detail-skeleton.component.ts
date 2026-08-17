import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { SkeletonComponent } from '../skeleton/skeleton.component';
import { LeaderboardSkeletonComponent } from '../leaderboard-skeleton/leaderboard-skeleton.component';

@Component({
  selector: 'app-challenge-detail-skeleton',
  imports: [SkeletonComponent, LeaderboardSkeletonComponent],
  templateUrl: './challenge-detail-skeleton.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './challenge-detail-skeleton.component.scss',
})
export class ChallengeDetailSkeletonComponent {
  readonly ariaLabel = input('Loading');
}

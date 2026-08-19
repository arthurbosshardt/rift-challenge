import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { SkeletonComponent } from '../skeleton/skeleton.component';
import { MatchHistorySkeletonComponent } from '../match-history-skeleton/match-history-skeleton.component';

@Component({
  selector: 'app-leaderboard-skeleton',
  imports: [SkeletonComponent, MatchHistorySkeletonComponent],
  templateUrl: './leaderboard-skeleton.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './leaderboard-skeleton.component.scss',
})
export class LeaderboardSkeletonComponent {
  readonly rowCount = input(6);
  readonly showSort = input(false);
  readonly duoMode = input(false);

  protected readonly rows = computed(() =>
    Array.from({ length: this.rowCount() }, (_, index) => index),
  );

  protected readonly duoSlots = [0, 1];
}

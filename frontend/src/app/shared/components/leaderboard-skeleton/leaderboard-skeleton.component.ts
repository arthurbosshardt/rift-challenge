import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { SkeletonComponent } from '../skeleton/skeleton.component';

@Component({
  selector: 'app-leaderboard-skeleton',
  imports: [SkeletonComponent],
  templateUrl: './leaderboard-skeleton.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './leaderboard-skeleton.component.scss',
})
export class LeaderboardSkeletonComponent {
  readonly rowCount = input(6);

  protected readonly rows = computed(() =>
    Array.from({ length: this.rowCount() }, (_, index) => index),
  );
}

import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ChallengeCardSkeletonComponent } from '../challenge-card-skeleton/challenge-card-skeleton.component';

@Component({
  selector: 'app-challenge-list-skeleton',
  imports: [ChallengeCardSkeletonComponent],
  templateUrl: './challenge-list-skeleton.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './challenge-list-skeleton.component.scss',
})
export class ChallengeListSkeletonComponent {
  readonly count = input(3);
  readonly summaryOnly = input(false);
  readonly ariaLabel = input('Loading');

  protected readonly items = computed(() => Array.from({ length: this.count() }, (_, index) => index));
}

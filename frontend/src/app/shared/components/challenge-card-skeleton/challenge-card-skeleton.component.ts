import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { SkeletonComponent } from '../skeleton/skeleton.component';

@Component({
  selector: 'app-challenge-card-skeleton',
  imports: [SkeletonComponent],
  templateUrl: './challenge-card-skeleton.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './challenge-card-skeleton.component.scss',
})
export class ChallengeCardSkeletonComponent {
  readonly summaryOnly = input(false);
}

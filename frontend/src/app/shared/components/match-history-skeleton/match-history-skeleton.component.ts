import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { SkeletonComponent } from '../skeleton/skeleton.component';

@Component({
  selector: 'app-match-history-skeleton',
  imports: [SkeletonComponent],
  templateUrl: './match-history-skeleton.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './match-history-skeleton.component.scss',
})
export class MatchHistorySkeletonComponent {
  readonly duoMode = input(false);

  protected readonly columns: readonly number[][] = [
    [0, 1],
    [0, 1, 2],
    [0],
    [0, 1, 2],
    [0, 1],
    [0],
    [0, 1, 2],
    [0, 1],
    [0, 1, 2],
    [0],
  ];
}

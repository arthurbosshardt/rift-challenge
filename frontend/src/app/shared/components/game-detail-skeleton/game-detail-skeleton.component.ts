import { ChangeDetectionStrategy, Component } from '@angular/core';
import { SkeletonComponent } from '../skeleton/skeleton.component';

@Component({
  selector: 'app-game-detail-skeleton',
  imports: [SkeletonComponent],
  templateUrl: './game-detail-skeleton.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './game-detail-skeleton.component.scss',
})
export class GameDetailSkeletonComponent {
  protected readonly rows = [0, 1, 2, 3, 4];
  protected readonly itemSlots = [0, 1, 2, 3, 4, 5, 6];
}

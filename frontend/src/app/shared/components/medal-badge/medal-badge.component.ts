import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { PodiumTier } from '../../../core/utils/leaderboard-sort';

let nextMedalBadgeId = 0;

@Component({
  selector: 'app-medal-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class]': 'hostClass()',
  },
  templateUrl: './medal-badge.component.html',
  styleUrl: './medal-badge.component.scss',
})
export class MedalBadgeComponent {
  readonly position = input.required<number>();
  readonly eligible = input(true);
  readonly size = input<'sm' | 'md'>('md');

  protected readonly gradientId = `medal-flame-${++nextMedalBadgeId}`;

  protected readonly tier = computed<PodiumTier | null>(() => {
    if (!this.eligible()) {
      return null;
    }
    const position = this.position();
    if (position === 1) {
      return 'gold';
    }
    if (position === 2) {
      return 'silver';
    }
    if (position === 3) {
      return 'bronze';
    }
    return null;
  });

  protected readonly hostClass = computed(() => {
    const classes = ['medal-badge', `medal-badge--${this.size()}`];
    const tier = this.tier();
    if (tier) {
      classes.push(`medal-badge--${tier}`, 'medal-badge--crest');
    }
    return classes.join(' ');
  });
}

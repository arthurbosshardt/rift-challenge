import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { LeaderboardCategory } from '../../../core/models/leaderboard.models';
import { LeaderboardSort } from '../../../core/utils/leaderboard-sort';

export function sortCriterionToCategoryIcon(criterion: LeaderboardSort): LeaderboardCategory {
  switch (criterion) {
    case 'RANK':
      return 'rank';
    case 'LP_GAIN':
      return 'lpGained';
    case 'WIN_RATE':
      return 'winRate';
  }
}

@Component({
  selector: 'app-leaderboard-category-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'leaderboard-category-icon',
    '[class.leaderboard-category-icon--compact]': 'compact()',
    'aria-hidden': 'true',
  },
  template: `
    @switch (category()) {
      @case ('winRate') {
        <svg viewBox="0 0 24 24">
          <path
            d="M4 19V5M4 19h16M8 16V9M12 16V7M16 16v-4"
            fill="none"
            stroke="currentColor"
            stroke-width="1.75"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      }
      @case ('lpGained') {
        <svg viewBox="0 0 24 24">
          <path
            d="M4 17l5-5 4 4 7-8M14 7h6v6"
            fill="none"
            stroke="currentColor"
            stroke-width="1.75"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      }
      @case ('winStreak') {
        <svg viewBox="0 0 24 24">
          <path
            d="M13.5.67s.74 2.65.74 4.8c0 2.06-1.35 3.73-3.41 3.73-2.07 0-3.63-1.67-3.63-3.73l.03-.36C5.21 7.51 4 10.62 4 14c0 4.42 3.58 8 8 8s8-3.58 8-8C20 8.61 17.41 3.8 13.5.67zM11.71 19c-1.78 0-3.22-1.4-3.22-3.14 0-1.62 1.05-2.76 2.81-3.12 1.77-.36 3.6-1.21 4.62-2.58.39 1.29.59 2.65.59 4.04 0 2.65-2.15 4.8-4.8 4.8z"
            fill="currentColor"
          />
        </svg>
      }
      @case ('rank') {
        <svg viewBox="0 0 24 24">
          <path
            d="M7 4V2h10v2h5v3a5 5 0 0 1-5 5h-.101a7.014 7.014 0 0 1-3.899 3.899V20h3v2H8v-2h3v-2.101A7.014 7.014 0 0 1 7.101 14H7a5 5 0 0 1-5-5V4h5zm0 2H4v1a3 3 0 0 0 3 3V6zm10 0v4a3 3 0 0 0 3-3V6h-3z"
            fill="currentColor"
          />
        </svg>
      }
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      flex-shrink: 0;
      width: 0.9375rem;
      height: 0.9375rem;
      color: currentColor;
    }

    :host(.leaderboard-category-icon--compact) {
      width: 0.8125rem;
      height: 0.8125rem;
    }

    svg {
      width: 100%;
      height: 100%;
    }
  `,
})
export class LeaderboardCategoryIconComponent {
  readonly category = input.required<LeaderboardCategory>();
  readonly compact = input(false);
}

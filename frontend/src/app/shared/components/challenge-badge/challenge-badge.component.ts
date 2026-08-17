import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ChallengeStatus, ChallengeType } from '../../../core/models/challenge.models';

export type ChallengeBadgeKind = 'soloq' | 'duoq' | 'not-started' | 'active' | 'finished' | 'public' | 'private';

export function challengeTypeBadgeKind(type: ChallengeType): ChallengeBadgeKind {
  return type === 'SOLOQ' ? 'soloq' : 'duoq';
}

export function challengeStatusBadgeKind(status: ChallengeStatus): ChallengeBadgeKind {
  if (status === 'NOT_STARTED') {
    return 'not-started';
  }
  if (status === 'FINISHED') {
    return 'finished';
  }
  return 'active';
}

@Component({
  selector: 'app-challenge-badge',
  templateUrl: './challenge-badge.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './challenge-badge.component.scss',
})
export class ChallengeBadgeComponent {
  readonly kind = input.required<ChallengeBadgeKind>();
  readonly label = input.required<string>();
  readonly size = input<'default' | 'title'>('default');

  protected readonly badgeClass = computed(() => {
    const sizeClass = this.size() === 'title' ? ' badge--title' : '';
    switch (this.kind()) {
      case 'soloq':
        return `badge badge--soloq${sizeClass}`;
      case 'duoq':
        return `badge badge--duoq${sizeClass}`;
      case 'not-started':
        return `badge badge--status-not-started${sizeClass}`;
      case 'active':
        return `badge badge--status-active${sizeClass}`;
      case 'finished':
        return `badge badge--status-finished${sizeClass}`;
      case 'public':
        return `badge badge--public${sizeClass}`;
      case 'private':
        return `badge badge--private${sizeClass}`;
    }
  });
}

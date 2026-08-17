import { Component, input } from '@angular/core';
import { rankEmblemUrl, tierLabelFr } from '../../../core/utils/rank-display';

@Component({
  selector: 'app-rank-emblem',
  templateUrl: './rank-emblem.component.html',
  styleUrl: './rank-emblem.component.scss',
})
export class RankEmblemComponent {
  readonly tier = input<string | null>(null);
  readonly size = input<'sm' | 'md'>('md');

  protected emblemUrl(): string | null {
    return rankEmblemUrl(this.tier());
  }

  protected emblemAlt(): string {
    return `Emblème ${tierLabelFr(this.tier())}`;
  }
}

import { Component, inject, input } from '@angular/core';
import { rankEmblemUrl, tierLabel } from '../../../core/utils/rank-display';
import { I18nService } from '../../../core/i18n/i18n.service';

@Component({
  selector: 'app-rank-emblem',
  templateUrl: './rank-emblem.component.html',
  styleUrl: './rank-emblem.component.scss',
})
export class RankEmblemComponent {
  readonly tier = input<string | null>(null);
  readonly size = input<'sm' | 'md'>('md');
  private readonly i18n = inject(I18nService);

  protected emblemUrl(): string | null {
    return rankEmblemUrl(this.tier());
  }

  protected emblemAlt(): string {
    return this.i18n.t('rank.emblemAlt', {
      tier: tierLabel(this.tier(), this.i18n.locale()),
    });
  }
}

import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { I18nService } from '../../../core/i18n/i18n.service';
import { ClampTooltipDirective } from '../../directives/clamp-tooltip.directive';
import { SkeletonComponent } from '../skeleton/skeleton.component';

@Component({
  selector: 'app-champion-pool-skeleton',
  imports: [ClampTooltipDirective, SkeletonComponent],
  templateUrl: './champion-pool-skeleton.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './champion-pool-skeleton.component.scss',
})
export class ChampionPoolSkeletonComponent {
  private readonly i18n = inject(I18nService);

  readonly remainingGames = input(0);
  readonly remainingBaseline = input(0);
  /** When true, only the sync header is shown (stats table rendered separately below). */
  readonly headerOnly = input(false);
  /** When true, only the placeholder rows are shown (sync header rendered separately elsewhere). */
  readonly rowsOnly = input(false);

  protected readonly rows = [0, 1, 2, 3, 4];

  protected readonly loadingAriaLabel = computed(() => {
    this.i18n.locale();
    return this.i18n.t('activity.loading');
  });

  protected readonly syncStatusLabel = computed(() => {
    this.i18n.locale();
    const count = this.remainingGames();
    const percent = this.progressPercent();
    if (count === 1) {
      return this.i18n.t('activity.championsSyncStatusOne', { percent });
    }
    return this.i18n.t('activity.championsSyncStatusMany', { count, percent });
  });

  protected readonly syncHintLabel = computed(() => {
    this.i18n.locale();
    return this.i18n.t('activity.championsSyncHint');
  });

  protected readonly syncHintAriaLabel = computed(() => {
    this.i18n.locale();
    return this.i18n.t('activity.championsSyncHintAria');
  });

  protected readonly progressPercent = computed(() => {
    const baseline = Math.max(this.remainingBaseline(), 0);
    const remaining = Math.max(this.remainingGames(), 0);
    if (baseline <= 0) {
      return remaining <= 0 ? 100 : 0;
    }
    const syncedInSession = Math.max(0, baseline - remaining);
    return Math.min(100, Math.round((syncedInSession / baseline) * 100));
  });

  protected readonly syncedInSession = computed(() => {
    const baseline = Math.max(this.remainingBaseline(), 0);
    const remaining = Math.max(this.remainingGames(), 0);
    return Math.max(0, baseline - remaining);
  });
}

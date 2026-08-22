import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { LeaderboardSort, SortDirection, sortDirectionArrow } from '../../../core/utils/leaderboard-sort';
import { ClampTooltipDirective } from '../../directives/clamp-tooltip.directive';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/t.pipe';

@Component({
  selector: 'app-leaderboard-sort-controls',
  imports: [TranslatePipe, ClampTooltipDirective],
  templateUrl: './leaderboard-sort-controls.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './leaderboard-sort-controls.component.scss',
})
export class LeaderboardSortControlsComponent {
  private readonly i18n = inject(I18nService);

  readonly sortCriterion = input<LeaderboardSort>('LP_GAIN');
  readonly sortDirection = input<SortDirection>('desc');
  readonly disabled = input(false);
  readonly showFinishedHelp = input(false);

  readonly sortCriterionChange = output<LeaderboardSort>();
  readonly sortDirectionToggle = output<void>();

  protected readonly sortOptions = computed(() => {
    this.i18n.locale();
    return [
      ['LP_GAIN', this.i18n.t('sort.lp')],
      ['WIN_RATE', this.i18n.t('sort.winRate')],
      ['RANK', this.i18n.t('sort.rank')],
    ] as [LeaderboardSort, string][];
  });

  protected setSortCriterion(criterion: LeaderboardSort): void {
    if (this.disabled()) {
      return;
    }
    this.sortCriterionChange.emit(criterion);
  }

  protected toggleSortDirection(): void {
    if (this.disabled()) {
      return;
    }
    this.sortDirectionToggle.emit();
  }

  protected sortDirectionAriaLabel(): string {
    const direction = this.sortDirection() === 'desc' ? this.i18n.t('sort.desc') : this.i18n.t('sort.asc');
    return `${this.i18n.t('sort.directionAria')}: ${direction}`;
  }

  protected sortDirectionIcon(): string {
    return sortDirectionArrow(this.sortDirection());
  }
}

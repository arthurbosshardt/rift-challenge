import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, OnInit } from '@angular/core';
import { ChampionStat } from '../../../core/models/challenge.models';
import { ChampionDataService } from '../../../core/services/champion-data.service';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { apiUrl } from '../../../core/utils/api-url';

import { LeaderboardCategoryIconComponent } from '../leaderboard-category-icon/leaderboard-category-icon.component';

@Component({
  selector: 'app-champion-pool',
  imports: [NgTemplateOutlet, TranslatePipe, LeaderboardCategoryIconComponent],
  templateUrl: './champion-pool.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './champion-pool.component.scss',
})
export class ChampionPoolComponent implements OnInit {
  readonly champions = input.required<ChampionStat[]>();

  protected readonly overviewEntry = computed(
    () => this.champions().find((entry) => entry.championId == null) ?? null,
  );

  protected readonly championEntries = computed(() =>
    this.champions().filter((entry) => entry.championId != null),
  );

  private readonly championData = inject(ChampionDataService);
  private readonly i18n = inject(I18nService);

  ngOnInit(): void {
    void this.championData.ensureLoaded();
  }

  protected isOverview(entry: ChampionStat): boolean {
    return entry.championId == null;
  }

  protected displayName(entry: ChampionStat): string {
    if (this.isOverview(entry)) {
      return this.i18n.t('activity.championsAll');
    }
    if (entry.championName) {
      return entry.championName;
    }
    const mapped = this.championData.ready().get(entry.championId ?? 0);
    return mapped ?? `#${entry.championId}`;
  }

  protected iconSrc(entry: ChampionStat): string | null {
    if (this.isOverview(entry)) {
      return null;
    }
    if (entry.championIconUrl) {
      return apiUrl(entry.championIconUrl);
    }
    if (entry.championId != null && entry.championId > 0) {
      return apiUrl(`/api/champion-icons/${entry.championId}.png`);
    }
    return null;
  }

  protected winRateLabel(entry: ChampionStat): string {
    return `${Math.round(entry.winRate * 100)}%`;
  }

  protected winRateClass(entry: ChampionStat): string {
    return entry.winRate >= 0.5
      ? 'champion-pool__winrate-badge--positive'
      : 'champion-pool__winrate-badge--negative';
  }

  protected kdaClass(entry: ChampionStat): string {
    return entry.kda >= 3 ? 'champion-pool__kda--high' : 'champion-pool__kda--neutral';
  }

  protected kdaLabel(entry: ChampionStat): string {
    return this.i18n.t('activity.championsKda', { ratio: formatStat(entry.kda) });
  }

  protected kdaBreakdown(entry: ChampionStat): string {
    return `${formatStat(entry.avgKills)} / ${formatStat(entry.avgDeaths)} / ${formatStat(entry.avgAssists)}`;
  }

  protected csLabel(entry: ChampionStat): string {
    return this.i18n.t('activity.championsCs', {
      cs: entry.avgCs,
      csPerMin: entry.avgCsPerMin.toFixed(1),
    });
  }

  protected gamesLabel(entry: ChampionStat): string {
    return this.i18n.t('activity.championsGames', { count: entry.games });
  }
}

function formatStat(value: number): string {
  return Number.isInteger(value) ? `${value}` : value.toFixed(1);
}

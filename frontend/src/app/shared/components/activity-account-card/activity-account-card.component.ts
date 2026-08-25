import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { ActivityAccount } from '../../../core/services/activity-cache.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/t.pipe';
import { formatRankLabel, tierColor } from '../../../core/utils/rank-display';
import { hasPlayedRecord, winRateLabel, winRateToneModifier } from '../../../core/utils/record-display';
import { championSplashUrl } from '../../../core/utils/champion-splash';
import { ordinalParts } from '../../../core/utils/ordinal-rank';
import { MatchHistoryStripComponent } from '../match-history-strip/match-history-strip.component';
import { ChampionPoolComponent } from '../champion-pool/champion-pool.component';
import { ChampionPoolSkeletonComponent } from '../champion-pool-skeleton/champion-pool-skeleton.component';
import { LeaderboardCategoryIconComponent } from '../leaderboard-category-icon/leaderboard-category-icon.component';

export interface ChampionRankBlock {
  championId: number;
  championName: string;
  rankNumber: string;
  rankSuffix: string;
  poolSize: number | null;
  splashUrl: string | null;
  winRate: number;
  kda: number;
  games: number;
  farmPerMin: number;
  avgKills: number;
  avgDeaths: number;
  avgAssists: number;
  avgCs: number;
}

export interface PlaystyleAxis {
  key: 'kda' | 'versatility' | 'aggression' | 'resilience' | 'soloCarry' | 'competitiveness';
  labelKey: string;
  detailKey: string;
  score: number;
  rawLabel: string;
}

const HIGH_KDA_THRESHOLD = 3;
const HIGH_FARM_THRESHOLD = 8;
const NEUTRAL_KDA = 2.2;
const NEUTRAL_FARM_PER_MIN = 6;
const GAMES_CONFIDENCE_WEIGHT = 10;
const VERSATILITY_CHAMPION_TARGET = 15;

function formatStat(value: number): string {
  return Number.isInteger(value) ? `${value}` : value.toFixed(1);
}

// Pulls small-sample stats toward a neutral baseline so a strong ratio needs more games behind it
// to count as "high" — the same value earned over few games weighs less than over many.
function confidenceWeighted(value: number, games: number, neutral: number): number {
  return (value * games + neutral * GAMES_CONFIDENCE_WEIGHT) / (games + GAMES_CONFIDENCE_WEIGHT);
}

@Component({
  selector: 'app-activity-account-card',
  imports: [
    MatchHistoryStripComponent,
    ChampionPoolComponent,
    ChampionPoolSkeletonComponent,
    LeaderboardCategoryIconComponent,
    TranslatePipe,
  ],
  templateUrl: './activity-account-card.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './activity-account-card.component.scss',
})
export class ActivityAccountCardComponent {
  readonly account = input.required<ActivityAccount>();
  readonly matchClick = output<string>();

  private readonly i18n = inject(I18nService);

  protected readonly selectedView = signal<'champion' | 'playstyle'>('champion');

  protected setSelectedView(view: 'champion' | 'playstyle'): void {
    this.selectedView.set(view);
  }

  protected readonly topRankedChampions = computed<ChampionRankBlock[]>(() => {
    const champions = this.account().champions ?? [];
    const locale = this.i18n.locale();
    return champions
      .filter((entry) => entry.championId != null && entry.championId > 0 && entry.rank != null)
      .sort((a, b) => (a.rank ?? 0) - (b.rank ?? 0))
      .slice(0, 3)
      .map((entry) => {
        const { number, suffix } = ordinalParts(entry.rank as number, locale);
        return {
          championId: entry.championId as number,
          championName: entry.championName ?? '',
          rankNumber: number,
          rankSuffix: suffix,
          poolSize: entry.rankPoolSize,
          splashUrl: championSplashUrl(entry.championId),
          winRate: entry.winRate,
          kda: entry.kda,
          games: entry.games,
          farmPerMin: entry.avgCsPerMin,
          avgKills: entry.avgKills,
          avgDeaths: entry.avgDeaths,
          avgAssists: entry.avgAssists,
          avgCs: entry.avgCs,
        };
      });
  });

  protected championWinRateLabel(block: ChampionRankBlock): string {
    return `${Math.round(block.winRate * 100)}%`;
  }

  protected championWinRateClass(block: ChampionRankBlock): string {
    const weighted = confidenceWeighted(block.winRate, block.games, 0.5);
    return weighted >= 0.5
      ? 'activity-account__rank-card-stat--positive'
      : 'activity-account__rank-card-stat--negative';
  }

  protected championKdaLabel(block: ChampionRankBlock): string {
    return this.i18n.t('activity.championsKda', { ratio: formatStat(block.kda) });
  }

  protected championKdaClass(block: ChampionRankBlock): string {
    const weighted = confidenceWeighted(block.kda, block.games, NEUTRAL_KDA);
    return weighted >= HIGH_KDA_THRESHOLD ? 'activity-account__rank-card-stat--high' : '';
  }

  protected championKdaRawLabel(block: ChampionRankBlock): string {
    return `${formatStat(block.avgKills)}/${formatStat(block.avgDeaths)}/${formatStat(block.avgAssists)}`;
  }

  protected championGamesLabel(block: ChampionRankBlock): string {
    return this.i18n.t('activity.championsGames', { count: block.games });
  }

  protected championFarmLabel(block: ChampionRankBlock): string {
    return `${formatStat(block.farmPerMin)} CS/min`;
  }

  protected championFarmClass(block: ChampionRankBlock): string {
    const weighted = confidenceWeighted(block.farmPerMin, block.games, NEUTRAL_FARM_PER_MIN);
    return weighted >= HIGH_FARM_THRESHOLD ? 'activity-account__rank-card-stat--high' : '';
  }

  protected championFarmAvgLabel(block: ChampionRankBlock): string {
    return `${formatStat(block.avgCs)} CS`;
  }

  protected readonly playstyleAxes = computed<PlaystyleAxis[] | null>(() => {
    const playstyle = this.account().playstyle;
    if (!playstyle) {
      return null;
    }
    const versatility = this.versatility();
    const competitiveness = this.competitiveness();
    return [
      {
        key: 'kda',
        labelKey: 'activity.playstyleKda',
        detailKey: 'activity.playstyleKdaDetail',
        score: playstyle.kdaScore,
        rawLabel: this.i18n.t('activity.championsKda', { ratio: formatStat(playstyle.kda) }),
      },
      {
        key: 'versatility',
        labelKey: 'activity.playstyleVersatility',
        detailKey: 'activity.playstyleVersatilityDetail',
        score: versatility.score,
        rawLabel: this.i18n.t('activity.playstyleVersatilityValue', { count: versatility.championCount }),
      },
      {
        key: 'aggression',
        labelKey: 'activity.playstyleAggression',
        detailKey: 'activity.playstyleAggressionDetail',
        score: playstyle.aggressionScore,
        rawLabel: `${formatStat(playstyle.aggressionPer10)}/10min`,
      },
      {
        key: 'resilience',
        labelKey: 'activity.playstyleResilience',
        detailKey: 'activity.playstyleResilienceDetail',
        score: playstyle.resilienceScore,
        rawLabel: `${formatStat(playstyle.resiliencePer10)}/10min`,
      },
      {
        key: 'soloCarry',
        labelKey: 'activity.playstyleSoloCarry',
        detailKey: 'activity.playstyleSoloCarryDetail',
        score: playstyle.soloCarryScore,
        rawLabel: `${Math.round(playstyle.soloCarryIndex * 100)}%`,
      },
      {
        key: 'competitiveness',
        labelKey: 'activity.playstyleCompetitiveness',
        detailKey: 'activity.playstyleCompetitivenessDetail',
        score: competitiveness.score,
        rawLabel: `${Math.round(competitiveness.score)}%`,
      },
    ];
  });

  // Champion pool breadth: distinct champions played this season, scaled against a target pool size.
  private versatility(): { score: number; championCount: number } {
    const champions = this.account().champions ?? [];
    const championCount = champions.filter((entry) => entry.championId != null && entry.games > 0).length;
    const score = Math.min(100, (championCount / VERSATILITY_CHAMPION_TARGET) * 100);
    return { score, championCount };
  }

  // Average rank percentile across played champions, weighted by games so mains count more than one-offs.
  private competitiveness(): { score: number } {
    const champions = this.account().champions ?? [];
    const ranked = champions.filter(
      (entry) => entry.rank != null && entry.rankPoolSize != null && entry.rankPoolSize > 0 && entry.games > 0,
    );
    if (ranked.length === 0) {
      return { score: 0 };
    }
    let weightedPercentile = 0;
    let totalGames = 0;
    for (const entry of ranked) {
      const poolSize = entry.rankPoolSize as number;
      const rank = entry.rank as number;
      const percentile = ((poolSize - rank + 1) / poolSize) * 100;
      weightedPercentile += percentile * entry.games;
      totalGames += entry.games;
    }
    return { score: totalGames > 0 ? weightedPercentile / totalGames : 0 };
  }

  protected readonly radarGeometry = computed(() => {
    const axes = this.playstyleAxes();
    if (!axes || axes.length === 0) {
      return null;
    }
    const center = 100;
    const radius = 85;
    const count = axes.length;
    const pointAt = (index: number, r: number): { x: number; y: number } => {
      const angle = (Math.PI * 2 * index) / count - Math.PI / 2;
      return { x: center + r * Math.cos(angle), y: center + r * Math.sin(angle) };
    };
    const toPolygon = (points: { x: number; y: number }[]): string =>
      points.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');

    const rings = [0.25, 0.5, 0.75, 1].map((fraction) =>
      toPolygon(Array.from({ length: count }, (_, i) => pointAt(i, radius * fraction))),
    );
    const axisLines = Array.from({ length: count }, (_, i) => pointAt(i, radius));
    const labelPoints = Array.from({ length: count }, (_, i) => pointAt(i, radius + 18));
    const polygon = toPolygon(
      axes.map((axis, i) => pointAt(i, (Math.max(0, Math.min(100, axis.score)) / 100) * radius)),
    );

    return { center, rings, axisLines, labelPoints, polygon };
  });

  protected rankLabel(): string {
    const account = this.account();
    return formatRankLabel(account.tier, account.rank, account.leaguePoints ?? 0, this.i18n.locale());
  }

  protected rankColor(): string {
    return tierColor(this.account().tier);
  }

  protected hasRecord(): boolean {
    const account = this.account();
    return hasPlayedRecord(account.wins ?? 0, account.losses ?? 0);
  }

  protected winRateLabelText(): string {
    const account = this.account();
    return winRateLabel(this.winRate(), account.wins ?? 0, account.losses ?? 0);
  }

  protected winRateClass(): string {
    const account = this.account();
    const tone = winRateToneModifier(this.winRate(), account.wins ?? 0, account.losses ?? 0);
    return `activity-account__winrate--${tone}`;
  }

  protected syncInProgress(): boolean {
    return !this.account().seasonSyncComplete;
  }

  protected syncRemaining(): number {
    const account = this.account();
    return Math.max(0, account.seasonGames - account.syncedGames);
  }

  protected onMatchClick(matchId: string): void {
    this.matchClick.emit(matchId);
  }

  private winRate(): number {
    const account = this.account();
    const wins = account.wins ?? 0;
    const losses = account.losses ?? 0;
    const total = wins + losses;
    return total > 0 ? wins / total : 0;
  }
}

import { Component, inject, OnInit, signal, ChangeDetectionStrategy, computed } from '@angular/core';
import { ChallengeApiService } from '../../core/services/challenge-api.service';
import { AuthService } from '../../core/services/auth.service';
import { SettingsModalService } from '../../core/services/settings-modal.service';
import { PageShellComponent } from '../../shared/components/page-shell/page-shell.component';
import { ChallengeListSkeletonComponent } from '../../shared/components/challenge-list-skeleton/challenge-list-skeleton.component';
import { TranslatePipe } from '../../core/i18n/t.pipe';
import { I18nService } from '../../core/i18n/i18n.service';
import { RecentGameResponse } from '../../core/models/challenge.models';
import { CommonModule } from '@angular/common';
import { GameDetailModalService } from '../../shared/services/game-detail-modal.service';
import { PlayerAvatarComponent } from '../../shared/components/player-avatar/player-avatar.component';

interface AccountGameGroup {
  accountId: string;
  gameName: string;
  tagLine: string;
  profileIconId?: number | null;
  currentTier?: string | null;
  currentRank?: string | null;
  currentLp?: number;
  wins?: number;
  losses?: number;
  winRate?: number;
  games: RecentGameResponse[];
}

@Component({
  selector: 'app-dashboard-page',
  imports: [
    CommonModule,
    PageShellComponent,
    ChallengeListSkeletonComponent,
    TranslatePipe,
    PlayerAvatarComponent,
  ],
  templateUrl: './dashboard-page.component.html',
  changeDetection: ChangeDetectionStrategy.Default,
  styleUrl: './dashboard-page.component.scss',
})
export class DashboardPageComponent implements OnInit {
  private readonly challengeApi = inject(ChallengeApiService);
  protected readonly auth = inject(AuthService);
  protected readonly settingsModal = inject(SettingsModalService);
  private readonly i18n = inject(I18nService);
  protected readonly modalService = inject(GameDetailModalService);

  protected readonly games = signal<RecentGameResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  // Computed signal to group games by account
  protected readonly gamesByAccount = computed(() => {
    const allGames = this.games();
    const accountMap = new Map<string, AccountGameGroup>();

    // Group games by account
    allGames.forEach((game) => {
      const accountId = `${game.gameName}#${game.tagLine}`;
      if (!accountMap.has(accountId)) {
        accountMap.set(accountId, {
          accountId,
          gameName: game.gameName,
          tagLine: game.tagLine,
          profileIconId: game.profileIconId,
          currentTier: game.currentTier,
          currentRank: game.currentRank,
          currentLp: game.currentLp,
          wins: game.wins,
          losses: game.losses,
          winRate: game.winRate,
          games: [],
        });
      }
      const group = accountMap.get(accountId)!;
      group.games.push(game);
      // Update account stats from the most recent (first) game, since games are sorted by date descending
      if (game.currentTier !== undefined && game.currentTier !== null) {
        group.currentTier = game.currentTier;
        group.currentRank = game.currentRank;
        group.currentLp = game.currentLp;
      }
      if (game.wins !== undefined && game.losses !== undefined) {
        group.wins = game.wins;
        group.losses = game.losses;
        group.winRate = game.winRate;
      }
      if (game.profileIconId) {
        group.profileIconId = game.profileIconId;
      }
    });

    return Array.from(accountMap.values());
  });

  ngOnInit(): void {
    void this.loadPage();
  }

  protected openGameDetail(game: RecentGameResponse): void {
    this.modalService.open(game);
  }

  protected getGameAriaLabel(game: RecentGameResponse): string {
    const resultKey = game.win ? 'game.victory' : 'game.defeat';
    const result = this.i18n.t(resultKey);
    return this.i18n.t('game.ariaLabel', {
      result: result,
      playerName: game.gameName,
    });
  }

  private async loadPage(): Promise<void> {
    await this.auth.waitUntilReady();

    if (!(await this.auth.resolveAccessToken())) {
      this.error.set(this.i18n.t('home.sessionExpired'));
      this.loading.set(false);
      return;
    }

    if (!this.auth.linkedAccount()) {
      await this.auth.refreshProfile();
    }

    if (!this.auth.linkedAccount()) {
      this.loading.set(false);
      return;
    }

    void this.loadGames();
  }

  private async loadGames(): Promise<void> {
    this.loading.set(true);
    this.challengeApi.listRecentGames().subscribe({
      next: (games) => {
        this.games.set(games);
        this.loading.set(false);
      },
      error: (err: { status?: number }) => {
        if (err.status === 401) {
          this.error.set(this.i18n.t('home.sessionExpired'));
        } else {
          this.error.set(this.i18n.t('dashboard.loadError'));
        }
        this.loading.set(false);
      },
    });
  }

  protected formatPlayedAt(playedAt: string): string {
    const date = new Date(playedAt);
    return date.toLocaleString(this.i18n.locale());
  }
}

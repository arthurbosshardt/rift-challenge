export interface LeaderboardMatchHistory {
  matchId: string;
  championId: number | null;
  championIconUrl: string | null;
  win: boolean;
  lpDelta: number;
  playedAt: string;
}

export interface LeaderboardEntry {
  puuid: string;
  gameName: string;
  tagLine: string;
  riotId: string;
  profileIconId: number | null;
  tier: string | null;
  rankDivision: string | null;
  leaguePoints: number;
  wins: number;
  losses: number;
  gamesPlayed: number;
  winRate: number;
  winStreak: number;
  lpGained: number;
  position: number;
  recentMatches: LeaderboardMatchHistory[];
}

export interface LeaderboardWindow {
  byRank: LeaderboardEntry[];
  byWinRate: LeaderboardEntry[];
  byWinStreak: LeaderboardEntry[];
  byLpGained: LeaderboardEntry[];
  byGamesPlayed: LeaderboardEntry[];
  winRateMinGames: number;
}

export interface LeaderboardResponse {
  season: LeaderboardWindow;
  last7Days: LeaderboardWindow;
  generatedAt: string;
  nextRefreshAt: string;
  canRefresh: boolean;
  seasonYear: number;
}

export type LeaderboardTimeframe = 'season' | 'last7Days';

export type LeaderboardCategory = 'rank' | 'winRate' | 'winStreak' | 'lpGained' | 'gamesPlayed';

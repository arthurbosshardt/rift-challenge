import { winRateLabel as formatWinRateLabel } from './leaderboard-sort';

export function winRateLabel(winRate: number, wins: number, losses: number): string {
  return formatWinRateLabel(winRate, wins, losses);
}

export function hasPlayedRecord(wins: number, losses: number): boolean {
  return wins + losses > 0;
}

export function winRateToneModifier(winRate: number, wins: number, losses: number): 'positive' | 'negative' | 'neutral' {
  if (!hasPlayedRecord(wins, losses)) {
    return 'neutral';
  }
  return winRate >= 0.5 ? 'positive' : 'negative';
}

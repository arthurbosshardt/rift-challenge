import { ParticipantProgress, DuoProgress } from '../models/race.models';

export type LeaderboardSort = 'RANK' | 'LP_GAIN' | 'WIN_RATE';
export type SortDirection = 'asc' | 'desc';

export const LEADERBOARD_SORT_LABELS: Record<LeaderboardSort, string> = {
  RANK: 'Meilleur rang',
  LP_GAIN: 'Gain de LP',
  WIN_RATE: 'Ratio V/D',
};

export function sortParticipants(
  participants: ParticipantProgress[],
  sort: LeaderboardSort,
  direction: SortDirection = 'desc',
): ParticipantProgress[] {
  const sorted = [...participants];
  sorted.sort((left, right) => applyDirection(compareParticipants(left, right, sort), direction));
  return sorted.map((participant, index) => ({ ...participant, position: index + 1 }));
}

export function sortDuos(
  duos: DuoProgress[],
  sort: LeaderboardSort,
  direction: SortDirection = 'desc',
): DuoProgress[] {
  const sorted = [...duos];
  sorted.sort((left, right) => applyDirection(compareDuos(left, right, sort), direction));
  return sorted.map((duo, index) => ({ ...duo, position: index + 1 }));
}

function applyDirection(value: number, direction: SortDirection): number {
  return direction === 'desc' ? value : -value;
}

function compareParticipants(
  left: ParticipantProgress,
  right: ParticipantProgress,
  sort: LeaderboardSort,
): number {
  switch (sort) {
    case 'LP_GAIN':
      return right.lpGained - left.lpGained;
    case 'WIN_RATE':
      return compareWinRate(left.winRate, left.wins, right.winRate, right.wins);
    case 'RANK':
    default:
      return right.rankScore - left.rankScore;
  }
}

function compareDuos(left: DuoProgress, right: DuoProgress, sort: LeaderboardSort): number {
  if (!left.eligible && right.eligible) {
    return 1;
  }
  if (left.eligible && !right.eligible) {
    return -1;
  }

  switch (sort) {
    case 'LP_GAIN':
      return right.combinedLpGained - left.combinedLpGained;
    case 'WIN_RATE':
      return compareWinRate(left.winRate, left.wins, right.winRate, right.wins);
    case 'RANK':
    default:
      return right.combinedRankScore - left.combinedRankScore;
  }
}

function compareWinRate(
  leftRate: number,
  leftWins: number,
  rightRate: number,
  rightWins: number,
): number {
  if (rightRate !== leftRate) {
    return rightRate - leftRate;
  }
  return rightWins - leftWins;
}

export function winRateLabel(winRate: number, wins: number, losses: number): string {
  if (wins + losses === 0) {
    return '—';
  }
  return `${Math.round(winRate * 100)} %`;
}

export function sortDirectionArrow(direction: SortDirection): string {
  return direction === 'desc' ? '↓' : '↑';
}

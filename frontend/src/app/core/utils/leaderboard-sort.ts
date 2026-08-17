import { ParticipantProgress, DuoProgress } from '../models/challenge.models';

export type LeaderboardSort = 'RANK' | 'LP_GAIN' | 'WIN_RATE';
export type SortDirection = 'asc' | 'desc';

export function sortParticipants(
  participants: ParticipantProgress[],
  sort: LeaderboardSort,
  direction: SortDirection = 'desc',
): ParticipantProgress[] {
  const sorted = [...participants];
  sorted.sort((left, right) => compareParticipants(left, right, sort, direction));
  return assignPositions(sorted, direction);
}

export function sortDuos(
  duos: DuoProgress[],
  sort: LeaderboardSort,
  direction: SortDirection = 'desc',
): DuoProgress[] {
  const sorted = [...duos];
  sorted.sort((left, right) => compareDuos(left, right, sort, direction));
  return assignPositions(sorted, direction);
}

function assignPositions<T extends { position: number }>(
  sorted: T[],
  direction: SortDirection,
): T[] {
  const total = sorted.length;
  return sorted.map((entry, index) => ({
    ...entry,
    position: leaderboardPosition(index, total, direction),
  }));
}

export function leaderboardPosition(
  index: number,
  total: number,
  direction: SortDirection,
): number {
  return direction === 'desc' ? index + 1 : total - index;
}

export function leaderPosition(): number {
  return 1;
}

function compareParticipants(
  left: ParticipantProgress,
  right: ParticipantProgress,
  sort: LeaderboardSort,
  direction: SortDirection,
): number {
  let result = 0;

  switch (sort) {
    case 'LP_GAIN':
      result = compareNumbers(left.lpGained, right.lpGained, direction);
      break;
    case 'WIN_RATE':
      result = compareWinRate(left.winRate, left.wins, right.winRate, right.wins, direction);
      break;
    case 'RANK':
    default:
      result = compareNumbers(left.rankScore, right.rankScore, direction);
      break;
  }

  if (result !== 0) {
    return result;
  }

  return compareStrings(left.riotId, right.riotId, direction);
}

function compareDuos(
  left: DuoProgress,
  right: DuoProgress,
  sort: LeaderboardSort,
  direction: SortDirection,
): number {
  if (!left.eligible && right.eligible) {
    return 1;
  }
  if (left.eligible && !right.eligible) {
    return -1;
  }

  let result = 0;

  switch (sort) {
    case 'LP_GAIN':
      result = compareNumbers(left.combinedLpGained, right.combinedLpGained, direction);
      break;
    case 'WIN_RATE':
      result = compareWinRate(left.winRate, left.wins, right.winRate, right.wins, direction);
      break;
    case 'RANK':
    default:
      result = compareNumbers(left.combinedRankScore, right.combinedRankScore, direction);
      break;
  }

  if (result !== 0) {
    return result;
  }

  return compareStrings(left.id, right.id, direction);
}

function compareNumbers(left: number, right: number, direction: SortDirection): number {
  return direction === 'desc' ? right - left : left - right;
}

function compareStrings(left: string, right: string, direction: SortDirection): number {
  const result = left.localeCompare(right, undefined, { sensitivity: 'base' });
  return direction === 'desc' ? result : -result;
}

function compareWinRate(
  leftRate: number,
  leftWins: number,
  rightRate: number,
  rightWins: number,
  direction: SortDirection,
): number {
  if (leftRate !== rightRate) {
    return compareNumbers(leftRate, rightRate, direction);
  }
  return compareNumbers(leftWins, rightWins, direction);
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

export type PodiumTier = 'gold' | 'silver' | 'bronze';

export function podiumTier(
  position: number,
  total: number,
  eligible = true,
): PodiumTier | null {
  if (!eligible || total <= 0) {
    return null;
  }
  if (position === 1) {
    return 'gold';
  }
  if (position === 2) {
    return 'silver';
  }
  if (position === 3 && total >= 3) {
    return 'bronze';
  }
  return null;
}

export function isLeaderboardLeader(
  position: number,
  _direction: SortDirection,
  options: { eligible?: boolean; total: number; eligibleCount?: number },
): boolean {
  if (options.eligible === false || options.total <= 0) {
    return false;
  }
  return position === leaderPosition();
}

export type RaceType = 'SOLOQ' | 'DUOQ';

export type RaceStatus = 'NOT_STARTED' | 'ACTIVE' | 'FINISHED';

export interface RaceSummary {
  id: string;
  shareSlug: string;
  name: string;
  type: RaceType;
  startAt: string;
  endAt: string | null;
  isPublic: boolean;
  status: RaceStatus;
}

export interface ParticipantProgress {
  id: string;
  gameName: string;
  tagLine: string;
  riotId: string;
  position: number;
  currentTier: string | null;
  currentRank: string | null;
  currentLp: number;
  lpGained: number;
  rankScore: number;
  wins: number;
  losses: number;
  winRate: number;
  profileIconId: number | null;
  hasRankData: boolean;
}

export interface DuoProgress {
  id: string;
  player1: ParticipantProgress;
  player2: ParticipantProgress;
  combinedRankScore: number;
  combinedLpGained: number;
  wins: number;
  losses: number;
  winRate: number;
  eligible: boolean;
  ineligibilityReason: string | null;
  position: number;
}

export interface RaceDetail extends RaceSummary {
  sharePath: string;
  isOwner: boolean;
  lastRefreshedAt: string | null;
  nextRefreshAvailableAt: string | null;
  refreshAvailable: boolean;
  participants: ParticipantProgress[];
  duos: DuoProgress[];
}

export interface CreateRaceRequest {
  name: string;
  type: RaceType;
  startAt: string;
  endAt: string;
  isPublic: boolean;
}

export interface UpdateRaceEndRequest {
  endAt: string;
}

export interface AddParticipantRequest {
  riotId: string;
}

export interface AddDuoRequest {
  player1RiotId: string;
  player2RiotId: string;
}

export interface AuthMeResponse {
  userId: string;
  username: string;
}

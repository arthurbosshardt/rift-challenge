export type RaceType = 'SOLOQ' | 'DUOQ';

export type RaceStatus = 'NOT_STARTED' | 'ACTIVE';

export interface RaceSummary {
  id: string;
  shareSlug: string;
  name: string;
  type: RaceType;
  startAt: string;
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
  wins: number;
  losses: number;
  hasRankData: boolean;
}

export interface RaceDetail extends RaceSummary {
  sharePath: string;
  ownerId: string;
  isOwner: boolean;
  lastRefreshedAt: string | null;
  nextRefreshAvailableAt: string | null;
  refreshAvailable: boolean;
  participants: ParticipantProgress[];
}

export interface CreateRaceRequest {
  name: string;
  type: RaceType;
  startAt: string;
  isPublic: boolean;
}

export interface AddParticipantRequest {
  riotId: string;
}

export interface AuthMeResponse {
  userId: string;
}

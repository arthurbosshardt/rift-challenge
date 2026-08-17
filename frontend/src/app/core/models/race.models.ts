export type RaceType = 'SOLOQ' | 'DUOQ';

export type RaceStatus = 'NOT_STARTED' | 'ACTIVE' | 'FINISHED';

export interface ParticipantPreview {
  id: string;
  gameName: string;
  tagLine: string;
  riotId: string;
  profileIconId: number | null;
  currentTier: string | null;
  currentRank: string | null;
  currentLp: number;
  lpGained: number;
  wins: number;
  losses: number;
  winRate: number;
  position: number;
}

export interface DuoPreview {
  id: string;
  player1: ParticipantPreview;
  player2: ParticipantPreview;
  combinedLpGained: number;
  wins: number;
  losses: number;
  winRate: number;
  position: number;
  eligible: boolean;
}

export interface RaceSummary {
  id: string;
  shareSlug: string;
  name: string;
  type: RaceType;
  startAt: string;
  endAt: string | null;
  isPublic: boolean;
  status: RaceStatus;
  entryCount: number;
  participantGameNames: string[];
  previewParticipants: ParticipantPreview[];
  previewDuos: DuoPreview[];
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

export interface UpdateRaceStartRequest {
  startAt: string;
}

export interface UpdateRaceScheduleRequest {
  startAt: string;
  endAt: string;
}

export interface UpdateRaceVisibilityRequest {
  isPublic: boolean;
}

export interface UpdateRaceNameRequest {
  name: string;
}

export interface AddParticipantRequest {
  riotId: string;
}

export interface AddDuoRequest {
  player1RiotId: string;
  player2RiotId: string;
}

export interface LinkedRiotAccount {
  id: string;
  gameName: string;
  tagLine: string;
  riotId: string;
  profileIconId: number | null;
}

export interface AuthMeResponse {
  userId: string;
  username: string;
  linkedRiotAccount: LinkedRiotAccount | null;
}

export interface UserRiotAccount {
  id: string;
  gameName: string;
  tagLine: string;
  riotId: string;
  profileIconId: number | null;
}

export interface LinkRiotAccountRequest {
  riotId: string;
}

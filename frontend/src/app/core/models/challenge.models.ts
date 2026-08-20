export type ChallengeType = 'SOLOQ' | 'DUOQ';

export type ChallengeStatus = 'NOT_STARTED' | 'ACTIVE' | 'FINISHED';

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

export interface ChallengeSummary {
  id: string;
  shareSlug: string;
  name: string;
  type: ChallengeType;
  startAt: string;
  endAt: string | null;
  maxGames: number | null;
  status: ChallengeStatus;
  entryCount: number;
  participantGameNames: string[];
  previewParticipants: ParticipantPreview[];
  previewDuos: DuoPreview[];
}

export interface ChallengeListResponse {
  challenges: ChallengeSummary[];
  generatedAt: string | null;
  refreshAvailable: boolean;
  nextRefreshAvailableAt: string | null;
}

export interface ParticipantMatchHistory {
  matchId: string;
  championId: number | null;
  championIconUrl: string | null;
  win: boolean;
  lpDelta: number;
  playedAt: string;
}

export interface DuoMatchHistory {
  matchId: string;
  win: boolean;
  player1ChampionId: number;
  player1ChampionIconUrl: string | null;
  player1LpDelta: number;
  player2ChampionId: number;
  player2ChampionIconUrl: string | null;
  player2LpDelta: number;
  playedAt: string;
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
  rankEstimated: boolean;
  recentMatches: ParticipantMatchHistory[];
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
  recentMatches: DuoMatchHistory[];
}

export interface ChallengeDetail extends ChallengeSummary {
  sharePath: string;
  isOwner: boolean;
  lastRefreshedAt: string | null;
  nextRefreshAvailableAt: string | null;
  refreshAvailable: boolean;
  participants: ParticipantProgress[];
  duos: DuoProgress[];
}

export interface CreateChallengeRequest {
  name: string;
  type: ChallengeType;
  startAt: string;
  endAt?: string;
  maxGames?: number;
}

export interface UpdateChallengeEndRequest {
  endAt: string;
}

export interface UpdateChallengeStartRequest {
  startAt: string;
}

export interface UpdateChallengeScheduleRequest {
  startAt: string;
  endAt: string;
}

export interface UpdateChallengeNameRequest {
  name: string;
}

export interface UpdateChallengeRequest {
  name?: string;
  startAt?: string;
  endAt?: string;
  maxGames?: number;
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
  primary: boolean;
}

export interface LinkRiotAccountRequest {
  riotId: string;
  smurf?: boolean;
}


export interface RecentGameResponse {
  id: string;
  gameName: string;
  tagLine: string;
  championId: number | null;
  championIconUrl: string | null;
  win: boolean;
  playedAt: string;
}

export interface AccountRecentGames {
  accountId: string;
  gameName: string;
  tagLine: string;
  profileIconId: number | null;
  primary: boolean;
  tier: string | null;
  rank: string | null;
  leaguePoints: number | null;
  wins: number | null;
  losses: number | null;
  games: RecentGameResponse[];
}

export interface MatchItem {
  itemId: number | null;
  iconUrl: string | null;
}

export interface MatchParticipant {
  gameName: string;
  tagLine: string;
  profileIconId: number | null;
  profileIconUrl: string | null;
  championId: number | null;
  championIconUrl: string | null;
  champLevel: number;
  role: string | null;
  teamId: number;
  win: boolean;
  kills: number;
  deaths: number;
  assists: number;
  cs: number;
  goldEarned: number;
  damageDealt: number;
  visionScore: number;
  wardsPlaced: number;
  summoner1IconUrl: string | null;
  summoner2IconUrl: string | null;
  primaryRuneIconUrl: string | null;
  secondaryStyleIconUrl: string | null;
  rankTier: string | null;
  rankDivision: string | null;
  rankLeaguePoints: number | null;
  items: MatchItem[];
  isFocusPlayer: boolean;
}

export interface MatchTeamObjectives {
  towerKills: number;
  dragonKills: number;
  baronKills: number;
  riftHeraldKills: number;
  inhibitorKills: number;
}

export interface MatchDetail {
  matchId: string;
  playedAt: string;
  durationSeconds: number;
  win: boolean;
  myTeam: MatchParticipant[];
  enemyTeam: MatchParticipant[];
  myTeamObjectives: MatchTeamObjectives;
  enemyTeamObjectives: MatchTeamObjectives;
}

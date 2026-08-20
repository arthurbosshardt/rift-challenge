import {
  DuoPreview,
  DuoProgress,
  ParticipantPreview,
  ParticipantProgress,
  ChallengeDetail,
  ChallengeSummary,
} from '../models/challenge.models';

export const CHALLENGE_PREVIEW_FETCH_LIMIT = 10;

function previewEntryCount(challenge: Partial<ChallengeSummary>): number {
  if (challenge.type === 'DUOQ') {
    return challenge.previewDuos?.length ?? 0;
  }
  return challenge.previewParticipants?.length ?? 0;
}

export function summaryChallengeNeedsEnrichment(challenge: Partial<ChallengeSummary>): boolean {
  if (challenge.entryCount === undefined) {
    return true;
  }

  const entryCount = challenge.entryCount;
  if (entryCount <= 0) {
    return false;
  }

  const namesFromApi = challenge.participantGameNames?.length ?? 0;
  if (namesFromApi < entryCount) {
    return true;
  }

  const previewCount = previewEntryCount(challenge);
  const targetPreview = Math.min(entryCount, CHALLENGE_PREVIEW_FETCH_LIMIT);
  return previewCount < targetPreview;
}

export function summaryChallengeNeedsStatsRefresh(challenge: Pick<ChallengeSummary, 'status'>): boolean {
  return challenge.status === 'ACTIVE' || challenge.status === 'FINISHED';
}

export type RawChallengeSummary = Partial<ChallengeSummary> &
  Pick<ChallengeSummary, 'id' | 'shareSlug' | 'name' | 'type' | 'startAt' | 'isPublic' | 'status'>;

export function normalizeChallengeSummary(raw: RawChallengeSummary): ChallengeSummary {
  return {
    ...raw,
    endAt: raw.endAt ?? null,
    maxGames: raw.maxGames ?? null,
    entryCount: raw.entryCount ?? 0,
    participantGameNames: normalizeParticipantGameNames(raw),
    previewParticipants: normalizeParticipants(raw.previewParticipants),
    previewDuos: normalizeDuos(raw.previewDuos),
  };
}

function normalizeParticipantGameNames(raw: Partial<ChallengeSummary>): string[] {
  if (Array.isArray(raw.participantGameNames) && raw.participantGameNames.length > 0) {
    return raw.participantGameNames;
  }

  const fromPreview: string[] = [];
  for (const participant of raw.previewParticipants ?? []) {
    fromPreview.push(participant.gameName);
  }
  for (const duo of raw.previewDuos ?? []) {
    fromPreview.push(duo.player1.gameName, duo.player2.gameName);
  }
  return fromPreview;
}

export function normalizeChallengeSummaries(raw: RawChallengeSummary[]): ChallengeSummary[] {
  return raw.map(normalizeChallengeSummary);
}

export function summaryNeedsPreviewEnrichment(raw: Partial<ChallengeSummary>[]): boolean {
  return raw.some(summaryChallengeNeedsEnrichment);
}

export function enrichSummaryFromDetail(summary: ChallengeSummary, detail: ChallengeDetail): ChallengeSummary {
  const participantGameNames =
    detail.type === 'DUOQ'
      ? detail.duos.flatMap((duo) => [duo.player1.gameName, duo.player2.gameName])
      : detail.participants.map((participant) => participant.gameName);

  if (detail.type === 'DUOQ') {
    return {
      ...summary,
      entryCount: detail.duos.length,
      participantGameNames,
      previewParticipants: [],
      previewDuos: detail.duos.slice(0, CHALLENGE_PREVIEW_FETCH_LIMIT).map(toDuoPreview),
    };
  }

  return {
    ...summary,
    entryCount: detail.participants.length,
    participantGameNames,
    previewParticipants: detail.participants.slice(0, CHALLENGE_PREVIEW_FETCH_LIMIT).map(toParticipantPreview),
    previewDuos: [],
  };
}

function toParticipantPreview(participant: ParticipantProgress): ParticipantPreview {
  return {
    id: participant.id,
    gameName: participant.gameName,
    tagLine: participant.tagLine,
    riotId: participant.riotId,
    profileIconId: participant.profileIconId,
    currentTier: participant.currentTier,
    currentRank: participant.currentRank,
    currentLp: participant.currentLp,
    lpGained: participant.lpGained,
    wins: participant.wins,
    losses: participant.losses,
    winRate: participant.winRate,
    position: participant.position,
  };
}

function toDuoPreview(duo: DuoProgress): DuoPreview {
  return {
    id: duo.id,
    player1: toParticipantPreview(duo.player1),
    player2: toParticipantPreview(duo.player2),
    combinedLpGained: duo.combinedLpGained,
    wins: duo.wins,
    losses: duo.losses,
    winRate: duo.winRate,
    position: duo.position,
    eligible: duo.eligible,
  };
}

function normalizeParticipantPreview(participant: ParticipantPreview): ParticipantPreview {
  return {
    ...participant,
    wins: participant.wins ?? 0,
    losses: participant.losses ?? 0,
    winRate: participant.winRate ?? 0,
  };
}

function normalizeDuoPreview(duo: DuoPreview): DuoPreview {
  return {
    ...duo,
    player1: normalizeParticipantPreview(duo.player1),
    player2: normalizeParticipantPreview(duo.player2),
    wins: duo.wins ?? 0,
    losses: duo.losses ?? 0,
    winRate: duo.winRate ?? 0,
  };
}

function normalizeParticipants(raw: ParticipantPreview[] | undefined): ParticipantPreview[] {
  return Array.isArray(raw) ? raw.map(normalizeParticipantPreview) : [];
}

function normalizeDuos(raw: DuoPreview[] | undefined): DuoPreview[] {
  return Array.isArray(raw) ? raw.map(normalizeDuoPreview) : [];
}

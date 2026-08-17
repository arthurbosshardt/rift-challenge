import {
  DuoPreview,
  DuoProgress,
  ParticipantPreview,
  ParticipantProgress,
  RaceDetail,
  RaceSummary,
} from '../models/race.models';

export const RACE_PREVIEW_FETCH_LIMIT = 10;

export function resolveRaceCardPreviewLimit(viewportWidth: number): number {
  if (viewportWidth >= 1600) {
    return 10;
  }
  if (viewportWidth >= 1400) {
    return 9;
  }
  if (viewportWidth >= 1200) {
    return 8;
  }
  if (viewportWidth >= 1000) {
    return 7;
  }
  if (viewportWidth >= 820) {
    return 6;
  }
  if (viewportWidth >= 680) {
    return 5;
  }
  return 3;
}

function previewEntryCount(race: Partial<RaceSummary>): number {
  if (race.type === 'DUOQ') {
    return race.previewDuos?.length ?? 0;
  }
  return race.previewParticipants?.length ?? 0;
}

export function summaryRaceNeedsEnrichment(race: Partial<RaceSummary>): boolean {
  if (race.entryCount === undefined) {
    return true;
  }

  const entryCount = race.entryCount;
  if (entryCount <= 0) {
    return false;
  }

  const namesFromApi = race.participantGameNames?.length ?? 0;
  if (namesFromApi < entryCount) {
    return true;
  }

  const previewCount = previewEntryCount(race);
  const targetPreview = Math.min(entryCount, RACE_PREVIEW_FETCH_LIMIT);
  return previewCount < targetPreview;
}

export type RawRaceSummary = Partial<RaceSummary> &
  Pick<RaceSummary, 'id' | 'shareSlug' | 'name' | 'type' | 'startAt' | 'isPublic' | 'status'>;

export function normalizeRaceSummary(raw: RawRaceSummary): RaceSummary {
  return {
    ...raw,
    endAt: raw.endAt ?? null,
    entryCount: raw.entryCount ?? 0,
    participantGameNames: normalizeParticipantGameNames(raw),
    previewParticipants: normalizeParticipants(raw.previewParticipants),
    previewDuos: normalizeDuos(raw.previewDuos),
  };
}

function normalizeParticipantGameNames(raw: Partial<RaceSummary>): string[] {
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

export function normalizeRaceSummaries(raw: RawRaceSummary[]): RaceSummary[] {
  return raw.map(normalizeRaceSummary);
}

export function summaryNeedsPreviewEnrichment(raw: Partial<RaceSummary>[]): boolean {
  return raw.some(summaryRaceNeedsEnrichment);
}

export function enrichSummaryFromDetail(summary: RaceSummary, detail: RaceDetail): RaceSummary {
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
      previewDuos: detail.duos.slice(0, RACE_PREVIEW_FETCH_LIMIT).map(toDuoPreview),
    };
  }

  return {
    ...summary,
    entryCount: detail.participants.length,
    participantGameNames,
    previewParticipants: detail.participants.slice(0, RACE_PREVIEW_FETCH_LIMIT).map(toParticipantPreview),
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

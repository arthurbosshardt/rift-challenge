import { ChallengeDetail } from '../models/challenge.models';

export function normalizeChallengeDetail(challenge: ChallengeDetail): ChallengeDetail {
  return {
    ...challenge,
    region: challenge.region ?? 'EUW',
    endAt: challenge.endAt ?? null,
    maxGames: challenge.maxGames ?? null,
    participants: (challenge.participants ?? []).map((participant) => ({
      ...participant,
      rankScore: participant.rankScore ?? 0,
      winRate: participant.winRate ?? 0,
      profileIconId: participant.profileIconId ?? null,
      rankEstimated: participant.rankEstimated ?? false,
      recentMatches: participant.recentMatches ?? [],
    })),
    duos: (challenge.duos ?? []).map((duo) => ({
      ...duo,
      winRate: duo.winRate ?? 0,
      recentMatches: duo.recentMatches ?? [],
      player1: {
        ...duo.player1,
        profileIconId: duo.player1.profileIconId ?? null,
        recentMatches: duo.player1.recentMatches ?? [],
      },
      player2: {
        ...duo.player2,
        profileIconId: duo.player2.profileIconId ?? null,
        recentMatches: duo.player2.recentMatches ?? [],
      },
    })),
    isOwner: challenge.isOwner ?? false,
    refreshAvailable: challenge.refreshAvailable ?? false,
  };
}

/** Conserve le classement affiché quand seules les métadonnées du challenge changent. */
export function mergeChallengeMetadataUpdate(
  previous: ChallengeDetail,
  updated: ChallengeDetail,
): ChallengeDetail {
  return normalizeChallengeDetail({
    ...updated,
    startAt: updated.startAt,
    endAt: updated.endAt,
    maxGames: updated.maxGames,
    status: updated.status,
    entryCount: previous.entryCount,
    participantGameNames: previous.participantGameNames,
    previewParticipants: previous.previewParticipants,
    previewDuos: previous.previewDuos,
    participants: previous.participants,
    duos: previous.duos,
  });
}

import { RaceDetail } from '../models/race.models';

export function normalizeRaceDetail(race: RaceDetail): RaceDetail {
  return {
    ...race,
    endAt: race.endAt ?? null,
    participants: (race.participants ?? []).map((participant) => ({
      ...participant,
      rankScore: participant.rankScore ?? 0,
      winRate: participant.winRate ?? 0,
      profileIconId: participant.profileIconId ?? null,
    })),
    duos: (race.duos ?? []).map((duo) => ({
      ...duo,
      winRate: duo.winRate ?? 0,
      player1: {
        ...duo.player1,
        profileIconId: duo.player1.profileIconId ?? null,
      },
      player2: {
        ...duo.player2,
        profileIconId: duo.player2.profileIconId ?? null,
      },
    })),
    isOwner: race.isOwner ?? false,
    refreshAvailable: race.refreshAvailable ?? false,
  };
}

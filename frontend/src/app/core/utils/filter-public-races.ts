import { RaceSummary, RaceType } from '../models/race.models';
import { normalizeGameName } from './riot-id';

export type PublicRaceTypeFilter = 'ALL' | RaceType;

export type PublicRaceFilters = {
  raceName: string;
  summoner: string;
  type: PublicRaceTypeFilter;
};

function normalizeSearchTerm(value: string): string {
  return value.trim().toLowerCase().replace(/\s/g, '');
}

export function normalizeSummonerSearch(value: string): string {
  const trimmed = value.trim();
  const hashIndex = trimmed.indexOf('#');
  const gameName = hashIndex > 0 ? trimmed.slice(0, hashIndex) : trimmed;
  return normalizeSearchTerm(normalizeGameName(gameName));
}

function collectParticipantNames(race: RaceSummary): string[] {
  if (race.participantGameNames.length > 0) {
    return race.participantGameNames.map((name) => normalizeSearchTerm(name));
  }

  const names = race.previewParticipants.map((participant) =>
    normalizeSearchTerm(participant.gameName),
  );

  for (const duo of race.previewDuos) {
    names.push(normalizeSearchTerm(duo.player1.gameName));
    names.push(normalizeSearchTerm(duo.player2.gameName));
  }

  return names;
}

export function filterPublicRaces(races: RaceSummary[], filters: PublicRaceFilters): RaceSummary[] {
  const raceNameQuery = normalizeSearchTerm(filters.raceName);
  const summonerQuery = normalizeSummonerSearch(filters.summoner);

  return races.filter((race) => {
    if (filters.type !== 'ALL' && race.type !== filters.type) {
      return false;
    }

    if (raceNameQuery.length >= 3 && !normalizeSearchTerm(race.name).includes(raceNameQuery)) {
      return false;
    }

    if (summonerQuery.length >= 3) {
      const matchesSummoner = collectParticipantNames(race).some((name) =>
        name.includes(summonerQuery),
      );
      if (!matchesSummoner) {
        return false;
      }
    }

    return true;
  });
}

export function hasActivePublicRaceFilters(filters: PublicRaceFilters): boolean {
  return (
    filters.raceName.trim().length >= 3 ||
    filters.summoner.trim().length >= 3 ||
    filters.type !== 'ALL'
  );
}

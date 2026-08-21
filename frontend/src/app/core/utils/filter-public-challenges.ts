import { ChallengeRegion, ChallengeStatus, ChallengeSummary, ChallengeType } from '../models/challenge.models';
import { normalizeGameName } from './riot-id';

export type PublicChallengeTypeFilter = 'ALL' | ChallengeType;
export type PublicChallengeStatusFilter = 'ALL' | ChallengeStatus;

export const DEFAULT_PUBLIC_CHALLENGE_REGION_FILTER: ChallengeRegion = 'EUW';
export const DEFAULT_PUBLIC_CHALLENGE_STATUS_FILTER: PublicChallengeStatusFilter = 'ACTIVE';

export type PublicChallengeFilters = {
  challengeName: string;
  summoner: string;
  type: PublicChallengeTypeFilter;
  status: PublicChallengeStatusFilter;
  region: ChallengeRegion;
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

function collectParticipantNames(challenge: ChallengeSummary): string[] {
  if (challenge.participantGameNames.length > 0) {
    return challenge.participantGameNames.map((name) => normalizeSearchTerm(name));
  }

  const names = challenge.previewParticipants.map((participant) =>
    normalizeSearchTerm(participant.gameName),
  );

  for (const duo of challenge.previewDuos) {
    names.push(normalizeSearchTerm(duo.player1.gameName));
    names.push(normalizeSearchTerm(duo.player2.gameName));
  }

  return names;
}

export function filterPublicChallenges(challenges: ChallengeSummary[], filters: PublicChallengeFilters): ChallengeSummary[] {
  const challengeNameQuery = normalizeSearchTerm(filters.challengeName);
  const summonerQuery = normalizeSummonerSearch(filters.summoner);

  return challenges.filter((challenge) => {
    if (filters.type !== 'ALL' && challenge.type !== filters.type) {
      return false;
    }

    if (filters.status !== 'ALL' && challenge.status !== filters.status) {
      return false;
    }

    if (challenge.region !== filters.region) {
      return false;
    }

    if (challengeNameQuery.length >= 3 && !normalizeSearchTerm(challenge.name).includes(challengeNameQuery)) {
      return false;
    }

    if (summonerQuery.length >= 3) {
      const matchesSummoner = collectParticipantNames(challenge).some((name) =>
        name.includes(summonerQuery),
      );
      if (!matchesSummoner) {
        return false;
      }
    }

    return true;
  });
}

export function hasActivePublicChallengeFilters(filters: PublicChallengeFilters): boolean {
  return (
    filters.challengeName.trim().length >= 3 ||
    filters.summoner.trim().length >= 3 ||
    filters.type !== 'ALL' ||
    filters.status !== DEFAULT_PUBLIC_CHALLENGE_STATUS_FILTER ||
    filters.region !== DEFAULT_PUBLIC_CHALLENGE_REGION_FILTER
  );
}

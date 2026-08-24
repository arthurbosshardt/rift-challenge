import type { AppLocale } from '../i18n/translations';
import type { ChallengeRegion } from '../models/challenge.models';

export function defaultRiotTagLine(locale: AppLocale | string): string {
  return locale === 'fr' ? 'EUW' : 'NA';
}

export function normalizeGameName(gameName: string): string {
  return gameName.replace(/\s/g, '');
}

export function normalizeTagLine(tagLine: string): string {
  return tagLine.replace(/\u00A0/g, ' ').trim().replace(/^#+/, '');
}

export function normalizeRiotId(riotId: string, defaultTagLine?: string): string {
  const trimmed = riotId.replace(/\u00A0/g, ' ').trim();
  if (!trimmed) {
    return trimmed;
  }

  const hashIndex = trimmed.indexOf('#');
  if (hashIndex < 0) {
    if (defaultTagLine) {
      const gameName = normalizeGameName(trimmed);
      const tagLine = normalizeTagLine(defaultTagLine);
      if (gameName && tagLine) {
        return `${gameName}#${tagLine}`;
      }
    }
    return trimmed;
  }

  if (hashIndex === 0 || hashIndex >= trimmed.length - 1) {
    return trimmed;
  }

  const gameName = normalizeGameName(trimmed.slice(0, hashIndex));
  const tagLine = normalizeTagLine(trimmed.slice(hashIndex + 1));
  if (!gameName || !tagLine) {
    return trimmed;
  }

  return `${gameName}#${tagLine}`;
}

export function normalizeRiotIdForLocale(riotId: string, locale: AppLocale | string): string {
  return normalizeRiotId(riotId, defaultRiotTagLine(locale));
}

export function parseRiotId(
  riotId: string,
  defaultTagLine?: string,
): { gameName: string; tagLine: string } | null {
  const normalized = normalizeRiotId(riotId, defaultTagLine);
  const hashIndex = normalized.indexOf('#');
  if (hashIndex <= 0 || hashIndex >= normalized.length - 1) {
    return null;
  }

  const gameName = normalized.slice(0, hashIndex);
  const tagLine = normalized.slice(hashIndex + 1);
  if (!gameName || !tagLine) {
    return null;
  }

  return { gameName, tagLine };
}

export function parseRiotIdForLocale(
  riotId: string,
  locale: AppLocale | string,
): { gameName: string; tagLine: string } | null {
  return parseRiotId(riotId, defaultRiotTagLine(locale));
}

export function parseRiotIdForRegion(
  riotId: string,
  region: ChallengeRegion,
): { gameName: string; tagLine: string } | null {
  return parseRiotId(riotId, region);
}

export function normalizeRiotIdForRegion(riotId: string, region: ChallengeRegion): string {
  return normalizeRiotId(riotId, region);
}

export function buildRiotId(gameName: string, tagLine: string): string | null {
  const name = normalizeGameName(gameName);
  const tag = normalizeTagLine(tagLine);

  if (!name || !tag) {
    return null;
  }

  return `${name}#${tag}`;
}

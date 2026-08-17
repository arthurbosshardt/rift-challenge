export function normalizeGameName(gameName: string): string {
  return gameName.replace(/\s/g, '');
}

export function normalizeTagLine(tagLine: string): string {
  return tagLine.replace(/\u00A0/g, ' ').trim().replace(/^#+/, '');
}

export function normalizeRiotId(riotId: string): string {
  const trimmed = riotId.replace(/\u00A0/g, ' ').trim();
  const hashIndex = trimmed.indexOf('#');
  if (hashIndex <= 0 || hashIndex >= trimmed.length - 1) {
    return trimmed;
  }

  const gameName = normalizeGameName(trimmed.slice(0, hashIndex));
  const tagLine = normalizeTagLine(trimmed.slice(hashIndex + 1));
  if (!gameName || !tagLine) {
    return trimmed;
  }

  return `${gameName}#${tagLine}`;
}

export function buildRiotId(gameName: string, tagLine: string): string | null {
  const name = normalizeGameName(gameName);
  const tag = normalizeTagLine(tagLine);

  if (!name || !tag) {
    return null;
  }

  return `${name}#${tag}`;
}

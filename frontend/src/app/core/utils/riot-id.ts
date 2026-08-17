export function normalizeTagLine(tagLine: string): string {
  return tagLine.trim().replace(/^#+/, '');
}

export function buildRiotId(gameName: string, tagLine: string): string | null {
  const name = gameName.trim();
  const tag = normalizeTagLine(tagLine);

  if (!name || !tag) {
    return null;
  }

  return `${name}#${tag}`;
}

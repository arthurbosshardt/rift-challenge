export interface OrdinalParts {
  number: string;
  suffix: string;
}

export function ordinalParts(rank: number, locale: 'fr' | 'en' = 'fr'): OrdinalParts {
  if (locale === 'en') {
    const mod100 = rank % 100;
    if (mod100 >= 11 && mod100 <= 13) {
      return { number: `${rank}`, suffix: 'th' };
    }
    switch (rank % 10) {
      case 1:
        return { number: `${rank}`, suffix: 'st' };
      case 2:
        return { number: `${rank}`, suffix: 'nd' };
      case 3:
        return { number: `${rank}`, suffix: 'rd' };
      default:
        return { number: `${rank}`, suffix: 'th' };
    }
  }
  return rank === 1 ? { number: '1', suffix: 'er' } : { number: `${rank}`, suffix: 'e' };
}

export function ordinalLabel(rank: number, locale: 'fr' | 'en' = 'fr'): string {
  const { number, suffix } = ordinalParts(rank, locale);
  return `${number}${suffix}`;
}

const TIER_LABELS_FR: Record<string, string> = {
  IRON: 'Fer',
  BRONZE: 'Bronze',
  SILVER: 'Argent',
  GOLD: 'Or',
  PLATINUM: 'Platine',
  EMERALD: 'Émeraude',
  DIAMOND: 'Diamant',
  MASTER: 'Master',
  GRANDMASTER: 'Grandmaster',
  CHALLENGER: 'Challenger',
};

const TIER_LABELS_EN: Record<string, string> = {
  IRON: 'Iron',
  BRONZE: 'Bronze',
  SILVER: 'Silver',
  GOLD: 'Gold',
  PLATINUM: 'Platinum',
  EMERALD: 'Emerald',
  DIAMOND: 'Diamond',
  MASTER: 'Master',
  GRANDMASTER: 'Grandmaster',
  CHALLENGER: 'Challenger',
};

const HIGH_TIERS = new Set(['MASTER', 'GRANDMASTER', 'CHALLENGER']);

const TIER_COLORS: Record<string, string> = {
  IRON: '#5b5559',
  BRONZE: '#8c5a34',
  SILVER: '#9fa8b2',
  GOLD: '#d4af37',
  PLATINUM: '#3fa79b',
  EMERALD: '#2fbf71',
  DIAMOND: '#5aa5e0',
  MASTER: '#a355d9',
  GRANDMASTER: '#e0555a',
  CHALLENGER: '#f0e08c',
};

export function tierColor(tier: string | null | undefined): string {
  if (!tier) {
    return 'var(--text-muted)';
  }
  return TIER_COLORS[tier.toUpperCase()] ?? 'var(--text-muted)';
}

export function tierLabel(
  tier: string | null | undefined,
  locale: 'fr' | 'en' = 'fr',
): string {
  if (!tier) {
    return locale === 'en' ? 'Unranked' : 'Non classé';
  }
  const labels = locale === 'en' ? TIER_LABELS_EN : TIER_LABELS_FR;
  return labels[tier.toUpperCase()] ?? tier;
}

export function tierLabelFr(tier: string | null | undefined): string {
  return tierLabel(tier, 'fr');
}

export function rankEmblemUrl(tier: string | null | undefined): string | null {
  if (!tier) {
    return null;
  }
  return `https://opgg-static.akamaized.net/images/medals_mini/${tier.toLowerCase()}.png`;
}

export function formatRankLabel(
  tier: string | null | undefined,
  rank: string | null | undefined,
  lp: number,
  locale: 'fr' | 'en' = 'fr',
  includeLp = true,
): string {
  if (!tier) {
    return locale === 'en' ? 'Unranked' : 'Non classé';
  }

  const labeledTier = tierLabel(tier, locale);
  if (HIGH_TIERS.has(tier.toUpperCase())) {
    return includeLp ? `${labeledTier} · ${lp} LP` : labeledTier;
  }

  const division = rank ? ` ${rank}` : '';
  const rankLabel = `${labeledTier}${division}`;
  return includeLp ? `${rankLabel} · ${lp} LP` : rankLabel;
}

export function formatFinishedRankLabel(
  tier: string | null | undefined,
  rank: string | null | undefined,
  lp: number,
  locale: 'fr' | 'en' = 'fr',
): string {
  const rankText = formatRankLabel(tier, rank, lp, locale, false);
  if (locale === 'en') {
    return `finished at ${rankText}`;
  }
  return `a terminé ${rankText}`;
}

export function formatDurationCountdown(
  targetIso: string,
  nowMs = Date.now(),
  locale: 'fr' | 'en' = 'fr',
): string | null {
  const remainingMs = new Date(targetIso).getTime() - nowMs;
  if (Number.isNaN(remainingMs) || remainingMs <= 0) {
    return null;
  }

  const totalSeconds = Math.ceil(remainingMs / 1000);
  const days = Math.floor(totalSeconds / 86_400);
  const hours = Math.floor((totalSeconds % 86_400) / 3_600);
  const minutes = Math.floor((totalSeconds % 3_600) / 60);
  const seconds = totalSeconds % 60;
  const dayUnit = locale === 'en' ? 'd' : 'j';

  if (days > 0) {
    return `${days}${dayUnit} ${hours}h ${minutes.toString().padStart(2, '0')}m`;
  }
  if (hours > 0) {
    return `${hours}h ${minutes.toString().padStart(2, '0')}m ${seconds.toString().padStart(2, '0')}s`;
  }
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

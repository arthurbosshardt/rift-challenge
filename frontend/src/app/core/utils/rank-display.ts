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

const HIGH_TIERS = new Set(['MASTER', 'GRANDMASTER', 'CHALLENGER']);

export function tierLabelFr(tier: string | null | undefined): string {
  if (!tier) {
    return 'Non classé';
  }
  return TIER_LABELS_FR[tier.toUpperCase()] ?? tier;
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
): string {
  if (!tier) {
    return 'Non classé';
  }

  const tierFr = tierLabelFr(tier);
  if (HIGH_TIERS.has(tier.toUpperCase())) {
    return `${tierFr} · ${lp} LP`;
  }

  const division = rank ? ` ${rank}` : '';
  return `${tierFr}${division} · ${lp} LP`;
}

export function formatDurationCountdown(targetIso: string, nowMs = Date.now()): string | null {
  const remainingMs = new Date(targetIso).getTime() - nowMs;
  if (Number.isNaN(remainingMs) || remainingMs <= 0) {
    return null;
  }

  const totalSeconds = Math.ceil(remainingMs / 1000);
  const days = Math.floor(totalSeconds / 86_400);
  const hours = Math.floor((totalSeconds % 86_400) / 3_600);
  const minutes = Math.floor((totalSeconds % 3_600) / 60);
  const seconds = totalSeconds % 60;

  if (days > 0) {
    return `${days}j ${hours}h ${minutes.toString().padStart(2, '0')}m`;
  }
  if (hours > 0) {
    return `${hours}h ${minutes.toString().padStart(2, '0')}m ${seconds.toString().padStart(2, '0')}s`;
  }
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

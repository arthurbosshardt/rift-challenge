export function formatRefreshCountdown(nextRefreshAvailableAt: string, nowMs = Date.now()): string | null {
  const remainingMs = new Date(nextRefreshAvailableAt).getTime() - nowMs;
  if (Number.isNaN(remainingMs) || remainingMs <= 0) {
    return null;
  }

  const totalSeconds = Math.ceil(remainingMs / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

export function isRefreshCooldownActive(race: {
  refreshAvailable: boolean;
  nextRefreshAvailableAt: string | null;
}): boolean {
  if (race.refreshAvailable || !race.nextRefreshAvailableAt) {
    return false;
  }
  return formatRefreshCountdown(race.nextRefreshAvailableAt) !== null;
}

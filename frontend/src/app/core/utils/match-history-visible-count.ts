export const MATCH_HISTORY_SOLO_ENTRY_WIDTH = 3.75;
export const MATCH_HISTORY_DUO_ENTRY_WIDTH = 7.75;
export const MATCH_HISTORY_DAY_GROUP_GAP_REM = 0.625;
export const MATCH_HISTORY_HINT_RESERVE_REM = 0;

export function resolveVisibleMatchHistoryCount(
  trackWidthPx: number,
  entryWidthRem: number,
  total: number,
  rootFontSizePx = 16,
  hintReserveRem = MATCH_HISTORY_HINT_RESERVE_REM,
): number {
  if (total <= 0 || trackWidthPx <= 0 || entryWidthRem <= 0) {
    return 0;
  }

  const entryWidthPx = entryWidthRem * rootFontSizePx;
  const hintReservePx = hintReserveRem * rootFontSizePx;
  const available = Math.max(0, trackWidthPx - hintReservePx);
  const capacity = Math.floor(available / entryWidthPx);

  if (capacity <= 0) {
    return Math.min(total, 1);
  }

  return Math.min(total, capacity);
}

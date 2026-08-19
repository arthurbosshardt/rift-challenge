export const CHALLENGE_CARD_PREVIEW_MIN_COLUMN_WIDTH = 280;
export const CHALLENGE_CARD_PREVIEW_COLUMN_MAX_ITEMS = 9;
export const CHALLENGE_CARD_PODIUM_COLUMN_SIZE = 3;
export const CHALLENGE_CARD_PREVIEW_SLOT_COLUMNS = 3;
export const CHALLENGE_CARD_PREVIEW_NARROW_VISIBLE_COUNT = 3;

function resolveMaxColumnsForWidth(viewportWidth: number): number {
  if (viewportWidth <= 0) {
    return 1;
  }
  return Math.max(1, Math.min(3, Math.floor(viewportWidth / CHALLENGE_CARD_PREVIEW_MIN_COLUMN_WIDTH)));
}

export function resolveChallengeCardPreviewVisibleCount(
  viewportWidth: number,
  totalCount: number,
): number {
  if (totalCount <= 0) {
    return 0;
  }
  if (viewportWidth < CHALLENGE_CARD_PREVIEW_MIN_COLUMN_WIDTH) {
    return Math.min(CHALLENGE_CARD_PREVIEW_NARROW_VISIBLE_COUNT, totalCount);
  }
  return totalCount;
}

export function resolveChallengeCardPreviewColumnCount(
  viewportWidth: number,
  displayedCount: number,
): number {
  if (displayedCount <= 0 || displayedCount > CHALLENGE_CARD_PREVIEW_COLUMN_MAX_ITEMS) {
    return 1;
  }

  const maxColumns = resolveMaxColumnsForWidth(viewportWidth);
  if (maxColumns >= 3 && displayedCount > CHALLENGE_CARD_PODIUM_COLUMN_SIZE * 2) {
    return 3;
  }
  if (maxColumns >= 2 && displayedCount > CHALLENGE_CARD_PODIUM_COLUMN_SIZE) {
    return 2;
  }
  return 1;
}

export function challengeCardPreviewGridColumn(index: number, columnCount: number): number {
  if (columnCount <= 1) {
    return 1;
  }
  if (index < CHALLENGE_CARD_PODIUM_COLUMN_SIZE) {
    return 1;
  }
  if (columnCount === 2) {
    return 2;
  }
  if (index < 6) {
    return 2;
  }
  return 3;
}

export function groupChallengeCardPreviewItems<T>(items: readonly T[], columnCount: number): T[][] {
  if (items.length === 0) {
    return [];
  }

  const columns: T[][] = Array.from({ length: columnCount }, () => []);

  for (let index = 0; index < items.length; index++) {
    const columnIndex = challengeCardPreviewGridColumn(index, columnCount) - 1;
    columns[columnIndex].push(items[index]);
  }

  return columns.filter((column) => column.length > 0);
}

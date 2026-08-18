export const CHALLENGE_CARD_PREVIEW_NARROW_MAX_WIDTH = 420;
export const CHALLENGE_CARD_LAYOUT_COMPACT_MAX_WIDTH = 760;
export const CHALLENGE_CARD_DESKTOP_MIN_WIDTH = 900;
export const CHALLENGE_CARD_PREVIEW_TWO_COLUMN_MIN_WIDTH = 421;
export const CHALLENGE_CARD_PREVIEW_THIRD_COLUMN_MIN_WIDTH = 680;
export const CHALLENGE_CARD_PREVIEW_COLUMN_MAX_ITEMS = 9;
export const CHALLENGE_CARD_PODIUM_COLUMN_SIZE = 3;
export const CHALLENGE_CARD_PREVIEW_SLOT_COLUMNS = 3;
export const CHALLENGE_CARD_PREVIEW_NARROW_VISIBLE_COUNT = 3;

function isCompactChallengeCardPreviewLayout(
  cardWidth: number | undefined,
  viewportWidth: number,
): boolean {
  if (viewportWidth <= CHALLENGE_CARD_PREVIEW_NARROW_MAX_WIDTH) {
    return true;
  }
  if (
    viewportWidth < CHALLENGE_CARD_DESKTOP_MIN_WIDTH &&
    cardWidth !== undefined &&
    cardWidth > 0 &&
    cardWidth <= CHALLENGE_CARD_LAYOUT_COMPACT_MAX_WIDTH
  ) {
    return true;
  }
  return false;
}

export function resolveChallengeCardPreviewVisibleCount(
  viewportWidth: number,
  totalCount: number,
  cardWidth?: number,
): number {
  if (totalCount <= 0) {
    return 0;
  }
  if (isCompactChallengeCardPreviewLayout(cardWidth, viewportWidth)) {
    return Math.min(CHALLENGE_CARD_PREVIEW_NARROW_VISIBLE_COUNT, totalCount);
  }
  return totalCount;
}

export function resolveChallengeCardPreviewColumnCount(
  viewportWidth: number,
  displayedCount: number,
  cardWidth?: number,
): number {
  if (displayedCount <= 0 || isCompactChallengeCardPreviewLayout(cardWidth, viewportWidth)) {
    return 1;
  }
  if (displayedCount > CHALLENGE_CARD_PREVIEW_COLUMN_MAX_ITEMS) {
    return 1;
  }
  if (
    viewportWidth >= CHALLENGE_CARD_PREVIEW_THIRD_COLUMN_MIN_WIDTH &&
    displayedCount > CHALLENGE_CARD_PODIUM_COLUMN_SIZE * 2
  ) {
    return 3;
  }
  return 2;
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

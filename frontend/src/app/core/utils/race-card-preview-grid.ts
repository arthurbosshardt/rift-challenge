export const RACE_CARD_PREVIEW_COLUMN_MIN_WIDTH = 900;
export const RACE_CARD_PREVIEW_THIRD_COLUMN_MIN_WIDTH = 1280;
export const RACE_CARD_PREVIEW_COLUMN_MAX_ITEMS = 9;
export const RACE_CARD_PODIUM_COLUMN_SIZE = 3;

export function resolveRaceCardPreviewColumnCount(
  viewportWidth: number,
  displayedCount: number,
): number {
  if (displayedCount <= 0 || viewportWidth < RACE_CARD_PREVIEW_COLUMN_MIN_WIDTH) {
    return 1;
  }
  if (displayedCount > RACE_CARD_PREVIEW_COLUMN_MAX_ITEMS) {
    return 1;
  }
  if (viewportWidth >= RACE_CARD_PREVIEW_THIRD_COLUMN_MIN_WIDTH && displayedCount > 6) {
    return 3;
  }
  return 2;
}

export function shouldUseRaceCardPreviewColumns(
  viewportWidth: number,
  displayedCount: number,
): boolean {
  return resolveRaceCardPreviewColumnCount(viewportWidth, displayedCount) > 1;
}

export function raceCardPreviewGridColumn(
  index: number,
  columnCount: number,
): number | null {
  if (columnCount <= 1) {
    return null;
  }
  if (index < RACE_CARD_PODIUM_COLUMN_SIZE) {
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

export function raceCardPreviewGridRow(index: number, columnCount: number): number | null {
  if (columnCount <= 1) {
    return null;
  }
  if (index < RACE_CARD_PODIUM_COLUMN_SIZE) {
    return index + 1;
  }
  if (columnCount === 2) {
    return index - 2;
  }
  if (index < 6) {
    return index - 2;
  }
  return index - 5;
}

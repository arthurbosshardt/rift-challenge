export const RACE_CARD_PREVIEW_COLUMN_MIN_WIDTH = 900;
export const RACE_CARD_PREVIEW_COLUMN_MAX_ITEMS = 8;
export const RACE_CARD_PODIUM_COLUMN_SIZE = 3;

export function shouldUseRaceCardPreviewColumns(
  viewportWidth: number,
  displayedCount: number,
): boolean {
  return (
    viewportWidth >= RACE_CARD_PREVIEW_COLUMN_MIN_WIDTH &&
    displayedCount > 0 &&
    displayedCount <= RACE_CARD_PREVIEW_COLUMN_MAX_ITEMS
  );
}

export function raceCardPreviewGridColumn(
  index: number,
  useColumns: boolean,
): number | null {
  if (!useColumns) {
    return null;
  }
  return index < RACE_CARD_PODIUM_COLUMN_SIZE ? 1 : 2;
}

export function raceCardPreviewGridRow(index: number, useColumns: boolean): number | null {
  if (!useColumns) {
    return null;
  }
  return index < RACE_CARD_PODIUM_COLUMN_SIZE ? index + 1 : index - 2;
}

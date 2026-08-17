const COMMUNITY_DRAGON_CHAMPION_ICON_BASE =
  'https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champion-icons';
const COMMUNITY_DRAGON_CDN_CHAMPION_ICON_BASE =
  'https://cdn.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champion-icons';

export function championIconUrls(championId: number | null | undefined): string[] {
  if (championId == null || championId <= 0) {
    return [];
  }

  return [
    `${COMMUNITY_DRAGON_CHAMPION_ICON_BASE}/${championId}.png`,
    `${COMMUNITY_DRAGON_CDN_CHAMPION_ICON_BASE}/${championId}.png`,
  ];
}

export function championIconUrl(championId: number | null | undefined): string | null {
  const urls = championIconUrls(championId);
  return urls[0] ?? null;
}

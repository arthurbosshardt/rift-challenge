const DATA_DRAGON_VERSION = '16.16.1';

export function profileIconUrls(profileIconId: number | null | undefined): string[] {
  if (profileIconId == null) {
    return [];
  }
  return [
    `https://ddragon.leagueoflegends.com/cdn/${DATA_DRAGON_VERSION}/img/profileicon/${profileIconId}.png`,
    `https://raw.communitydragon.net/latest/plugins/rcp-be-lol-game-data/global/default/v1/profile-icons/${profileIconId}.jpg`,
  ];
}

export function profileIconUrl(profileIconId: number | null | undefined): string | null {
  const urls = profileIconUrls(profileIconId);
  return urls[0] ?? null;
}

export function profileIconInitial(gameName: string | null | undefined): string {
  if (!gameName) {
    return '?';
  }
  return gameName.charAt(0).toUpperCase();
}

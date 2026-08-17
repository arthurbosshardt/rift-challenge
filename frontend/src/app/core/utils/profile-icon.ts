const DATA_DRAGON_VERSION = '16.16.1';

export function profileIconUrl(profileIconId: number | null | undefined): string | null {
  if (profileIconId == null) {
    return null;
  }
  return `https://ddragon.leagueoflegends.com/cdn/${DATA_DRAGON_VERSION}/img/profileicon/${profileIconId}.png`;
}

export function profileIconInitial(gameName: string | null | undefined): string {
  if (!gameName) {
    return '?';
  }
  return gameName.charAt(0).toUpperCase();
}

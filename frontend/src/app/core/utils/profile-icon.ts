import { DATA_DRAGON_VERSION, DDRAGON_CDN_BASE, COMMUNITY_DRAGON_PROFILE_ICON_BASE } from '../constants/ddragon-constants';

export function profileIconUrls(profileIconId: number | null | undefined): string[] {
  if (profileIconId == null) {
    return [];
  }
  return [
    `${DDRAGON_CDN_BASE}/cdn/${DATA_DRAGON_VERSION}/img/profileicon/${profileIconId}.png`,
    `${COMMUNITY_DRAGON_PROFILE_ICON_BASE}/${profileIconId}.jpg`,
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

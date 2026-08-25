import { COMMUNITY_DRAGON_CHAMPION_SPLASH_BASE } from '../constants/ddragon-constants';

export function championSplashUrl(championId: number | null | undefined): string | null {
  if (championId == null || championId <= 0) {
    return null;
  }
  return `${COMMUNITY_DRAGON_CHAMPION_SPLASH_BASE}/${championId}/splash-art/centered`;
}

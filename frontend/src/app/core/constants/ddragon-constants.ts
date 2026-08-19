/**
 * League of Legends Data Dragon CDN URLs and constants.
 * Includes official Data Dragon CDN URLs and Community Dragon fallback URLs.
 */

// Data Dragon CDN base URL for official League of Legends assets
export const DDRAGON_CDN_BASE = 'https://ddragon.leagueoflegends.com';

// Data Dragon API base URL for accessing league data
export const DDRAGON_API_BASE = `${DDRAGON_CDN_BASE}/api`;

// Data Dragon version used for profile icons and other versioned assets
export const DATA_DRAGON_VERSION = '16.16.1';

/**
 * Community Dragon CDN URLs for champion icons.
 * Provides primary and fallback CDN URLs for redundancy.
 */
export const COMMUNITY_DRAGON_CHAMPION_ICON_BASE =
  'https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champion-icons';
export const COMMUNITY_DRAGON_CDN_CHAMPION_ICON_BASE =
  'https://cdn.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champion-icons';

/**
 * Community Dragon base URL for profile icons.
 */
export const COMMUNITY_DRAGON_PROFILE_ICON_BASE =
  'https://raw.communitydragon.net/latest/plugins/rcp-be-lol-game-data/global/default/v1/profile-icons';

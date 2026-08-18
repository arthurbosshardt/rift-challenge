/** Single source for the official Rift Challenge logo served from `frontend/public/logo.svg`. */
export const BRAND_LOGO_PATH = '/logo.svg';

export function brandLogoUrl(version = '20260818'): string {
  return `${BRAND_LOGO_PATH}?v=${version}`;
}

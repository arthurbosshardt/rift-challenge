import { Injectable, inject } from '@angular/core';
import { TitleStrategy, RouterStateSnapshot } from '@angular/router';
import { I18nService } from '../i18n/i18n.service';
import { SeoPageConfig, SeoService } from './seo.service';

export interface RouteSeoData {
  /** i18n key for document title (without brand suffix). */
  titleKey?: string;
  /** i18n key for meta description. */
  descriptionKey?: string;
  /** Absolute path for the canonical URL. */
  path?: string;
  /** When true, route sets its own SEO after data loads. */
  defer?: boolean;
  noindex?: boolean;
}

@Injectable()
export class AppTitleStrategy extends TitleStrategy {
  private readonly seo = inject(SeoService);
  private readonly i18n = inject(I18nService);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const data = this.deepestSeoData(snapshot);
    if (data?.defer) {
      return;
    }

    const path = data?.path ?? this.pathFromSnapshot(snapshot);
    const noindex = data?.noindex === true;
    const title = data?.titleKey
      ? `${this.i18n.t(data.titleKey)} | Rift Challenge`
      : 'Rift Challenge — défis SoloQ et DuoQ League of Legends';
    const description = data?.descriptionKey
      ? this.i18n.t(data.descriptionKey)
      : this.i18n.t('seo.home.description');

    const config: SeoPageConfig = { title, description, path, noindex };
    this.seo.apply(config);
  }

  private deepestSeoData(snapshot: RouterStateSnapshot): RouteSeoData | null {
    let node = snapshot.root;
    let seo: RouteSeoData | null = null;
    while (node) {
      const candidate = node.data?.['seo'] as RouteSeoData | undefined;
      if (candidate) {
        seo = candidate;
      }
      node = node.firstChild!;
    }
    return seo;
  }

  private pathFromSnapshot(snapshot: RouterStateSnapshot): string {
    const url = snapshot.url || '/';
    return url.startsWith('/') ? url : `/${url}`;
  }
}

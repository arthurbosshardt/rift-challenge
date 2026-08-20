import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { I18nService } from '../i18n/i18n.service';
import { DATA_DRAGON_VERSION, DDRAGON_API_BASE, DDRAGON_CDN_BASE } from '../constants/ddragon-constants';

interface DataDragonItem {
  name: string;
  description: string;
  plaintext: string;
}

interface DataDragonItemPayload {
  data: Record<string, DataDragonItem>;
}

@Injectable({ providedIn: 'root' })
export class ItemDataService {
  private readonly i18n = inject(I18nService);

  private readonly cache = new Map<string, Record<string, DataDragonItem>>();
  private readonly pending = new Map<string, Promise<void>>();
  private readonly items = signal<Record<string, DataDragonItem>>({});
  private resolvedVersion: string | null = null;

  constructor() {
    effect(() => {
      void this.ensureLoaded(this.i18n.locale() === 'en' ? 'en_US' : 'fr_FR');
    });
  }

  readonly ready = computed(() => Object.keys(this.items()).length > 0);

  details(itemId: number | null): { name: string; description: string } | null {
    if (!itemId) {
      return null;
    }
    const entry = this.items()[String(itemId)];
    if (!entry) {
      return null;
    }
    const description = this.stripHtml(entry.description) || entry.plaintext?.trim() || '';
    return { name: entry.name, description };
  }

  ensureLoaded(ddragonLocale = this.i18n.locale() === 'en' ? 'en_US' : 'fr_FR'): Promise<void> {
    const cached = this.cache.get(ddragonLocale);
    if (cached) {
      this.items.set(cached);
      return Promise.resolve();
    }

    const inFlight = this.pending.get(ddragonLocale);
    if (inFlight) {
      return inFlight;
    }

    const loadPromise = this.load(ddragonLocale).finally(() => {
      this.pending.delete(ddragonLocale);
    });
    this.pending.set(ddragonLocale, loadPromise);
    return loadPromise;
  }

  private async load(ddragonLocale: string): Promise<void> {
    try {
      const version = await this.resolveVersion();
      // Use fetch (not HttpClient) so the auth interceptor never attaches a
      // Bearer token — that would force a CORS preflight Data Dragon rejects.
      const response = await fetch(`${DDRAGON_CDN_BASE}/cdn/${version}/data/${ddragonLocale}/item.json`);
      if (!response.ok) {
        return;
      }

      const payload = (await response.json()) as DataDragonItemPayload;
      const data = payload.data ?? {};
      this.cache.set(ddragonLocale, data);
      this.items.set(data);
    } catch {
      // Tooltips stay unavailable until a later retry.
    }
  }

  private async resolveVersion(): Promise<string> {
    if (this.resolvedVersion) {
      return this.resolvedVersion;
    }

    try {
      const response = await fetch(`${DDRAGON_API_BASE}/versions.json`);
      if (response.ok) {
        const versions = (await response.json()) as string[];
        if (versions[0]) {
          this.resolvedVersion = versions[0];
          return this.resolvedVersion;
        }
      }
    } catch {
      // Fall back to the pinned constant below.
    }

    this.resolvedVersion = DATA_DRAGON_VERSION;
    return this.resolvedVersion;
  }

  private stripHtml(html: string | undefined): string {
    if (!html) {
      return '';
    }
    return html
      .replace(/<br\s*\/?>/gi, '\n')
      .replace(/<[^>]*>/g, '')
      .replace(/[ \t]+/g, ' ')
      .replace(/\n{2,}/g, '\n')
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean)
      .join('\n')
      .trim();
  }
}

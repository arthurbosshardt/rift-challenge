import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { I18nService } from '../i18n/i18n.service';

const DDRAGON_VERSION = '16.16.1';

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
  private readonly http = inject(HttpClient);
  private readonly i18n = inject(I18nService);

  private readonly cache = new Map<string, Record<string, DataDragonItem>>();
  private readonly pending = new Set<string>();
  private readonly items = signal<Record<string, DataDragonItem>>({});

  constructor() {
    effect(() => {
      this.load(this.i18n.locale() === 'en' ? 'en_US' : 'fr_FR');
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

  private load(ddragonLocale: string): void {
    const cached = this.cache.get(ddragonLocale);
    if (cached) {
      this.items.set(cached);
      return;
    }
    if (this.pending.has(ddragonLocale)) {
      return;
    }
    this.pending.add(ddragonLocale);

    this.http
      .get<DataDragonItemPayload>(
        `https://ddragon.leagueoflegends.com/cdn/${DDRAGON_VERSION}/data/${ddragonLocale}/item.json`,
      )
      .subscribe({
        next: (payload) => {
          const data = payload.data ?? {};
          this.cache.set(ddragonLocale, data);
          this.items.set(data);
          this.pending.delete(ddragonLocale);
        },
        error: () => {
          this.pending.delete(ddragonLocale);
        },
      });
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

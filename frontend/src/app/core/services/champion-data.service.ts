import { Injectable, signal } from '@angular/core';

const COMMUNITY_DRAGON_CHAMPION_ICON_BASE =
  'https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champion-icons';
const COMMUNITY_DRAGON_CDN_CHAMPION_ICON_BASE =
  'https://cdn.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champion-icons';

interface DdragonChampionEntry {
  key: string;
  id: string;
}

interface DdragonChampionResponse {
  data: Record<string, DdragonChampionEntry>;
}

@Injectable({ providedIn: 'root' })
export class ChampionDataService {
  private readonly idToDdragonName = signal<Map<number, string>>(new Map());
  private readonly ddragonVersion = signal<string | null>(null);
  private loadPromise: Promise<void> | null = null;

  readonly ready = this.idToDdragonName.asReadonly();

  iconUrls(championId: number | null | undefined): string[] {
    if (championId == null || championId <= 0) {
      return [];
    }

    const urls: string[] = [];
    const name = this.idToDdragonName().get(championId);
    const version = this.ddragonVersion();
    if (name && version) {
      urls.push(`https://ddragon.leagueoflegends.com/cdn/${version}/img/champion/${name}.png`);
    }

    urls.push(
      `${COMMUNITY_DRAGON_CHAMPION_ICON_BASE}/${championId}.png`,
      `${COMMUNITY_DRAGON_CDN_CHAMPION_ICON_BASE}/${championId}.png`,
    );

    return urls;
  }

  ensureLoaded(): Promise<void> {
    if (!this.loadPromise) {
      this.loadPromise = this.load();
    }
    return this.loadPromise;
  }

  private async load(): Promise<void> {
    try {
      const versionsResponse = await fetch('https://ddragon.leagueoflegends.com/api/versions.json');
      if (!versionsResponse.ok) {
        return;
      }

      const versions = (await versionsResponse.json()) as string[];
      const version = versions[0];
      if (!version) {
        return;
      }

      const championsResponse = await fetch(
        `https://ddragon.leagueoflegends.com/cdn/${version}/data/en_US/champion.json`,
      );
      if (!championsResponse.ok) {
        return;
      }

      const payload = (await championsResponse.json()) as DdragonChampionResponse;
      const mapping = new Map<number, string>();
      for (const champion of Object.values(payload.data)) {
        const numericId = Number(champion.key);
        if (Number.isFinite(numericId) && numericId > 0) {
          mapping.set(numericId, champion.id);
        }
      }

      this.ddragonVersion.set(version);
      this.idToDdragonName.set(mapping);
    } catch {
      // Community Dragon URLs remain available without Data Dragon.
    }
  }
}

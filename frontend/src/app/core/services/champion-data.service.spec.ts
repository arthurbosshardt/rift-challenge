import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ChampionDataService } from './champion-data.service';
import {
  COMMUNITY_DRAGON_CDN_CHAMPION_ICON_BASE,
  COMMUNITY_DRAGON_CHAMPION_ICON_BASE,
  DDRAGON_API_BASE,
  DDRAGON_CDN_BASE,
} from '../constants/champion-icons';

describe('ChampionDataService', () => {
  let fetchMock: ReturnType<typeof vi.fn>;
  let service: ChampionDataService;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    service = new ChampionDataService();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('initializes with an empty readonly mapping and fallback-only icon urls', () => {
    expect(service.ready()).toEqual(new Map());
    expect(service.iconUrls(null)).toEqual([]);
    expect(service.iconUrls(undefined)).toEqual([]);
    expect(service.iconUrls(0)).toEqual([]);
    expect(service.iconUrls(-1)).toEqual([]);
    expect(service.iconUrls(266)).toEqual([
      `${COMMUNITY_DRAGON_CHAMPION_ICON_BASE}/266.png`,
      `${COMMUNITY_DRAGON_CDN_CHAMPION_ICON_BASE}/266.png`,
    ]);
  });

  it('loads version and champion data, then prepends Data Dragon icon urls', async () => {
    fetchMock
      .mockResolvedValueOnce({ ok: true, json: async () => ['15.16.1'] })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          data: {
            Aatrox: { key: '266', id: 'Aatrox' },
            Bad: { key: 'not-a-number', id: 'Ignored' },
          },
        }),
      });

    await service.ensureLoaded();

    expect(fetchMock).toHaveBeenNthCalledWith(1, `${DDRAGON_API_BASE}/versions.json`);
    expect(fetchMock).toHaveBeenNthCalledWith(2, `${DDRAGON_CDN_BASE}/cdn/15.16.1/data/en_US/champion.json`);
    expect(service.ready().get(266)).toBe('Aatrox');
    expect(service.iconUrls(266)).toEqual([
      `${DDRAGON_CDN_BASE}/cdn/15.16.1/img/champion/Aatrox.png`,
      `${COMMUNITY_DRAGON_CHAMPION_ICON_BASE}/266.png`,
      `${COMMUNITY_DRAGON_CDN_CHAMPION_ICON_BASE}/266.png`,
    ]);
  });

  it('reuses the same in-flight load promise', async () => {
    let resolveVersions: ((value: unknown) => void) | undefined;
    fetchMock.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveVersions = resolve;
        }),
    );

    const first = service.ensureLoaded();
    const second = service.ensureLoaded();

    expect(first).toBe(second);
    resolveVersions?.({ ok: false, json: async () => [] });
    await first;
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('stops loading when versions response is not ok or empty', async () => {
    fetchMock.mockResolvedValueOnce({ ok: false, json: async () => [] });
    await service.ensureLoaded();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(service.ready()).toEqual(new Map());

    fetchMock.mockReset();
    service = new ChampionDataService();
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockResolvedValueOnce({ ok: true, json: async () => [] });
    await service.ensureLoaded();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(service.iconUrls(266)).toEqual([
      `${COMMUNITY_DRAGON_CHAMPION_ICON_BASE}/266.png`,
      `${COMMUNITY_DRAGON_CDN_CHAMPION_ICON_BASE}/266.png`,
    ]);
  });

  it('keeps fallback behavior when champion response is not ok or fetch rejects', async () => {
    fetchMock
      .mockResolvedValueOnce({ ok: true, json: async () => ['15.16.1'] })
      .mockResolvedValueOnce({ ok: false, json: async () => ({}) });

    await expect(service.ensureLoaded()).resolves.toBeUndefined();
    expect(service.ready()).toEqual(new Map());

    fetchMock.mockReset();
    service = new ChampionDataService();
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockRejectedValueOnce(new Error('network error'));

    await expect(service.ensureLoaded()).resolves.toBeUndefined();
    expect(service.iconUrls(999)).toEqual([
      `${COMMUNITY_DRAGON_CHAMPION_ICON_BASE}/999.png`,
      `${COMMUNITY_DRAGON_CDN_CHAMPION_ICON_BASE}/999.png`,
    ]);
  });
});

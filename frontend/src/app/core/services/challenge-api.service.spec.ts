import { of, throwError, firstValueFrom } from 'rxjs';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { ChallengeApiService } from './challenge-api.service';
import type { ChallengeDetail, ChallengeSummary, RecentGameResponse } from '../models/challenge.models';

vi.mock('../../../environments/environment', () => ({
  environment: {
    apiBaseUrl: 'https://api.test/',
  },
}));

describe('ChallengeApiService', () => {
  let http: {
    get: ReturnType<typeof vi.fn>;
    post: ReturnType<typeof vi.fn>;
    patch: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };
  let service: ChallengeApiService;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-18T12:00:00Z'));
    http = {
      get: vi.fn(),
      post: vi.fn(),
      patch: vi.fn(),
      delete: vi.fn(),
    };
    service = new ChallengeApiService(http as never);
  });

  it('constructs with the expected base url', () => {
    expect(service).toBeInstanceOf(ChallengeApiService);
    expect((service as unknown as { baseUrl: string }).baseUrl).toBe('https://api.test/api/challenges');
  });

  it('normalizes public challenges and supports cache busting', async () => {
    const raw = [
      {
        id: '1',
        shareSlug: 'slug',
        name: 'Test',
        type: 'SOLOQ',
        startAt: '2026-01-01T00:00:00Z',
        isPublic: true,
        status: 'ACTIVE',
        previewParticipants: [
          {
            id: 'p1',
            gameName: 'Alice',
            tagLine: 'EUW',
            riotId: 'Alice#EUW',
            profileIconId: null,
            currentTier: null,
            currentRank: null,
            currentLp: 0,
            lpGained: 0,
            position: 1,
          },
        ],
      },
    ];
    http.get.mockReturnValueOnce(of(raw)).mockReturnValueOnce(of([]));

    const normal = await firstValueFrom(service.listPublicChallenges());
    const refreshed = await firstValueFrom(service.listPublicChallenges(true));

    expect(normal[0]).toMatchObject<Partial<ChallengeSummary>>({
      participantGameNames: ['Alice'],
      previewDuos: [],
      entryCount: 0,
    });
    expect(http.get).toHaveBeenNthCalledWith(1, 'https://api.test/api/challenges/public');
    expect(http.get).toHaveBeenNthCalledWith(2, 'https://api.test/api/challenges/public?_=1787054400000');
    expect(refreshed).toEqual([]);
  });

  it('lists owned, participating and my challenges through the expected endpoints', async () => {
    http.get.mockReturnValue(of([]));

    await firstValueFrom(service.listOwnedChallenges());
    await firstValueFrom(service.listParticipatingChallenges());
    await firstValueFrom(service.listMyChallenges());

    expect(http.get).toHaveBeenNthCalledWith(1, 'https://api.test/api/challenges/owned');
    expect(http.get).toHaveBeenNthCalledWith(2, 'https://api.test/api/challenges/participating');
    expect(http.get).toHaveBeenNthCalledWith(3, 'https://api.test/api/challenges/owned');
  });

  it('encodes share slug and propagates observable errors', async () => {
    const expected = { id: '1' } as ChallengeDetail;
    http.get.mockReturnValueOnce(of(expected)).mockReturnValueOnce(throwError(() => new Error('boom')));

    await expect(firstValueFrom(service.getChallengeByShareSlug('a/b c', true))).resolves.toEqual(expected);
    await expect(firstValueFrom(service.getChallengeByShareSlug('slug'))).rejects.toThrow('boom');

    expect(http.get).toHaveBeenNthCalledWith(1, 'https://api.test/api/challenges/share/a%2Fb%20c?_=1787054400000');
    expect(http.get).toHaveBeenNthCalledWith(2, 'https://api.test/api/challenges/share/slug');
  });

  it('posts, patches and deletes challenge resources with provided payloads', async () => {
    const detail = { id: 'challenge-1' } as ChallengeDetail;
    http.post.mockReturnValue(of(detail));
    http.patch.mockReturnValue(of(detail));
    http.delete.mockReturnValue(of(void 0));

    await firstValueFrom(service.createChallenge({ name: 'Challenge', type: 'SOLOQ', startAt: 's', endAt: 'e', isPublic: true }));
    await firstValueFrom(service.updateChallenge('1', { name: 'Updated' }));
    await firstValueFrom(service.updateChallengeSchedule('1', { startAt: 's', endAt: 'e' }));
    await firstValueFrom(service.updateChallengeEnd('1', { endAt: 'e' }));
    await firstValueFrom(service.updateChallengeStart('1', { startAt: 's' }));
    await firstValueFrom(service.updateChallengeVisibility('1', { isPublic: false }));
    await firstValueFrom(service.updateChallengeName('1', { name: 'Renamed' }));
    await firstValueFrom(service.deleteChallenge('1'));
    await firstValueFrom(service.refreshChallenge('1'));

    expect(http.post).toHaveBeenCalledWith('https://api.test/api/challenges', expect.objectContaining({ name: 'Challenge' }));
    expect(http.patch).toHaveBeenCalledWith('https://api.test/api/challenges/1', { name: 'Updated' });
    expect(http.patch).toHaveBeenCalledWith('https://api.test/api/challenges/1/schedule', { startAt: 's', endAt: 'e' });
    expect(http.patch).toHaveBeenCalledWith('https://api.test/api/challenges/1/end', { endAt: 'e' });
    expect(http.patch).toHaveBeenCalledWith('https://api.test/api/challenges/1/start', { startAt: 's' });
    expect(http.patch).toHaveBeenCalledWith('https://api.test/api/challenges/1/visibility', { isPublic: false });
    expect(http.patch).toHaveBeenCalledWith('https://api.test/api/challenges/1/name', { name: 'Renamed' });
    expect(http.delete).toHaveBeenCalledWith('https://api.test/api/challenges/1');
    expect(http.post).toHaveBeenCalledWith('https://api.test/api/challenges/1/refresh', {});
  });

  it('normalizes riot ids for duo and participant creation, including whitespace edge cases', async () => {
    http.post.mockReturnValue(of(void 0));

    await firstValueFrom(
      service.addDuo('42', { player1RiotId: ' Hide on bush # EUW1 ', player2RiotId: ' Faker # KR1 ' }),
    );
    await firstValueFrom(service.addParticipant('42', { riotId: '  John Doe # EUW ' }));

    expect(http.post).toHaveBeenNthCalledWith(1, 'https://api.test/api/challenges/42/duos', {
      player1RiotId: 'Hideonbush#EUW1',
      player2RiotId: 'Faker#KR1',
    });
    expect(http.post).toHaveBeenNthCalledWith(2, 'https://api.test/api/challenges/42/participants', {
      riotId: 'JohnDoe#EUW',
    });
  });

  it('deletes duo and participant resources', async () => {
    http.delete.mockReturnValue(of(void 0));

    await firstValueFrom(service.removeDuo('c1', 'd1'));
    await firstValueFrom(service.removeParticipant('c1', 'p1'));

    expect(http.delete).toHaveBeenNthCalledWith(1, 'https://api.test/api/challenges/c1/duos/d1');
    expect(http.delete).toHaveBeenNthCalledWith(2, 'https://api.test/api/challenges/c1/participants/p1');
  });

  it('requests current user and recent games endpoints and preserves empty arrays', async () => {
    const recent: RecentGameResponse[] = [];
    http.get.mockReturnValueOnce(of({ userId: 'u1', username: 'name', linkedRiotAccount: null })).mockReturnValueOnce(of(recent));

    const me = await firstValueFrom(service.getCurrentUser());
    const games = await firstValueFrom(service.listRecentGames());

    expect(me.username).toBe('name');
    expect(games).toEqual([]);
    expect(http.get).toHaveBeenNthCalledWith(1, 'https://api.test/api/auth/me');
    expect(http.get).toHaveBeenNthCalledWith(2, 'https://api.test/api/challenges/recent');
  });
});

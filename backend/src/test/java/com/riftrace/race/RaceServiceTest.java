package com.riftrace.race;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.riftrace.account.AppUserRepository;
import com.riftrace.race.dto.RaceSummaryResponse;
import com.riftrace.synchronization.RaceSyncService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RaceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T10:00:00Z");

    @Mock
    private RaceRepository raceRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RaceParticipantRepository participantRepository;

    @Mock
    private RaceProgressService progressService;

    @Mock
    private RaceDuoProgressService duoProgressService;

    @Mock
    private ParticipantProfileService participantProfileService;

    @Mock
    private RaceRefreshRepository raceRefreshRepository;

    @Mock
    private RaceSyncService raceSyncService;

    @InjectMocks
    private RaceService raceService;

    @Test
    void listPublicRaces_returnsOnlyStartedPublicRaces() {
        Race activeRace = Race.create(
                UUID.randomUUID(),
                "Active",
                RaceType.SOLOQ,
                NOW.minusSeconds(60),
                true
        );
        when(raceRepository.findByIsPublicTrueAndStartAtLessThanEqualOrderByStartAtDesc(NOW))
                .thenReturn(List.of(activeRace));

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        raceService = new RaceService(
                raceRepository,
                appUserRepository,
                participantRepository,
                progressService,
                duoProgressService,
                participantProfileService,
                raceRefreshRepository,
                raceSyncService,
                clock
        );

        List<RaceSummaryResponse> races = raceService.listPublicRaces();

        assertThat(races).hasSize(1);
        assertThat(races.getFirst().status()).isEqualTo("ACTIVE");
    }
}

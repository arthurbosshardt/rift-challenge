package com.riftrace.synchronization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftrace.race.Race;
import com.riftrace.race.RaceParticipantRepository;
import com.riftrace.race.RaceRefresh;
import com.riftrace.race.RaceRefreshRecordService;
import com.riftrace.race.RaceRefreshRepository;
import com.riftrace.race.RaceRepository;
import com.riftrace.race.RaceType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RaceSyncServiceTest {

    @Mock
    private RaceRepository raceRepository;

    @Mock
    private RaceParticipantRepository participantRepository;

    @Mock
    private RaceRefreshRepository raceRefreshRepository;

    @Mock
    private RaceParticipantSyncService participantSyncService;

    @Mock
    private RaceRefreshRecordService refreshRecordService;

    @InjectMocks
    private RaceSyncService raceSyncService;

    @Test
    void refreshRace_beforeCooldown_throwsTooManyRequests() {
        UUID raceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-16T10:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        raceSyncService = new RaceSyncService(
                raceRepository,
                participantRepository,
                raceRefreshRepository,
                participantSyncService,
                refreshRecordService,
                clock
        );

        Race race = Race.create(ownerId, "Test", RaceType.SOLOQ, Instant.parse("2026-08-16T09:00:00Z"), false);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceRefreshRepository.findByRaceId(raceId)).thenReturn(Optional.of(
                RaceRefresh.create(raceId, Instant.parse("2026-08-16T09:59:30Z"))
        ));

        assertThatThrownBy(() -> raceSyncService.refreshRace(raceId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Refresh available at");

        verify(participantRepository, never()).findByRaceIdOrderByCreatedAtAsc(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshRace_beforeStart_throwsBadRequest() {
        UUID raceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-16T09:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        raceSyncService = new RaceSyncService(
                raceRepository,
                participantRepository,
                raceRefreshRepository,
                participantSyncService,
                refreshRecordService,
                clock
        );

        Race race = Race.create(ownerId, "Test", RaceType.SOLOQ, Instant.parse("2026-08-16T10:00:00Z"), false);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> raceSyncService.refreshRace(raceId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Race has not started yet");
    }
}

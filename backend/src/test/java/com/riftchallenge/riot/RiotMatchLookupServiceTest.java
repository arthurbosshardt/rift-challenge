package com.riftchallenge.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiotMatchLookupServiceTest {

    @Mock
    private RiotMatchClient riotMatchClient;

    @InjectMocks
    private RiotMatchLookupService riotMatchLookupService;

    @Test
    void getMatch_withoutRefreshScope_fetchesEveryTime() {
        RiotMatchDetailDto match = sampleMatch("EUW1_1");
        when(riotMatchClient.getMatch("EUW1_1")).thenReturn(match);

        assertThat(riotMatchLookupService.getMatch("EUW1_1")).isSameAs(match);
        assertThat(riotMatchLookupService.getMatch("EUW1_1")).isSameAs(match);

        verify(riotMatchClient, times(2)).getMatch("EUW1_1");
    }

    @Test
    void getMatch_withinRefreshScope_reusesCachedMatch() {
        RiotMatchDetailDto match = sampleMatch("EUW1_2");
        when(riotMatchClient.getMatch("EUW1_2")).thenReturn(match);

        riotMatchLookupService.beginRefreshScope();
        try {
            assertThat(riotMatchLookupService.getMatch("EUW1_2")).isSameAs(match);
            assertThat(riotMatchLookupService.getMatch("EUW1_2")).isSameAs(match);
        } finally {
            riotMatchLookupService.endRefreshScope();
        }

        verify(riotMatchClient, times(1)).getMatch("EUW1_2");
    }

    private static RiotMatchDetailDto sampleMatch(String matchId) {
        return new RiotMatchDetailDto(
                new RiotMatchDetailDto.Metadata(matchId),
                new RiotMatchDetailDto.Info(
                        1_700_000_000_000L,
                        420,
                        List.of(new RiotMatchDetailDto.Participant("puuid", true, 1, 157, "Ahri"))
                )
        );
    }
}

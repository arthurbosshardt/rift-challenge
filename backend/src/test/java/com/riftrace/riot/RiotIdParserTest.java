package com.riftrace.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RiotIdParserTest {

    @Test
    void parse_validRiotId_returnsParts() {
        RiotIdParser.ParsedRiotId parsed = RiotIdParser.parse("Tanor#7154");

        assertThat(parsed.gameName()).isEqualTo("Tanor");
        assertThat(parsed.tagLine()).isEqualTo("7154");
    }

    @Test
    void parse_missingHash_throwsBadRequest() {
        assertThatThrownBy(() -> RiotIdParser.parse("Tanor7154"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("gameName#tagLine");
    }
}

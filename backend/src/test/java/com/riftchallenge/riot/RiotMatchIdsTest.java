package com.riftchallenge.riot;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RiotMatchIdsTest {

    @Test
    void requireValid_acceptsRiotMatchIds() {
        assertThatCode(() -> RiotMatchIds.requireValid("EUW1_1234567890")).doesNotThrowAnyException();
        assertThatCode(() -> RiotMatchIds.requireValid("NA1_1")).doesNotThrowAnyException();
        assertThatCode(() -> RiotMatchIds.requireValid("KR_98765432101234567890")).doesNotThrowAnyException();
    }

    @Test
    void requireValid_rejectsInvalidIds() {
        assertThatThrownBy(() -> RiotMatchIds.requireValid(null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> RiotMatchIds.requireValid(""))
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> RiotMatchIds.requireValid("../etc/passwd"))
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> RiotMatchIds.requireValid("euw1_123"))
                .isInstanceOf(ResponseStatusException.class);

        assertThatThrownBy(() -> RiotMatchIds.requireValid("EUW1_"))
                .isInstanceOf(ResponseStatusException.class);
    }
}

package com.riftchallenge.riot;

import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class RiotMatchIds {

    private static final Pattern RIOT_MATCH_ID = Pattern.compile("^[A-Z0-9]{2,10}_\\d{1,20}$");

    private RiotMatchIds() {
    }

    public static void requireValid(String matchId) {
        if (matchId == null || !RIOT_MATCH_ID.matcher(matchId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid match id");
        }
    }
}

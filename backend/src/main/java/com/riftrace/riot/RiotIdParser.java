package com.riftrace.riot;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class RiotIdParser {

    private RiotIdParser() {
    }

    public record ParsedRiotId(String gameName, String tagLine) {
    }

    public static ParsedRiotId parse(String riotId) {
        if (riotId == null || riotId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Riot ID is required");
        }

        String trimmed = riotId.trim();
        int hashIndex = trimmed.indexOf('#');
        if (hashIndex <= 0 || hashIndex >= trimmed.length() - 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Riot ID must be in gameName#tagLine format"
            );
        }

        String gameName = normalizeGameName(trimmed.substring(0, hashIndex));
        String tagLine = trimmed.substring(hashIndex + 1).trim();
        if (gameName.isEmpty() || tagLine.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Riot ID must be in gameName#tagLine format"
            );
        }
        if (gameName.length() > 16) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Game name is too long");
        }
        if (tagLine.length() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag line is too long");
        }

        return new ParsedRiotId(gameName, tagLine);
    }

    private static String normalizeGameName(String gameName) {
        return gameName.replaceAll("\\s+", "");
    }
}

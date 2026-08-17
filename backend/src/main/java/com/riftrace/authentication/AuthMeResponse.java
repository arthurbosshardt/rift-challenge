package com.riftrace.authentication;

public record AuthMeResponse(
        String userId,
        String username,
        LinkedRiotAccount linkedRiotAccount
) {

    public record LinkedRiotAccount(
            String id,
            String gameName,
            String tagLine,
            String riotId,
            Integer profileIconId
    ) {
    }
}

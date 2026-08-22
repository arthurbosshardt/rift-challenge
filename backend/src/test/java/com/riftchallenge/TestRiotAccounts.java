package com.riftchallenge;

import com.riftchallenge.account.RiotAccount;
import com.riftchallenge.account.UserRiotAccount;
import com.riftchallenge.riot.dto.RiotAccountDto;
import java.util.UUID;

public final class TestRiotAccounts {

    private TestRiotAccounts() {
    }

    public static RiotAccount riotAccount(String puuid, String gameName, String tagLine) {
        return RiotAccount.create(new RiotAccountDto(puuid, gameName, tagLine), null);
    }

    public static RiotAccount riotAccount(String puuid, String gameName, String tagLine, Integer profileIconId) {
        return RiotAccount.create(new RiotAccountDto(puuid, gameName, tagLine), profileIconId);
    }

    public static UserRiotAccount linkedAccount(UUID userId, RiotAccount riotAccount) {
        return UserRiotAccount.create(userId, riotAccount);
    }

    public static UserRiotAccount linkedAccount(UUID userId, String puuid, String gameName, String tagLine) {
        return linkedAccount(userId, riotAccount(puuid, gameName, tagLine));
    }

    public static UserRiotAccount linkedAccount(
            UUID userId,
            String puuid,
            String gameName,
            String tagLine,
            Integer profileIconId
    ) {
        return linkedAccount(userId, riotAccount(puuid, gameName, tagLine, profileIconId));
    }
}

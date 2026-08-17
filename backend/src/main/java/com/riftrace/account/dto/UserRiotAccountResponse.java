package com.riftrace.account.dto;

import com.riftrace.account.UserRiotAccount;
import java.util.UUID;

public record UserRiotAccountResponse(
        UUID id,
        String gameName,
        String tagLine,
        String riotId,
        Integer profileIconId
) {

    public static UserRiotAccountResponse from(UserRiotAccount account) {
        return new UserRiotAccountResponse(
                account.getId(),
                account.getRiotGameName(),
                account.getRiotTagLine(),
                account.getRiotGameName() + "#" + account.getRiotTagLine(),
                account.getProfileIconId()
        );
    }
}

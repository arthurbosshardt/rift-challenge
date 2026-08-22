package com.riftchallenge.account.dto;

import java.util.List;

public record BulkRegisterRiotAccountsResponse(
        List<String> created,
        List<String> existing,
        List<String> errors
) {
}

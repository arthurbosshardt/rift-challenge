package com.riftchallenge.account.dto;

import java.util.List;

public record BulkRegisterRiotAccountsRequest(List<String> riotIds) {
}

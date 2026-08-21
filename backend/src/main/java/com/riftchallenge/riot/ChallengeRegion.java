package com.riftchallenge.riot;

/**
 * A Riot server a challenge's matches are synced from. Fixed on the challenge at creation
 * (see {@code Challenge.create}) and never updated afterward.
 */
public enum ChallengeRegion {
    EUW("euw1", "europe"),
    EUNE("eun1", "europe"),
    NA("na1", "americas"),
    KR("kr", "asia");

    private final String platform;
    private final String continentalRouting;

    ChallengeRegion(String platform, String continentalRouting) {
        this.platform = platform;
        this.continentalRouting = continentalRouting;
    }

    /** Platform routing value for summoner-v4 / league-v4 (e.g. "euw1"). */
    public String platform() {
        return platform;
    }

    /** Continental routing value for match-v5 (e.g. "europe"). */
    public String continentalRouting() {
        return continentalRouting;
    }

    /** Riot match ids are prefixed with the platform they were played on (e.g. "EUW1_1234"). */
    public static ChallengeRegion fromMatchId(String matchId) {
        int separator = matchId.indexOf('_');
        String prefix = separator > 0 ? matchId.substring(0, separator) : matchId;
        for (ChallengeRegion region : values()) {
            if (region.platform.equalsIgnoreCase(prefix)) {
                return region;
            }
        }
        throw new IllegalStateException("Unknown region for match id " + matchId);
    }
}

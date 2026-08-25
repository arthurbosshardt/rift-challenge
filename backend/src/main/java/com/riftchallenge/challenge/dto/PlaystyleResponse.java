package com.riftchallenge.challenge.dto;

/**
 * Season-aggregate 5-axis playstyle profile for the radar chart on the player profile page.
 * The {@code *Score} fields are 0-100 normalizations of the raw values over fixed, clamped
 * domains (KDA [0,6], Farm/Aggression/Resilience [0,10], Solo carry [0,1]) chosen for a
 * readable radar shape, not statistically derived.
 */
public record PlaystyleResponse(
        double kda,
        double farmPerMin,
        double aggressionPer10,
        double resiliencePer10,
        double soloCarryIndex,
        double kdaScore,
        double farmScore,
        double aggressionScore,
        double resilienceScore,
        double soloCarryScore
) {
}

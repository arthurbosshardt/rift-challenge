package com.riftchallenge.health;

import com.riftchallenge.challenge.ChallengeRepository;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Touches the JPA/DataSource stack (not just the web layer) so that "UP" reflects a backend that
 * can actually serve data, not just a running process — important now that lazy bean
 * initialization defers Hikari/Hibernate startup past the first real request.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final ChallengeRepository challengeRepository;

    public HealthController(ChallengeRepository challengeRepository) {
        this.challengeRepository = challengeRepository;
    }

    @GetMapping
    public Map<String, String> health() {
        challengeRepository.count();
        return Map.of("status", "UP");
    }
}

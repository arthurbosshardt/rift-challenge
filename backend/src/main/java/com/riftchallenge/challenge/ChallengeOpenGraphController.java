package com.riftchallenge.challenge;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/challenges/share")
public class ChallengeOpenGraphController {

    private final ChallengeOpenGraphService openGraphService;

    public ChallengeOpenGraphController(ChallengeOpenGraphService openGraphService) {
        this.openGraphService = openGraphService;
    }

    @GetMapping(value = "/{shareSlug}/preview", produces = "text/html;charset=UTF-8")
    public String preview(@PathVariable String shareSlug) {
        return openGraphService.renderPreviewHtml(shareSlug);
    }
}

package com.riftchallenge.challenge;

import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @GetMapping(value = "/{shareSlug}/preview-image.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> previewImage(@PathVariable String shareSlug) {
        byte[] image = openGraphService.renderPreviewImage(shareSlug);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(image);
    }
}

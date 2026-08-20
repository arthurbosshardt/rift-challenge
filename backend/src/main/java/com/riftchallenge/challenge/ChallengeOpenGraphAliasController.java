package com.riftchallenge.challenge;

import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Short public aliases used by Vercel bot rewrites and og:image URLs
 * ({@code /api/challenge-preview?slug=} / {@code /api/challenge-preview-image?slug=}).
 */
@RestController
public class ChallengeOpenGraphAliasController {

    private final ChallengeOpenGraphService openGraphService;

    public ChallengeOpenGraphAliasController(ChallengeOpenGraphService openGraphService) {
        this.openGraphService = openGraphService;
    }

    @GetMapping(value = "/api/challenge-preview", produces = "text/html;charset=UTF-8")
    public String preview(@RequestParam("slug") String slug) {
        return openGraphService.renderPreviewHtml(slug);
    }

    @GetMapping(value = "/api/challenge-preview-image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> previewImage(@RequestParam("slug") String slug) {
        byte[] image = openGraphService.renderPreviewImage(slug);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(image);
    }
}

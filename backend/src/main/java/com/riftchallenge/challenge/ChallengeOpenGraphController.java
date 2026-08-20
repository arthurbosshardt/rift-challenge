package com.riftchallenge.challenge;

import jakarta.servlet.http.HttpServletRequest;
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
    private final OpenGraphRequestThrottle openGraphRequestThrottle;

    public ChallengeOpenGraphController(
            ChallengeOpenGraphService openGraphService,
            OpenGraphRequestThrottle openGraphRequestThrottle
    ) {
        this.openGraphService = openGraphService;
        this.openGraphRequestThrottle = openGraphRequestThrottle;
    }

    @GetMapping(value = "/{shareSlug}/preview", produces = "text/html;charset=UTF-8")
    public String preview(@PathVariable String shareSlug, HttpServletRequest request) {
        openGraphRequestThrottle.enforce(request, "html");
        return openGraphService.renderPreviewHtml(shareSlug);
    }

    @GetMapping(value = "/{shareSlug}/preview-image.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> previewImage(@PathVariable String shareSlug, HttpServletRequest request) {
        openGraphRequestThrottle.enforce(request, "image");
        byte[] image = openGraphService.renderPreviewImage(shareSlug);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(image);
    }
}

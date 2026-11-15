package com.thriftbite.backend.photo;

import com.thriftbite.backend.auth.TokenService;
import com.thriftbite.backend.common.ApiExceptions.BadRequestException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.photo.Photos.PhotoResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@RestController
public class PhotoController {

    private final Photos photos;

    PhotoController(Photos photos) {
        this.photos = photos;
    }

    /** A store uploads a photo, then puts the id it gets back on a listing or on its profile. */
    @PostMapping(path = "/api/owner/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoResponse upload(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
        try {
            return photos.upload(TokenService.userId(jwt), file.getBytes());
        } catch (IOException e) {
            throw new BadRequestException("The upload was cut off. Try again.");
        }
    }

    /**
     * Anyone can see a photo, the same as the listing it's on. A photo never changes (a new
     * upload gets a new id), so browsers and proxies may keep it for a year without asking again.
     *
     * @param size "thumb" for the small one used in lists; anything else is the full one
     */
    @GetMapping("/api/photos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable UUID id, @RequestParam(defaultValue = "full") String size) {
        byte[] jpeg = photos.jpeg(id, "thumb".equals(size));
        if (jpeg == null) {
            throw new NotFoundException("Photo not found");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(jpeg);
    }
}

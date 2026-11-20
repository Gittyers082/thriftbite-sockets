package com.thriftbite.backend.photo;

import com.jayway.jsonpath.JsonPath;
import com.thriftbite.backend.ApiTestSupport;
import com.thriftbite.backend.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Photos through the API: uploading, who can see them, and putting them on listings and stores. */
class PhotoTest extends ApiTestSupport {

    @Autowired
    private Photos photos;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aStoreUploadsAPhotoAndAnyoneCanSeeIt() throws Exception {
        String owner = tokenFor(Role.OWNER);
        byte[] file = picture(1600, 1200, 1);

        String photoId = JsonPath.read(upload(owner, file)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.width").value(960))
                .andExpect(jsonPath("$.height").value(720))
                .andReturn().getResponse().getContentAsString(), "$.id");
        // the same file again is the same photo
        upload(owner, file).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(photoId));

        byte[] full = mvc.perform(get("/api/photos/{id}", photoId))   // no login
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        byte[] thumb = mvc.perform(get("/api/photos/{id}", photoId).param("size", "thumb"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(thumb.length).isLessThan(full.length);
        assertThat(full.length).isLessThan(file.length);

        mvc.perform(get("/api/photos/{id}", "00000000-0000-0000-0000-000000000000")).andExpect(status().isNotFound());
    }

    @Test
    void onlyStoresUploadAndOnlyPhotos() throws Exception {
        byte[] file = picture(800, 600, 2);
        upload(tokenFor(Role.CONSUMER), file).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/owner/photos").file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", file)))
                .andExpect(status().isUnauthorized());

        upload(tokenFor(Role.OWNER), "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Use a JPG or PNG photo"));
    }

    @Test
    void aListingCarriesItsPhoto() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String photoId = photoOf(owner, 3);

        String item = createItem(owner, "Pandesal", photoId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.photoId").value(photoId))
                .andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(item, "$.id");
        mvc.perform(get("/api/items/{id}", itemId)).andExpect(jsonPath("$.photoId").value(photoId));

        // Saving the listing without a photo takes it off.
        int version = JsonPath.read(item, "$.version");
        mvc.perform(put("/api/owner/items/{id}", itemId)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemJson("Pandesal", null, version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoId").value(nullValue()));
    }

    @Test
    void aStoreCannotUseAnotherStoresPhoto() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String someoneElses = photoOf(tokenFor(Role.OWNER), 4);

        createItem(owner, "Pandesal", someoneElses)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("isn't one of your store's")));
        createItem(owner, "Pandesal", "00000000-0000-0000-0000-000000000000").andExpect(status().isBadRequest());

        // In a batch, one bad photo means none of the listings are posted.
        Instant now = Instant.now();
        mvc.perform(post("/api/owner/items/batch")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupStart": "%s", "pickupEnd": "%s", "items": [
                                  {"name": "Monay", "price": 10, "originalPrice": 20, "quantity": 2, "pickupLocation": "Counter"},
                                  {"name": "Ensaymada", "price": 10, "originalPrice": 20, "quantity": 2, "pickupLocation": "Counter",
                                   "photoId": "%s"}]}
                                """.formatted(now.plus(Duration.ofMinutes(10)), now.plus(Duration.ofHours(2)), someoneElses)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/owner/items").header("Authorization", bearer(owner))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void theStoreProfileTakesAPhoto() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String photoId = photoOf(owner, 5);
        String mine = mvc.perform(get("/api/owner/store").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.photoId").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(mine, "$.slug");
        int version = JsonPath.read(mine, "$.version");

        mvc.perform(put("/api/owner/store")
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sunrise Bakery", "gcashAccepted": false, "hours": [], "version": %d, "photoId": "%s"}
                                """.formatted(version, photoId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoId").value(photoId));

        mvc.perform(get("/api/stores/{slug}", slug)).andExpect(jsonPath("$.store.photoId").value(photoId));
    }

    @Test
    void photosNothingShowsAnymoreAreClearedOut() throws Exception {
        String owner = tokenFor(Role.OWNER);
        String onAListing = photoOf(owner, 6);
        String neverUsed = photoOf(owner, 7);
        String uploadedJustNow = photoOf(owner, 8);
        createItem(owner, "Pandesal", onAListing).andExpect(status().isCreated());
        // The first two were uploaded two days ago; the third's form may still be open.
        jdbc.update("UPDATE photos SET created_at = now() - interval '2 days' WHERE id IN (?::uuid, ?::uuid)", onAListing, neverUsed);

        photos.deleteUnused();

        mvc.perform(get("/api/photos/{id}", onAListing)).andExpect(status().isOk());
        mvc.perform(get("/api/photos/{id}", neverUsed)).andExpect(status().isNotFound());
        mvc.perform(get("/api/photos/{id}", uploadedJustNow)).andExpect(status().isOk());
    }

    // --- helpers ---

    private ResultActions upload(String token, byte[] file) throws Exception {
        return mvc.perform(multipart("/api/owner/photos")
                .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", file))
                .header("Authorization", bearer(token)));
    }

    private String photoOf(String ownerToken, int seed) throws Exception {
        return JsonPath.read(upload(ownerToken, picture(800, 600, seed))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions createItem(String ownerToken, String name, String photoId) throws Exception {
        return mvc.perform(post("/api/owner/items")
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(itemJson(name, photoId, null)));
    }

    /** @param photoId null leaves the photo out; @param version null for a new listing */
    private static String itemJson(String name, String photoId, Integer version) {
        Instant now = Instant.now();
        return """
                {
                  "name": "%s",
                  "price": 20.00,
                  "originalPrice": 40.00,
                  "quantity": 5,
                  "expiresAt": "%s",
                  "pickupLocation": "Bonifacio St, Baguio",
                  "pickupStart": "%s",
                  "pickupEnd": "%s"%s%s
                }
                """.formatted(name, now.plus(Duration.ofHours(6)), now, now.plus(Duration.ofHours(3)),
                photoId == null ? "" : ", \"photoId\": \"" + photoId + "\"",
                version == null ? "" : ", \"version\": " + version);
    }

    /** A JPEG; a different seed gives a different file. */
    private static byte[] picture(int width, int height, int seed) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(200, 100 + seed * 13 % 150, 60));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(40, 40, 40 + seed * 29 % 200));
        for (int x = 0; x < width; x += 30 + seed) {
            g.drawLine(x, 0, 0, x);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}

package com.thriftbite.backend.photo;

import com.thriftbite.backend.common.ApiExceptions.BadRequestException;
import com.thriftbite.backend.common.ApiExceptions.ConflictException;
import com.thriftbite.backend.common.ApiExceptions.ForbiddenException;
import com.thriftbite.backend.common.ApiExceptions.NotFoundException;
import com.thriftbite.backend.photo.PhotoProcessor.Processed;
import com.thriftbite.backend.store.StoreService;
import com.thriftbite.backend.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * A store's photos, kept in the database next to the listings they belong to:
 * one backup, one thing to deploy, and a photo can't outlive or go missing
 * from its listing. They're small (about 100 KB for both sizes), and browsers
 * are told to keep them for a year, so each one is fetched once per device.
 */
@Service
public class Photos {

    /** Enough for a few hundred different foods; unused ones are cleared out by {@link #deleteUnused()}. */
    static final int MAX_PER_STORE = 200;

    private final JdbcTemplate jdbc;
    private final PhotoProcessor processor;
    private final StoreService stores;
    private final UserRepository users;

    Photos(JdbcTemplate jdbc, PhotoProcessor processor, StoreService stores, UserRepository users) {
        this.jdbc = jdbc;
        this.processor = processor;
        this.stores = stores;
        this.users = users;
    }

    /** @param width of the stored full-size photo, so a page can leave the right space before it loads */
    public record PhotoResponse(UUID id, int width, int height) {
    }

    /**
     * Adds a photo to the uploader's store. Uploading the same file again returns the photo
     * already stored. Not one transaction on purpose: shrinking a photo takes a moment, and
     * that shouldn't hold a database connection.
     */
    public PhotoResponse upload(UUID userId, byte[] file) {
        if (users.findById(userId).orElseThrow(() -> new NotFoundException("User no longer exists")).isDemo()) {
            throw new ForbiddenException("Photo uploads are off for the shared demo accounts, so nobody can put "
                    + "a picture on the public demo. Sign up for your own store to try it.");
        }
        return store(stores.storeIdFor(userId), userId, file);
    }

    /** For code that adds photos itself (the demo data). */
    public PhotoResponse store(UUID storeId, UUID uploadedBy, byte[] file) {
        if (file == null || file.length == 0) {
            throw new BadRequestException("Choose a photo to upload");
        }
        String sha256 = sha256(file);
        PhotoResponse existing = find(storeId, sha256);
        if (existing != null) {
            return existing;
        }
        Integer stored = jdbc.queryForObject("SELECT COUNT(*) FROM photos WHERE store_id = ?", Integer.class, storeId);
        if (stored != null && stored >= MAX_PER_STORE) {
            throw new ConflictException("Your store already has " + MAX_PER_STORE + " photos, which is the most it can keep. "
                    + "Reuse one from an earlier listing with \"Post again\".");
        }
        return insert(storeId, uploadedBy, sha256, processor.process(file));
    }

    /** Stores a photo that's already been shrunk. Storing the same one again changes nothing. */
    public PhotoResponse insert(UUID storeId, UUID uploadedBy, String sha256, Processed photo) {
        jdbc.update("""
                INSERT INTO photos (store_id, uploaded_by, sha256, full_jpeg, thumb_jpeg, width, height)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (store_id, sha256) DO NOTHING
                """, storeId, uploadedBy, sha256, photo.full(), photo.thumb(), photo.width(), photo.height());
        return find(storeId, sha256);   // also right if the same file was uploaded twice at once
    }

    /** The JPEG to send to a browser, or null if there's no such photo. */
    public byte[] jpeg(UUID photoId, boolean thumb) {
        List<byte[]> found = jdbc.query("SELECT " + (thumb ? "thumb_jpeg" : "full_jpeg") + " FROM photos WHERE id = ?",
                (rs, row) -> rs.getBytes(1), photoId);
        return found.isEmpty() ? null : found.get(0);
    }

    /** A listing or store profile may only use its own store's photos. */
    public void checkBelongsTo(UUID storeId, UUID photoId) {
        if (photoId == null) {
            return;
        }
        Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM photos WHERE id = ? AND store_id = ?",
                Integer.class, photoId, storeId);
        if (found == null || found == 0) {
            throw new BadRequestException("That photo isn't one of your store's. Upload it again.");
        }
    }

    /**
     * Clears out photos nothing shows anymore: uploaded but never used, or used only by
     * listings that ended more than two months ago ("Post again" looks back five weeks).
     * A day's grace, so a photo isn't removed while its listing form is still open.
     */
    public int deleteUnused() {
        return jdbc.update("""
                DELETE FROM photos p
                 WHERE p.created_at < now() - interval '1 day'
                   AND NOT EXISTS (SELECT 1 FROM stores s WHERE s.photo_id = p.id)
                   AND NOT EXISTS (SELECT 1 FROM items i
                                    WHERE i.photo_id = p.id AND i.expires_at > now() - interval '60 days')
                """);
    }

    private PhotoResponse find(UUID storeId, String sha256) {
        List<PhotoResponse> found = jdbc.query("SELECT id, width, height FROM photos WHERE store_id = ? AND sha256 = ?",
                (rs, row) -> new PhotoResponse(rs.getObject("id", UUID.class), rs.getInt("width"), rs.getInt("height")),
                storeId, sha256);
        return found.isEmpty() ? null : found.get(0);
    }

    public static String sha256(byte[] file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}

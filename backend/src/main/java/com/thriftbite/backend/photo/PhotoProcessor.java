package com.thriftbite.backend.photo;

import com.thriftbite.backend.common.ApiExceptions.BadRequestException;
import com.thriftbite.backend.common.ApiExceptions.TooManyRequestsException;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Turns an uploaded file into the two JPEGs the app keeps: one for a store or
 * listing page and a smaller one for lists.
 *
 * Nothing from the upload is stored as it arrived. The picture is decoded and
 * drawn again, which
 * - proves it really is a picture (a renamed script or a half-broken file fails to decode);
 * - drops everything else a photo file carries: where it was taken, the phone model, comments;
 * - makes every photo the same format and a size that's quick to load on mobile data.
 *
 * Memory is the thing to watch: the free host has 512 MB, and a 24-megapixel
 * photo is about 100 MB once decoded. So the size is read from the file's
 * header before any pixels are, big photos are decoded at a fraction of their
 * size, and only two are worked on at once.
 */
@Component
public class PhotoProcessor {

    /** Longest side of the stored photo, and of the small one used in lists. */
    static final int FULL_SIDE = 960;
    static final int THUMB_SIDE = 480;
    /** Smaller than this and it looks bad even on a phone. */
    static final int MIN_SIDE = 200;
    /** 25 megapixels. The app's own pages shrink a photo before sending it, so this only limits direct uploads. */
    static final long MAX_PIXELS = 25_000_000L;

    private static final float FULL_QUALITY = 0.82f;
    private static final float THUMB_QUALITY = 0.78f;

    static {
        ImageIO.setUseCache(false);   // work in memory; the default spools to temp files on disk
    }

    private final Semaphore slots = new Semaphore(2);

    /** @param width  of the full-size JPEG, in pixels */
    public record Processed(byte[] full, byte[] thumb, int width, int height) {
    }

    public Processed process(byte[] upload) {
        String format = formatOf(upload);
        if (format == null) {
            throw new BadRequestException("Use a JPG or PNG photo");
        }
        acquireSlot();
        try {
            BufferedImage full = fit(decode(upload, format), FULL_SIDE);
            BufferedImage thumb = fit(full, THUMB_SIDE);
            return new Processed(jpeg(full, FULL_QUALITY), jpeg(thumb, THUMB_QUALITY), full.getWidth(), full.getHeight());
        } finally {
            slots.release();
        }
    }

    /** Goes by the file's first bytes, never by its name or the type the browser claimed. */
    static String formatOf(byte[] file) {
        if (file.length >= 3 && (file[0] & 0xFF) == 0xFF && (file[1] & 0xFF) == 0xD8 && (file[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if (file.length >= 8 && (file[0] & 0xFF) == 0x89 && file[1] == 'P' && file[2] == 'N' && file[3] == 'G'
                && file[4] == 0x0D && file[5] == 0x0A && file[6] == 0x1A && file[7] == 0x0A) {
            return "png";
        }
        return null;
    }

    private void acquireSlot() {
        try {
            if (!slots.tryAcquire(15, TimeUnit.SECONDS)) {
                throw new TooManyRequestsException("Lots of photos are being uploaded right now. Try again in a moment.",
                        Duration.ofSeconds(15), "photos_busy");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to process a photo", e);
        }
    }

    private static BufferedImage decode(byte[] upload, String format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
        if (!readers.hasNext()) {
            throw new IllegalStateException("No image reader for " + format);
        }
        ImageReader reader = readers.next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            reader.setInput(in, true, true);   // read forward only, and skip the metadata
            // Java draws a JPEG that stops halfway as the top of the picture over grey, and only
            // mentions it as a warning. A half-saved photo should be refused, not stored.
            boolean[] cutOff = {false};
            reader.addIIOReadWarningListener((source, warning) -> {
                String text = warning.toLowerCase(Locale.ROOT);   // "Truncated File - Missing EOI marker"
                cutOff[0] |= text.contains("truncated") || text.contains("premature end");
            });
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width < MIN_SIDE || height < MIN_SIDE) {
                throw new BadRequestException("That photo is too small. Use one at least " + MIN_SIDE + " pixels on each side.");
            }
            if ((long) width * height > MAX_PIXELS) {
                throw new BadRequestException("That photo is too large. Use one under 25 megapixels.");
            }
            // Reading every 2nd, 3rd... pixel keeps a big photo small in memory. It stays at
            // least twice the stored size, so the shrinking below still has detail to work with.
            int step = Math.max(1, Math.max(width, height) / (FULL_SIDE * 2));
            ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceSubsampling(step, step, 0, 0);
            BufferedImage image = reader.read(0, param);
            if (image == null || cutOff[0]) {
                throw unreadable();
            }
            return image;
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Cut off mid-upload, corrupted, or a kind of JPEG Java can't read (some print-ready CMYK files).
            throw unreadable();
        } finally {
            reader.dispose();
        }
    }

    private static BadRequestException unreadable() {
        return new BadRequestException("We couldn't read that photo. Try another one, or take it again.");
    }

    /**
     * Shrinks the picture so its longest side is at most {@code maxSide}, and flattens it
     * onto white (JPEG has no see-through). Halving first, then one careful final step,
     * avoids the jagged edges of shrinking a lot in one go.
     */
    static BufferedImage fit(BufferedImage source, int maxSide) {
        double ratio = Math.min(1.0, (double) maxSide / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        BufferedImage current = source;
        while (current.getWidth() / 2 >= width * 2 || current.getHeight() / 2 >= height * 2) {
            current = draw(current, Math.max(1, current.getWidth() / 2), Math.max(1, current.getHeight() / 2),
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return draw(current, width, height, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height, Object interpolation) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] jpeg(BufferedImage image, float quality) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 * 1024);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write a JPEG", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}

package com.thriftbite.backend.photo;

import com.thriftbite.backend.common.ApiExceptions.BadRequestException;
import com.thriftbite.backend.photo.PhotoProcessor.Processed;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What happens to an uploaded file before anything is stored. No database or web server needed. */
class PhotoProcessorTest {

    private final PhotoProcessor processor = new PhotoProcessor();

    @Test
    void aCameraPhotoIsShrunkToTwoSizes() throws IOException {
        Processed photo = processor.process(picture(3000, 2000, "jpg"));

        BufferedImage full = ImageIO.read(new ByteArrayInputStream(photo.full()));
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(photo.thumb()));
        assertEquals(960, full.getWidth());
        assertEquals(640, full.getHeight());
        assertEquals(960, photo.width());
        assertEquals(640, photo.height());
        assertEquals(480, thumb.getWidth());
        assertEquals(320, thumb.getHeight());
        assertTrue(photo.thumb().length < photo.full().length);
    }

    @Test
    void aSmallPhotoIsNotBlownUp() {
        Processed photo = processor.process(picture(300, 240, "jpg"));

        assertEquals(300, photo.width());
        assertEquals(240, photo.height());
    }

    @Test
    void aPngIsStoredAsAJpegOnWhite() throws IOException {
        BufferedImage cutOut = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);   // fully see-through
        Graphics2D g = cutOut.createGraphics();
        g.setColor(Color.RED);
        g.fillOval(100, 100, 200, 200);
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(cutOut, "png", png);

        byte[] stored = processor.process(png.toByteArray()).full();

        assertEquals("jpeg", PhotoProcessor.formatOf(stored));
        Color corner = new Color(ImageIO.read(new ByteArrayInputStream(stored)).getRGB(5, 5));
        assertTrue(corner.getRed() > 235 && corner.getGreen() > 235 && corner.getBlue() > 235, "the corner is " + corner);
    }

    @Test
    void whatElseTheFileCarriedIsNotKept() {
        // A comment inside the JPEG, standing in for the location and camera details phones add.
        byte[] plain = picture(800, 600, "jpg");
        byte[] note = "taken-at-12-secret-street".getBytes(StandardCharsets.US_ASCII);
        ByteBuffer withNote = ByteBuffer.allocate(plain.length + 4 + note.length);
        withNote.put(plain, 0, 2).put((byte) 0xFF).put((byte) 0xFE).putShort((short) (note.length + 2)).put(note)
                .put(plain, 2, plain.length - 2);
        assertTrue(new String(withNote.array(), StandardCharsets.ISO_8859_1).contains("secret-street"));

        Processed photo = processor.process(withNote.array());

        assertFalse(new String(photo.full(), StandardCharsets.ISO_8859_1).contains("secret-street"));
        assertFalse(new String(photo.thumb(), StandardCharsets.ISO_8859_1).contains("secret-street"));
    }

    @Test
    void filesThatAreNotPhotosAreRefused() {
        byte[] jpeg = picture(800, 600, "jpg");
        byte[] fakeJpeg = Arrays.copyOf(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, 600);

        assertThrows(BadRequestException.class, () -> processor.process("<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(BadRequestException.class, () -> processor.process("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(BadRequestException.class, () -> processor.process(picture(400, 400, "gif")));
        assertThrows(BadRequestException.class, () -> processor.process(fakeJpeg));
        assertThrows(BadRequestException.class, () -> processor.process(Arrays.copyOf(jpeg, jpeg.length / 2)));   // cut off halfway
        assertThrows(BadRequestException.class, () -> processor.process(picture(120, 90, "jpg")));                 // too small
    }

    @Test
    void aFileClaimingToBeEnormousIsRefusedBeforeItIsDecoded() throws IOException {
        // A "decompression bomb": a few bytes that say they're a 60,000 x 60,000 picture (over 10 GB decoded).
        byte[] bomb = pngHeaderClaiming(60_000, 60_000);

        long started = System.nanoTime();
        BadRequestException refused = assertThrows(BadRequestException.class, () -> processor.process(bomb));

        assertTrue(refused.getMessage().contains("too large"), refused.getMessage());
        assertTrue(System.nanoTime() - started < 2_000_000_000L, "it should be refused from the header alone");
    }

    // --- helpers ---

    private static byte[] picture(int width, int height, String format) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(214, 150, 70));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(60, 40, 30));
        for (int x = 0; x < width; x += 40) {
            g.drawLine(x, 0, 0, x);
        }
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A valid PNG signature and header with the given size, and no picture data behind it. */
    private static byte[] pngHeaderClaiming(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        chunk(out, "IHDR", ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0).array());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        out.write(ByteBuffer.allocate(4).putInt(data.length).array());
        out.write(name);
        out.write(data);
        out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}

package com.markoonyskiv.postcards.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * Guards the heap cost of an upload, which is the whole reason
 * {@link ImageStorageService} decodes with subsampling instead of calling
 * {@code ImageIO.read}. These run in their own forked JVM with a deliberately
 * small {@code -Xmx} (see the {@code low-heap-image-tests} surefire execution
 * in pom.xml), so a regression that reintroduces a full-size decode or a
 * full-size intermediate copy fails here with OutOfMemoryError rather than
 * only once it's deployed to a 512MB container.
 *
 * <p>The image fixtures are grayscale on purpose. A 6000x4000 gray raster is
 * 24MB where the same image in color is 72MB, so this way the heap limit
 * measures the service rather than the fixture that feeds it. Nothing about
 * the service's memory use depends on the source's color model, and the color
 * path is covered by {@code PostcardImageUploadIntegrationTest}.
 */
@EnabledIfSystemProperty(
        named = "postcards.lowHeapImageTests",
        matches = "true",
        disabledReason = "only meaningful in the low-heap surefire fork; run `mvnw test`, "
                + "or pass -Xmx96m -Dpostcards.lowHeapImageTests=true to run them directly")
class ImageStorageServiceLargeImageTest {

    private static final int MAX_WIDTH_PX = 1600;

    /** Mirrors ImageStorageService.MAX_DECODED_PIXELS. */
    private static final long MAX_DECODED_PIXELS = 12_000_000L;

    private final S3Client r2Client = mock(S3Client.class);

    @BeforeAll
    static void requireSmallHeap() {
        long maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        assertThat(maxHeapMb)
                .withFailMessage(
                        "This test only proves anything under a constrained heap, but max heap is %dMB. "
                                + "Expected the -Xmx96m from the low-heap-image-tests surefire execution.",
                        maxHeapMb)
                .isLessThan(192L);
    }

    private ImageStorageService imageStorageService() {
        when(r2Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        return new ImageStorageService(
                r2Client,
                new WatermarkService("Pavlyshyn Postcards"),
                "test-bucket",
                "https://pub-test.r2.dev",
                10_485_760L,
                MAX_WIDTH_PX);
    }

    private BufferedImage storedImage() throws Exception {
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(r2Client).putObject(any(PutObjectRequest.class), bodyCaptor.capture());
        byte[] uploaded = bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes();
        return ImageIO.read(new ByteArrayInputStream(uploaded));
    }

    /** A grayscale JPEG of the given size, costing one byte per pixel to build. */
    private static byte[] grayscaleJpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        // A gradient rather than a flat fill, so there's real high-frequency
        // content to decode and the JPEG doesn't compress away to nothing.
        for (int y = 0; y < height; y++) {
            int rowStart = y * width;
            for (int x = 0; x < width; x++) {
                pixels[rowStart + x] = (byte) ((x + y) & 0xFF);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    @Test
    void store_sixThousandByFourThousandImage_processesWithinASmallHeap() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("front", "huge.jpg", "image/jpeg", grayscaleJpeg(6000, 4000));
        ImageStorageService service = imageStorageService();

        String url = service.store(UUID.randomUUID(), "front", file);

        assertThat(url).startsWith("https://pub-test.r2.dev/postcards/");

        BufferedImage stored = storedImage();
        assertThat(stored.getWidth()).isEqualTo(MAX_WIDTH_PX);
        // 3:2 source, so the resize keeps the aspect ratio.
        assertThat(stored.getHeight()).isEqualTo(1067);
    }

    @Test
    void store_veryTallImage_subsamplesToStayInsideTheDecodeBudget() throws Exception {
        // 600x40000 is narrower than the resize target, so width alone asks
        // for no subsampling at all - but it is 24 megapixels, so the decode
        // budget has to be what forces subsampling here.
        MockMultipartFile file =
                new MockMultipartFile("front", "tall.jpg", "image/jpeg", grayscaleJpeg(600, 40000));
        ImageStorageService service = imageStorageService();

        service.store(UUID.randomUUID(), "front", file);

        BufferedImage stored = storedImage();
        assertThat(stored.getWidth()).isLessThan(600);
        assertThat((long) stored.getWidth() * stored.getHeight()).isLessThan(MAX_DECODED_PIXELS);
    }

    @Test
    void store_dimensionsBeyondTheHardCeiling_isRejectedAsTooLarge() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("front", "bomb.png", "image/png", headerOnlyPng(40_000, 40_000));
        ImageStorageService service = imageStorageService();

        assertThatThrownBy(() -> service.store(UUID.randomUUID(), "front", file))
                .isInstanceOf(ImageTooLargeException.class)
                .hasMessageContaining("too large");

        verifyNoInteractions(r2Client);
    }

    /**
     * A 300-byte PNG whose IHDR declares the given (gigapixel) dimensions,
     * standing in for a decompression bomb: readers report width and height
     * straight from that header, which is all the ceiling check reads before
     * rejecting. The pixel data deliberately doesn't cover the declared size -
     * nothing should ever get far enough to inflate it.
     */
    private static byte[] headerOnlyPng(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

        ByteBuffer ihdr = ByteBuffer.allocate(13);
        ihdr.putInt(width).putInt(height);
        ihdr.put((byte) 8); // bit depth
        ihdr.put((byte) 0); // color type: grayscale
        ihdr.put((byte) 0); // deflate
        ihdr.put((byte) 0); // standard filtering
        ihdr.put((byte) 0); // non-interlaced
        writeChunk(out, "IHDR", ihdr.array());

        Deflater deflater = new Deflater();
        deflater.setInput(new byte[] {0, 0}); // one filter byte, one pixel
        deflater.finish();
        byte[] compressed = new byte[64];
        int compressedLength = deflater.deflate(compressed);
        deflater.end();
        writeChunk(out, "IDAT", java.util.Arrays.copyOf(compressed, compressedLength));

        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws Exception {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.write(ByteBuffer.allocate(4).putInt(data.length).array());
        out.write(typeBytes);
        out.write(data);

        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}

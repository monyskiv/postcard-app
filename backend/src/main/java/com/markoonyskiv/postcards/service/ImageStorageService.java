package com.markoonyskiv.postcards.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Validates, resizes, watermarks, and uploads postcard images to R2. Every
 * accepted image is re-encoded as JPEG regardless of source format, which
 * keeps output compression predictable and sidesteps the lack of a WEBP
 * encoder in the JDK (WEBP uploads are still accepted and decoded via the
 * TwelveMonkeys ImageIO plugin, just never written back out as WEBP).
 *
 * <p>The watermark is baked in before the image is ever uploaded - no
 * unwatermarked copy is stored anywhere, in R2 or otherwise.
 *
 * <p>Processing is written to stay inside a small heap, because it has to:
 * the deployment target is a 512MB container, and a modern phone photo
 * decoded at full size is a 150-200MB raster. The decode is subsampled down
 * towards the output size instead, and each stage after it holds one raster
 * at a time rather than a before-and-after pair.
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final float JPEG_QUALITY = 0.85f;

    /**
     * Ceiling on the pixels we'll let a decode allocate, whatever the source
     * dimensions. A TYPE_INT_RGB raster costs 4 bytes a pixel, so this caps
     * one in-flight decode at ~48MB - small enough that a few concurrent
     * uploads still fit the ~360MB heap of a 512MB container.
     */
    private static final long MAX_DECODED_PIXELS = 12_000_000L;

    /**
     * Ceiling on the dimensions a file may *declare*, checked from the header
     * before any pixels are read. Subsampling means we'd never allocate the
     * full raster anyway, but a decompression bomb (a tiny PNG declaring
     * gigapixel dimensions) would still cost real time to inflate.
     */
    private static final long MAX_SOURCE_PIXELS = 200_000_000L;

    private static final String TOO_LARGE_MESSAGE =
            "Image is too large to process - please upload a smaller or lower-resolution version";

    private final S3Client r2Client;
    private final WatermarkService watermarkService;
    private final String bucketName;
    private final String publicBaseUrl;
    private final long maxFileSizeBytes;
    private final int maxWidthPx;

    public ImageStorageService(
            S3Client r2Client,
            WatermarkService watermarkService,
            @Value("${app.r2.bucket-name}") String bucketName,
            @Value("${app.r2.public-base-url}") String publicBaseUrl,
            @Value("${app.image.max-file-size-bytes}") long maxFileSizeBytes,
            @Value("${app.image.max-width-px}") int maxWidthPx) {
        this.r2Client = r2Client;
        this.watermarkService = watermarkService;
        this.bucketName = bucketName;
        this.publicBaseUrl = publicBaseUrl;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxWidthPx = maxWidthPx;
    }

    /**
     * Validates, resizes/watermarks/compresses, and uploads the given image
     * under the given postcard/side, returning its public URL.
     */
    public String store(UUID postcardId, String side, MultipartFile file) {
        validate(file);
        byte[] jpegBytes = processToJpeg(file);

        String key = "postcards/%s/%s-%s.jpg".formatted(postcardId, side, UUID.randomUUID());
        r2Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType("image/jpeg")
                        .contentLength((long) jpegBytes.length)
                        .build(),
                RequestBody.fromBytes(jpegBytes));

        return publicBaseUrl + "/" + key;
    }

    /**
     * Deletes the R2 object backing the given URL, if it actually points
     * into this app's bucket (an unset/placeholder URL never uploaded
     * through this service - e.g. left over from postcard creation - is
     * silently ignored, as is a key that doesn't exist in R2: R2's delete
     * is idempotent, so there's nothing to fail on either way).
     */
    public void deleteIfPresent(String imageUrl) {
        String prefix = publicBaseUrl + "/";
        if (imageUrl == null || !imageUrl.startsWith(prefix)) {
            return;
        }
        String key = imageUrl.substring(prefix.length());
        r2Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidImageException("Image file is empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new InvalidImageException(
                    "Image exceeds the maximum allowed size of " + (maxFileSizeBytes / (1024 * 1024)) + "MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidImageException("Unsupported image type: only JPEG, PNG, and WEBP are accepted");
        }
    }

    private byte[] processToJpeg(MultipartFile file) {
        try {
            return decodeAndRender(file);
        } catch (OutOfMemoryError e) {
            // Decoding is far and away the largest allocation this app ever
            // makes, and the heap it has to make it in is small (see
            // MaxRAMPercentage in the Dockerfile). The failed allocation is
            // already unwound by the time we get here, so the request is
            // recoverable - report it as the client-fixable problem it is
            // rather than letting it surface as a bare 500. Logged at warn
            // because reaching this backstop at all means the decode budget
            // above needs another look.
            log.warn("Ran out of heap processing an upload of {} bytes ({})",
                    file.getSize(), file.getOriginalFilename(), e);
            throw new ImageTooLargeException(TOO_LARGE_MESSAGE);
        }
    }

    /**
     * Decodes, flattens, resizes, watermarks, and JPEG-encodes in that order,
     * holding at most one full raster at a time. The decode is subsampled
     * (see {@link #subsamplingFor}) so an oversized source is never fully
     * materialised, and each step hands its only reference to the next so the
     * previous raster is collectable immediately.
     */
    private byte[] decodeAndRender(MultipartFile file) {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.getInputStream())) {
            if (input == null) {
                throw new InvalidImageException("Could not read uploaded image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidImageException("Could not decode uploaded image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                BufferedImage image = readSubsampled(reader);
                image = toRgbAtTargetWidth(image);
                watermarkService.applyInPlace(image);
                return encodeJpeg(image);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new InvalidImageException("Could not read uploaded image");
        }
    }

    /**
     * Reads the image at a reduced sampling rate rather than at full size.
     * This is the step that fixes the heap: the decoder skips pixels as it
     * goes, so a 48MP photo costs the memory of the ~2MP image we actually
     * want instead of 192MB of raster we'd immediately throw away.
     */
    private BufferedImage readSubsampled(ImageReader reader) throws IOException {
        int sourceWidth = reader.getWidth(0);
        int sourceHeight = reader.getHeight(0);
        if ((long) sourceWidth * sourceHeight > MAX_SOURCE_PIXELS) {
            throw new ImageTooLargeException(TOO_LARGE_MESSAGE);
        }

        ImageReadParam param = reader.getDefaultReadParam();
        int subsampling = subsamplingFor(sourceWidth, sourceHeight);
        if (subsampling > 1) {
            param.setSourceSubsampling(subsampling, subsampling, 0, 0);
        }

        BufferedImage image = reader.read(0, param);
        if (image == null) {
            throw new InvalidImageException("Could not decode uploaded image");
        }
        return image;
    }

    /**
     * Picks the subsampling factor to decode at: the largest one that still
     * leaves the image at or above {@code maxWidthPx}, so the resize
     * afterwards has real pixels to average and the result is no softer than
     * decoding at full size would have made it.
     *
     * <p>A lopsided source (a tall panorama) can still be over
     * {@link #MAX_DECODED_PIXELS} at that factor, since the factor only
     * follows the width. The budget wins there - some sharpness is a cheaper
     * price than an OOM.
     */
    private int subsamplingFor(int width, int height) {
        int subsampling = Math.max(1, width / maxWidthPx);
        while (subsampledPixels(width, height, subsampling) > MAX_DECODED_PIXELS) {
            subsampling++;
        }
        return subsampling;
    }

    /** Pixel count ImageIO yields for a given factor: ceil(dimension / factor) each way. */
    private static long subsampledPixels(int width, int height, int subsampling) {
        long sampledWidth = (width + subsampling - 1L) / subsampling;
        long sampledHeight = (height + subsampling - 1L) / subsampling;
        return sampledWidth * sampledHeight;
    }

    /**
     * Flattens to opaque RGB and scales to the target width in a single draw.
     * Doing both at once is the point: flattening first and resizing second
     * meant a full-size RGB copy living alongside the decoded source, which
     * is what ran the container out of heap.
     *
     * <p>Returns the source itself when it's already the right size and type,
     * so the common case of an already-web-sized JPEG allocates nothing.
     */
    private BufferedImage toRgbAtTargetWidth(BufferedImage source) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int targetWidth = Math.min(sourceWidth, maxWidthPx);
        int targetHeight = targetWidth == sourceWidth
                ? sourceHeight
                : Math.max(1, (int) Math.round((double) sourceHeight * targetWidth / sourceWidth));

        if (targetWidth == sourceWidth && source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }

        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // White underneath, so a transparent PNG flattens onto white
            // rather than onto JPEG's default black.
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] encodeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}

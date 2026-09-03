package com.markoonyskiv.postcards.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
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
 */
@Service
public class ImageStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final float JPEG_QUALITY = 0.85f;

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
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
        } catch (IOException e) {
            throw new InvalidImageException("Could not read uploaded image");
        }
        if (source == null) {
            throw new InvalidImageException("Could not decode uploaded image");
        }

        BufferedImage rgb = toRgb(source);
        BufferedImage scaled = scaleDownIfNeeded(rgb);
        BufferedImage watermarked = watermarkService.apply(scaled);

        try {
            return encodeJpeg(watermarked);
        } catch (IOException e) {
            throw new InvalidImageException("Could not process uploaded image");
        }
    }

    private BufferedImage toRgb(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return rgb;
    }

    private BufferedImage scaleDownIfNeeded(BufferedImage source) {
        if (source.getWidth() <= maxWidthPx) {
            return source;
        }
        int targetWidth = maxWidthPx;
        int targetHeight = Math.round((float) source.getHeight() * targetWidth / source.getWidth());

        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return scaled;
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

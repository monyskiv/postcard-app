package com.markoonyskiv.postcards.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class WatermarkServiceTest {

    private final WatermarkService watermarkService = new WatermarkService("Ukrainian Postcards");

    private BufferedImage solidColorImage(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    @Test
    void apply_preservesImageDimensions() {
        BufferedImage source = solidColorImage(800, 500, Color.BLUE);

        BufferedImage watermarked = watermarkService.apply(source);

        assertThat(watermarked.getWidth()).isEqualTo(800);
        assertThat(watermarked.getHeight()).isEqualTo(500);
    }

    @Test
    void apply_changesASubstantialPortionOfPixels() {
        int width = 800;
        int height = 500;
        Color background = Color.BLUE;
        BufferedImage source = solidColorImage(width, height, background);
        int originalRgb = background.getRGB();

        BufferedImage watermarked = watermarkService.apply(source);

        long changedPixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (watermarked.getRGB(x, y) != originalRgb) {
                    changedPixels++;
                }
            }
        }

        long totalPixels = (long) width * height;
        double changedFraction = changedPixels / (double) totalPixels;

        // A repeating diagonal watermark should touch a meaningful chunk of
        // the image, not just a single corner - but should still leave most
        // of the postcard visible (semi-transparent, not a solid overlay).
        assertThat(changedFraction).isGreaterThan(0.01).isLessThan(0.5);
    }

    @Test
    void apply_producesDifferentOutputForDifferentText() {
        BufferedImage source = solidColorImage(800, 500, Color.BLUE);
        WatermarkService other = new WatermarkService("Something Else Entirely");

        BufferedImage first = watermarkService.apply(source);
        BufferedImage second = other.apply(source);

        boolean anyPixelDiffers = false;
        outer:
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    anyPixelDiffers = true;
                    break outer;
                }
            }
        }

        assertThat(anyPixelDiffers).isTrue();
    }
}

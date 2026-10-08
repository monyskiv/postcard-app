package com.markoonyskiv.postcards.service;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Bakes a semi-transparent, repeating diagonal text watermark into an
 * image's pixels. Repeating and diagonal (rather than a single corner
 * placement) so it can't be defeated by a simple crop. Text is outlined
 * in black and filled in white so it stays legible over both light and
 * dark postcard scans.
 */
@Service
public class WatermarkService {

    private static final double ANGLE_DEGREES = -30;
    private static final float OPACITY = 0.28f;
    private static final Color OUTLINE_COLOR = Color.BLACK;
    private static final Color FILL_COLOR = Color.WHITE;

    private final String text;

    public WatermarkService(@Value("${app.image.watermark-text}") String text) {
        this.text = text;
    }

    /**
     * Draws the watermark straight onto the given image's pixels. In place
     * rather than onto a copy: the images this runs on are a sizeable
     * fraction of the heap available on the deployment target, and the only
     * caller has just decoded the image and has no use for an unmarked
     * version of it.
     */
    public void applyInPlace(BufferedImage target) {
        int width = target.getWidth();
        int height = target.getHeight();

        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(16, width / 20));
            graphics.setFont(font);
            FontMetrics metrics = graphics.getFontMetrics();
            int textWidth = metrics.stringWidth(text);
            int textHeight = metrics.getHeight();
            int stepX = textWidth + font.getSize() * 3;
            int stepY = textHeight + font.getSize() * 3;

            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, OPACITY));
            graphics.rotate(Math.toRadians(ANGLE_DEGREES), width / 2.0, height / 2.0);

            int diagonal = (int) Math.ceil(Math.hypot(width, height));
            for (int y = -diagonal; y < diagonal; y += stepY) {
                for (int x = -diagonal; x < diagonal; x += stepX) {
                    drawOutlinedText(graphics, x, y);
                }
            }
        } finally {
            graphics.dispose();
        }
    }

    private void drawOutlinedText(Graphics2D graphics, int x, int y) {
        FontRenderContext frc = graphics.getFontRenderContext();
        GlyphVector glyphs = graphics.getFont().createGlyphVector(frc, text);
        Shape outline = glyphs.getOutline(x, y);

        graphics.setColor(FILL_COLOR);
        graphics.fill(outline);
        graphics.setColor(OUTLINE_COLOR);
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.draw(outline);
    }
}

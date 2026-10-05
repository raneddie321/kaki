package com.kaki.snakebrawl.desktop;

import com.kaki.snakebrawl.game.Gfx;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

/** Java2D implementation of {@link Gfx} used for headless screenshots and tests. */
public final class AwtGfx implements Gfx {
    private final BufferedImage img;
    private final Graphics2D g;
    private final Font baseFont;
    private final ArrayDeque<AffineTransform> stack = new ArrayDeque<>();
    private Color color = Color.WHITE;

    public AwtGfx(BufferedImage img, Font font) {
        this.img = img;
        this.g = img.createGraphics();
        this.baseFont = font;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    @Override
    public float width() {
        return img.getWidth();
    }

    @Override
    public float height() {
        return img.getHeight();
    }

    @Override
    public void color(int argb) {
        color = new Color(argb, true);
        g.setColor(color);
    }

    @Override
    public void fillCircle(float x, float y, float r) {
        g.fill(new Ellipse2D.Float(x - r, y - r, r * 2, r * 2));
    }

    @Override
    public void strokeCircle(float x, float y, float r, float sw) {
        g.setStroke(new BasicStroke(sw));
        g.draw(new Ellipse2D.Float(x - r, y - r, r * 2, r * 2));
    }

    @Override
    public void fillRect(float l, float t, float r, float b) {
        g.fill(new Rectangle2D.Float(l, t, r - l, b - t));
    }

    @Override
    public void fillRoundRect(float l, float t, float r, float b, float radius) {
        g.fill(new RoundRectangle2D.Float(l, t, r - l, b - t, radius * 2, radius * 2));
    }

    @Override
    public void strokeRoundRect(float l, float t, float r, float b, float radius, float sw) {
        g.setStroke(new BasicStroke(sw));
        g.draw(new RoundRectangle2D.Float(l, t, r - l, b - t, radius * 2, radius * 2));
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float sw) {
        g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(x1, y1, x2, y2));
    }

    @Override
    public void arc(float cx, float cy, float radius, float startDeg, float sweepDeg, float sw) {
        g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Java2D measures angles counter-clockwise; Android clockwise.
        g.draw(new Arc2D.Float(cx - radius, cy - radius, radius * 2, radius * 2, -startDeg, -sweepDeg, Arc2D.OPEN));
    }

    @Override
    public void fillPoly(float[] xy, int count) {
        if (count < 3) return;
        Path2D.Float p = new Path2D.Float();
        p.moveTo(xy[0], xy[1]);
        for (int i = 1; i < count; i++) p.lineTo(xy[i * 2], xy[i * 2 + 1]);
        p.closePath();
        g.fill(p);
    }

    @Override
    public void text(String s, float x, float y, float size, int align, float outline, int outlineColor) {
        Font f = baseFont.deriveFont(size);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        float tw = (float) fm.getStringBounds(s, g).getWidth();
        float tx = align == ALIGN_CENTER ? x - tw / 2 : (align == ALIGN_RIGHT ? x - tw : x);
        GlyphVector gv = f.createGlyphVector(g.getFontRenderContext(), s);
        java.awt.Shape shape = gv.getOutline(tx, y);
        if (outline > 0) {
            g.setColor(new Color(outlineColor, true));
            g.setStroke(new BasicStroke(outline * 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(shape);
        }
        g.setColor(color);
        g.fill(shape);
    }

    @Override
    public float measureText(String s, float size) {
        g.setFont(baseFont.deriveFont(size));
        return (float) g.getFontMetrics().getStringBounds(s, g).getWidth();
    }

    @Override
    public void radial(float x, float y, float r, int inner, int outer) {
        if (r <= 0.5f) return;
        java.awt.Paint old = g.getPaint();
        g.setPaint(new java.awt.RadialGradientPaint(x, y, r, new float[]{0f, 1f},
                new Color[]{new Color(inner, true), new Color(outer, true)}));
        g.fill(new Ellipse2D.Float(x - r, y - r, r * 2, r * 2));
        g.setPaint(old);
    }

    @Override
    public void vertical(float l, float t, float r, float b, int top, int bottom) {
        if (b <= t || r <= l) return;
        java.awt.Paint old = g.getPaint();
        g.setPaint(new java.awt.GradientPaint(0, t, new Color(top, true), 0, b, new Color(bottom, true)));
        g.fill(new Rectangle2D.Float(l, t, r - l, b - t));
        g.setPaint(old);
    }

    private final ArrayDeque<java.awt.Shape> clips = new ArrayDeque<>();

    @Override
    public void clip(float l, float t, float r, float b) {
        g.clip(new Rectangle2D.Float(l, t, r - l, b - t));
    }

    @Override
    public void save() {
        stack.push(g.getTransform());
        java.awt.Shape c = g.getClip();
        clips.push(c != null ? c : new Rectangle2D.Float(-1e6f, -1e6f, 2e6f, 2e6f));
    }

    @Override
    public void restore() {
        g.setTransform(stack.pop());
        g.setClip(clips.pop());
    }

    @Override
    public void translate(float dx, float dy) {
        g.translate(dx, dy);
    }

    @Override
    public void scale(float s) {
        g.scale(s, s);
    }

    @Override
    public void rotate(float degrees) {
        g.rotate(Math.toRadians(degrees));
    }
}

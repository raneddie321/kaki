package com.kaki.snakebrawl;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.kaki.snakebrawl.game.Gfx;

/** {@link Gfx} backed by an Android {@link Canvas}. */
final class AndroidGfx implements Gfx {
    private Canvas c;
    private float w, h;
    private int color;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    AndroidGfx(Typeface font) {
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setTypeface(font);
        text.setStrokeJoin(Paint.Join.ROUND);
        text.setSubpixelText(true);
    }

    void begin(Canvas canvas, float width, float height) {
        c = canvas;
        w = width;
        h = height;
    }

    @Override
    public float width() {
        return w;
    }

    @Override
    public float height() {
        return h;
    }

    @Override
    public void color(int argb) {
        color = argb;
        fill.setColor(argb);
        stroke.setColor(argb);
    }

    @Override
    public void fillCircle(float x, float y, float r) {
        if (r > 0) c.drawCircle(x, y, r, fill);
    }

    @Override
    public void strokeCircle(float x, float y, float r, float sw) {
        stroke.setStrokeWidth(sw);
        if (r > 0) c.drawCircle(x, y, r, stroke);
    }

    @Override
    public void fillRect(float l, float t, float r, float b) {
        c.drawRect(l, t, r, b, fill);
    }

    @Override
    public void fillRoundRect(float l, float t, float r, float b, float radius) {
        rect.set(l, t, r, b);
        c.drawRoundRect(rect, radius, radius, fill);
    }

    @Override
    public void strokeRoundRect(float l, float t, float r, float b, float radius, float sw) {
        stroke.setStrokeWidth(sw);
        rect.set(l, t, r, b);
        c.drawRoundRect(rect, radius, radius, stroke);
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, float sw) {
        stroke.setStrokeWidth(sw);
        c.drawLine(x1, y1, x2, y2, stroke);
    }

    @Override
    public void arc(float cx, float cy, float radius, float startDeg, float sweepDeg, float sw) {
        stroke.setStrokeWidth(sw);
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        c.drawArc(rect, startDeg, sweepDeg, false, stroke);
    }

    @Override
    public void fillPoly(float[] xy, int count) {
        if (count < 3) return;
        path.rewind();
        path.moveTo(xy[0], xy[1]);
        for (int i = 1; i < count; i++) path.lineTo(xy[i * 2], xy[i * 2 + 1]);
        path.close();
        c.drawPath(path, fill);
    }

    @Override
    public void text(String s, float x, float y, float size, int align, float outline, int outlineColor) {
        text.setTextSize(size);
        text.setTextAlign(align == ALIGN_CENTER ? Paint.Align.CENTER : (align == ALIGN_RIGHT ? Paint.Align.RIGHT : Paint.Align.LEFT));
        if (outline > 0) {
            text.setStyle(Paint.Style.STROKE);
            text.setStrokeWidth(outline * 2f);
            text.setColor(outlineColor);
            c.drawText(s, x, y, text);
        }
        text.setStyle(Paint.Style.FILL);
        text.setColor(color);
        c.drawText(s, x, y, text);
    }

    @Override
    public float measureText(String s, float size) {
        text.setTextSize(size);
        return text.measureText(s);
    }

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    @Override
    public void image(Object img, float l, float t, float r, float b, float alpha) {
        if (!(img instanceof Bitmap)) return;
        bitmapPaint.setAlpha((int) (Math.max(0f, Math.min(1f, alpha)) * 255));
        rect.set(l, t, r, b);
        c.drawBitmap((Bitmap) img, null, rect, bitmapPaint);
    }

    @Override
    public void clip(float l, float t, float r, float b) {
        c.clipRect(l, t, r, b);
    }

    @Override
    public void save() {
        c.save();
    }

    @Override
    public void restore() {
        c.restore();
    }

    @Override
    public void translate(float dx, float dy) {
        c.translate(dx, dy);
    }

    @Override
    public void scale(float s) {
        c.scale(s, s);
    }

    @Override
    public void rotate(float degrees) {
        c.rotate(degrees);
    }
}

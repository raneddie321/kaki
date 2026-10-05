package com.kaki.snakebrawl.game;

/**
 * Minimal drawing surface used by the game. The Android build implements it on top of
 * {@code android.graphics.Canvas}; the desktop test harness implements it with Java2D.
 * Colors are packed ARGB ints.
 */
public interface Gfx {
    int ALIGN_LEFT = 0;
    int ALIGN_CENTER = 1;
    int ALIGN_RIGHT = 2;

    float width();
    float height();

    void color(int argb);

    void fillCircle(float x, float y, float r);
    void strokeCircle(float x, float y, float r, float strokeWidth);
    void fillRect(float l, float t, float r, float b);
    void fillRoundRect(float l, float t, float r, float b, float radius);
    void strokeRoundRect(float l, float t, float r, float b, float radius, float strokeWidth);
    void line(float x1, float y1, float x2, float y2, float strokeWidth);
    void arc(float cx, float cy, float radius, float startDeg, float sweepDeg, float strokeWidth);
    /** Fills a polygon given as x0,y0,x1,y1,... using the first {@code count} points. */
    void fillPoly(float[] xy, int count);

    /** Draws text with its baseline at y. A positive outline draws a dark border first. */
    void text(String s, float x, float y, float size, int align, float outline, int outlineColor);
    float measureText(String s, float size);

    void save();
    void restore();
    void translate(float dx, float dy);
    void scale(float s);
    void rotate(float degrees);
}

package com.snakebrawl.myapp.web;

import com.snakebrawl.myapp.game.Gfx;

import org.teavm.jso.JSBody;

/** {@link Gfx} on an HTML canvas. The drawing itself lives in the page's sb.js (the SB object). */
final class WebGfx implements Gfx {
    private float w, h;

    void begin(float width, float height) {
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

    @JSBody(params = "c", script = "SB.color(c);")
    private static native void jsColor(int c);

    @Override
    public void color(int argb) {
        jsColor(argb);
    }

    @JSBody(params = {"x", "y", "r"}, script = "SB.fillCircle(x, y, r);")
    private static native void jsFillCircle(float x, float y, float r);

    @Override
    public void fillCircle(float x, float y, float r) {
        if (r > 0) jsFillCircle(x, y, r);
    }

    @JSBody(params = {"x", "y", "r", "w"}, script = "SB.strokeCircle(x, y, r, w);")
    private static native void jsStrokeCircle(float x, float y, float r, float w);

    @Override
    public void strokeCircle(float x, float y, float r, float sw) {
        if (r > 0) jsStrokeCircle(x, y, r, sw);
    }

    @JSBody(params = {"l", "t", "r", "b"}, script = "SB.fillRect(l, t, r, b);")
    private static native void jsFillRect(float l, float t, float r, float b);

    @Override
    public void fillRect(float l, float t, float r, float b) {
        jsFillRect(l, t, r, b);
    }

    @JSBody(params = {"l", "t", "r", "b", "rad"}, script = "SB.fillRoundRect(l, t, r, b, rad);")
    private static native void jsFillRoundRect(float l, float t, float r, float b, float rad);

    @Override
    public void fillRoundRect(float l, float t, float r, float b, float radius) {
        jsFillRoundRect(l, t, r, b, radius);
    }

    @JSBody(params = {"l", "t", "r", "b", "rad", "w"}, script = "SB.strokeRoundRect(l, t, r, b, rad, w);")
    private static native void jsStrokeRoundRect(float l, float t, float r, float b, float rad, float w);

    @Override
    public void strokeRoundRect(float l, float t, float r, float b, float radius, float sw) {
        jsStrokeRoundRect(l, t, r, b, radius, sw);
    }

    @JSBody(params = {"x1", "y1", "x2", "y2", "w"}, script = "SB.line(x1, y1, x2, y2, w);")
    private static native void jsLine(float x1, float y1, float x2, float y2, float w);

    @Override
    public void line(float x1, float y1, float x2, float y2, float sw) {
        jsLine(x1, y1, x2, y2, sw);
    }

    @JSBody(params = {"x", "y", "r", "s", "e", "w"}, script = "SB.arc(x, y, r, s, e, w);")
    private static native void jsArc(float x, float y, float r, float s, float e, float w);

    @Override
    public void arc(float cx, float cy, float radius, float startDeg, float sweepDeg, float sw) {
        if (radius > 0) jsArc(cx, cy, radius, startDeg, sweepDeg, sw);
    }

    @JSBody(params = {"x", "y"}, script = "SB.moveTo(x, y);")
    private static native void jsMoveTo(float x, float y);

    @JSBody(params = {"x", "y"}, script = "SB.lineTo(x, y);")
    private static native void jsLineTo(float x, float y);

    @JSBody(script = "SB.fillPath();")
    private static native void jsFillPath();

    @Override
    public void fillPoly(float[] xy, int count) {
        if (count < 3) return;
        jsMoveTo(xy[0], xy[1]);
        for (int i = 1; i < count; i++) jsLineTo(xy[i * 2], xy[i * 2 + 1]);
        jsFillPath();
    }

    @JSBody(params = {"s", "x", "y", "size", "align", "o", "oc"}, script = "SB.text(s, x, y, size, align, o, oc);")
    private static native void jsText(String s, float x, float y, float size, int align, float o, int oc);

    @Override
    public void text(String s, float x, float y, float size, int align, float outline, int outlineColor) {
        jsText(s, x, y, size, align, outline, outlineColor);
    }

    @JSBody(params = {"s", "size"}, script = "return SB.measure(s, size);")
    private static native float jsMeasure(String s, float size);

    @Override
    public float measureText(String s, float size) {
        return jsMeasure(s, size);
    }

    @JSBody(params = {"x", "y", "r", "a", "b"}, script = "SB.radial(x, y, r, a, b);")
    private static native void jsRadial(float x, float y, float r, int a, int b);

    @Override
    public void radial(float x, float y, float r, int inner, int outer) {
        if (r > 0.5f) jsRadial(x, y, r, inner, outer);
    }

    @JSBody(params = {"l", "t", "r", "b", "a", "c"}, script = "SB.vertical(l, t, r, b, a, c);")
    private static native void jsVertical(float l, float t, float r, float b, int a, int c);

    @Override
    public void vertical(float l, float t, float r, float b, int top, int bottom) {
        if (b > t && r > l) jsVertical(l, t, r, b, top, bottom);
    }

    @JSBody(params = {"l", "t", "r", "b"}, script = "SB.clip(l, t, r, b);")
    private static native void jsClip(float l, float t, float r, float b);

    @Override
    public void clip(float l, float t, float r, float b) {
        jsClip(l, t, r, b);
    }

    @JSBody(script = "SB.save();")
    private static native void jsSave();

    @Override
    public void save() {
        jsSave();
    }

    @JSBody(script = "SB.restore();")
    private static native void jsRestore();

    @Override
    public void restore() {
        jsRestore();
    }

    @JSBody(params = {"x", "y"}, script = "SB.translate(x, y);")
    private static native void jsTranslate(float x, float y);

    @Override
    public void translate(float dx, float dy) {
        jsTranslate(dx, dy);
    }

    @JSBody(params = "s", script = "SB.scale(s);")
    private static native void jsScale(float s);

    @Override
    public void scale(float s) {
        jsScale(s);
    }

    @JSBody(params = "d", script = "SB.rotate(d);")
    private static native void jsRotate(float d);

    @Override
    public void rotate(float degrees) {
        jsRotate(degrees);
    }
}

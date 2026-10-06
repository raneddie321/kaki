package com.snakebrawl.myapp.web;

import com.snakebrawl.myapp.game.Game;
import com.snakebrawl.myapp.game.Platform;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Browser entry point: wires the page's canvas, touches, sounds and storage to the game. */
public final class WebMain implements Platform {
    /** Events from sb.js: kind 0 down, 1 move, 2 up, 3 back, 4 hidden, 5 frame (x = seconds). */
    @JSFunctor
    interface Handler extends JSObject {
        void on(int kind, int id, float x, float y);
    }

    @JSBody(params = "h", script = "SB.start(h);")
    private static native void start(Handler h);

    @JSBody(script = "return SB.width();")
    private static native float canvasWidth();

    @JSBody(script = "return SB.height();")
    private static native float canvasHeight();

    @JSBody(params = "i", script = "return SB.inset(i);")
    private static native float inset(int i);

    @JSBody(params = {"id", "v"}, script = "SB.play(id, v);")
    private static native void play(int id, float v);

    @JSBody(params = "ms", script = "SB.vibrate(ms);")
    private static native void jsVibrate(int ms);

    @JSBody(params = "k", script = "return SB.load(k);")
    private static native String load(String k);

    @JSBody(params = {"k", "v"}, script = "SB.store(k, v);")
    private static native void store(String k, String v);

    @JSBody(params = {"title", "initial"}, script = "return SB.prompt(title, initial);")
    private static native String prompt(String title, String initial);

    private Game game;
    private final WebGfx gfx = new WebGfx();

    public static void main(String[] args) {
        final WebMain m = new WebMain();
        m.game = new Game(m);
        start(new Handler() {
            @Override
            public void on(int kind, int id, float x, float y) {
                m.event(kind, id, x, y);
            }
        });
    }

    private void event(int kind, int id, float x, float y) {
        switch (kind) {
            case 0:
                game.touchDown(id, x, y);
                break;
            case 1:
                game.touchMove(id, x, y);
                break;
            case 2:
                game.touchUp(id, x, y);
                break;
            case 3:
                game.onBack();
                break;
            case 4:
                game.onPause();
                break;
            default:
                gfx.begin(canvasWidth(), canvasHeight());
                game.setInsets(inset(0), inset(1), inset(2), inset(3));
                game.frame(x, gfx);
                break;
        }
    }

    @Override
    public void playSound(int id, float volume) {
        play(id, volume);
    }

    @Override
    public int loadInt(String key, int def) {
        String v = load(key);
        if (v == null) return def;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public void saveInt(String key, int value) {
        store(key, Integer.toString(value));
    }

    @Override
    public String loadString(String key, String def) {
        String v = load("s_" + key);
        return v == null ? def : v;
    }

    @Override
    public void saveString(String key, String value) {
        store("s_" + key, value);
    }

    @Override
    public void vibrate(int millis) {
        jsVibrate(millis);
    }

    @Override
    public void requestText(String title, String initial, int maxLength, boolean numeric, TextCallback callback) {
        String t = prompt(title, initial == null ? "" : initial);
        if (t != null && t.length() > maxLength) t = t.substring(0, maxLength);
        callback.onText(t);
    }

    @Override
    public boolean launchPurchase(String productId) {
        return false;
    }

    @Override
    public void setNetworkDiscovery(boolean on) {}
}

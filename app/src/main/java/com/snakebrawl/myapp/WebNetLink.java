package com.snakebrawl.myapp;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.snakebrawl.myapp.game.Platform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Online play for the app. It runs the browser version's own WebRTC code (PeerJS + sb-net.js,
 * bundled in assets/net) in a hidden 1x1 WebView, so app and browser players use exactly the same
 * connection and can join each other's rooms. All calls come from the UI thread.
 */
final class WebNetLink implements Platform.OnlineLink {
    private final Activity activity;
    private final FrameLayout root;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private boolean loaded, broken;
    private final ArrayList<String> pending = new ArrayList<String>();
    private final ConcurrentLinkedQueue<String> inbox = new ConcurrentLinkedQueue<String>();
    private volatile int state = 3;
    private volatile String reason, code = "";
    private final StringBuilder outgoing = new StringBuilder();
    private boolean flushPosted;

    WebNetLink(Activity activity, FrameLayout root) {
        this.activity = activity;
        this.root = root;
    }

    @Override
    public boolean available() {
        return !broken;
    }

    private void ensure() {
        if (web != null || broken) return;
        try {
            String html = "<!doctype html><html><head><meta charset='utf-8'></head><body><script>" + asset("net/peerjs.min.js")
                    + "</script><script>" + asset("net/sb-net.js") + "</script><script>"
                    + "window.SBNetSink = function (d) {"
                    + "  var h = ''; for (var i = 0; i < d.length; i++) { var c = d.charCodeAt(i) & 255; h += (c < 16 ? '0' : '') + c.toString(16); }"
                    + "  AndroidNet.onData(h); };"
                    + "var last = '';"
                    + "setInterval(function () { if (!window.__started) return;"
                    + "  var r = SBNet.reason() || '', s = SBNet.state() + '|' + r + '|' + SBNet.code();"
                    + "  if (s !== last) { last = s; AndroidNet.onState(SBNet.state(), r, SBNet.code() || ''); } }, 40);"
                    + "</script></body></html>";
            web = new WebView(activity);
            WebSettings ws = web.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            web.addJavascriptInterface(new Bridge(), "AndroidNet");
            web.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    loaded = true;
                    for (String js : pending) web.evaluateJavascript(js, null);
                    pending.clear();
                }
            });
            root.addView(web, 0, new FrameLayout.LayoutParams(1, 1));
            web.loadDataWithBaseURL("https://snakebrawl.app/net/", html, "text/html", "utf-8", null);
        } catch (Exception e) {
            // No WebView on this device: online play is unavailable, Wi-Fi play still works
            broken = true;
            web = null;
            state = 3;
            reason = "Online play is not available on this device";
        }
    }

    private String asset(String name) throws IOException {
        InputStream in = activity.getAssets().open(name);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8").replace("</script", "<\\/script");
        } finally {
            in.close();
        }
    }

    private void run(String js) {
        ensure();
        if (web == null) return;
        if (loaded) web.evaluateJavascript(js, null);
        else pending.add(js);
    }

    /** Quotes a string of byte values as a JavaScript string literal. */
    private static String quote(String s) {
        StringBuilder b = new StringBuilder(s.length() * 2 + 2).append('\'');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c < 0x7f && c != '\'' && c != '\\') {
                b.append(c);
            } else {
                b.append("\\u00").append(Character.forDigit((c >> 4) & 15, 16)).append(Character.forDigit(c & 15, 16));
            }
        }
        return b.append('\'').toString();
    }

    @Override
    public void host() {
        inbox.clear();
        state = 0;
        reason = null;
        code = "";
        run("window.__started = true; SBNet.host();");
    }

    @Override
    public void search() {
        inbox.clear();
        state = 0;
        reason = null;
        code = "";
        run("window.__started = true; SBNet.search();");
    }

    @Override
    public void join(String c) {
        state = 1;
        reason = null;
        run("SBNet.join(" + quote(c) + ");");
    }

    @Override
    public int state() {
        return state;
    }

    @Override
    public String reason() {
        return reason;
    }

    @Override
    public void clearReason() {
        reason = null;
        run("SBNet.clearReason();");
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public void send(String data) {
        // Batch everything sent during this frame into one script call
        outgoing.append(quote(data)).append(',');
        if (!flushPosted) {
            flushPosted = true;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    flushPosted = false;
                    if (outgoing.length() == 0) return;
                    String js = "[" + outgoing + "].forEach(function (s) { SBNet.send(s); });";
                    outgoing.setLength(0);
                    WebNetLink.this.run(js);
                }
            });
        }
    }

    @Override
    public String poll() {
        return inbox.poll();
    }

    @Override
    public void close() {
        state = 3;
        inbox.clear();
        // Flush a pending goodbye before closing
        if (outgoing.length() > 0) {
            String js = "[" + outgoing + "].forEach(function (s) { SBNet.send(s); });";
            outgoing.setLength(0);
            run(js);
        }
        run("SBNet.close();");
    }

    @Override
    public boolean exactFloats() {
        return true;
    }

    @Override
    public boolean isBrowser() {
        return false;
    }

    void destroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
    }

    /** Called by the page on a background thread. */
    private final class Bridge {
        @JavascriptInterface
        public void onData(String hex) {
            char[] c = new char[hex.length() / 2];
            for (int i = 0; i < c.length; i++) c[i] = (char) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            inbox.add(new String(c));
        }

        @JavascriptInterface
        public void onState(int s, String r, String c) {
            state = s;
            reason = r == null || r.length() == 0 ? null : r;
            code = c == null ? "" : c;
        }
    }
}

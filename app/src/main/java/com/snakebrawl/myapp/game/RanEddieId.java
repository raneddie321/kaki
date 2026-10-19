package com.snakebrawl.myapp.game;

/**
 * RanEddie ID: an account that keeps the player's progress in the cloud, so it survives a new
 * phone and can be continued on other devices.
 *
 * Connecting: the game opens {@link #SITE}/connect in the browser with a random state and a PKCE
 * challenge. The player signs in or signs up there and approves; the site sends them back with
 * snakebrawl://raneddie?state=...&code=... . The game trades the code plus its secret verifier for
 * a game token (kept on the phone), then reconciles its progress with the cloud save.
 *
 * Syncing: every profile save marks the progress dirty; it is uploaded a few seconds later (and
 * when the app goes to the background). The game downloads the cloud save on start, when it comes
 * back to the foreground and every so often on the menus, so playing on another device carries
 * over. Uploads say which cloud save they build on; if another device saved in between, the
 * server refuses (409) and the two are merged (see {@link Profile#mergeText}) and uploaded again.
 *
 * Network calls run on background threads; their results are applied on the game thread in
 * {@link #update(float)}.
 */
final class RanEddieId {
    /** The RanEddie ID website (see the raneddie-id folder). */
    static final String SITE = "https://raneddie-id.vercel.app";
    /** RanEddie ID accounts start at this age (the website asks for the date of birth too). */
    static final int MIN_AGE = 13;

    private static final float UPLOAD_DELAY = 8f;
    /** How often the menus check for progress made on another device, in seconds. */
    private static final float PULL_EVERY = 45f;

    private final Game game;
    private final Platform p;
    private final Profile pr;

    private String token, username;
    /** Server time of the cloud save this device last uploaded or downloaded. */
    private long syncedAt;
    /** Progress changed here since the last upload. */
    private boolean dirty;
    private float dirtyFor;
    /** Coins and trophies in the cloud save at {@link #syncedAt}, to merge changes from two devices. */
    private int baseCoins, baseTrophies;
    private float sincePull;
    private volatile boolean pullSoon;

    // Background work and its result, handed to the game thread
    private volatile boolean busy;
    private volatile Runnable result;

    RanEddieId(Game game) {
        this.game = game;
        this.p = game.gated;
        this.pr = game.profile;
        token = p.loadString("rid_token", "");
        username = p.loadString("rid_user", "");
        syncedAt = parseLong(p.loadString("rid_synced", "0"));
        dirty = p.loadInt("rid_dirty", 0) == 1;
        baseCoins = p.loadInt("rid_baseCoins", pr.coins);
        baseTrophies = p.loadInt("rid_baseTrophies", pr.trophies);
    }

    boolean linked() {
        return token.length() > 0;
    }

    String username() {
        return username;
    }

    /** The username for a button label: upper case, at most 12 characters. */
    String shortName() {
        String n = username.length() > 0 ? username.toUpperCase() : "CONNECTED";
        return n.length() > 12 ? n.substring(0, 11) + "…" : n;
    }

    boolean allowed() {
        return pr.age >= MIN_AGE;
    }

    boolean busy() {
        return busy;
    }

    // ------------------------------------------------------------------ connect

    /** Opens the website to sign in or sign up and approve this game. */
    void connect() {
        String verifier = base64url(p.randomBytes(32));
        String state = base64url(p.randomBytes(12));
        p.saveString("rid_verifier", verifier);
        p.saveString("rid_state", state);
        byte[] ascii = new byte[verifier.length()];
        for (int i = 0; i < ascii.length; i++) ascii[i] = (byte) verifier.charAt(i);
        String challenge = base64url(sha256(ascii));
        p.openUrl(SITE + "/connect?game=snakebrawl&state=" + state + "&ch=" + challenge);
    }

    /** Handles snakebrawl://raneddie?state=...&code=... from the website. */
    void onDeepLink(String uri) {
        if (uri == null || !uri.startsWith("snakebrawl://raneddie")) return;
        String state = queryParam(uri, "state"), code = queryParam(uri, "code");
        String wantState = p.loadString("rid_state", "");
        final String verifier = p.loadString("rid_verifier", "");
        if (code == null || state == null || wantState.length() == 0 || !state.equals(wantState) || verifier.length() == 0) {
            game.showInfo("RANEDDIE ID", "That link is out of date. Tap Connect RanEddie ID again.", 0, 0);
            return;
        }
        p.saveString("rid_state", "");
        final String linkCode = code;
        run(new Runnable() {
            @Override
            public void run() {
                String body = "{\"code\":" + Json.quote(linkCode) + ",\"verifier\":" + Json.quote(verifier) + "}";
                String[] res = http("POST", "/api/game/token", null, body);
                final String tok = res != null && res[0].equals("200") ? Json.string(res[1], "token") : null;
                final String user = res != null ? Json.string(res[1], "username") : null;
                if (tok == null) {
                    post(new Runnable() {
                        @Override
                        public void run() {
                            game.showInfo("RANEDDIE ID", "We couldn't connect your RanEddie ID. Check your internet and try again.", 0, 0);
                        }
                    });
                    return;
                }
                // Signed in: look at the cloud save before deciding what to keep
                String[] save = http("GET", "/api/game/save", tok, null);
                final boolean ok = save != null && save[0].equals("200");
                final String cloud = ok ? Json.string(save[1], "save") : null;
                final long cloudAt = ok ? Json.number(save[1], "updatedAt") : 0;
                post(new Runnable() {
                    @Override
                    public void run() {
                        p.saveString("rid_verifier", "");
                        setLinked(tok, user == null ? "" : user);
                        if (cloud == null || cloud.length() == 0) {
                            // New RanEddie ID: this device's progress becomes the cloud save
                            markDirty();
                            dirtyFor = UPLOAD_DELAY;
                            game.showInfo("RANEDDIE ID CONNECTED!", "Your progress is now saved to " + displayUser()
                                    + ". Sign in with it on any device to keep playing.", 1, 0);
                        } else {
                            // The RanEddie ID already has progress: it always wins, no question asked
                            useCloud(cloud, cloudAt);
                            game.showInfo("WELCOME BACK!", "Your progress from " + displayUser() + " is loaded. You have "
                                    + pr.trophies + " trophies.", 1, 0);
                        }
                    }
                });
            }
        });
    }

    /** Signs this device out of RanEddie ID. Progress stays on the device. */
    void disconnect() {
        flush();
        setLinked("", "");
        p.saveString("rid_synced", "0");
        syncedAt = 0;
    }

    private void setLinked(String tok, String user) {
        token = tok;
        username = user;
        p.saveString("rid_token", tok);
        p.saveString("rid_user", user);
        game.layout();
    }

    private String displayUser() {
        return username.length() > 0 ? username : "your RanEddie ID";
    }

    private void useCloud(String save, long at) {
        pr.importText(save);
        game.applyProfileChange();
        dirty = false;
        p.saveInt("rid_dirty", 0);
        setSynced(at, save);
    }

    /**
     * Newer progress arrived from another device. With nothing new here it simply replaces this
     * device's progress; if this device changed things too, the two are merged and the result is
     * uploaded, so nothing bought or earned on either device is lost.
     */
    private void takeCloud(String save, long at) {
        if (!dirty) {
            useCloud(save, at);
            return;
        }
        pr.mergeText(save, baseCoins, baseTrophies);
        game.applyProfileChange();
        setSynced(at, save);
        markDirty();
        dirtyFor = UPLOAD_DELAY; // upload the merged progress right away
    }

    // ------------------------------------------------------------------ sync

    /** Called after every profile save. */
    void markDirty() {
        if (!dirty) {
            dirty = true;
            p.saveInt("rid_dirty", 1);
        }
        dirtyFor = 0;
    }

    /** Checks the cloud soon (the app came back to the foreground). Safe to call from any thread. */
    void requestPull() {
        pullSoon = true;
    }

    /** On start: download the cloud save in case progress was made on another device. */
    void pullOnStart() {
        pull();
    }

    private void pull() {
        if (!linked() || busy) return;
        sincePull = 0;
        final String tok = token;
        run(new Runnable() {
            @Override
            public void run() {
                final String[] res = http("GET", "/api/game/save", tok, null);
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (res == null || !tok.equals(token)) return; // offline: try again later
                        if (res[0].equals("401")) {
                            signedOutByServer();
                            return;
                        }
                        if (!res[0].equals("200")) return;
                        String cloud = Json.string(res[1], "save");
                        long at = Json.number(res[1], "updatedAt");
                        String user = Json.string(res[1], "username");
                        if (user != null && !user.equals(username)) setLinked(token, user);
                        if (cloud == null || cloud.length() == 0 || at <= syncedAt) return;
                        takeCloud(cloud, at);
                    }
                });
            }
        });
    }

    /** Uploads now if there are unsaved changes (the app is going to the background). */
    void flush() {
        if (linked() && dirty) upload();
    }

    void update(float dt) {
        Runnable r = result;
        if (r != null) {
            result = null;
            r.run();
        }
        String link = p.takeDeepLink();
        if (link != null) onDeepLink(link);
        if (!linked() || busy) return;
        if (dirty) {
            dirtyFor += dt;
            if (dirtyFor >= UPLOAD_DELAY) upload();
            return;
        }
        // Look for progress made on another device: when the app comes back, and now and then
        // while the player is on the menus (never in the middle of a match)
        sincePull += dt;
        if (pullSoon || (sincePull >= PULL_EVERY && !game.inMatch())) {
            pullSoon = false;
            pull();
        }
    }

    private void upload() {
        if (busy) return;
        final String tok = token;
        final String save = pr.exportText();
        final String body = "{\"save\":" + Json.quote(save) + ",\"baseAt\":" + syncedAt + ",\"summary\":{\"nickname\":" + Json.quote(pr.displayName())
                + ",\"trophies\":" + pr.trophies + ",\"bestTrophies\":" + pr.bestTrophies + ",\"coins\":" + pr.coins
                + ",\"wins\":" + pr.wins + ",\"games\":" + pr.games + ",\"snakes\":" + Integer.bitCount(pr.unlocked) + "}}";
        dirty = false;
        dirtyFor = 0;
        run(new Runnable() {
            @Override
            public void run() {
                final String[] res = http("PUT", "/api/game/save", tok, body);
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (!tok.equals(token)) return;
                        if (res != null && res[0].equals("200")) {
                            if (!dirty) p.saveInt("rid_dirty", 0);
                            setSynced(Json.number(res[1], "updatedAt"), save);
                        } else if (res != null && res[0].equals("409")) {
                            // Another device saved first: merge its progress with ours, then upload again
                            dirty = true;
                            String cloud = Json.string(res[1], "save");
                            long at = Json.number(res[1], "updatedAt");
                            if (cloud != null && cloud.length() > 0) takeCloud(cloud, at);
                        } else {
                            dirty = true; // try again later
                            if (res != null && res[0].equals("401")) signedOutByServer();
                        }
                    }
                });
            }
        });
    }

    /** Remembers the cloud save this device now matches (its time, and its coins and trophies for merging). */
    private void setSynced(long at, String save) {
        syncedAt = at;
        int[] ct = Profile.coinsAndTrophies(save);
        baseCoins = ct[0];
        baseTrophies = ct[1];
        p.saveString("rid_synced", Long.toString(at));
        p.saveInt("rid_baseCoins", baseCoins);
        p.saveInt("rid_baseTrophies", baseTrophies);
    }

    /** The token was revoked (signed out everywhere, or the account was removed). */
    private void signedOutByServer() {
        setLinked("", "");
        game.showInfo("RANEDDIE ID", "You were signed out of RanEddie ID. Your progress is still on this device; connect again "
                + "in Settings to keep saving it.", 0, 0);
    }

    // ------------------------------------------------------------------ plumbing

    private void run(final Runnable work) {
        busy = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    work.run();
                } finally {
                    busy = false;
                }
            }
        }, "raneddie-id");
        t.setDaemon(true);
        t.start();
    }

    /** Hands a result to the game thread (one at a time; the next call waits for the game to take it). */
    private void post(Runnable r) {
        while (result != null) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                return;
            }
        }
        result = r;
    }

    /** {status, body}, or null when offline. */
    private String[] http(String method, String path, String bearer, String body) {
        String res = p.httpJson(method, SITE + path, bearer, body);
        if (res == null) return null;
        int nl = res.indexOf('\n');
        return nl < 0 ? new String[]{res, ""} : new String[]{res.substring(0, nl), res.substring(nl + 1)};
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String queryParam(String uri, String name) {
        int q = uri.indexOf('?');
        if (q < 0) return null;
        for (String part : uri.substring(q + 1).split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(name)) return urlDecode(part.substring(eq + 1));
        }
        return null;
    }

    private static String urlDecode(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                b.append((char) Integer.parseInt(s.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                b.append(c == '+' ? ' ' : c);
            }
        }
        return b.toString();
    }

    static String base64url(byte[] data) {
        final String abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        StringBuilder b = new StringBuilder();
        int i = 0;
        for (; i + 2 < data.length; i += 3) {
            int v = (data[i] & 255) << 16 | (data[i + 1] & 255) << 8 | (data[i + 2] & 255);
            b.append(abc.charAt(v >> 18 & 63)).append(abc.charAt(v >> 12 & 63)).append(abc.charAt(v >> 6 & 63)).append(abc.charAt(v & 63));
        }
        if (data.length - i == 1) {
            int v = (data[i] & 255) << 16;
            b.append(abc.charAt(v >> 18 & 63)).append(abc.charAt(v >> 12 & 63));
        } else if (data.length - i == 2) {
            int v = (data[i] & 255) << 16 | (data[i + 1] & 255) << 8;
            b.append(abc.charAt(v >> 18 & 63)).append(abc.charAt(v >> 12 & 63)).append(abc.charAt(v >> 6 & 63));
        }
        return b.toString();
    }

    /** SHA-256 in plain Java (the browser build has no MessageDigest). */
    static byte[] sha256(byte[] msg) {
        final int[] k = {
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2};
        int[] h = {0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};
        int padded = ((msg.length + 9 + 63) / 64) * 64;
        byte[] m = new byte[padded];
        System.arraycopy(msg, 0, m, 0, msg.length);
        m[msg.length] = (byte) 0x80;
        long bits = (long) msg.length * 8;
        for (int i = 0; i < 8; i++) m[padded - 1 - i] = (byte) (bits >>> (8 * i));
        int[] w = new int[64];
        for (int off = 0; off < padded; off += 64) {
            for (int i = 0; i < 16; i++) {
                w[i] = (m[off + 4 * i] & 255) << 24 | (m[off + 4 * i + 1] & 255) << 16 | (m[off + 4 * i + 2] & 255) << 8 | (m[off + 4 * i + 3] & 255);
            }
            for (int i = 16; i < 64; i++) {
                int s0 = Integer.rotateRight(w[i - 15], 7) ^ Integer.rotateRight(w[i - 15], 18) ^ (w[i - 15] >>> 3);
                int s1 = Integer.rotateRight(w[i - 2], 17) ^ Integer.rotateRight(w[i - 2], 19) ^ (w[i - 2] >>> 10);
                w[i] = w[i - 16] + s0 + w[i - 7] + s1;
            }
            int a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
            for (int i = 0; i < 64; i++) {
                int t1 = hh + (Integer.rotateRight(e, 6) ^ Integer.rotateRight(e, 11) ^ Integer.rotateRight(e, 25)) + ((e & f) ^ (~e & g)) + k[i] + w[i];
                int t2 = (Integer.rotateRight(a, 2) ^ Integer.rotateRight(a, 13) ^ Integer.rotateRight(a, 22)) + ((a & b) ^ (a & c) ^ (b & c));
                hh = g;
                g = f;
                f = e;
                e = d + t1;
                d = c;
                c = b;
                b = a;
                a = t1 + t2;
            }
            h[0] += a;
            h[1] += b;
            h[2] += c;
            h[3] += d;
            h[4] += e;
            h[5] += f;
            h[6] += g;
            h[7] += hh;
        }
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) out[i] = (byte) (h[i / 4] >>> (24 - 8 * (i % 4)));
        return out;
    }

    /** Just enough JSON for the RanEddie ID API: quoting, and reading a top-level string or number. */
    static final class Json {
        private Json() {}

        static String quote(String s) {
            StringBuilder b = new StringBuilder("\"");
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"' || c == '\\') b.append('\\').append(c);
                else if (c == '\n') b.append("\\n");
                else if (c == '\r') b.append("\\r");
                else if (c == '\t') b.append("\\t");
                else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                else b.append(c);
            }
            return b.append('"').toString();
        }

        /** Index just after `"key":` (whitespace skipped), or -1. */
        private static int valueAt(String json, String key) {
            if (json == null) return -1;
            String k = "\"" + key + "\"";
            int i = json.indexOf(k);
            while (i >= 0) {
                int j = i + k.length();
                while (j < json.length() && json.charAt(j) == ' ') j++;
                if (j < json.length() && json.charAt(j) == ':') {
                    j++;
                    while (j < json.length() && json.charAt(j) == ' ') j++;
                    return j;
                }
                i = json.indexOf(k, i + 1);
            }
            return -1;
        }

        static String string(String json, String key) {
            int i = valueAt(json, key);
            if (i < 0 || i >= json.length() || json.charAt(i) != '"') return null;
            StringBuilder b = new StringBuilder();
            for (i++; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '"') return b.toString();
                if (c == '\\' && i + 1 < json.length()) {
                    char n = json.charAt(++i);
                    switch (n) {
                        case 'n': b.append('\n'); break;
                        case 'r': b.append('\r'); break;
                        case 't': b.append('\t'); break;
                        case 'b': b.append('\b'); break;
                        case 'f': b.append('\f'); break;
                        case 'u':
                            if (i + 4 < json.length()) {
                                b.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                                i += 4;
                            }
                            break;
                        default: b.append(n);
                    }
                } else {
                    b.append(c);
                }
            }
            return null;
        }

        static long number(String json, String key) {
            int i = valueAt(json, key);
            if (i < 0) return 0;
            int j = i;
            while (j < json.length() && (Character.isDigit(json.charAt(j)) || json.charAt(j) == '-')) j++;
            return j > i ? parseLong(json.substring(i, j)) : 0;
        }
    }
}

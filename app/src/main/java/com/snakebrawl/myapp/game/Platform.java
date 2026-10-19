package com.snakebrawl.myapp.game;

/** Services the game needs from the host (Android app or desktop harness). */
public interface Platform {
    int SND_SHOOT = 0;
    int SND_SHOTGUN = 1;
    int SND_BOLT = 2;
    int SND_THROW = 3;
    int SND_EXPLODE = 4;
    int SND_FLAME = 5;
    int SND_HIT = 6;
    int SND_EAT = 7;
    int SND_POWER = 8;
    int SND_SUPER_READY = 9;
    int SND_SUPER = 10;
    int SND_DEATH = 11;
    int SND_KILL = 12;
    int SND_VICTORY = 13;
    int SND_DEFEAT = 14;
    int SND_CLICK = 15;
    int SND_BOX = 16;
    int SND_COUNT = 17;

    void playSound(int id, float volume);

    int loadInt(String key, int def);

    void saveInt(String key, int value);

    void vibrate(int millis);

    String loadString(String key, String def);

    void saveString(String key, String value);

    /** Receives text typed by the player, or null if the dialog was cancelled. */
    interface TextCallback {
        void onText(String text);
    }

    /** Shows the system keyboard in a small dialog to type a line of text. */
    void requestText(String title, String initial, int maxLength, boolean numeric, TextCallback callback);

    /**
     * Starts a real store purchase (Google Play Billing) and reports back through
     * {@link Game#onPurchaseResult}. Returns false if real billing is not available, in which
     * case the game shows its simulated test checkout instead.
     */
    boolean launchPurchase(String productId);

    /**
     * Turns Wi-Fi room discovery on or off. On Android this holds a multicast lock so the phone
     * can hear room announcements from other phones on the network.
     */
    void setNetworkDiscovery(boolean on);

    /** This app's version code, or 0 where updates don't apply (the browser version). */
    default int appVersionCode() {
        return 0;
    }

    /** Downloads a small text file (blocking; call from a background thread). Null on any failure. */
    default String fetchText(String url) {
        return null;
    }

    /** Opens the game's page in the store so the player can update. */
    default void openStorePage() {}

    /** Keeps the Wi-Fi radio awake and in low-latency mode while friends are connected. */
    default void setNetworkFast(boolean on) {}

    /**
     * Internet play over WebRTC (shared by the app and the browser so they can play together).
     * State codes match {@link NetSession}: 0 waiting, 1 connecting, 2 connected, 3 closed.
     * Messages are strings whose chars are byte values 0..255.
     */
    interface OnlineLink {
        boolean available();

        /** Opens a room; {@link #code()} becomes its room code. */
        void host();

        /** Gets ready to join a room. */
        void search();

        void join(String code);

        int state();

        /** Last error or close reason, or null. */
        String reason();

        void clearReason();

        String code();

        void send(String data);

        /** Next received message, or null. */
        String poll();

        void close();

        /** True when the game here runs with real 32-bit float math (needed for cross-play). */
        boolean exactFloats();

        boolean isBrowser();
    }

    /** Online play, or null when this platform has none. */
    default OnlineLink online() {
        return null;
    }

    /**
     * Opens the system share sheet (or copies the text) so the player can send it to friends.
     * Returns false when this platform can't share.
     */
    default boolean share(String text) {
        return false;
    }

    /** Opens a web page in the player's browser. */
    default void openUrl(String url) {}

    /**
     * Sends a JSON request (blocking; call from a background thread). {@code bearer} goes in the
     * Authorization header when not null. Returns the HTTP status and the body as
     * "status\nbody", or null when the request couldn't be made at all.
     */
    default String httpJson(String method, String url, String bearer, String body) {
        return null;
    }

    /** A link that opened the game (like snakebrawl://raneddie?...), handed over once; null if none. */
    default String takeDeepLink() {
        return null;
    }

    /** Unpredictable bytes for secrets (the RanEddie ID link verifier). */
    default byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        java.util.Random r = new java.util.Random(System.nanoTime() ^ System.currentTimeMillis() * 6364136223846793005L);
        r.nextBytes(b);
        return b;
    }
}

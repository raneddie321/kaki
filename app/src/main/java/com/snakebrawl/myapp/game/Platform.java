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
    OnlineLink online();

    /**
     * Opens the system share sheet (or copies the text) so the player can send it to friends.
     * Returns false when this platform can't share.
     */
    default boolean share(String text) {
        return false;
    }
}
